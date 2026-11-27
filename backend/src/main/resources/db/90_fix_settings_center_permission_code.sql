-- ============================================================
-- 90_fix_settings_center_permission_code.sql
-- 用途：修复「设置中心菜单权限码丢失」→ 非管理员账号的小助手看不到设置中心类工具
-- 背景（2026-09-18 用户报障实锤）：
--   设置中心菜单由 deploy/sql/56_ai_token_menu.sql 初始化，该 INSERT 当时未写 permission_code，
--   库里这条码一直是 NULL；而 AI 小助手判权限读的正是它（ToolRegistry.SETTING_PERM_VIEW
--   = 'system:settings:view'）→ 除超管/管理员外，41 个 setting_* 工具（系统参数/自定义字段/
--   打印模板/审批设置/价格策略/编号规则…）全部被静默隐藏。
--   实测：李工 lyc123（非管理员）AI 可用工具 258 → 215，且日志里 "setting_config_list" 出现 0 次。
-- 幂等：只补空值，重复执行 0 副作用（守则 2.7.1-2）。
-- 执行：psql -h 127.0.0.1 -U erp_user -d erp_db -f 90_fix_settings_center_permission_code.sql
--       （多租户：每个租户库都要执行；应用启动时 SchemaUpgradeEnsurer 也会对当前库自动补一次）
-- ============================================================

BEGIN;

UPDATE sys_menu
   SET permission_code = 'system:settings:view',
       updated_at      = NOW()
 WHERE route_path = '/system/settings'
   AND (permission_code IS NULL OR permission_code = '');

COMMIT;

-- ============================================================
-- 验证 SQL（执行后核对：permission_code 必须非空）
-- ============================================================
-- SELECT menu_id, menu_code, route_path, permission_code
--   FROM sys_menu WHERE route_path = '/system/settings';
--
-- 验证角色侧是否已能取到该码（以李工 role_id=4 为例，预期返回 1 行 system:settings:view）：
-- SELECT DISTINCT m.permission_code
--   FROM sys_role_permission p
--   JOIN sys_menu m ON m.route_path = p.menu_id
--  WHERE p.role_id = 4 AND p.permission_type = '菜单'
--    AND m.permission_code LIKE 'system:%';
