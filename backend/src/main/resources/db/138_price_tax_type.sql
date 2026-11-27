-- ============================================================
-- 138：单据「计税方式」与含税/不含税价（2026-10-08 · 销售订单试点第一步）
-- 说明：全部幂等（ADD COLUMN IF NOT EXISTS / UPSERT / 定向 UPDATE），可重复执行。
-- 配套：SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 已登记 sale_order_item 两列，启动逐租户库自愈。
-- ============================================================

-- ① 销售订单明细新增「含税价 / 不含税价」两列（按表头「计税方式」由「单价」换算落库）
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sale_order_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sale_order_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

-- ② 设置中心「默认计税方式」（sys.tax.method）改为三选下拉：不含税价 / 含税价 / 不计税
UPDATE sys_config
   SET config_type    = 'select',
       config_options = '[{"value":"不含税价","label":"不含税价"},{"value":"含税价","label":"含税价"},{"value":"不计税","label":"不计税"}]',
       config_name    = '默认计税方式',
       config_group   = 'sys_param',
       config_section = '基础设置',
       remark         = '含税价/不含税价/不计税（单据表头「计税方式」默认值）',
       updated_at     = NOW()
 WHERE config_key = 'sys.tax.method';

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'sys.tax.method', '不含税价', '默认计税方式', 'sys_param', 'select',
       '[{"value":"不含税价","label":"不含税价"},{"value":"含税价","label":"含税价"},{"value":"不计税","label":"不计税"}]',
       5, '含税价/不含税价/不计税（单据表头「计税方式」默认值）', NOW(), NOW(), 'system', 'system'
 WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.tax.method');

-- ③ 存量归一：销售订单表头「计税方式」历史值（价内税/价外税/空）→ 含税价/不含税价
UPDATE sale_order SET tax_calc_method = '含税价'   WHERE tax_calc_method = '价内税';
UPDATE sale_order SET tax_calc_method = '不含税价' WHERE tax_calc_method = '价外税' OR tax_calc_method IS NULL OR tax_calc_method = '';

-- ④ 存量回填：已有明细按「不含税价」口径回填两列（只填空值，幂等）
UPDATE sale_order_item
   SET price_without_tax = unit_price,
       price_with_tax    = ROUND(unit_price * (1 + COALESCE(tax_rate, 0) / 100), 6)
 WHERE price_without_tax IS NULL OR price_with_tax IS NULL;
