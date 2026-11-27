-- ============================================================
-- 70_purchase_detail_reports_menu.sql
-- 用途：采购模块 6 个单据明细报表 —— 菜单初始化 + 角色自动授权（守则 2.7.1-7 / 2.8.1-6）
--
-- 背景：采购管理 → 报表 下原先只有「分析报表」「供应商对账单」。
--   本脚本插入 6 个单据明细报表菜单（申请单/合同/订单/入库单/退货单/发票）。
--
-- 口径（均已对本地实库核对）：
--   1. 父级 = 「采购管理」：取 /purchase/report/analysis 的 parent_menu_id（实库 = 20）；
--   2. 排序：分析报表原为 7、供应商对账单 11、操作手册 12 → 6 个明细报表插在中间用 8~13，
--      并把 供应商对账单/操作手册 依次后移到 14/15，保证菜单顺序 = 明细报表紧跟分析报表；
--   3. permission_code 用「模块:对象」形式，对象名 = 前端 menuConfig 的 itemKey，
--      与设置中心「按钮权限」里列出的对象码一致；
--   4. menu_id 走序列，禁止硬编码；
--   5. 角色授权：ADMIN（超管）+ PURCHASE_MANAGER + PURCHASE（模块应有的角色）
--      + SALES（实库核对：采购菜单历史上已授权给 SALES，加上它避免"原来能看现在看不到"）；
--   6. 注意：sys_role_permission.menu_id 字段实际存的是 sys_menu.route_path（VARCHAR），不是数字 ID。
--
-- 幂等：INSERT 走 NOT EXISTS；UPDATE 目标值为常量，重复执行结果一致。
-- 执行后无需重启后端，但用户需**重新登录**（menuRoutePaths 在登录时缓存）。
-- ============================================================

BEGIN;

-- 1) 序列对齐
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

-- 2) 6 个菜单（幂等）
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT v.menu_code, v.menu_name,
       (SELECT parent_menu_id FROM sys_menu WHERE route_path = '/purchase/report/analysis'),
       'MENU', v.route_path, v.permission_code,
       v.sort_order, 'DataLine', 1, 1, 0, 1, '启用', NULL
FROM (VALUES
    ('purchase_report_request_detail', '采购申请单明细报表', '/purchase/report/purchase-request-detail', 'purchase:report-request-detail',  8),
    ('purchase_report_contract_detail','采购合同明细报表',   '/purchase/report/purchase-contract-detail','purchase:report-contract-detail', 9),
    ('purchase_report_order_detail',   '采购订单明细报表',   '/purchase/report/purchase-order-detail',   'purchase:report-order-detail',   10),
    ('purchase_report_inbound_detail', '采购入库单明细报表', '/purchase/report/purchase-inbound-detail', 'purchase:report-inbound-detail', 11),
    ('purchase_report_return_detail',  '采购退货单明细报表', '/purchase/report/purchase-return-detail',  'purchase:report-return-detail',  12),
    ('purchase_report_invoice_detail', '采购发票明细报表',   '/purchase/report/purchase-invoice-detail', 'purchase:report-invoice-detail', 13)
) AS v(menu_code, menu_name, route_path, permission_code, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = v.menu_code);

-- 3) 兜底修复父子关系
UPDATE sys_menu
   SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE route_path = '/purchase/report/analysis' LIMIT 1)
 WHERE menu_code IN ('purchase_report_request_detail','purchase_report_contract_detail','purchase_report_order_detail',
                     'purchase_report_inbound_detail','purchase_report_return_detail','purchase_report_invoice_detail')
   AND parent_menu_id IS NULL;

-- 4) 排到 6 个明细报表之后的两个既有菜单依次后移（保持菜单顺序）
UPDATE sys_menu SET sort_order = 14 WHERE menu_code = 'purchase_report_supplier_statement' AND sort_order <> 14;
UPDATE sys_menu SET sort_order = 15 WHERE menu_code = 'purchase_help' AND sort_order <> 15;

-- 5) 角色授权（幂等）
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'PURCHASE_MANAGER', 'PURCHASE', 'SALES')
  AND m.menu_code IN ('purchase_report_request_detail','purchase_report_contract_detail','purchase_report_order_detail',
                      'purchase_report_inbound_detail','purchase_report_return_detail','purchase_report_invoice_detail')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id
       AND p.menu_id = m.route_path
       AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（预期：6 行菜单 + 18~24 行权限）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, parent_menu_id, route_path, permission_code, sort_order
--   FROM sys_menu WHERE route_path LIKE '/purchase/report/%' ORDER BY sort_order;
--
-- SELECT p.menu_id, count(*) FROM sys_role_permission p
--  WHERE p.menu_id LIKE '/purchase/report/purchase-%-detail' GROUP BY p.menu_id;
