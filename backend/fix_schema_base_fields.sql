-- 修复 purchase_inbound_item 表缺失字段
ALTER TABLE purchase_inbound_item ADD COLUMN IF NOT EXISTS base_unit_price NUMERIC(18,4) DEFAULT 0;
ALTER TABLE purchase_inbound_item ADD COLUMN IF NOT EXISTS base_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_inbound_item ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_inbound_item ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_inbound_item ADD COLUMN IF NOT EXISTS tax_type VARCHAR(10);

-- 修复 purchase_order_item 表缺失字段（如果下推入库用到）
ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS base_unit_price NUMERIC(18,4) DEFAULT 0;
ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS base_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS tax_type VARCHAR(10);

-- 修复 sale_order_item 表缺失字段（统一性）
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS base_unit_price NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS base_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS tax_type VARCHAR(10);

-- 修复 sale_quote_item 表缺失字段
ALTER TABLE sale_quote_item ADD COLUMN IF NOT EXISTS base_unit_price NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sale_quote_item ADD COLUMN IF NOT EXISTS base_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_quote_item ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_quote_item ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_quote_item ADD COLUMN IF NOT EXISTS tax_type VARCHAR(10);

-- 修复 sale_invoice_item 表缺失字段
ALTER TABLE sale_invoice_item ADD COLUMN IF NOT EXISTS base_unit_price NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sale_invoice_item ADD COLUMN IF NOT EXISTS base_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_invoice_item ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_invoice_item ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_invoice_item ADD COLUMN IF NOT EXISTS tax_type VARCHAR(10);

-- 修复 purchase_invoice_item 表缺失字段
ALTER TABLE purchase_invoice_item ADD COLUMN IF NOT EXISTS base_unit_price NUMERIC(18,4) DEFAULT 0;
ALTER TABLE purchase_invoice_item ADD COLUMN IF NOT EXISTS base_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_invoice_item ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_invoice_item ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0;

SELECT 'base_*_price fields added' AS result;
