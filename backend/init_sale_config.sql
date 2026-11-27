-- 销售设置配置项初始化
-- 若已存在则跳过

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'sale.order.prefix', 'SO', '销售单号前缀', 'sale', 'string', NULL, 1, '销售订单编号前缀', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.order.prefix');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'sale.price.lowest.check', 'true', '最低售价检查', 'sale', 'boolean', NULL, 2, '低于最低售价时是否拦截', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.price.lowest.check');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'sale.price.strategy.enabled', 'true', '价格策略启用', 'sale', 'boolean', NULL, 3, '是否启用销售价格策略匹配', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.price.strategy.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'sale.price.fetch.priority', 'strategy,level,material', '取价优先级', 'sale', 'select', '[{"label":"价格策略优先","value":"strategy,level,material"},{"label":"价格等级优先","value":"level,strategy,material"},{"label":"物料默认价优先","value":"material,strategy,level"}]', 4, '销售取价方式优先级，逗号分隔', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.price.fetch.priority');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'sale.delivery.default.days', '3', '默认交货天数', 'sale', 'number', NULL, 5, '销售单默认交货周期(天)', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.delivery.default.days');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'sale.return.days', '7', '退货期限(天)', 'sale', 'number', NULL, 6, '销售退货有效期限', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.return.days');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'sale.credit.check', 'true', '客户信用检查', 'sale', 'boolean', NULL, 7, '下单前检查客户信用额度', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.credit.check');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'sale.discount.max', '30', '最大折扣比例(%)', 'sale', 'number', NULL, 8, '销售单据最大允许折扣比例', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.discount.max');
