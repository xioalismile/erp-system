-- ========================================================
-- 17_invoice_mutex_and_red_invoice.sql
-- 销售发票选单互斥规则 + 退货自动生成红字发票
-- ========================================================

-- 1. 销售出库明细表：新增"已开票数量"字段
-- 用于跟踪销售出库明细已开票数量，防止同一出库明细被多次开票
ALTER TABLE sale_delivery_item
  ADD COLUMN IF NOT EXISTS turned_invoice_quantity DECIMAL(18,4) DEFAULT 0;
COMMENT ON COLUMN sale_delivery_item.turned_invoice_quantity IS '已开票数量（被销售发票引用的累计数量）';

-- 2. 销售退货明细表：新增"已开票数量"字段
-- 用于跟踪销售退货明细已开红字发票数量，防止同一退货明细被多次开票
ALTER TABLE sale_return_item
  ADD COLUMN IF NOT EXISTS turned_invoice_quantity DECIMAL(18,4) DEFAULT 0;
COMMENT ON COLUMN sale_return_item.turned_invoice_quantity IS '已开票数量（被红字销售发票引用的累计数量）';

-- 3. 销售退货主表：新增"原发票关联"字段
-- 用于退货单关联原蓝字发票，便于红字发票生成时冲减原发票应收
ALTER TABLE sale_return
  ADD COLUMN IF NOT EXISTS original_invoice_id BIGINT;
ALTER TABLE sale_return
  ADD COLUMN IF NOT EXISTS original_invoice_no VARCHAR(50);
COMMENT ON COLUMN sale_return.original_invoice_id IS '原蓝字发票ID（可选，退货冲减时关联）';
COMMENT ON COLUMN sale_return.original_invoice_no IS '原蓝字发票号（可选，退货冲减时关联）';

-- 4. 销售发票主表：新增"红字发票"和"原发票关联"字段
-- is_red_invoice: 0=蓝字发票（正常）, 1=红字发票（退货冲减）
-- original_invoice_id/no: 红字发票关联的原蓝字发票
ALTER TABLE sale_invoice
  ADD COLUMN IF NOT EXISTS is_red_invoice SMALLINT DEFAULT 0;
ALTER TABLE sale_invoice
  ADD COLUMN IF NOT EXISTS original_invoice_id BIGINT;
ALTER TABLE sale_invoice
  ADD COLUMN IF NOT EXISTS original_invoice_no VARCHAR(50);
COMMENT ON COLUMN sale_invoice.is_red_invoice IS '是否红字发票: 0-蓝字(正常), 1-红字(退货冲减)';
COMMENT ON COLUMN sale_invoice.original_invoice_id IS '原蓝字发票ID（红字发票专用）';
COMMENT ON COLUMN sale_invoice.original_invoice_no IS '原蓝字发票号（红字发票专用）';

-- 5. 索引：按源单查询发票引用关系（用于互斥校验）
CREATE INDEX IF NOT EXISTS idx_sale_invoice_item_source ON sale_invoice_item(source_id, source_item_id);
CREATE INDEX IF NOT EXISTS idx_sale_invoice_red ON sale_invoice(is_red_invoice, original_invoice_id);
CREATE INDEX IF NOT EXISTS idx_sale_return_original_invoice ON sale_return(original_invoice_id);
