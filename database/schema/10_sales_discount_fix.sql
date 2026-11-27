-- 销售模块发票表补充折扣字段（其他4个单据明细表已有折扣字段）
-- 10_sales_discount_fix.sql

-- 销售发票主表补充折扣字段
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS discount_rate NUMERIC(8,4) DEFAULT 0.0000;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,2) DEFAULT 0.00;

-- 销售发票明细表补充折扣字段
ALTER TABLE sale_invoice_item ADD COLUMN IF NOT EXISTS discount_rate NUMERIC(8,4) DEFAULT 0.0000;
ALTER TABLE sale_invoice_item ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,2) DEFAULT 0.00;

-- 注: sale_quote/sale_order/sale_delivery/sale_return 的主表和明细表已包含折扣字段，无需修改
