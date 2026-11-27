-- ============================================================
-- 114_inventory_detail_reports_menu.sql
-- 用途：库存模块 5 个单据明细报表 —— 菜单初始化 + 角色自动授权（守则 2.7.1-7 / 2.8.1-6）
--
-- 背景：库存管理 → 报表 下原先只有「分析报表」。
--   本脚本插入 5 个单据明细报表菜单（其他入库单 / 其他出库单 / 库存调拨单 / 库存盘点单 / 生产领料单）。
--
-- 口径（均已对本地实库核对）：
--   1. 父级 = 「库存管理」（menu_id=30）：取 /inventory/report/analysis 的 parent_menu_id；
--   2. 排序：「分析报表」实库为 9、「操作手册」15 → 5 个明细报表用 10~14，插在两者之间，
--      不移动任何既有菜单（无需像采购那样后移，因为 9 与 15 之间本就空着）；
--   3. permission_code 用「模块:对象」形式，对象名 = 前端 menuConfig 的 itemKey，
--      与设置中心「按钮权限」里列出的对象码一致；
--   4. menu_id 走序列，禁止硬编码；
--   5. 角色授权：按库存模块自身承载的角色面授权
--      （ADMIN + SUPER_ADMIN + 仓库/采购/销售/生产/财务 各角色）——
--      **刻意比同组「分析报表」（实库仅 ADMIN+SALES）宽**：
--      库存单据明细是仓管员/仓管主管的日常报表，只授 ADMIN+SALES 会让真正用它的人看不到。
--      数据行仍由 DataScopeResolver 按角色数据范围过滤，菜单可见 ≠ 数据可见。
--   6. 注意：sys_role_permission.menu_id 字段实际存的是 sys_menu.route_path（VARCHAR），不是数字 ID。
--
-- 幂等：INSERT 走 NOT EXISTS；重复执行结果一致。
-- 执行后无需重启后端，但用户需**重新登录**（menuRoutePaths 在登录时缓存）。
-- 同内容文件另存 deploy/sql/71_inventory_detail_reports_menu.sql（守则04 编号体系）。
-- ============================================================

BEGIN;

-- 1) 序列对齐
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

-- 2) 5 个菜单（幂等）
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT v.menu_code, v.menu_name,
       (SELECT parent_menu_id FROM sys_menu WHERE route_path = '/inventory/report/analysis'),
       'MENU', v.route_path, v.permission_code,
       v.sort_order, 'DataLine', 1, 1, 0, 1, '启用', NULL
FROM (VALUES
    ('inventory_report_other_receipt_detail',      '其他入库单明细报表', '/inventory/report/other-receipt-detail',      'inventory:report-other-receipt-detail',      10),
    ('inventory_report_other_issue_detail',        '其他出库单明细报表', '/inventory/report/other-issue-detail',        'inventory:report-other-issue-detail',        11),
    ('inventory_report_stock_transfer_detail',     '库存调拨单明细报表', '/inventory/report/stock-transfer-detail',     'inventory:report-stock-transfer-detail',     12),
    ('inventory_report_stock_check_detail',        '库存盘点单明细报表', '/inventory/report/stock-check-detail',        'inventory:report-stock-check-detail',        13),
    ('inventory_report_material_requisition_detail','生产领料单明细报表', '/inventory/report/material-requisition-detail','inventory:report-material-requisition-detail',14)
) AS v(menu_code, menu_name, route_path, permission_code, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = v.menu_code);

-- 3) 兜底修复父子关系
UPDATE sys_menu
   SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE route_path = '/inventory/report/analysis' LIMIT 1)
 WHERE menu_code IN ('inventory_report_other_receipt_detail','inventory_report_other_issue_detail',
                     'inventory_report_stock_transfer_detail','inventory_report_stock_check_detail',
                     'inventory_report_material_requisition_detail')
   AND parent_menu_id IS NULL;

-- 4) 角色授权（幂等）
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN', 'WAREHOUSE_MANAGER', 'WAREHOUSE',
                      'PURCHASE_MANAGER', 'PURCHASE', 'SALES_MANAGER', 'SALES',
                      'PRODUCTION_MANAGER', 'PRODUCTION', 'FINANCE_MANAGER', 'FINANCE')
  AND m.menu_code IN ('inventory_report_other_receipt_detail','inventory_report_other_issue_detail',
                      'inventory_report_stock_transfer_detail','inventory_report_stock_check_detail',
                      'inventory_report_material_requisition_detail')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id
       AND p.menu_id = m.route_path
       AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（预期：5 行菜单 + 60 行权限 = 5 × 12 角色）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, parent_menu_id, route_path, permission_code, sort_order
--   FROM sys_menu WHERE route_path LIKE '/inventory/report/%' ORDER BY sort_order;
--
-- SELECT p.menu_id, count(*) FROM sys_role_permission p
--  WHERE p.menu_id LIKE '/inventory/report/%-detail' GROUP BY p.menu_id;
