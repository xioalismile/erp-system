-- ============================================================
-- 报表联动改造：MRP分析→生产/委外→销售订单回写
-- 1. mrp_plan_item 增加销售订单明细关联字段
-- 2. sale_order_item 增加"已转生产数量"和"已转委外数量"字段
-- 3. material 增加"供应类型"字段（区分自制/委外/外购）
-- 4. material 增加"默认供应商"字段（用于销售订单下推采购订单）
-- ============================================================

-- 1. MRP 计划明细：增加销售订单关联字段
ALTER TABLE mrp_plan_item ADD COLUMN IF NOT EXISTS sale_order_id BIGINT;
ALTER TABLE mrp_plan_item ADD COLUMN IF NOT EXISTS sale_order_item_id BIGINT;
ALTER TABLE mrp_plan_item ADD COLUMN IF NOT EXISTS sale_order_no VARCHAR(64);
COMMENT ON COLUMN mrp_plan_item.sale_order_id IS '来源销售订单ID';
COMMENT ON COLUMN mrp_plan_item.sale_order_item_id IS '来源销售订单明细ID';
COMMENT ON COLUMN mrp_plan_item.sale_order_no IS '来源销售订单号';

-- 2. 销售订单明细：增加已转生产数量、已转委外数量字段
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS turned_produce_quantity DECIMAL(18,4) DEFAULT 0;
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS turned_subcontract_quantity DECIMAL(18,4) DEFAULT 0;
COMMENT ON COLUMN sale_order_item.turned_produce_quantity IS '已转生产数量（MRP转生产工单回写）';
COMMENT ON COLUMN sale_order_item.turned_subcontract_quantity IS '已转委外数量（MRP转委外订单回写）';

-- 初始化已有记录的回写字段为0
UPDATE sale_order_item SET turned_produce_quantity = 0 WHERE turned_produce_quantity IS NULL;
UPDATE sale_order_item SET turned_subcontract_quantity = 0 WHERE turned_subcontract_quantity IS NULL;

-- 3. 物料档案：增加供应类型字段（用于MRP区分自制/委外/外购）
-- supply_type: SELF_MADE=自制, SUBCONTRACT=委外, PURCHASE=外购
ALTER TABLE material ADD COLUMN IF NOT EXISTS supply_type VARCHAR(20) DEFAULT 'PURCHASE';
COMMENT ON COLUMN material.supply_type IS '供应类型：SELF_MADE自制/SUBCONTRACT委外/PURCHASE外购';

-- 4. 物料档案：增加默认供应商字段（用于销售订单下推采购订单）
ALTER TABLE material ADD COLUMN IF NOT EXISTS default_supplier_id BIGINT;
ALTER TABLE material ADD COLUMN IF NOT EXISTS default_supplier_name VARCHAR(100);
COMMENT ON COLUMN material.default_supplier_id IS '默认供应商ID';
COMMENT ON COLUMN material.default_supplier_name IS '默认供应商名称';

-- 5. 索引（加速按销售订单明细反查）
CREATE INDEX IF NOT EXISTS idx_mrp_plan_item_sale_order_item ON mrp_plan_item(sale_order_id, sale_order_item_id);
CREATE INDEX IF NOT EXISTS idx_material_supply_type ON material(supply_type);
CREATE INDEX IF NOT EXISTS idx_material_default_supplier ON material(default_supplier_id);

-- 6. 委外订单明细：增加来源销售订单关联字段（用于委外回写）
ALTER TABLE subcontract_order_item ADD COLUMN IF NOT EXISTS source_sale_order_id BIGINT;
ALTER TABLE subcontract_order_item ADD COLUMN IF NOT EXISTS source_sale_order_item_id BIGINT;
COMMENT ON COLUMN subcontract_order_item.source_sale_order_id IS '来源销售订单ID（用于回写销售订单已转委外数量）';
COMMENT ON COLUMN subcontract_order_item.source_sale_order_item_id IS '来源销售订单明细ID（用于回写销售订单已转委外数量）';
CREATE INDEX IF NOT EXISTS idx_subcontract_item_sale_order ON subcontract_order_item(source_sale_order_id, source_sale_order_item_id);

-- 7. 生产工单：增加来源销售订单明细ID（用于回写销售订单已转生产数量）
-- production_order 已有 source_type / source_no / sales_order_no 字段，但缺销售订单明细ID
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS source_sale_order_id BIGINT;
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS source_sale_order_item_id BIGINT;
COMMENT ON COLUMN production_order.source_sale_order_id IS '来源销售订单ID（用于回写销售订单已转生产数量）';
COMMENT ON COLUMN production_order.source_sale_order_item_id IS '来源销售订单明细ID（用于回写销售订单已转生产数量）';
CREATE INDEX IF NOT EXISTS idx_production_order_sale_order ON production_order(source_sale_order_id, source_sale_order_item_id);
