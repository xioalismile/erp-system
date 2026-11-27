-- ========== CRM-销售-财务联动改造 ==========
-- 1. 销售单据新增 opportunity_id 字段（关联商机）
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS opportunity_id BIGINT;
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS opportunity_no VARCHAR(50);
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS opportunity_id BIGINT;
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS opportunity_no VARCHAR(50);
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS opportunity_id BIGINT;
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS opportunity_no VARCHAR(50);
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS opportunity_id BIGINT;
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS opportunity_no VARCHAR(50);
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS opportunity_id BIGINT;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS opportunity_no VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_sale_order_opportunity ON sale_order(opportunity_id);
CREATE INDEX IF NOT EXISTS idx_sale_quote_opportunity ON sale_quote(opportunity_id);
CREATE INDEX IF NOT EXISTS idx_sale_contract_opportunity ON sale_contract(opportunity_id);

-- 2. 商机表新增成交销售订单字段
ALTER TABLE crm_opportunity ADD COLUMN IF NOT EXISTS sale_order_id BIGINT;
ALTER TABLE crm_opportunity ADD COLUMN IF NOT EXISTS sale_order_no VARCHAR(50);
ALTER TABLE crm_opportunity ADD COLUMN IF NOT EXISTS actual_amount DECIMAL(18,2);

-- 3. 新增应收来源单据类型配置项
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('finance.ar.source_bill_type', 'sale_invoice', '应收账款来源单据', 'finance', 'select', '销售出库单,销售发票', 1, '财务应收账款从哪种销售单据自动生成：销售出库单（发货即应收）或销售发票（开票即应收）', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- 4. 删除CRM三张表（先删依赖，再删主表）
DROP TABLE IF EXISTS crm_payment CASCADE;
DROP TABLE IF EXISTS crm_contract CASCADE;
DROP TABLE IF EXISTS crm_quote CASCADE;
