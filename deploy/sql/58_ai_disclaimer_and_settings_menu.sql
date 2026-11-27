-- ============================================================
-- 58_ai_disclaimer_and_settings_menu.sql
-- 用途：设置中心「AI」分组 4 个新入口 菜单初始化 + 角色自动授权（守则2.7.1-7 / 2.8-6）
-- 背景：设置中心新增第 5 个一级分组「AI」（前端 SettingsCenter.vue），其中 4 项为新入口：
--       AI 设置总览 / AI 助手设置 / 协议与免责声明 / AI 工具权限。
--       若 sys_menu 不插入这 4 行，canAccessItem() 会因 menuRoutePaths 里没有对应
--       route_path 而把子项过滤掉 → 非管理员用户看不到入口（守则2.8 第 5/6/7 层）。
--       （另 3 项 ai-data-policy / ai-token-usage / ai-knowledge 的菜单已存在，item key 不变，
--        仅从「系统」组迁到「AI」组，权限不受影响，无需重复插入。）
-- 幂等：所有 INSERT 用 NOT EXISTS 去重，重复执行 0 副作用。
-- 执行：psql -h 127.0.0.1 -U erp_user -d erp_db -f 58_ai_disclaimer_and_settings_menu.sql
--       （首次部署时由 SchemaUpgradeEnsurer.ensureMenusExist() 自动执行相同逻辑，
--        本SQL文件是独立部署/手动修复的备用入口）
-- ============================================================

BEGIN;

-- ---------- 1. 菜单：AI 分组 4 个子项（挂在设置中心下，route_path 带 ?item= 参数定位）----------
-- 注意：parent_menu_id 取 menu_code='system_settings_center' 的 menu_id，
--       与实际库中设置中心子项（如 basic-info）同一父级；permission_code 留空（沿用新口径）。

INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status)
SELECT 'system_ai_overview', 'AI 设置总览',
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'system_settings_center'),
       'MENU', '/system/settings?item=ai-overview', NULL,
       30, 'MagicStick', 1, 1, 0, 1, '启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_ai_overview');

INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status)
SELECT 'system_ai_assistant_settings', 'AI 助手设置',
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'system_settings_center'),
       'MENU', '/system/settings?item=ai-assistant-settings', NULL,
       31, 'Setting', 1, 1, 0, 1, '启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_ai_assistant_settings');

INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status)
SELECT 'system_ai_disclaimer', '协议与免责声明',
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'system_settings_center'),
       'MENU', '/system/settings?item=ai-disclaimer', NULL,
       32, 'Document', 1, 1, 0, 1, '启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_ai_disclaimer');

INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status)
SELECT 'system_ai_tool_permission', 'AI 工具权限',
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'system_settings_center'),
       'MENU', '/system/settings?item=ai-tool-permission', NULL,
       33, 'Key', 1, 1, 0, 1, '启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_ai_tool_permission');

-- ---------- 2. 角色权限：超级管理员(ADMIN) + 平台超级管理员(SUPER_ADMIN) 自动授权 ----------
-- 用 role_code 动态匹配 role_id，不硬编码 ID（避免不同库 ID 不一致）
-- 注意：sys_role_permission.menu_id 字段实际存的是 sys_menu.route_path（VARCHAR），
--       不是 menu_id 数字（样例：menu_id='/report/custom'）；permission_type='菜单'。
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND m.menu_code IN (
    'system_ai_overview',
    'system_ai_assistant_settings',
    'system_ai_disclaimer',
    'system_ai_tool_permission'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
    WHERE p.role_id = r.role_id AND p.menu_id = m.route_path AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（执行后手动核对，预期返回 4 行菜单 + 8 行权限）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, parent_menu_id, route_path, permission_code, sort_order
--   FROM sys_menu
--  WHERE menu_code IN ('system_ai_overview','system_ai_assistant_settings',
--                      'system_ai_disclaimer','system_ai_tool_permission')
--  ORDER BY sort_order;
--
-- SELECT p.role_id, r.role_code, m.menu_code
--   FROM sys_role_permission p
--   JOIN sys_role r ON r.role_id = p.role_id
--   JOIN sys_menu m ON m.menu_id = p.menu_id
--  WHERE m.menu_code IN ('system_ai_overview','system_ai_assistant_settings',
--                        'system_ai_disclaimer','system_ai_tool_permission')
--  ORDER BY p.role_id, m.menu_code;
