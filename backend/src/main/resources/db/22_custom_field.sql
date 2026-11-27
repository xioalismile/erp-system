-- =====================================================
-- 自定义字段系统建表脚本
-- 设计：EAV 表（source of truth）+ 业务表 ext_data JSONB 列（快照，便于 WHERE 过滤）
-- 支持：text/textarea/number/select/date/datetime/switch/formula 8种字段类型
-- 首批模块：customer / opportunity / material
-- =====================================================

-- 1. 自定义字段定义表（元数据）
CREATE TABLE IF NOT EXISTS sys_custom_field (
    field_id        BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(50)  NOT NULL,                 -- 实体类型：customer/opportunity/material 等
    field_key       VARCHAR(50)  NOT NULL,                 -- 字段标识（英文，如 custom_discount_rate）
    field_label     VARCHAR(100) NOT NULL,                 -- 显示名称
    field_type      VARCHAR(20)  NOT NULL,                 -- text/textarea/number/select/date/datetime/switch/formula
    field_options   TEXT,                                  -- select 类型的选项 JSON：[{"label":"x","value":"y"}]
    default_value   VARCHAR(500),                          -- 默认值
    placeholder     VARCHAR(200),                          -- 输入提示
    precision_val   INT,                                   -- number 类型小数位（2/4/6）
    min_value       NUMERIC(20,4),                         -- number 最小值
    max_value       NUMERIC(20,4),                         -- number 最大值
    formula_expr    TEXT,                                  -- formula 类型的表达式：如 {{qty}} * {{price}} * (1 - {{rate}}/100)
    formula_deps    TEXT,                                  -- formula 依赖字段key列表 JSON：["qty","price","rate"]
    section_name    VARCHAR(50),                           -- 所属分区（表单中显示位置，如"自定义信息"）
    sort_order      INT          NOT NULL DEFAULT 0,       -- 排序
    is_required     SMALLINT     NOT NULL DEFAULT 0,       -- 是否必填：0否 1是
    is_list_show    SMALLINT     NOT NULL DEFAULT 0,       -- 是否在列表显示：0否 1是
    is_search       SMALLINT     NOT NULL DEFAULT 0,       -- 是否同步到 ext_data 便于 WHERE 过滤：0否 1是
    is_enabled      SMALLINT     NOT NULL DEFAULT 1,       -- 是否启用：0否 1是
    remark          VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    created_by      VARCHAR(50),
    updated_by      VARCHAR(50)
);

-- 唯一约束：同一实体类型下字段标识唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_custom_field_entity_key
    ON sys_custom_field (entity_type, field_key);

-- 索引：按实体类型查询
CREATE INDEX IF NOT EXISTS idx_custom_field_entity
    ON sys_custom_field (entity_type, is_enabled, sort_order);

COMMENT ON TABLE sys_custom_field IS '自定义字段定义表（元数据）';
COMMENT ON COLUMN sys_custom_field.entity_type IS '实体类型：customer/opportunity/material 等';
COMMENT ON COLUMN sys_custom_field.field_key IS '字段标识（英文，唯一）';
COMMENT ON COLUMN sys_custom_field.field_type IS '字段类型：text/textarea/number/select/date/datetime/switch/formula';
COMMENT ON COLUMN sys_custom_field.formula_expr IS '公式表达式，支持 {{field}} 变量和 + - * / () 运算';
COMMENT ON COLUMN sys_custom_field.is_search IS '是否同步到 ext_data JSONB 列便于 WHERE 过滤';

-- 2. 自定义字段值表（EAV 模式，source of truth）
CREATE TABLE IF NOT EXISTS sys_custom_field_value (
    value_id        BIGSERIAL PRIMARY KEY,
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       BIGINT       NOT NULL,
    field_key       VARCHAR(50)  NOT NULL,
    field_value     TEXT,                                  -- 统一存文本，读取时按 field_type 转换
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    created_by      VARCHAR(50),
    updated_by      VARCHAR(50)
);

-- 唯一约束：同一实体同一字段只有一条值记录（upsert）
CREATE UNIQUE INDEX IF NOT EXISTS uk_custom_field_value
    ON sys_custom_field_value (entity_type, entity_id, field_key);

-- 索引：按实体查询所有字段值
CREATE INDEX IF NOT EXISTS idx_cfv_entity
    ON sys_custom_field_value (entity_type, entity_id);

COMMENT ON TABLE sys_custom_field_value IS '自定义字段值表（EAV 模式）';
COMMENT ON COLUMN sys_custom_field_value.field_value IS '字段值（文本存储，读取时按字段类型转换）';

-- 3. 业务表增加 ext_data JSONB 列（快照，便于 WHERE 过滤高性能查询）
-- 仅对 is_search=1 的高频字段同步到此列
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='customer' AND column_name='ext_data') THEN
        ALTER TABLE customer ADD COLUMN ext_data JSONB;
        COMMENT ON COLUMN customer.ext_data IS '自定义字段快照（JSONB，仅 is_search=1 的高频字段）';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='crm_opportunity' AND column_name='ext_data') THEN
        ALTER TABLE crm_opportunity ADD COLUMN ext_data JSONB;
        COMMENT ON COLUMN crm_opportunity.ext_data IS '自定义字段快照（JSONB，仅 is_search=1 的高频字段）';
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='material' AND column_name='ext_data') THEN
        ALTER TABLE material ADD COLUMN ext_data JSONB;
        COMMENT ON COLUMN material.ext_data IS '自定义字段快照（JSONB，仅 is_search=1 的高频字段）';
    END IF;
END $$;

-- 4. 预置示例自定义字段（演示用，可删除）
-- 客户：自定义"企业规模"下拉字段
INSERT INTO sys_custom_field (entity_type, field_key, field_label, field_type, field_options, section_name, sort_order, is_required, is_list_show, is_search, is_enabled, remark, created_by)
VALUES ('customer', 'custom_company_scale', '企业规模', 'select',
        '[{"label":"微型企业","value":"micro"},{"label":"小型企业","value":"small"},{"label":"中型企业","value":"medium"},{"label":"大型企业","value":"large"}]',
        '自定义信息', 1, 0, 1, 1, 1, '示例：客户企业规模分级', 'system')
ON CONFLICT DO NOTHING;

-- 客户：自定义"官网地址"文本字段
INSERT INTO sys_custom_field (entity_type, field_key, field_label, field_type, placeholder, section_name, sort_order, is_required, is_list_show, is_search, is_enabled, remark, created_by)
VALUES ('customer', 'custom_website', '官网地址', 'text', 'https://example.com',
        '自定义信息', 2, 0, 0, 0, 1, '示例：客户官方网站', 'system')
ON CONFLICT DO NOTHING;

-- 商机：自定义"决策人职位"文本字段
INSERT INTO sys_custom_field (entity_type, field_key, field_label, field_type, section_name, sort_order, is_required, is_list_show, is_search, is_enabled, remark, created_by)
VALUES ('opportunity', 'custom_decision_maker_title', '决策人职位', 'text',
        '自定义信息', 1, 0, 1, 0, 1, '示例：商机决策人职位', 'system')
ON CONFLICT DO NOTHING;

-- 物料：自定义"保质期天数"数字字段
INSERT INTO sys_custom_field (entity_type, field_key, field_label, field_type, default_value, precision_val, min_value, section_name, sort_order, is_required, is_list_show, is_search, is_enabled, remark, created_by)
VALUES ('material', 'custom_shelf_life_days', '保质期天数', 'number', '365', 0, 0,
        '自定义信息', 1, 0, 1, 0, 1, '示例：物料保质期（天）', 'system')
ON CONFLICT DO NOTHING;
