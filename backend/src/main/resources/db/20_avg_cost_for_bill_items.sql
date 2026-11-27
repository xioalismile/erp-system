-- =====================================================
-- 20_avg_cost_for_bill_items.sql
-- 为各业务单据明细表新增 avg_cost 字段
-- 用于记录成本核算后的物料平均成本(回填快照)
-- 已生效的单据成本不再修改(由应用层保证)
-- =====================================================

-- 销售模块
ALTER TABLE sale_contract_item    ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN sale_contract_item.avg_cost    IS '成本核算回填的平均成本快照(审核时冻结)';

ALTER TABLE sale_order_item       ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN sale_order_item.avg_cost       IS '成本核算回填的平均成本快照(审核时冻结)';

ALTER TABLE sale_delivery_item    ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN sale_delivery_item.avg_cost    IS '成本核算回填的平均成本快照(审核时冻结)';

-- 采购模块
ALTER TABLE purchase_order_item    ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN purchase_order_item.avg_cost  IS '成本核算回填的平均成本快照(审核时冻结)';

ALTER TABLE purchase_receipt_item  ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN purchase_receipt_item.avg_cost IS '成本核算回填的平均成本快照(审核时冻结)';

-- 库存模块
ALTER TABLE other_receipt_item     ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN other_receipt_item.avg_cost   IS '成本核算回填的平均成本快照(审核时冻结)';

ALTER TABLE other_issue_item       ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN other_issue_item.avg_cost     IS '成本核算回填的平均成本快照(审核时冻结)';

ALTER TABLE stock_transfer_item    ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN stock_transfer_item.avg_cost  IS '成本核算回填的平均成本快照(审核时冻结)';

ALTER TABLE stock_check_item       ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN stock_check_item.avg_cost     IS '成本核算回填的平均成本快照(审核时冻结)';

-- 生产模块
ALTER TABLE material_requisition_item ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN material_requisition_item.avg_cost IS '成本核算回填的平均成本快照(审核时冻结)';

ALTER TABLE production_receipt_item   ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,4) DEFAULT 0.0000;
COMMENT ON COLUMN production_receipt_item.avg_cost   IS '成本核算回填的平均成本快照(审核时冻结)';
