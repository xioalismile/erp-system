-- 修复 sale_delivery_item 表缺失字段
ALTER TABLE sale_delivery_item ADD COLUMN IF NOT EXISTS tax_type VARCHAR(10);
ALTER TABLE sale_delivery_item ADD COLUMN IF NOT EXISTS base_unit_price NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sale_delivery_item ADD COLUMN IF NOT EXISTS base_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_delivery_item ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_delivery_item ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0;

-- 授予 erp_user 对 purchase_contract 表的权限
GRANT ALL PRIVILEGES ON TABLE purchase_contract TO erp_user;
GRANT ALL PRIVILEGES ON TABLE purchase_contract_item TO erp_user;
GRANT USAGE, SELECT ON SEQUENCE purchase_contract_contract_id_seq TO erp_user;
GRANT USAGE, SELECT ON SEQUENCE purchase_contract_item_item_id_seq TO erp_user;

SELECT 'Fix2 completed' AS result;
