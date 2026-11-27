-- ============================================================================
-- 测试数据初始化脚本 (PostgreSQL, 幂等可重复执行)
-- 数据库: erp_db
-- 说明: 每条语句独立执行, 遇错误跳过, 不影响整体
-- 主键字段名对照(部分表主键非 id):
--   operation_expense=expense_id, operation_expense_allocation=allocation_id
--   sale_order/purchase_order/production_order=order_id
--   sale_quote=quote_id, sale_delivery=delivery_id, sale_return=return_id
--   sale_invoice/purchase_invoice=invoice_id
--   material_requisition=requisition_id, stock_transfer=transfer_id
--   stock_check=check_id, bom=bom_id, routing=routing_id
--   operation_report=report_id, work_order=work_order_id(待确认)
--   account_payable=ap_id, account_receivable=ar_id
--   crm_lead=lead_id, crm_opportunity=opportunity_id, crm_follow_up=follow_up_id
--   crm_survey=survey_id, material=material_id, customer=customer_id
--   supplier=supplier_id, warehouse=stock_id, department=dept_id
--   employee=employee_id, sys_user=user_id, sys_role=role_id
-- ============================================================================

-- 关闭外键约束检查(临时)
SET session_replication_role = 'replica';

-- ============================================================================
-- 1. 清理历史测试数据 (FT 前缀 + test_user 残留)
--    按业务编号列删除, 避免主键字段名差异
-- ============================================================================

-- 财务模块
DELETE FROM cost_calculation_item WHERE calc_id IN (SELECT id FROM cost_calculation WHERE calc_no LIKE 'FT%' OR calc_no LIKE 'CC-FT%');
DELETE FROM operation_expense_allocation WHERE expense_id IN (SELECT expense_id FROM operation_expense WHERE expense_no LIKE 'FT%');
DELETE FROM operation_expense WHERE expense_no LIKE 'FT%';
DELETE FROM cost_calculation WHERE calc_no LIKE 'FT%' OR calc_no LIKE 'CC-FT%';
DELETE FROM account_payable WHERE ap_no LIKE 'FT%';
DELETE FROM account_receivable WHERE ar_no LIKE 'FT%';
DELETE FROM receipt WHERE receipt_no LIKE 'FT%';
DELETE FROM payment WHERE payment_no LIKE 'FT%';

-- 销售模块
DELETE FROM sale_order_item WHERE order_id IN (SELECT order_id FROM sale_order WHERE order_no LIKE 'FT%' OR order_no LIKE 'SO-FT%');
DELETE FROM sale_quote_item WHERE quote_id IN (SELECT quote_id FROM sale_quote WHERE quote_no LIKE 'FT%' OR quote_no LIKE 'QUT-FT%');
DELETE FROM sale_delivery_item WHERE delivery_id IN (SELECT delivery_id FROM sale_delivery WHERE delivery_no LIKE 'FT%' OR delivery_no LIKE 'SOUT-FT%');
DELETE FROM sale_return_item WHERE return_id IN (SELECT return_id FROM sale_return WHERE return_no LIKE 'FT%' OR return_no LIKE 'SRT-FT%');
DELETE FROM sale_invoice_item WHERE invoice_id IN (SELECT invoice_id FROM sale_invoice WHERE invoice_no LIKE 'FT%' OR invoice_no LIKE 'SINV-FT%');
DELETE FROM sale_order WHERE order_no LIKE 'FT%' OR order_no LIKE 'SO-FT%';
DELETE FROM sale_quote WHERE quote_no LIKE 'FT%' OR quote_no LIKE 'QUT-FT%';
DELETE FROM sale_delivery WHERE delivery_no LIKE 'FT%' OR delivery_no LIKE 'SOUT-FT%';
DELETE FROM sale_return WHERE return_no LIKE 'FT%' OR return_no LIKE 'SRT-FT%';
DELETE FROM sale_invoice WHERE invoice_no LIKE 'FT%' OR invoice_no LIKE 'SINV-FT%';

-- 采购模块
DELETE FROM purchase_order_item WHERE order_id IN (SELECT order_id FROM purchase_order WHERE order_no LIKE 'FT%' OR order_no LIKE 'PO-FT%');
DELETE FROM purchase_inbound_item WHERE inbound_id IN (SELECT id FROM purchase_inbound WHERE inbound_no LIKE 'FT%' OR inbound_no LIKE 'PIN-FT%');
DELETE FROM purchase_return_item WHERE return_id IN (SELECT return_id FROM purchase_return WHERE return_no LIKE 'FT%');
DELETE FROM purchase_invoice_item WHERE invoice_id IN (SELECT invoice_id FROM purchase_invoice WHERE invoice_no LIKE 'FT%' OR invoice_no LIKE 'PINV-FT%');
DELETE FROM purchase_order WHERE order_no LIKE 'FT%' OR order_no LIKE 'PO-FT%';
DELETE FROM purchase_inbound WHERE inbound_no LIKE 'FT%' OR inbound_no LIKE 'PIN-FT%';
DELETE FROM purchase_return WHERE return_no LIKE 'FT%';
DELETE FROM purchase_invoice WHERE invoice_no LIKE 'FT%' OR invoice_no LIKE 'PINV-FT%';

-- 库存模块
DELETE FROM material_requisition_item WHERE requisition_id IN (SELECT requisition_id FROM material_requisition WHERE requisition_no LIKE 'FT%' OR requisition_no LIKE 'MR-FT%');
DELETE FROM stock_transfer_item WHERE transfer_id IN (SELECT transfer_id FROM stock_transfer WHERE transfer_no LIKE 'FT%' OR transfer_no LIKE 'ST-FT%');
DELETE FROM stock_check_item WHERE check_id IN (SELECT check_id FROM stock_check WHERE check_no LIKE 'FT%');
DELETE FROM material_requisition WHERE requisition_no LIKE 'FT%' OR requisition_no LIKE 'MR-FT%';
DELETE FROM stock_transfer WHERE transfer_no LIKE 'FT%' OR transfer_no LIKE 'ST-FT%';
DELETE FROM stock_check WHERE check_no LIKE 'FT%';
DELETE FROM stock_movement WHERE business_no LIKE 'FT%';

-- 生产模块
DELETE FROM production_order_item WHERE order_id IN (SELECT order_id FROM production_order WHERE order_no LIKE 'FT%');
DELETE FROM work_order_item WHERE work_order_id IN (SELECT id FROM work_order WHERE work_order_no LIKE 'FT%' OR work_order_no LIKE 'WO-FT%');
DELETE FROM operation_report WHERE report_no LIKE 'FT%' OR report_no LIKE 'OR-FT%';
DELETE FROM work_order WHERE work_order_no LIKE 'FT%' OR work_order_no LIKE 'WO-FT%';
DELETE FROM production_order WHERE order_no LIKE 'FT%';
DELETE FROM bom_item WHERE bom_id IN (SELECT bom_id FROM bom WHERE bom_code LIKE 'FT%');
DELETE FROM bom WHERE bom_code LIKE 'FT%';
DELETE FROM routing_operation WHERE routing_id IN (SELECT routing_id FROM routing WHERE routing_code LIKE 'FT%');
DELETE FROM routing WHERE routing_code LIKE 'FT%';

-- CRM 模块
DELETE FROM crm_follow_up WHERE lead_id IN (SELECT lead_id FROM crm_lead WHERE lead_no LIKE 'FT%' OR lead_no LIKE 'L-FT%');
DELETE FROM crm_lead WHERE lead_no LIKE 'FT%' OR lead_no LIKE 'L-FT%';
DELETE FROM crm_opportunity WHERE opportunity_no LIKE 'FT%' OR opportunity_no LIKE 'OPP-FT%';
DELETE FROM crm_survey WHERE survey_no LIKE 'FT%' OR survey_no LIKE 'SUR-FT%';

-- 基础资料
DELETE FROM material WHERE material_code LIKE 'FT%';
DELETE FROM customer WHERE customer_code LIKE 'FT%';
DELETE FROM supplier WHERE supplier_code LIKE 'FT%';
DELETE FROM warehouse WHERE stock_code LIKE 'FT%';
DELETE FROM department WHERE dept_code LIKE 'FT%';
DELETE FROM employee WHERE employee_code LIKE 'FT%';

-- 系统模块
DELETE FROM sys_user_role WHERE user_id IN (SELECT user_id FROM sys_user WHERE username LIKE 'FT%' OR username = 'test_user');
DELETE FROM sys_user WHERE username LIKE 'FT%' OR username = 'test_user';
DELETE FROM sys_role WHERE role_code LIKE 'FT%';
DELETE FROM sys_config WHERE config_key LIKE 'FT.%';
DELETE FROM sys_dict_item WHERE dict_id IN (SELECT id FROM sys_dict WHERE dict_code LIKE 'FT%');
DELETE FROM sys_dict WHERE dict_code LIKE 'FT%';

-- ============================================================================
-- 2. 创建测试用普通用户 test_user (密码复用 admin 的 BCrypt hash = Erp@2026)
-- ============================================================================
INSERT INTO sys_user (user_code, username, password, real_name, status, is_admin, created_at, updated_at)
SELECT 'TEST_USER', 'test_user', password, '测试用户', '启用', 0, NOW(), NOW()
FROM sys_user
WHERE username = 'admin'
AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'test_user');

-- 分配普通角色(若存在)
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, r.role_id
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.username = 'test_user'
  AND r.role_code IN ('viewer', 'operator', '普通用户', '业务员')
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.user_id AND ur.role_id = r.role_id)
LIMIT 1;

-- ============================================================================
-- 3. 确保基础数据存在 (若表为空则插入最小化测试数据)
-- ============================================================================
INSERT INTO currency (currency_code, currency_name, symbol, exchange_rate, status, created_at, updated_at)
SELECT 'CNY', '人民币', '¥', 1.0, '启用', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM currency LIMIT 1);

INSERT INTO settle_method (method_code, method_name, status, created_at, updated_at)
SELECT 'CASH', '现金', '启用', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM settle_method LIMIT 1);

INSERT INTO payment_term (term_code, term_name, days, status, created_at, updated_at)
SELECT 'NET30', '30天付款', 30, '启用', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM payment_term LIMIT 1);

INSERT INTO unit (unit_code, unit_name, status, created_at, updated_at)
SELECT 'PCS', '个', '启用', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM unit LIMIT 1);

INSERT INTO department (dept_code, dept_name, status, created_at, updated_at)
SELECT 'TEST_DEPT', '测试部门', '启用', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM department LIMIT 1);

INSERT INTO warehouse (stock_code, stock_name, status, created_at, updated_at)
SELECT 'WH_TEST', '测试仓库', '启用', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM warehouse LIMIT 1);

INSERT INTO material (material_code, material_name, material_type, status, sale_price, purchase_price, created_at, updated_at)
SELECT 'M_TEST_DEFAULT', '测试默认物料', '成品', '正常', 100.00, 80.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM material LIMIT 1);

INSERT INTO customer (customer_code, customer_name, status, created_at, updated_at)
SELECT 'C_TEST_DEFAULT', '测试默认客户', '启用', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM customer LIMIT 1);

INSERT INTO supplier (supplier_code, supplier_name, status, created_at, updated_at)
SELECT 'S_TEST_DEFAULT', '测试默认供应商', '启用', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM supplier LIMIT 1);

INSERT INTO inventory_current (material_id, stock_id, quantity, available_quantity, frozen_quantity, created_at, updated_at)
SELECT m.material_id, w.stock_id, 10000.00, 10000.00, 0.00, NOW(), NOW()
FROM material m, warehouse w
WHERE m.material_code = 'M_TEST_DEFAULT'
  AND w.stock_code = 'WH_TEST'
  AND NOT EXISTS (SELECT 1 FROM inventory_current i WHERE i.material_id = m.material_id AND i.stock_id = w.stock_id);

-- ============================================================================
-- 4. 关键系统配置
-- ============================================================================
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, remark, created_at, updated_at)
SELECT 'inventory.negative.allowed', 'false', '是否允许负库存', 'inventory', 'boolean', '关闭时库存不足将被拦截', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'inventory.negative.allowed');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, remark, created_at, updated_at)
SELECT 'system.currency.default', 'CNY', '默认币种', 'system', 'string', '系统默认币种编码', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'system.currency.default');

-- 恢复外键约束检查
SET session_replication_role = 'origin';

DO $$ BEGIN
    RAISE NOTICE '测试数据初始化完成: test_user 密码=Erp@2026(复用admin hash), 已清理FT前缀历史数据';
END $$;
