-- ============================================================
-- 56_ai_token_menu.sql
-- 用途：AI 用量管理 菜单初始化 + 角色自动授权（守则2.7.1-7 / 2.8-6）
-- 背景：后端 AiTokenUsageController + 前端 AiTokenUsage.vue + 路由 早已完成，
--       但 sys_menu 从未插入 → MainLayout 齿轮 canAccessSettings=false
--       → 管理员看不到设置中心入口 → AI用量管理形同虚设（2026-09-05 实锤根因）。
-- 幂等：所有 INSERT 用 NOT EXISTS 去重，重复执行 0 副作用。
-- 执行：psql -h 127.0.0.1 -U erp_user -d erp_db -f 56_ai_token_menu.sql
--       （首次部署时由 SchemaUpgradeEnsurer.ensureMenusExist() 自动执行相同逻辑，
--        本SQL文件是独立部署/手动修复的备用入口）
-- ============================================================

BEGIN;

-- ---------- 1. 菜单：设置中心（顶级，route_path=/system/settings 是齿轮可见性判定条件）----------
-- 【2026-09-18 修正】补上 permission_code='system:settings:view'：
--   原 INSERT 未写该列 → 库里一直是 NULL → AI 小助手按此码判权限时，非管理员账号的
--   41 个 setting_* 工具（系统参数/自定义字段/…）被静默隐藏（用户报障"小助手没有这些工具"）。
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status)
SELECT 'system_settings_center', '设置中心', NULL, 'MENU', '/system/settings', 'system:settings:view',
       6, 'Setting', 1, 1, 0, 1, '启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_settings_center');

-- ---------- 1b. 已部署库补码（幂等：只补空值）----------
UPDATE sys_menu SET permission_code = 'system:settings:view', updated_at = NOW()
 WHERE route_path = '/system/settings' AND (permission_code IS NULL OR permission_code = '');

-- ---------- 2. 菜单：AI用量管理（挂在设置中心下，SettingsCenter 通过 query 参数定位）----------
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status)
SELECT 'system_ai_token_usage', 'AI用量管理',
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'system_settings_center'),
       'MENU', '/system/settings', 'system:ai-token-usage',
       99, 'Key', 1, 1, 0, 1, '启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_ai_token_usage');

-- ---------- 3. 角色权限：超级管理员(ADMIN) + 平台超级管理员(SUPER_ADMIN) 自动授权 ----------
-- 用 role_code 动态匹配 role_id，不硬编码 ID（避免不同库 ID 不一致）
-- 注意：sys_role_permission.menu_id 字段实际存的是 sys_menu.route_path（VARCHAR），
--       不是 menu_id 数字（样例：menu_id='/report/custom'）；permission_type='菜单'。
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND m.menu_code IN ('system_settings_center', 'system_ai_token_usage')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
    WHERE p.role_id = r.role_id AND p.menu_id = m.route_path AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（执行后手动核对，预期返回 2 行菜单 + 4 行权限）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, parent_menu_id, route_path, permission_code
--   FROM sys_menu WHERE menu_code IN ('system_settings_center','system_ai_token_usage')
--   ORDER BY menu_id;
--
-- SELECT p.role_id, r.role_code, m.menu_code
--   FROM sys_role_permission p
--   JOIN sys_role r ON r.role_id = p.role_id
--   JOIN sys_menu m ON m.menu_id = p.menu_id
--  WHERE m.menu_code IN ('system_settings_center','system_ai_token_usage')
--  ORDER BY p.role_id, m.menu_code;
