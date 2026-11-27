-- ============================================================
-- 49_data_board_menus.sql
-- 数据看板模块菜单（财务看板/销售看板/仓储看板）
-- 规则：
--   1. 幂等：按 route_path 判存在，可重复执行
--   2. menu_id 走序列（禁止硬编码，跨库 ID 体系不同）
--   3. 序列对齐必须在插入前（防显式ID插入后序列落后撞主键）
--   4. version=NULL（STANDARD/CRM/ULTIMATE 全版本可见；
--      财务看板含净利润等敏感指标，菜单级仅授权管理层+财务+管理员）
--   5. 授权角色：ADMIN/FINANCE/FINANCE_MANAGER/SALES_MANAGER/
--      WAREHOUSE_MANAGER/PURCHASE_MANAGER/PRODUCTION_MANAGER
--      （SUPER_ADMIN 走 '*' 通配权限，无需单条授权）
-- 执行后无需重启后端，但用户需重新登录（menuRoutePaths 登录时缓存）
-- ============================================================

-- 1) 序列对齐（防显式ID插入后序列落后撞主键）
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

-- 2) 父菜单：数据看板 CATALOG（sort_order=75，排在财务管理70与报表中心80之间）
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version)
SELECT 'databoard', '数据看板', NULL, 'CATALOG', 'DataAnalysis', '/databoard', 'Layout', 1, 75, NULL, '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/databoard');

-- 3) 子菜单：财务/销售/仓储看板
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version)
SELECT 'databoard_finance', '财务看板',
       (SELECT menu_id FROM sys_menu WHERE route_path = '/databoard'),
       'MENU', NULL, '/databoard/finance', 'views/databoard/FinanceBoard', 1, 1, 'databoard:finance', '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/databoard/finance');

INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version)
SELECT 'databoard_sale', '销售看板',
       (SELECT menu_id FROM sys_menu WHERE route_path = '/databoard'),
       'MENU', NULL, '/databoard/sale', 'views/databoard/SaleBoard', 1, 2, 'databoard:sale', '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/databoard/sale');

INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version)
SELECT 'databoard_inventory', '仓储看板',
       (SELECT menu_id FROM sys_menu WHERE route_path = '/databoard'),
       'MENU', NULL, '/databoard/inventory', 'views/databoard/InventoryBoard', 1, 3, 'databoard:inventory', '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/databoard/inventory');

-- 4) 兜底修复孤儿父子关系（父菜单插入失败重跑场景）
UPDATE sys_menu SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE route_path = '/databoard')
WHERE route_path IN ('/databoard/finance', '/databoard/sale', '/databoard/inventory')
  AND parent_menu_id IS NULL;

-- 5) 授权：管理层+财务+管理员角色可见数据看板菜单
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at)
SELECT r.role_id, m.route_path, '菜单', 1, CURRENT_TIMESTAMP
FROM sys_role r
JOIN sys_menu m ON m.route_path IN ('/databoard', '/databoard/finance', '/databoard/sale', '/databoard/inventory')
WHERE r.role_code IN ('ADMIN', 'FINANCE', 'FINANCE_MANAGER', 'SALES_MANAGER',
                      'WAREHOUSE_MANAGER', 'PURCHASE_MANAGER', 'PRODUCTION_MANAGER')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
    WHERE p.role_id = r.role_id AND p.menu_id = m.route_path
);
