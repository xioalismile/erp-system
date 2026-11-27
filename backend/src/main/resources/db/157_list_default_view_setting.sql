-- ============================================================
-- 157_list_default_view_setting.sql
-- 设置中心 → 系统参数 → 基础设置 新增「列表默认视图」（2026-10-14 列表页汇总/明细双视图二期）
-- 取值：汇总（默认，= 升级前行为）/ 明细
-- 口径：全局只作默认值；用户在某个列表页手动切换过视图的，该页仍以个人选择为准。
-- 幂等：缺该 key 才插入（存在则不动管理员已改过的值）。
-- 同内容另存：deploy/sql/155_list_default_view_setting.sql（守则 04 编号）
-- 等价启动自愈：SchemaUpgradeEnsurer.ensureListDefaultViewSetting（主库 + 逐租户库）
-- ============================================================

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'sys.list.default.view', '汇总', '列表默认视图', 'sys_param', '基础设置', 'select',
       '[{"label":"汇总","value":"汇总"},{"label":"明细","value":"明细"}]',
       27, '列表页默认按汇总显示一张单一行；切换为明细后，未单独设置过的列表页默认按明细行显示',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.list.default.view');

-- 验证
SELECT '验证: 列表默认视图设置项' AS info, config_value FROM sys_config WHERE config_key = 'sys.list.default.view';
