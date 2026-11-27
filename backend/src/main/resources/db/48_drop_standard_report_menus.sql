-- 48_drop_standard_report_menus.sql
-- 标准台账子系统下线（用户批准的整模块删除，非误删）：
--   删除 20 条标准台账菜单 + sys_role_permission 权限残留。
--   前后端代码已同步删除（5个Standard Controller + StandardReportCenter/Page页面），
--   仅保留客户对账单/供应商对账单（URL 不变，由新 Controller 承接）。
-- 幂等：精确匹配删除，重复执行影响 0 行。
-- 注意：本脚本含受控 DELETE（经用户批准的下线操作），范围严格限定在
--       20 个 menu_code / 对应 route_path / 对应 menu_id 数字形式，禁止扩大。

-- 1) 先删权限残留（必须在删菜单前，子查询依赖 sys_menu 行仍存在）
DELETE FROM sys_role_permission p
WHERE p.menu_id IN (
    SELECT CAST(m.menu_id AS VARCHAR) FROM sys_menu m
    WHERE m.menu_code IN (
        'sale_report_order_detail','sale_report_delivery_detail','sale_report_return_detail',
        'purchase_report_order_detail','purchase_report_receipt_detail','purchase_report_return_detail',
        'inventory_report_receive_send','inventory_report_ledger','inventory_report_stock_flow',
        'inventory_report_stock_check','inventory_report_balance',
        'production_report_work_order_detail','production_report_requisition_detail',
        'production_report_return_detail','production_report_receipt_detail',
        'finance_report_ar_detail','finance_report_ap_detail','finance_report_payment_flow',
        'finance_report_counterparty_account','report_standard'
    )
) OR p.menu_id IN (
    SELECT m.route_path FROM sys_menu m
    WHERE m.menu_code IN (
        'sale_report_order_detail','sale_report_delivery_detail','sale_report_return_detail',
        'purchase_report_order_detail','purchase_report_receipt_detail','purchase_report_return_detail',
        'inventory_report_receive_send','inventory_report_ledger','inventory_report_stock_flow',
        'inventory_report_stock_check','inventory_report_balance',
        'production_report_work_order_detail','production_report_requisition_detail',
        'production_report_return_detail','production_report_receipt_detail',
        'finance_report_ar_detail','finance_report_ap_detail','finance_report_payment_flow',
        'finance_report_counterparty_account','report_standard'
    )
);

-- 2) 删除菜单本体
DELETE FROM sys_menu
WHERE menu_code IN (
    'sale_report_order_detail','sale_report_delivery_detail','sale_report_return_detail',
    'purchase_report_order_detail','purchase_report_receipt_detail','purchase_report_return_detail',
    'inventory_report_receive_send','inventory_report_ledger','inventory_report_stock_flow',
    'inventory_report_stock_check','inventory_report_balance',
    'production_report_work_order_detail','production_report_requisition_detail',
    'production_report_return_detail','production_report_receipt_detail',
    'finance_report_ar_detail','finance_report_ap_detail','finance_report_payment_flow',
    'finance_report_counterparty_account','report_standard'
);
