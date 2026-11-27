-- 物流监控看板配置项
-- 注册到 sys_config 表，可由系统设置中心管理
-- 数据库迁移脚本，幂等执行

-- 物流看板自动刷新间隔（秒），默认30秒
-- 受快递100免费接口频率限制（1秒1次），建议不低于10秒
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'logistics.dashboard.refresh_interval', '30', '物流看板刷新间隔(秒)', 'logistics', 'number', NULL, 1,
        '物流看板自动刷新间隔，单位秒。受快递100频率限制建议不低于10秒',
        NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'logistics.dashboard.refresh_interval');

-- 物流看板默认显示数量上限
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'logistics.dashboard.max_items', '50', '物流看板最大显示数', 'logistics', 'number', NULL, 2,
        '物流看板默认加载的最大在途单数量',
        NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'logistics.dashboard.max_items');

-- 物流看板是否默认开启自动刷新
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'logistics.dashboard.auto_refresh', 'true', '看板自动刷新开关', 'logistics', 'boolean', NULL, 3,
        '物流看板默认是否开启自动刷新',
        NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'logistics.dashboard.auto_refresh');
