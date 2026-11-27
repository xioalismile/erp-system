-- ===========================================================================
-- 采购模块明细表字段约束修复脚本
-- 解决: 新实体类与旧表结构不兼容问题, 移除不必要的NOT NULL约束
-- ===========================================================================

-- ===== 采购入库单明细 purchase_receipt_item =====
ALTER TABLE purchase_receipt_item ALTER COLUMN stock_id DROP NOT NULL;
ALTER TABLE purchase_receipt_item ALTER COLUMN material_code DROP NOT NULL;
ALTER TABLE purchase_receipt_item ALTER COLUMN material_name DROP NOT NULL;

-- ===== 采购退货单明细 purchase_return_item =====
ALTER TABLE purchase_return_item ALTER COLUMN stock_id DROP NOT NULL;
ALTER TABLE purchase_return_item ALTER COLUMN material_code DROP NOT NULL;
ALTER TABLE purchase_return_item ALTER COLUMN material_name DROP NOT NULL;

-- ===== 采购订单明细 purchase_order_item =====
ALTER TABLE purchase_order_item ALTER COLUMN material_code DROP NOT NULL;
ALTER TABLE purchase_order_item ALTER COLUMN material_name DROP NOT NULL;

-- ===== 采购发票明细 purchase_invoice_item =====
ALTER TABLE purchase_invoice_item ALTER COLUMN material_code DROP NOT NULL;
ALTER TABLE purchase_invoice_item ALTER COLUMN material_name DROP NOT NULL;
