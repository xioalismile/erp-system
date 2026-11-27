-- ============================================================
-- 68_sale_order_detail_report_menu.sql
-- 用途：销售订单明细报表 —— 菜单初始化 + 角色自动授权（守则 2.7.1-7 / 2.8.1-6）
--
-- 背景：销售管理 → 报表 下此前只有「分析报表」「客户对账单」，没有单据明细报表。
--       本 SQL 插入「销售订单明细报表」菜单，否则即使用户有页面路由，
--       menuRoutePaths 里没有该 route_path → 左侧菜单渲染不出来（守则 2.8 第 5/6/7 层）。
--
-- 口径（均已对本地实库核对，非推测）：
--   1. 父级 = 「销售管理」（取自 /sale/report/analysis 的 parent_menu_id；库中并无独立「报表」菜单行，
--      「报表」只是前端 menuConfig 的分组壳）；
--   2. sort_order = 9（分析报表=8、客户对账单=12，插在两者之间，与登记表口径一致）；
--   3. permission_code 沿用 database/schema/99_init_menus.sql 第 106 行早先草拟的 sale:report:order-detail，
--      保证将来若执行该 seed 不会插出第二套编码；
--   4. menu_id 走序列，禁止硬编码（跨库 ID 体系不同）；
--   5. 角色授权对齐同组报表的实际口径：ADMIN / SALES_MANAGER / SALES
--     （本地库 sys_role 只有 ADMIN 与 SUPER_ADMIN 两个管理员角色，无「系统管理员」，
--      故按同组报表「分析报表」的授权角色对齐，不臆造角色码）；
--   6. 注意：sys_role_permission.menu_id 字段实际存的是 sys_menu.route_path（VARCHAR），不是数字 ID。
--
-- 幂等：全部 INSERT 走 NOT EXISTS，重复执行 0 副作用。
-- 执行：psql -h localhost -U erp_user -d erp_db -f 68_sale_order_detail_report_menu.sql
--       （首次部署时 SchemaUpgradeEnsurer.ensureMenusExist() 会执行等价逻辑；
--          本文件是独立部署/手动修复的备用入口）
-- 执行后无需重启后端，但用户需**重新登录**（menuRoutePaths 在登录时缓存）
-- ============================================================

BEGIN;

-- 1) 序列对齐（防历史上显式 ID 插入后序列落后撞主键）
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

-- 2) 菜单：销售订单明细报表
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT 'sale_report_order_detail', '销售订单明细报表',
       (SELECT parent_menu_id FROM sys_menu WHERE route_path = '/sale/report/analysis'),
       'MENU', '/sale/report/sale-order-detail', 'sale:report:order-detail',
       9, 'DataLine', 1, 1, 0, 1, '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'sale_report_order_detail');

-- 3) 兜底修复父子关系（父菜单解析失败重跑场景）
UPDATE sys_menu
   SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE route_path = '/sale/report/analysis' LIMIT 1)
 WHERE menu_code = 'sale_report_order_detail' AND parent_menu_id IS NULL;

-- 4) 角色授权：ADMIN / SALES_MANAGER / SALES（与「分析报表」同口径）
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SALES_MANAGER', 'SALES')
  AND m.menu_code = 'sale_report_order_detail'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id
       AND p.menu_id = m.route_path
       AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（执行后手动核对，预期：1 行菜单 + 3 行权限）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, parent_menu_id, route_path, permission_code, sort_order
--   FROM sys_menu WHERE menu_code = 'sale_report_order_detail';
--
-- SELECT p.role_id, r.role_code, p.menu_id
--   FROM sys_role_permission p JOIN sys_role r ON r.role_id = p.role_id
--  WHERE p.menu_id = '/sale/report/sale-order-detail';
