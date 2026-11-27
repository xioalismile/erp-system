-- =====================================================
-- 字段级权限表建表脚本
-- 说明：
--   1. page_key = 单据表名（如 sale_order / sale_delivery /
--      purchase_order / purchase_receipt），与 FieldMetaService、
--      EntityMetadataRegistry 数据源 key、自定义字段 entity_type 同口径
--   2. field_key = 实体属性名 camelCase（如 contactPerson / contactPhone /
--      receiveAddress / deliveryAddress），与前端 useFieldPermission 一致
--   3. 无权限记录 = 默认可见/可编辑（前端 isFieldVisible 默认 true），
--      因此无需初始化种子数据，仅配置需要关闭的字段
-- =====================================================

-- 角色字段权限表
CREATE TABLE IF NOT EXISTS sys_role_field_permission (
    id              BIGSERIAL PRIMARY KEY,
    role_id         BIGINT       NOT NULL,
    page_key        VARCHAR(100) NOT NULL,
    field_key       VARCHAR(100) NOT NULL,
    visible         SMALLINT     NOT NULL DEFAULT 1,
    editable        SMALLINT     NOT NULL DEFAULT 1,
    required        SMALLINT     NOT NULL DEFAULT 0,
    sort_order      INT          NOT NULL DEFAULT 0,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_role_field_permission_role_page
    ON sys_role_field_permission (role_id, page_key);

CREATE INDEX IF NOT EXISTS idx_role_field_permission_page
    ON sys_role_field_permission (page_key);

-- 唯一约束：同一角色同一页面同一字段只能有一条记录
CREATE UNIQUE INDEX IF NOT EXISTS uk_role_field_permission_role_page_field
    ON sys_role_field_permission (role_id, page_key, field_key);

-- 表注释
COMMENT ON TABLE sys_role_field_permission IS '角色字段权限表';
COMMENT ON COLUMN sys_role_field_permission.id IS '主键ID';
COMMENT ON COLUMN sys_role_field_permission.role_id IS '角色ID';
COMMENT ON COLUMN sys_role_field_permission.page_key IS '页面标识（单据表名，如sale_order）';
COMMENT ON COLUMN sys_role_field_permission.field_key IS '字段标识（camelCase，如contactPerson）';
COMMENT ON COLUMN sys_role_field_permission.visible IS '是否可见：0否 1是';
COMMENT ON COLUMN sys_role_field_permission.editable IS '是否可编辑：0否 1是';
COMMENT ON COLUMN sys_role_field_permission.required IS '是否必填：0否 1是';
COMMENT ON COLUMN sys_role_field_permission.sort_order IS '排序';
COMMENT ON COLUMN sys_role_field_permission.create_time IS '创建时间';
COMMENT ON COLUMN sys_role_field_permission.update_time IS '更新时间';
