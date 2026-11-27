-- ============================================================
-- 139：客户管理「自动化管控」分组 + 经营看板授权补齐（2026-10-09）
-- 说明：全部幂等（IF NOT EXISTS / IS DISTINCT FROM / NOT EXISTS），可重复执行。
-- 配套：SchemaUpgradeEnsurer.ensureCrmAutomationGroupMenu() 启动自愈（新环境不再漏）。
-- 口径：
--   ① 客户管理下新增二级分组「自动化管控」（纯分组 CATALOG、无 route_path，不参与权限判定），
--      把「销售目标 / 销售过程 / 销售预警」移入；「我的驾驶舱」保持客户管理直下。
--   ② 经营看板（/databoard/operation）此前只发给 ADMIN/SUPER_ADMIN/FINANCE_MANAGER，
--      导致能看另外三个看板的角色看不到它。现与另外三个看板口径对齐（凡有财务/销售/仓储看板的角色一并给）。
-- 注意：sys_role_permission.menu_id 存的是 sys_menu.route_path 字符串（VARCHAR），不是数字 menu_id。
-- ============================================================

-- ① 新增分组节点（挂在客户管理下）
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, route_path,
                      permission_code, component_path, sort_order, icon, is_visible, is_enabled,
                      is_cache, keepalive, status)
SELECT 'crm_automation', '自动化管控', c.menu_id, 'CATALOG', NULL,
       'crm:automation', NULL, 2, 'MagicStick', 1, 1, 0, 1, '启用'
  FROM sys_menu c
 WHERE c.menu_code = 'crm'
   AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = 'crm_automation');

-- ② 三项移入分组（幂等：父级已一致则不改）
UPDATE sys_menu SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE menu_code = 'crm_automation'),
                    sort_order = 10, updated_at = NOW()
 WHERE menu_code = 'crm_sales-target'
   AND parent_menu_id IS DISTINCT FROM (SELECT menu_id FROM sys_menu WHERE menu_code = 'crm_automation');

UPDATE sys_menu SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE menu_code = 'crm_automation'),
                    sort_order = 20, updated_at = NOW()
 WHERE menu_code = 'crm_warning'
   AND parent_menu_id IS DISTINCT FROM (SELECT menu_id FROM sys_menu WHERE menu_code = 'crm_automation');

UPDATE sys_menu SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE menu_code = 'crm_automation'),
                    sort_order = 30, updated_at = NOW()
 WHERE menu_code = 'crm_sales_activity'
   AND parent_menu_id IS DISTINCT FROM (SELECT menu_id FROM sys_menu WHERE menu_code = 'crm_automation');

-- ③ 经营看板授权补齐：凡已拥有「财务看板」权限的角色，补发「经营看板」权限
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, created_at)
SELECT p.role_id, '/databoard/operation', '菜单', NOW()
  FROM sys_role_permission p
 WHERE p.menu_id = '/databoard/finance'
   AND p.permission_type = '菜单'
   AND NOT EXISTS (
       SELECT 1 FROM sys_role_permission q
        WHERE q.role_id = p.role_id AND q.menu_id = '/databoard/operation' AND q.permission_type = '菜单'
   );
