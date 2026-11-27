SET client_encoding = 'UTF8';

-- 1. 查询当前成本核算配置 & finance.ar.source_bill_type & 负库存开关
SELECT config_key, config_value FROM sys_config
WHERE config_key IN ('finance.ar.source_bill_type', 'inventory.negative.allowed', 'inventory.low.stock.warn', 'inventory.overstock.warn')
ORDER BY config_key;

SELECT * FROM cost_accounting_config ORDER BY config_id DESC LIMIT 10;

-- 2. 检查是否存在org_code=DEMO_ORG基础数据关联
SELECT 'org' AS obj, org_code, org_name FROM sys_org WHERE org_code='DEMO_ORG' LIMIT 1
UNION ALL
SELECT 'warehouse', stock_id::text, stock_name FROM warehouse WHERE stock_id IN (1)
UNION ALL
SELECT 'customer', customer_id::text, customer_name FROM customer WHERE customer_id IN (2,3,4)
UNION ALL
SELECT 'employee', employee_id::text, employee_name FROM employee WHERE employee_id IN (1,2,3,4,5)
UNION ALL
SELECT 'material', material_id::text, material_name FROM material WHERE material_id IN (SELECT material_id FROM sale_delivery_item WHERE delivery_id IN (21,22,23,24,25) LIMIT 5)
UNION ALL
SELECT 'currency', currency_id::text, currency_code FROM currency WHERE currency_id=1
LIMIT 50;
