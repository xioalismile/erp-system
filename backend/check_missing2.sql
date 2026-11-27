SET client_encoding = 'UTF8';
-- 检查 finance.ar.source_bill_type 配置是否已存在（不存在时approve调用createArFromSaleDelivery用了getOne的默认sale_order，应该问题不大）
SELECT config_key, config_value FROM sys_config WHERE config_key IN ('finance.ar.source_bill_type','inventory.negative.allowed') ORDER BY 1;
-- 基础数据：检查关联的组织、仓库、客户、员工、币种、物料是否存在
SELECT 'org:'||org_code AS k, org_name AS v FROM sys_org WHERE org_code='DEMO_ORG' LIMIT 1;
SELECT 'wh:'||stock_id AS k, stock_name AS v FROM warehouse WHERE stock_id=1;
SELECT 'cust:'||customer_id AS k, customer_name AS v FROM customer WHERE customer_id IN (2,3,4);
SELECT 'emp:'||employee_id AS k, employee_name AS v FROM employee WHERE employee_id IN (1,2,3,4,5);
SELECT 'cur:'||currency_id AS k, currency_code AS v FROM currency WHERE currency_id=1;
SELECT 'mat:'||material_id AS k, material_code||' '||material_name AS v FROM material WHERE material_id IN (SELECT DISTINCT material_id FROM sale_delivery_item WHERE delivery_id IN (21,22,23,24,25)) LIMIT 20;
-- 库存是否存在
SELECT 'inv' AS k, 'stock_id='||stock_id||' mat_id='||material_id||' qty='||quantity AS v FROM inventory WHERE stock_id=1 AND material_id IN (SELECT DISTINCT material_id FROM sale_delivery_item WHERE delivery_id IN (21,22,23,24,25)) LIMIT 20;
