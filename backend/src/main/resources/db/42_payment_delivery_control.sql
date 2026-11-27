-- ============================================================
-- 42_payment_delivery_control.sql
-- 收款发货管控 + 客户付款分类 + 审批流程条件路由
-- ============================================================

-- 1. 销售订单新增已收款金额字段
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS received_amount DECIMAL(18,2) DEFAULT 0;
COMMENT ON COLUMN sale_order.received_amount IS '已收款金额（预收款+正常收款累计）';

-- 2. 客户档案新增付款管控分类字段
ALTER TABLE customer ADD COLUMN IF NOT EXISTS payment_category VARCHAR(50);
COMMENT ON COLUMN customer.payment_category IS '付款管控分类：现金客户/月结客户/账期客户/招投标客户/特批客户';

-- 3. 审批流程定义新增条件路由字段
ALTER TABLE approval_flow ADD COLUMN IF NOT EXISTS condition_expr TEXT;
ALTER TABLE approval_flow ADD COLUMN IF NOT EXISTS priority INT DEFAULT 99;
COMMENT ON COLUMN approval_flow.condition_expr IS '触发条件JSON，如 {"payment_category":"月结客户","min_amount":50000}';
COMMENT ON COLUMN approval_flow.priority IS '流程优先级（数字越小越高，同billType多流程时按优先级匹配）';

-- 4. 系统配置：发货收款管控参数（归财务参数组，财务管控规则）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, remark, sort_order, created_by)
SELECT 'sale.delivery.payment.control', 'none', '发货收款管控模式', 'finance', 'select',
       'none=不管控; advance=预付款达标才能发货; full=全额收款才能发货', 10, 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.delivery.payment.control');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, remark, sort_order, created_by)
SELECT 'sale.delivery.payment.control.scope', 'all', '发货收款管控范围', 'finance', 'select',
       'all=所有客户; by_customer=按客户付款分类区分', 11, 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.delivery.payment.control.scope');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, remark, sort_order, created_by)
SELECT 'sale.delivery.payment.advance.tolerance', '0', '预付款容差金额', 'finance', 'number',
       '允许的差额，如差100元以内也放行', 12, 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.delivery.payment.advance.tolerance');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, remark, sort_order, created_by)
SELECT 'sale.delivery.payment.check.bypass.approved', 'false', '审批通过后跳过收款校验', 'finance', 'boolean',
       'true=审批通过的发货单跳过收款校验（总经理特批放行）', 13, 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.delivery.payment.check.bypass.approved');

-- 5. 系统配置：超期账款阻断
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, remark, sort_order, created_by)
SELECT 'sale.credit.overdue.block', 'false', '超期账款阻断新订单', 'sale', 'boolean',
       'true=客户有超期未收款时禁止录入新销售订单', 14, 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.credit.overdue.block');

-- 6. 预置付款条件数据（月结/账期/招投标）
INSERT INTO payment_term (payment_term_code, payment_term_name, payment_days, discount_days, discount_rate, advance_rate, status, is_enabled, created_by)
SELECT 'MT30', '月结30天', 30, 0, 0, 0, '启用', 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM payment_term WHERE payment_term_code = 'MT30');

INSERT INTO payment_term (payment_term_code, payment_term_name, payment_days, discount_days, discount_rate, advance_rate, status, is_enabled, created_by)
SELECT 'MT60', '月结60天', 60, 0, 0, 0, '启用', 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM payment_term WHERE payment_term_code = 'MT60');

INSERT INTO payment_term (payment_term_code, payment_term_name, payment_days, discount_days, discount_rate, advance_rate, status, is_enabled, created_by)
SELECT 'MT90', '月结90天', 90, 0, 0, 0, '启用', 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM payment_term WHERE payment_term_code = 'MT90');

INSERT INTO payment_term (payment_term_code, payment_term_name, payment_days, discount_days, discount_rate, advance_rate, status, is_enabled, created_by)
SELECT 'CR90', '账期90天', 90, 0, 0, 0, '启用', 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM payment_term WHERE payment_term_code = 'CR90');

INSERT INTO payment_term (payment_term_code, payment_term_name, payment_days, discount_days, discount_rate, advance_rate, status, is_enabled, created_by)
SELECT 'BID', '招投标项目', 60, 0, 0, 30, '启用', 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM payment_term WHERE payment_term_code = 'BID');

-- 7. 更新已有客户的付款分类为默认值（现金客户），不影响存量数据逻辑
UPDATE customer SET payment_category = '现金客户' WHERE payment_category IS NULL AND status = '启用';

-- 8. 补充 select 类型参数的下拉选项（设置中心渲染用，无条件更新以兼容已执行环境）
UPDATE sys_config SET config_options = '[{"label":"不管控","value":"none"},{"label":"预付款达标才能发货","value":"advance"},{"label":"全额收款才能发货","value":"full"}]'
WHERE config_key = 'sale.delivery.payment.control';

UPDATE sys_config SET config_options = '[{"label":"所有客户","value":"all"},{"label":"按客户付款分类区分","value":"by_customer"}]'
WHERE config_key = 'sale.delivery.payment.control.scope';
