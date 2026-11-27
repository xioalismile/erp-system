-- 修复 purchase_receipt_item 表缺失字段（实体是 PurchaseInboundItem，映射到 purchase_receipt_item 表）
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS base_unit_price NUMERIC(18,4) DEFAULT 0;
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS base_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS tax_type VARCHAR(10);

SELECT 'purchase_receipt_item fields added' AS result;
