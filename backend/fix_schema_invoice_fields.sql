-- 修复 purchase_invoice 表缺失字段
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS is_closed INT DEFAULT 0;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS close_status INT DEFAULT 0;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS base_total_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS tax_total_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS tax_category VARCHAR(50);

-- 修复 purchase_invoice 表的 net_amount 字段
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2) DEFAULT 0;

-- 修复 sale_invoice 表（统一性）
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS is_closed INT DEFAULT 0;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS close_status INT DEFAULT 0;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS base_total_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0;

SELECT 'invoice fields added' AS result;
