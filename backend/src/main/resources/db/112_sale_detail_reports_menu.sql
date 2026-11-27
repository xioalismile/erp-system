-- ============================================================
-- 69_sale_detail_reports_menu.sql
-- 用途：销售单据明细报表 —— 三个报表的菜单初始化 + 角色自动授权（守则 2.7.1-7 / 2.8.1-6）
--
-- 背景：销售管理 → 报表 下原先只有「分析报表」「客户对账单」。
--   68 号脚本已插入「销售订单明细报表」；本脚本补齐「销售出库明细报表」「销售发票明细报表」，
--   并**顺带再 guard 一次订单报表**（NOT EXISTS 幂等），使本脚本自身即可独立执行，
--   不依赖 68 是否跑过。
--
-- 口径（均已对本地实库核对）：
--   1. 父级 = 「销售管理」：取 /sale/report/analysis 的 parent_menu_id
--      （库中并无独立「报表」菜单行，「报表」只是前端 menuConfig 的分组壳）；
--   2. sort_order = 9 / 10 / 11，紧随「分析报表」(8)，排在「客户对账单」(12) 之前；
--   3. permission_code 用「模块:对象」形式，对象名 = 前端 menuConfig 的 itemKey
--      （sale:report-order-detail / sale:report-delivery-detail / sale:report-invoice-detail），
--      与设置中心「按钮权限」里列出的对象码一致；
--   4. menu_code 前两个沿用 database/schema/99_init_menus.sql 早先草拟的编码口径
--      （sale_report_order_detail / sale_report_delivery_detail），避免将来执行该 seed 插出第二套菜单；
--   5. menu_id 走序列，禁止硬编码（跨库 ID 体系不同）；
--   6. 角色授权对齐同组报表的实际口径：ADMIN / SALES_MANAGER / SALES
--      （本地库 sys_role 只有 ADMIN 与 SUPER_ADMIN，无「系统管理员」，故按同组报表对齐，不臆造角色码）；
--   7. 注意：sys_role_permission.menu_id 字段实际存的是 sys_menu.route_path（VARCHAR），不是数字 ID。
--
-- 幂等：全部 INSERT 走 NOT EXISTS，重复执行 0 副作用。
-- 执行后无需重启后端，但用户需**重新登录**（menuRoutePaths 在登录时缓存）。
-- ============================================================

BEGIN;

-- 1) 序列对齐（防历史上显式 ID 插入后序列落后撞主键）
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

-- 2) 三个菜单（幂等）
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT v.menu_code, v.menu_name,
       (SELECT parent_menu_id FROM sys_menu WHERE route_path = '/sale/report/analysis'),
       'MENU', v.route_path, v.permission_code,
       v.sort_order, 'DataLine', 1, 1, 0, 1, '启用', NULL
FROM (VALUES
    ('sale_report_order_detail',    '销售订单明细报表', '/sale/report/sale-order-detail',    'sale:report-order-detail',    9),
    ('sale_report_delivery_detail', '销售出库明细报表', '/sale/report/sale-delivery-detail', 'sale:report-delivery-detail', 10),
    ('sale_report_invoice_detail',  '销售发票明细报表', '/sale/report/sale-invoice-detail',  'sale:report-invoice-detail',  11)
) AS v(menu_code, menu_name, route_path, permission_code, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = v.menu_code);

-- 3) 兜底修复父子关系（父菜单解析失败重跑场景）
UPDATE sys_menu
   SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE route_path = '/sale/report/analysis' LIMIT 1)
 WHERE menu_code IN ('sale_report_order_detail', 'sale_report_delivery_detail', 'sale_report_invoice_detail')
   AND parent_menu_id IS NULL;

-- 3.5) 对齐订单报表菜单的权限码口径：
--      68 号脚本写的是 sale:report:order-detail，而设置中心「按钮权限」里列出的对象码是按
--      menuConfig 的 itemKey 生成的 sale:report-order-detail —— 两者不一致会让人以为有两个权限对象，
--      故统一成与前端一致的形式（幂等：只改旧值那一行）。
UPDATE sys_menu SET permission_code = 'sale:report-order-detail'
 WHERE menu_code = 'sale_report_order_detail' AND permission_code = 'sale:report:order-detail';

-- 4) 角色授权：ADMIN / SALES_MANAGER / SALES（与「分析报表」同口径）
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SALES_MANAGER', 'SALES')
  AND m.menu_code IN ('sale_report_order_detail', 'sale_report_delivery_detail', 'sale_report_invoice_detail')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id
       AND p.menu_id = m.route_path
       AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（执行后手动核对，预期：3 行菜单 + 9 行权限）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, parent_menu_id, route_path, permission_code, sort_order
--   FROM sys_menu WHERE menu_code LIKE 'sale_report_%detail%' ORDER BY sort_order;
--
-- SELECT p.role_id, r.role_code, p.menu_id
--   FROM sys_role_permission p JOIN sys_role r ON r.role_id = p.role_id
--  WHERE p.menu_id LIKE '/sale/report/sale-%-detail' ORDER BY p.menu_id, p.role_id;
