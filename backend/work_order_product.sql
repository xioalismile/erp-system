-- ======================================================================
-- 工单成品明细表 - 支持一个工单多个成品
-- 兼容 PostgreSQL (与现有 fix_schema*.sql 风格一致)
-- ======================================================================

CREATE TABLE IF NOT EXISTS work_order_product (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    line_no INT DEFAULT 10,
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    specification VARCHAR(200),
    model VARCHAR(100),
    unit_id BIGINT,
    unit_name VARCHAR(50),
    quantity NUMERIC(18,4) DEFAULT 0,
    completed_quantity NUMERIC(18,4) DEFAULT 0,
    reported_quantity NUMERIC(18,4) DEFAULT 0,
    rejected_quantity NUMERIC(18,4) DEFAULT 0,
    source_type VARCHAR(50),
    source_no VARCHAR(50),
    source_item_id BIGINT,
    warehouse_id BIGINT,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_work_order_product_order_id ON work_order_product(order_id);

COMMENT ON TABLE  work_order_product                  IS '工单成品明细';
COMMENT ON COLUMN work_order_product.id               IS '主键ID';
COMMENT ON COLUMN work_order_product.order_id         IS '工单ID';
COMMENT ON COLUMN work_order_product.line_no         IS '行号';
COMMENT ON COLUMN work_order_product.material_id     IS '物料ID';
COMMENT ON COLUMN work_order_product.material_code   IS '物料编码';
COMMENT ON COLUMN work_order_product.material_name   IS '物料名称';
COMMENT ON COLUMN work_order_product.specification   IS '规格';
COMMENT ON COLUMN work_order_product.model           IS '型号';
COMMENT ON COLUMN work_order_product.unit_id         IS '单位ID';
COMMENT ON COLUMN work_order_product.unit_name       IS '单位名称';
COMMENT ON COLUMN work_order_product.quantity        IS '计划数量';
COMMENT ON COLUMN work_order_product.completed_quantity IS '已完工数量';
COMMENT ON COLUMN work_order_product.reported_quantity  IS '已报工合格量';
COMMENT ON COLUMN work_order_product.rejected_quantity  IS '报废数量';
COMMENT ON COLUMN work_order_product.source_type     IS '来源单类型: 销售订单/MRP计划';
COMMENT ON COLUMN work_order_product.source_no       IS '来源单号';
COMMENT ON COLUMN work_order_product.source_item_id  IS '来源单明细ID';
COMMENT ON COLUMN work_order_product.warehouse_id   IS '仓库ID';
COMMENT ON COLUMN work_order_product.remark          IS '备注';
COMMENT ON COLUMN work_order_product.created_at      IS '创建时间';
COMMENT ON COLUMN work_order_product.updated_at      IS '更新时间';
