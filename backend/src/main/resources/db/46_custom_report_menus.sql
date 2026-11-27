-- ============================================================
-- 46_custom_report_menus.sql
-- 自定义报表：报表中心菜单（自定义报表 + 标准台账报表）
-- 规则：
--   1. 幂等：按 route_path 判存在，可重复执行
--   2. menu_id 走序列（禁止硬编码，跨库 ID 体系不同）
--   3. 父菜单按 route_path 动态解析；序列对齐必须在子菜单插入前
--      （防显式ID插入后序列落后撞主键，导致父菜单插入失败、子菜单成孤儿）
--   4. version=NULL（STANDARD/CRM/ULTIMATE 全版本可见，
--      数据级权限由后端 CustomReportSqlBuilder 在 SQL 层强制过滤）
--   5. 授权所有角色（报表中心为全员功能；数据隔离靠数据权限）
-- 执行后无需重启后端，但用户需重新登录（menuRoutePaths 登录时缓存）
-- ============================================================

-- 1) 序列对齐（防显式ID插入后序列落后撞主键）
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

-- 2) 父菜单：报表中心 CATALOG
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version)
SELECT 'report', '报表中心', NULL, 'CATALOG', 'PieChart', '/report', 'Layout', 1, 80, NULL, '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/report');

-- 3) 子菜单：自定义报表
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version)
SELECT 'report_custom', '自定义报表',
       (SELECT menu_id FROM sys_menu WHERE route_path = '/report'),
       'MENU', NULL, '/report/custom', 'views/report/CustomReportList', 1, 1, 'report:custom', '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/report/custom');

-- 4) 标准台账报表菜单已下线（48_drop_standard_report_menus.sql 删除，前后端代码同步移除）

-- 5) 兜底修复孤儿父子关系（父菜单插入失败重跑场景）
UPDATE sys_menu SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE route_path = '/report')
WHERE route_path IN ('/report/custom') AND parent_menu_id IS NULL;

-- 6) 授权：所有角色可见报表中心菜单（含设计器/运行页路由前缀 /report）
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at)
SELECT r.role_id, m.route_path, '菜单', 1, CURRENT_TIMESTAMP
FROM sys_role r
JOIN sys_menu m ON m.route_path IN ('/report', '/report/custom')
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
    WHERE p.role_id = r.role_id AND p.menu_id = m.route_path
);
