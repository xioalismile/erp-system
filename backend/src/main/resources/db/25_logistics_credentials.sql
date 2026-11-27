-- 快递100授权配置项（敏感信息）
-- 注册到 sys_config 表，由设置中心「物流配置」页面维护
-- 幂等执行：不存在才插入

-- 快递100授权码（customer，必填，免费实时查询接口需要）
-- config_type='password' 表示前端用密码框显示，明文不返回API
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'logistics.kuaidi100.customer', '', '快递100授权码(customer)', 'logistics', 'password', NULL, 10,
        '在快递100官网注册后获取的授权码customer。注册地址 https://www.kuaidi100.com',
        NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'logistics.kuaidi100.customer');

-- 快递100签名密钥（key，部分高级接口需要，免费查询接口可不填）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'logistics.kuaidi100.key', '', '快递100签名密钥(key)', 'logistics', 'password', NULL, 11,
        '快递100签名密钥key，免费实时查询接口可不填，订阅类高级接口需要',
        NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'logistics.kuaidi100.key');

-- 物流查询功能总开关
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'logistics.kuaidi100.enabled', 'true', '物流查询功能开关', 'logistics', 'boolean', NULL, 12,
        '是否启用快递100物流查询功能，关闭后所有查询接口返回未启用提示',
        NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'logistics.kuaidi100.enabled');
