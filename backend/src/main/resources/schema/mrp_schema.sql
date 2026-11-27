-- =====================================================
-- MRP物料需求计划模块 数据库建表脚本
-- 数据库: PostgreSQL 15+
-- 说明: MRP计划表、MRP计划明细表
-- =====================================================

-- ============================================================================
-- MRP计划表
-- ============================================================================
CREATE TABLE IF NOT EXISTS mrp_plan (
    id BIGSERIAL PRIMARY KEY,
    plan_no VARCHAR(50) NOT NULL UNIQUE,
    plan_name VARCHAR(200) NOT NULL,
    plan_date DATE NOT NULL DEFAULT CURRENT_DATE,
    demand_source VARCHAR(20) NOT NULL DEFAULT 'SALE_ORDER',
    demand_order_ids TEXT,
    plan_status INT NOT NULL DEFAULT 0,
    total_net_demand NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    total_purchase_suggestion NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    total_produce_suggestion NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    remark VARCHAR(500),
    create_by VARCHAR(50),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP
);
COMMENT ON TABLE mrp_plan IS 'MRP计划表';
COMMENT ON COLUMN mrp_plan.plan_no IS '计划编号';
COMMENT ON COLUMN mrp_plan.plan_name IS '计划名称';
COMMENT ON COLUMN mrp_plan.plan_date IS '计划日期';
COMMENT ON COLUMN mrp_plan.demand_source IS '需求来源: SALE_ORDER销售订单/FORECAST预测/MANUAL手动';
COMMENT ON COLUMN mrp_plan.demand_order_ids IS '需求订单ID，JSON数组';
COMMENT ON COLUMN mrp_plan.plan_status IS '计划状态: 0草稿 1已运算 2已确认 3已执行';
COMMENT ON COLUMN mrp_plan.total_net_demand IS '总净需求';
COMMENT ON COLUMN mrp_plan.total_purchase_suggestion IS '采购建议数量';
COMMENT ON COLUMN mrp_plan.total_produce_suggestion IS '生产建议数量';
COMMENT ON COLUMN mrp_plan.remark IS '备注';
COMMENT ON COLUMN mrp_plan.create_by IS '创建人';
COMMENT ON COLUMN mrp_plan.create_time IS '创建时间';
COMMENT ON COLUMN mrp_plan.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_mrp_plan_plan_no ON mrp_plan(plan_no);
CREATE INDEX IF NOT EXISTS idx_mrp_plan_plan_status ON mrp_plan(plan_status);
CREATE INDEX IF NOT EXISTS idx_mrp_plan_plan_date ON mrp_plan(plan_date);

-- ============================================================================
-- MRP计划明细表
-- ============================================================================
CREATE TABLE IF NOT EXISTS mrp_plan_item (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    specification VARCHAR(200),
    unit VARCHAR(20),
    gross_demand NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    available_stock NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    safety_stock NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    net_demand NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    plan_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    suggest_type VARCHAR(20),
    lead_time INT NOT NULL DEFAULT 0,
    suggest_date DATE,
    related_bom_id BIGINT,
    level INT NOT NULL DEFAULT 0,
    parent_id BIGINT,
    remark VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP
);
COMMENT ON TABLE mrp_plan_item IS 'MRP计划明细表';
COMMENT ON COLUMN mrp_plan_item.plan_id IS '计划ID';
COMMENT ON COLUMN mrp_plan_item.material_id IS '商品ID';
COMMENT ON COLUMN mrp_plan_item.material_code IS '商品编码';
COMMENT ON COLUMN mrp_plan_item.material_name IS '商品名称';
COMMENT ON COLUMN mrp_plan_item.specification IS '规格';
COMMENT ON COLUMN mrp_plan_item.unit IS '单位';
COMMENT ON COLUMN mrp_plan_item.gross_demand IS '毛需求';
COMMENT ON COLUMN mrp_plan_item.available_stock IS '可用库存';
COMMENT ON COLUMN mrp_plan_item.safety_stock IS '安全库存';
COMMENT ON COLUMN mrp_plan_item.net_demand IS '净需求 = 毛需求 - 可用库存 + 安全库存';
COMMENT ON COLUMN mrp_plan_item.plan_quantity IS '计划数量';
COMMENT ON COLUMN mrp_plan_item.suggest_type IS '建议类型: PURCHASE采购/PRODUCE生产/外购件/自制件';
COMMENT ON COLUMN mrp_plan_item.lead_time IS '提前期天数';
COMMENT ON COLUMN mrp_plan_item.suggest_date IS '建议到货/开工日期';
COMMENT ON COLUMN mrp_plan_item.related_bom_id IS '关联BOM ID';
COMMENT ON COLUMN mrp_plan_item.level IS '层级, 用于BOM展开';
COMMENT ON COLUMN mrp_plan_item.parent_id IS '父项ID, 用于BOM展开';
COMMENT ON COLUMN mrp_plan_item.remark IS '备注';
COMMENT ON COLUMN mrp_plan_item.create_time IS '创建时间';
COMMENT ON COLUMN mrp_plan_item.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_mrp_plan_item_plan_id ON mrp_plan_item(plan_id);
CREATE INDEX IF NOT EXISTS idx_mrp_plan_item_material_id ON mrp_plan_item(material_id);
CREATE INDEX IF NOT EXISTS idx_mrp_plan_item_material_code ON mrp_plan_item(material_code);
CREATE INDEX IF NOT EXISTS idx_mrp_plan_item_suggest_type ON mrp_plan_item(suggest_type);
CREATE INDEX IF NOT EXISTS idx_mrp_plan_item_level ON mrp_plan_item(level);

ALTER TABLE mrp_plan_item ADD CONSTRAINT fk_mrp_plan_item_plan_id
    FOREIGN KEY (plan_id) REFERENCES mrp_plan(id) ON DELETE CASCADE;
