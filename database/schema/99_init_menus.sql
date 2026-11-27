-- =============================================
-- 版本化菜单数据初始化脚本
-- 确保 route_path 与前端 menuConfig.js 中的 path 字段一致
-- 版本说明：
--   STANDARD: 标准版功能（基础资料、销售、采购、库存、生产、组装拆卸、财务）
--   CRM: CRM获客版 = 标准版 + CRM + 获客管理
--   ULTIMATE: 旗舰版 = 全部功能
-- =============================================

DELETE FROM sys_menu WHERE menu_id >= 1 AND menu_id < 10000;

-- ==================== 一级菜单组（CATALOG） ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES

-- 工作台
(1, 'dashboard', '工作台', NULL, 'CATALOG', 'DataLine', '/dashboard', 'views/Dashboard', 1, 1, NULL, '启用', 'STANDARD'),

-- 基础资料
(2, 'basedata', '基础资料', NULL, 'CATALOG', 'Files', '/basedata', 'Layout', 1, 2, NULL, '启用', 'STANDARD'),

-- 客户管理 (CRM)
(3, 'crm', '客户管理', NULL, 'CATALOG', 'User', '/crm', 'Layout', 1, 3, NULL, '启用', 'CRM'),

-- 获客管理
(4, 'acquisition', '获客管理', NULL, 'CATALOG', 'Promotion', '/acquisition', 'Layout', 1, 4, NULL, '启用', 'CRM'),

-- 平台管理 (仅 SUPER_ADMIN 可见)
(5, 'platform', '平台管理', NULL, 'CATALOG', 'Monitor', '/admin', 'Layout', 1, 5, NULL, '启用', 'ULTIMATE'),

-- 销售管理
(10, 'sale', '销售管理', NULL, 'CATALOG', 'ShoppingCart', '/sale', 'Layout', 1, 10, NULL, '启用', 'STANDARD'),

-- 采购管理
(20, 'purchase', '采购管理', NULL, 'CATALOG', 'Goods', '/purchase', 'Layout', 1, 20, NULL, '启用', 'STANDARD'),

-- 库存管理
(30, 'inventory', '库存管理', NULL, 'CATALOG', 'Box', '/inventory', 'Layout', 1, 30, NULL, '启用', 'STANDARD'),

-- 生产管理
(40, 'production', '生产管理', NULL, 'CATALOG', 'Tools', '/production', 'Layout', 1, 40, NULL, '启用', 'ULTIMATE'),

-- 委外管理
(50, 'subcontract', '委外管理', NULL, 'CATALOG', 'Connection', '/subcontract', 'Layout', 1, 50, NULL, '启用', 'ULTIMATE'),

-- 打样管理
(60, 'sample', '打样管理', NULL, 'CATALOG', 'Files', '/sample', 'Layout', 1, 60, NULL, '启用', 'ULTIMATE'),

-- 财务管理（版本划分重构 2026-10-10：财务纳入标准版 → 标 STANDARD）
(70, 'finance', '财务管理', NULL, 'CATALOG', 'TrendCharts', '/finance', 'Layout', 1, 70, NULL, '启用', 'STANDARD');

-- ==================== 基础资料子菜单 ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(100, 'basedata_unit', '计量单位', 2, 'MENU', NULL, '/basedata/unit', 'views/basedata/Unit', 1, 1, 'basedata:unit', '启用', 'STANDARD'),
(101, 'basedata_currency', '币种', 2, 'MENU', NULL, '/basedata/currency', 'views/basedata/Currency', 1, 2, 'basedata:currency', '启用', 'STANDARD'),
(102, 'basedata_settle', '结算方式', 2, 'MENU', NULL, '/basedata/settle', 'views/basedata/Settle', 1, 3, 'basedata:settle', '启用', 'STANDARD'),
(103, 'basedata_payment-term', '付款条件', 2, 'MENU', NULL, '/basedata/payment-term', 'views/basedata/PaymentTerm', 1, 4, 'basedata:payment-term', '启用', 'STANDARD'),
(104, 'basedata_price-level', '价格等级', 2, 'MENU', NULL, '/basedata/price-level', 'views/basedata/PriceLevel', 1, 5, 'basedata:price-level', '启用', 'STANDARD'),
(105, 'basedata_department', '部门档案', 2, 'MENU', NULL, '/basedata/department', 'views/basedata/Department', 1, 6, 'basedata:department', '启用', 'STANDARD'),
(106, 'basedata_employee', '员工档案', 2, 'MENU', NULL, '/basedata/employee', 'views/basedata/Employee', 1, 7, 'basedata:employee', '启用', 'STANDARD'),
(107, 'basedata_warehouse', '仓库档案', 2, 'MENU', NULL, '/basedata/warehouse', 'views/basedata/Warehouse', 1, 8, 'basedata:warehouse', '启用', 'STANDARD'),
(108, 'basedata_location', '货位档案', 2, 'MENU', NULL, '/basedata/location', 'views/basedata/Location', 1, 9, 'basedata:location', '启用', 'STANDARD'),
(109, 'basedata_material-category', '物料分类', 2, 'MENU', NULL, '/basedata/material-category', 'views/basedata/MaterialCategory', 1, 10, 'basedata:material-category', '启用', 'STANDARD'),
(110, 'basedata_material', '物料档案', 2, 'MENU', NULL, '/basedata/material', 'views/basedata/Material', 1, 11, 'basedata:material', '启用', 'STANDARD'),
(111, 'basedata_customer', '客户档案', 2, 'MENU', NULL, '/basedata/customer', 'views/basedata/Customer', 1, 12, 'basedata:customer', '启用', 'STANDARD'),
(112, 'basedata_supplier', '供应商档案', 2, 'MENU', NULL, '/basedata/supplier', 'views/basedata/Supplier', 1, 13, 'basedata:supplier', '启用', 'STANDARD'),
(113, 'masterdata_bom', 'BOM', 2, 'MENU', NULL, '/masterdata/bom', 'views/masterdata/Bom', 1, 14, 'masterdata:bom', '启用', 'ULTIMATE');

-- ==================== CRM客户管理子菜单 ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(200, 'crm_dashboard', 'CRM仪表盘', 3, 'MENU', NULL, '/crm/dashboard', 'views/crm/Dashboard', 1, 1, 'crm:dashboard', '启用', 'CRM'),
(201, 'crm_customer', '我的私海', 3, 'MENU', NULL, '/crm/customer', 'views/crm/Customer', 1, 2, 'crm:customer', '启用', 'CRM'),
(202, 'crm_pool', '公海池', 3, 'MENU', NULL, '/crm/pool', 'views/crm/Pool', 1, 3, 'crm:pool', '启用', 'CRM'),
(203, 'crm_lead', '销售线索', 3, 'MENU', NULL, '/crm/lead', 'views/crm/Lead', 1, 4, 'crm:lead', '启用', 'CRM'),
(204, 'crm_opportunity', '商机管理', 3, 'MENU', NULL, '/crm/opportunity', 'views/crm/Opportunity', 1, 5, 'crm:opportunity', '启用', 'CRM'),
(205, 'crm_funnel', '销售漏斗', 3, 'MENU', NULL, '/crm/funnel', 'views/crm/Funnel', 1, 6, 'crm:funnel', '启用', 'CRM'),
(206, 'crm_contact', '联系人管理', 3, 'MENU', NULL, '/crm/contact', 'views/crm/Contact', 1, 7, 'crm:contact', '启用', 'CRM'),
(207, 'crm_follow-up', '跟进记录', 3, 'MENU', NULL, '/crm/follow-up', 'views/crm/FollowUp', 1, 8, 'crm:follow-up', '启用', 'CRM'),
(208, 'crm_warning', '跟进超期预警', 3, 'MENU', NULL, '/crm/warning', 'views/crm/Warning', 1, 9, 'crm:warning', '启用', 'CRM'),
(209, 'crm_complaint', '客户投诉', 3, 'MENU', NULL, '/crm/complaint', 'views/crm/Complaint', 1, 10, 'crm:complaint', '启用', 'CRM'),
(210, 'crm_survey', '满意度调查', 3, 'MENU', NULL, '/crm/survey', 'views/crm/Survey', 1, 11, 'crm:survey', '启用', 'CRM'),
(211, 'crm_sales-target', '销售目标', 3, 'MENU', NULL, '/crm/sales-target', 'views/crm/SalesTarget', 1, 12, 'crm:sales-target', '启用', 'CRM'),
(212, 'crm_report', '报表中心', 3, 'MENU', NULL, '/crm/report', 'views/crm/Report', 1, 13, 'crm:report', '启用', 'CRM'),
(213, 'crm_help', '操作手册', 3, 'MENU', NULL, '/crm/help', 'views/crm/Help', 1, 14, 'crm:help', '启用', 'CRM');

-- ==================== 获客管理子菜单 ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(300, 'acquisition_task', '获客任务', 4, 'MENU', NULL, '/acquisition/task', 'views/acquisition/Task', 1, 1, 'acquisition:task', '启用', 'CRM'),
(301, 'acquisition_result', '采集结果审核', 4, 'MENU', NULL, '/acquisition/result', 'views/acquisition/Result', 1, 2, 'acquisition:result', '启用', 'CRM'),
(302, 'acquisition_login', '企查查登录', 4, 'MENU', NULL, '/acquisition/login', 'views/acquisition/Login', 1, 3, 'acquisition:login', '启用', 'CRM'),
(303, 'acquisition_help', '操作手册', 4, 'MENU', NULL, '/acquisition/help', 'views/acquisition/Help', 1, 4, 'acquisition:help', '启用', 'CRM');

-- ==================== 平台管理子菜单 ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(400, 'platform_tenant_manage', '客户管理', 5, 'MENU', NULL, '/admin/tenant/manage', 'views/admin/TenantManage', 1, 1, 'platform:tenant-manage', '启用', 'ULTIMATE');

-- ==================== 销售管理子菜单 ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(500, 'sale_orders', '销售订单', 10, 'MENU', NULL, '/sale/orders', 'views/sale/Orders', 1, 1, 'sale:orders', '启用', 'STANDARD'),
(501, 'sale_contracts', '销售合同', 10, 'MENU', NULL, '/sale/contracts', 'views/sale/Contracts', 1, 2, 'sale:contracts', '启用', 'STANDARD'),
(502, 'sale_quotes', '销售报价单', 10, 'MENU', NULL, '/sale/quotes', 'views/sale/Quotes', 1, 3, 'sale:quotes', '启用', 'STANDARD'),
(503, 'sale_deliveries', '销售出库单', 10, 'MENU', NULL, '/sale/deliveries', 'views/sale/Deliveries', 1, 4, 'sale:deliveries', '启用', 'STANDARD'),
(504, 'logistics_dashboard', '物流监控看板', 10, 'MENU', NULL, '/logistics/dashboard', 'views/logistics/Dashboard', 1, 5, 'logistics:dashboard', '启用', 'STANDARD'),
(505, 'sale_returns', '销售退货', 10, 'MENU', NULL, '/sale/returns', 'views/sale/Returns', 1, 6, 'sale:returns', '启用', 'STANDARD'),
(506, 'sale_invoices', '销售发票', 10, 'MENU', NULL, '/sale/invoices', 'views/sale/Invoices', 1, 7, 'sale:invoices', '启用', 'STANDARD'),
(507, 'sale_report_analysis', '分析报表', 10, 'MENU', NULL, '/sale/report/analysis', 'views/sale/report/Analysis', 1, 8, 'sale:report:analysis', '启用', 'STANDARD'),
(508, 'sale_report_order_detail', '销售订单明细表', 10, 'MENU', NULL, '/sale/report/sale-order-detail', 'views/sale/report/OrderDetail', 1, 9, 'sale:report:order-detail', '启用', 'STANDARD'),
(509, 'sale_report_delivery_detail', '销售出库明细表', 10, 'MENU', NULL, '/sale/report/sale-delivery-detail', 'views/sale/report/DeliveryDetail', 1, 10, 'sale:report:delivery-detail', '启用', 'STANDARD'),
(510, 'sale_report_return_detail', '销售退货明细表', 10, 'MENU', NULL, '/sale/report/sale-return-detail', 'views/sale/report/ReturnDetail', 1, 11, 'sale:report:return-detail', '启用', 'STANDARD'),
(511, 'sale_report_customer_statement', '客户对账单', 10, 'MENU', NULL, '/sale/report/customer-statement', 'views/sale/report/CustomerStatement', 1, 12, 'sale:report:customer-statement', '启用', 'STANDARD'),
(512, 'sale_help', '操作手册', 10, 'MENU', NULL, '/sale/help', 'views/sale/Help', 1, 13, 'sale:help', '启用', 'STANDARD');

-- ==================== 采购管理子菜单 ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(600, 'purchase_requests', '采购申请单', 20, 'MENU', NULL, '/purchase/requests', 'views/purchase/Requests', 1, 1, 'purchase:requests', '启用', 'STANDARD'),
(601, 'purchase_contracts', '采购合同', 20, 'MENU', NULL, '/purchase/contracts', 'views/purchase/Contracts', 1, 2, 'purchase:contracts', '启用', 'STANDARD'),
(602, 'purchase_orders', '采购订单', 20, 'MENU', NULL, '/purchase/orders', 'views/purchase/Orders', 1, 3, 'purchase:orders', '启用', 'STANDARD'),
(603, 'purchase_inbounds', '采购入库单', 20, 'MENU', NULL, '/purchase/inbounds', 'views/purchase/Inbounds', 1, 4, 'purchase:inbounds', '启用', 'STANDARD'),
(604, 'purchase_returns', '采购退货单', 20, 'MENU', NULL, '/purchase/returns', 'views/purchase/Returns', 1, 5, 'purchase:returns', '启用', 'STANDARD'),
(605, 'purchase_invoices', '采购发票', 20, 'MENU', NULL, '/purchase/invoices', 'views/purchase/Invoices', 1, 6, 'purchase:invoices', '启用', 'STANDARD'),
(606, 'purchase_report_analysis', '分析报表', 20, 'MENU', NULL, '/purchase/report/analysis', 'views/purchase/report/Analysis', 1, 7, 'purchase:report:analysis', '启用', 'STANDARD'),
(607, 'purchase_report_order_detail', '采购订单明细表', 20, 'MENU', NULL, '/purchase/report/purchase-order-detail', 'views/purchase/report/OrderDetail', 1, 8, 'purchase:report:order-detail', '启用', 'STANDARD'),
(608, 'purchase_report_receipt_detail', '采购入库明细表', 20, 'MENU', NULL, '/purchase/report/purchase-receipt-detail', 'views/purchase/report/ReceiptDetail', 1, 9, 'purchase:report:receipt-detail', '启用', 'STANDARD'),
(609, 'purchase_report_return_detail', '采购退货明细表', 20, 'MENU', NULL, '/purchase/report/purchase-return-detail', 'views/purchase/report/ReturnDetail', 1, 10, 'purchase:report:return-detail', '启用', 'STANDARD'),
(610, 'purchase_report_supplier_statement', '供应商对账单', 20, 'MENU', NULL, '/purchase/report/supplier-statement', 'views/purchase/report/SupplierStatement', 1, 11, 'purchase:report:supplier-statement', '启用', 'STANDARD'),
(611, 'purchase_help', '操作手册', 20, 'MENU', NULL, '/purchase/help', 'views/purchase/Help', 1, 12, 'purchase:help', '启用', 'STANDARD');

-- ==================== 库存管理子菜单 ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(700, 'inventory_current', '即时库存', 30, 'MENU', NULL, '/inventory/current', 'views/inventory/Current', 1, 1, 'inventory:current', '启用', 'STANDARD'),
(701, 'inventory_batches', '批次库存', 30, 'MENU', NULL, '/inventory/batches', 'views/inventory/Batches', 1, 2, 'inventory:batches', '启用', 'STANDARD'),
(702, 'inventory_ledger', '库存流水', 30, 'MENU', NULL, '/inventory/ledger', 'views/inventory/Ledger', 1, 3, 'inventory:ledger', '启用', 'STANDARD'),
(703, 'inventory_other_receipts', '其他入库单', 30, 'MENU', NULL, '/inventory/other-receipts', 'views/inventory/OtherReceipts', 1, 4, 'inventory:other-receipts', '启用', 'STANDARD'),
(704, 'inventory_other_issues', '其他出库单', 30, 'MENU', NULL, '/inventory/other-issues', 'views/inventory/OtherIssues', 1, 5, 'inventory:other-issues', '启用', 'STANDARD'),
(705, 'inventory_stock_transfers', '库存调拨单', 30, 'MENU', NULL, '/inventory/stock-transfers', 'views/inventory/StockTransfers', 1, 6, 'inventory:stock-transfers', '启用', 'STANDARD'),
(706, 'inventory_stock_checks', '库存盘点单', 30, 'MENU', NULL, '/inventory/stock-checks', 'views/inventory/StockChecks', 1, 7, 'inventory:stock-checks', '启用', 'STANDARD'),
(707, 'inventory_material_requisitions', '生产领料单', 30, 'MENU', NULL, '/inventory/material-requisitions', 'views/inventory/MaterialRequisitions', 1, 8, 'inventory:material-requisitions', '启用', 'ULTIMATE'),
(708, 'inventory_report_analysis', '分析报表', 30, 'MENU', NULL, '/inventory/report/analysis', 'views/inventory/report/Analysis', 1, 9, 'inventory:report:analysis', '启用', 'STANDARD'),
(709, 'inventory_report_receive_send', '收发存汇总表', 30, 'MENU', NULL, '/inventory/report/receive-send-summary', 'views/inventory/report/ReceiveSend', 1, 10, 'inventory:report:receive-send', '启用', 'STANDARD'),
(710, 'inventory_report_ledger', '库存明细账', 30, 'MENU', NULL, '/inventory/report/inventory-ledger', 'views/inventory/report/Ledger', 1, 11, 'inventory:report:inventory-ledger', '启用', 'STANDARD'),
(711, 'inventory_report_stock_flow', '出入库流水表', 30, 'MENU', NULL, '/inventory/report/stock-flow', 'views/inventory/report/StockFlow', 1, 12, 'inventory:report:stock-flow', '启用', 'STANDARD'),
(712, 'inventory_report_stock_check', '库存盘点表', 30, 'MENU', NULL, '/inventory/report/stock-check', 'views/inventory/report/StockCheck', 1, 13, 'inventory:report:stock-check', '启用', 'STANDARD'),
(713, 'inventory_report_balance', '库存台账', 30, 'MENU', NULL, '/inventory/report/inventory-balance', 'views/inventory/report/Balance', 1, 14, 'inventory:report:inventory-balance', '启用', 'STANDARD'),
(714, 'inventory_help', '操作手册', 30, 'MENU', NULL, '/inventory/help', 'views/inventory/Help', 1, 15, 'inventory:help', '启用', 'STANDARD'),

-- 组装单（版本划分重构 2026-10-10：属生产管理，收归旗舰版）
(720, 'production_assemble', '生产组装单', 30, 'MENU', NULL, '/production/assemble', 'views/production/Assemble', 1, 15, 'production:assemble', '启用', 'ULTIMATE'),
-- 拆卸单（版本划分重构 2026-10-10：属生产管理，收归旗舰版）
(721, 'production_disassemble', '生产拆卸单', 30, 'MENU', NULL, '/production/disassemble', 'views/production/Disassemble', 1, 16, 'production:disassemble', '启用', 'ULTIMATE');

-- ==================== 生产管理子菜单 ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(800, 'production_routings', '工艺路线', 40, 'MENU', NULL, '/production/routings', 'views/production/Routings', 1, 1, 'production:routings', '启用', 'ULTIMATE'),
(801, 'production_mrp', 'MRP分析', 40, 'MENU', NULL, '/production/mrp', 'views/production/Mrp', 1, 2, 'production:mrp', '启用', 'ULTIMATE'),
(802, 'production_workorders', '生产工单', 40, 'MENU', NULL, '/production/workorders', 'views/production/Workorders', 1, 3, 'production:workorders', '启用', 'ULTIMATE'),
(803, 'production_schedule_board', '车间排程看板', 40, 'MENU', NULL, '/production/schedule-board', 'views/production/ScheduleBoard', 1, 4, 'production:schedule-board', '启用', 'ULTIMATE'),
-- 第 16 期（2026-09-28）：车间进度跟踪从排程看板拆出为独立菜单，紧随排程看板之后（原 804~812 排序顺延 +1）
(813, 'production_progress_tracking', '车间进度跟踪', 40, 'MENU', NULL, '/production/progress-tracking', 'views/production/ProgressTracking', 1, 5, 'production:progress-tracking', '启用', 'ULTIMATE'),
(804, 'production_returns', '生产退料单', 40, 'MENU', NULL, '/production/returns', 'views/production/Returns', 1, 6, 'production:returns', '启用', 'ULTIMATE'),
(805, 'production_supplements', '生产补料单', 40, 'MENU', NULL, '/production/supplements', 'views/production/Supplements', 1, 7, 'production:supplements', '启用', 'ULTIMATE'),
(806, 'production_receipts', '生产入库单', 40, 'MENU', NULL, '/production/receipts', 'views/production/Receipts', 1, 8, 'production:receipts', '启用', 'ULTIMATE'),
(807, 'production_help', '操作手册', 40, 'MENU', NULL, '/production/help', 'views/production/Help', 1, 9, 'production:help', '启用', 'ULTIMATE'),
(808, 'production_report_analysis', '分析报表', 40, 'MENU', NULL, '/production/report/analysis', 'views/production/report/Analysis', 1, 10, 'production:report:analysis', '启用', 'ULTIMATE'),
(809, 'production_report_work_order_detail', '生产工单明细表', 40, 'MENU', NULL, '/production/report/work-order-detail', 'views/production/report/WorkOrderDetail', 1, 11, 'production:report:work-order-detail', '启用', 'ULTIMATE'),
(810, 'production_report_requisition_detail', '领料明细表', 40, 'MENU', NULL, '/production/report/material-requisition-detail', 'views/production/report/RequisitionDetail', 1, 12, 'production:report:requisition-detail', '启用', 'ULTIMATE'),
(811, 'production_report_return_detail', '退料明细表', 40, 'MENU', NULL, '/production/report/material-return-detail', 'views/production/report/ReturnDetail', 1, 13, 'production:report:return-detail', '启用', 'ULTIMATE'),
(812, 'production_report_receipt_detail', '生产入库明细表', 40, 'MENU', NULL, '/production/report/production-receipt-detail', 'views/production/report/ReceiptDetail', 1, 14, 'production:report:receipt-detail', '启用', 'ULTIMATE');

-- ==================== 委外管理子菜单 ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(900, 'subcontract_orders', '委外加工单', 50, 'MENU', NULL, '/subcontract/orders', 'views/subcontract/Orders', 1, 1, 'subcontract:orders', '启用', 'ULTIMATE'),
(901, 'subcontract_picks', '委外发料单', 50, 'MENU', NULL, '/subcontract/picks', 'views/subcontract/Picks', 1, 2, 'subcontract:picks', '启用', 'ULTIMATE'),
(902, 'subcontract_inbounds', '委外入库单', 50, 'MENU', NULL, '/subcontract/inbounds', 'views/subcontract/Inbounds', 1, 3, 'subcontract:inbounds', '启用', 'ULTIMATE'),
(903, 'subcontract_supplements', '委外补料单', 50, 'MENU', NULL, '/subcontract/supplements', 'views/subcontract/Supplements', 1, 4, 'subcontract:supplements', '启用', 'ULTIMATE'),
(904, 'subcontract_returns', '委外退料单', 50, 'MENU', NULL, '/subcontract/returns', 'views/subcontract/Returns', 1, 5, 'subcontract:returns', '启用', 'ULTIMATE'),
(905, 'subcontract_help', '操作手册', 50, 'MENU', NULL, '/subcontract/help', 'views/subcontract/Help', 1, 6, 'subcontract:help', '启用', 'ULTIMATE');

-- ==================== 打样管理子菜单 ====================
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(1000, 'sample_list', '打样单', 60, 'MENU', NULL, '/sample/list', 'views/sample/List', 1, 1, 'sample:list', '启用', 'ULTIMATE'),
(1001, 'freesample_list', '免费样品订单', 60, 'MENU', NULL, '/freesample/list', 'views/freesample/List', 1, 2, 'freesample:list', '启用', 'ULTIMATE');

-- ==================== 财务管理子菜单 ====================
-- 版本划分重构（2026-10-10）：应收/应付/预收/预付/收付款/成本核算/报表 → 标准版；
--   费用分摊、成本核算配置、账户维护、工序工资核算仍为旗舰版
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version) VALUES
(1120, 'finance_bank_account', '账户维护', 70, 'MENU', NULL, '/finance/account', 'views/finance/BankAccount', 1, 0, 'finance:bank-account', '启用', 'ULTIMATE'),
(1100, 'finance_sale_pre_receipts', '销售预收款单', 70, 'MENU', NULL, '/finance/sale-pre-receipts', 'views/finance/SalePreReceipts', 1, 1, 'finance:sale-pre-receipts', '启用', 'STANDARD'),
(1101, 'finance_purchase_pre_payments', '采购预付款单', 70, 'MENU', NULL, '/finance/purchase-pre-payments', 'views/finance/PurchasePrePayments', 1, 2, 'finance:purchase-pre-payments', '启用', 'STANDARD'),
(1102, 'finance_receipt', '收款单', 70, 'MENU', NULL, '/finance/receipt', 'views/finance/Receipt', 1, 3, 'finance:receipt', '启用', 'STANDARD'),
(1103, 'finance_payment', '付款单', 70, 'MENU', NULL, '/finance/payment', 'views/finance/Payment', 1, 4, 'finance:payment', '启用', 'STANDARD'),
(1104, 'finance_operation_expense', '费用分摊', 70, 'MENU', NULL, '/finance/operation-expense', 'views/finance/OperationExpense', 1, 5, 'finance:operation-expense', '启用', 'ULTIMATE'),
(1105, 'finance_cost_calculation', '成本核算', 70, 'MENU', NULL, '/finance/cost-calculation', 'views/finance/CostCalculation', 1, 6, 'finance:cost-calculation', '启用', 'STANDARD'),
(1106, 'finance_cost_config', '成本核算配置', 70, 'MENU', NULL, '/finance/cost-config', 'views/finance/CostConfig', 1, 7, 'finance:cost-config', '启用', 'ULTIMATE'),
(1107, 'finance_report_analysis', '分析报表', 70, 'MENU', NULL, '/finance/report/analysis', 'views/finance/report/Analysis', 1, 8, 'finance:report:analysis', '启用', 'STANDARD'),
(1108, 'finance_report_ar_summary', '应收账款汇总表', 70, 'MENU', NULL, '/finance/report/ar-summary', 'views/finance/report/ArSummary', 1, 9, 'finance:report:ar-summary', '启用', 'STANDARD'),
(1109, 'finance_report_ar_detail', '应收账款明细表', 70, 'MENU', NULL, '/finance/report/ar-detail', 'views/finance/report/ArDetail', 1, 10, 'finance:report:ar-detail', '启用', 'STANDARD'),
(1110, 'finance_report_ap_summary', '应付账款汇总表', 70, 'MENU', NULL, '/finance/report/ap-summary', 'views/finance/report/ApSummary', 1, 11, 'finance:report:ap-summary', '启用', 'STANDARD'),
(1111, 'finance_report_ap_detail', '应付账款明细表', 70, 'MENU', NULL, '/finance/report/ap-detail', 'views/finance/report/ApDetail', 1, 12, 'finance:report:ap-detail', '启用', 'STANDARD'),
(1112, 'finance_report_payment_flow', '收付款流水表', 70, 'MENU', NULL, '/finance/report/payment-flow', 'views/finance/report/PaymentFlow', 1, 13, 'finance:report:payment-flow', '启用', 'STANDARD'),
(1113, 'finance_report_counterparty_account', '往来账', 70, 'MENU', NULL, '/finance/report/counterparty-account', 'views/finance/report/CounterpartyAccount', 1, 14, 'finance:report:counterparty-account', '启用', 'STANDARD'),
(1115, 'finance_help', '操作手册', 70, 'MENU', NULL, '/finance/help', 'views/finance/FinancesHelp', 1, 15, 'finance:help', '启用', 'STANDARD');
