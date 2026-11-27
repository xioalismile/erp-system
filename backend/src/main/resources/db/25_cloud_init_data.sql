-- ===========================================================================
-- 智能ERP 云端数据库缺失配置数据补全脚本
-- 版本: PostgreSQL 15+
-- 日期: 2026-07-23
-- 说明: 一次性补全云端数据库缺失的所有关键配置数据
--       1. sys_menu 菜单数据（目录 + 菜单页）
--       2. sys_role_permission 角色权限（admin 拥有所有菜单）
--       3. 修复 sys_user_role 表缺失字段
--       4. 清理 unit_of_measure 测试脏数据
--       5. 修复 material_code_rule 乱码
-- 使用方法: psql -U erp_user -d erp_db -f 25_cloud_init_data.sql
-- ===========================================================================
SET client_encoding TO 'UTF8';

-- 先添加唯一约束（如果不存在），使 ON CONFLICT 可用
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_menu_code') THEN
        ALTER TABLE sys_menu ADD CONSTRAINT uk_menu_code UNIQUE (menu_code);
    END IF;
END$$;

-- 清空旧数据（防止重复）
DELETE FROM sys_role_permission;
DELETE FROM sys_menu;

BEGIN;

-- ===========================================================================
-- 第一部分: sys_menu 菜单数据
-- ===========================================================================
-- menu_id 使用显式值,避免序列冲突; ON CONFLICT DO NOTHING 防止重复插入

-- ===== 一级目录 (menu_type = 'DIR') =====

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (100, 'dashboard',     '工作台',   NULL, 'DIR', 'Odometer',       '/dashboard',     NULL, 1, 0, 1, 0,  1, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (200, 'basedata',      '基础资料', NULL, 'DIR', 'FolderOpened',   '/basedata',      NULL, 1, 0, 1, 0,  2, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (300, 'acquisition',   '获客管理', NULL, 'DIR', 'Promotion',      '/acquisition',   NULL, 1, 0, 1, 0,  3, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (400, 'crm',           'CRM管理',  NULL, 'DIR', 'UserFilled',     '/crm',           NULL, 1, 0, 1, 0,  4, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (500, 'sale',          '销售管理', NULL, 'DIR', 'ShoppingCart',   '/sale',          NULL, 1, 0, 1, 0,  5, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (600, 'purchase',      '采购管理', NULL, 'DIR', 'Goods',          '/purchase',      NULL, 1, 0, 1, 0,  6, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (700, 'inventory',     '库存管理', NULL, 'DIR', 'Box',            '/inventory',     NULL, 1, 0, 1, 0,  7, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (800, 'production',    '生产管理', NULL, 'DIR', 'SetUp',          '/production',    NULL, 1, 0, 1, 0,  8, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (900, 'subcontract',   '委外管理', NULL, 'DIR', 'Connection',     '/subcontract',   NULL, 1, 0, 1, 0,  9, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (1000, 'finance',      '财务管理', NULL, 'DIR', 'Money',          '/finance',       NULL, 1, 0, 1, 0, 10, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (1100, 'system',       '系统管理', NULL, 'DIR', 'Setting',        '/system',        NULL, 1, 0, 1, 0, 11, NULL, '启用')
ON CONFLICT (menu_code) DO NOTHING;


-- ===== 二级菜单 (menu_type = 'MENU') =====

-- ----- 工作台 (parent=100) -----
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (101, 'dashboard_index', '工作台仪表盘', 100, 'MENU', 'Odometer',  '/dashboard/index',  'dashboard/DashboardIndex',  1, 0, 1, 1, 1, 'dashboard:view', '启用')
ON CONFLICT (menu_code) DO NOTHING;

-- ----- 基础资料 (parent=200) -----
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status) VALUES
(201, 'unit_of_measure',    '计量单位',   200, 'MENU', 'Scale',         '/basedata/unit',              'basedata/UnitOfMeasure',    1,0,1,1, 1, 'basedata:unit:view',     '启用'),
(202, 'currency',           '币种',       200, 'MENU', 'Coin',         '/basedata/currency',           'basedata/Currency',         1,0,1,1, 2, 'basedata:currency:view', '启用'),
(203, 'settlement_mode',    '结算方式',   200, 'MENU', 'CreditCard',   '/basedata/settlement-mode',    'basedata/SettlementMode',   1,0,1,1, 3, 'basedata:settle:view',   '启用'),
(204, 'payment_term',       '付款条件',   200, 'MENU', 'Timer',        '/basedata/payment-term',       'basedata/PaymentTerm',      1,0,1,1, 4, 'basedata:payterm:view',  '启用'),
(205, 'price_level',        '价格等级',   200, 'MENU', 'PriceTag',     '/basedata/price-level',        'basedata/PriceLevel',       1,0,1,1, 5, 'basedata:pricelevel:view','启用'),
(206, 'department',         '部门档案',   200, 'MENU', 'OfficeBuilding','/basedata/department',        'basedata/Department',       1,0,1,1, 6, 'basedata:dept:view',     '启用'),
(207, 'employee',           '员工档案',   200, 'MENU', 'User',         '/basedata/employee',           'basedata/Employee',         1,0,1,1, 7, 'basedata:employee:view', '启用'),
(208, 'warehouse',          '仓库档案',   200, 'MENU', 'House',        '/basedata/warehouse',          'basedata/Warehouse',        1,0,1,1, 8, 'basedata:warehouse:view','启用'),
(209, 'location',           '货位档案',   200, 'MENU', 'MapLocation',  '/basedata/location',           'basedata/Location',         1,0,1,1, 9, 'basedata:location:view', '启用'),
(210, 'material_category',  '物料分类',   200, 'MENU', 'Menu',         '/basedata/material-category',  'basedata/MaterialCategory', 1,0,1,1,10, 'basedata:matcat:view',   '启用'),
(211, 'material',           '物料档案',   200, 'MENU', 'Box',          '/basedata/material',           'basedata/Material',         1,0,1,1,11, 'basedata:material:view', '启用'),
(212, 'customer',           '客户档案',   200, 'MENU', 'Avatar',       '/basedata/customer',           'basedata/Customer',         1,0,1,1,12, 'basedata:customer:view', '启用'),
(213, 'supplier',           '供应商档案', 200, 'MENU', 'Van',          '/basedata/supplier',           'basedata/Supplier',         1,0,1,1,13, 'basedata:supplier:view', '启用'),
(214, 'account',            '会计科目',   200, 'MENU', 'Notebook',     '/basedata/account',            'basedata/Account',          1,0,1,1,14, 'basedata:account:view',  '启用'),
(215, 'material_code_rule', '物料编码规则',200, 'MENU', 'Key',         '/basedata/material-code-rule', 'basedata/MaterialCodeRule', 1,0,1,1,15, 'basedata:coderule:view', '启用')
ON CONFLICT (menu_code) DO NOTHING;

-- ----- 获客管理 (parent=300) -----
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status) VALUES
(301, 'acquisition_task',   '获客任务',       300, 'MENU', 'List',        '/acquisition/task',            'acquisition/AcquisitionTaskList',   1,0,1,1, 1, 'acquisition:task:view',    '启用'),
(302, 'acquisition_review', '采集结果审核',   300, 'MENU', 'Checked',     '/acquisition/result',          'acquisition/AcquisitionResultList', 1,0,1,1, 2, 'acquisition:review:view',  '启用'),
(303, 'qichacha_login',     '企查查登录',     300, 'MENU', 'Link',        '/acquisition/login',           'acquisition/AcquisitionLogin',      1,0,1,1, 3, 'acquisition:qichacha:view','启用')
ON CONFLICT (menu_code) DO NOTHING;

-- ----- CRM管理 (parent=400) -----
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status) VALUES
(401, 'crm_dashboard',   'CRM仪表盘',   400, 'MENU', 'DataLine',     '/crm/dashboard',     'crm/CrmDashboard',        1,0,1,1, 1, 'crm:dashboard:view', '启用'),
(402, 'crm_private',     '我的私海',     400, 'MENU', 'User',         '/crm/customer',        'crm/CrmCustomerList',     1,0,1,1, 2, 'crm:private:view',   '启用'),
(403, 'crm_pool',        '公海池',       400, 'MENU', 'Share',        '/crm/pool',            'crm/CustomerPoolList',    1,0,1,1, 3, 'crm:pool:view',      '启用'),
(404, 'crm_lead',        '销售线索',     400, 'MENU', 'Guide',        '/crm/lead',            'crm/LeadList',            1,0,1,1, 4, 'crm:lead:view',      '启用'),
(405, 'crm_opportunity', '商机管理',     400, 'MENU', 'Opportunity',  '/crm/opportunity',     'crm/OpportunityList',     1,0,1,1, 5, 'crm:opportunity:view','启用'),
(406, 'crm_funnel',      '销售漏斗',     400, 'MENU', 'TrendCharts',  '/crm/funnel',          'crm/SalesFunnelBoard',    1,0,1,1, 6, 'crm:funnel:view',    '启用'),
(407, 'crm_contact',     '联系人管理',   400, 'MENU', 'Phone',        '/crm/contact',         'crm/ContactList',         1,0,1,1, 7, 'crm:contact:view',   '启用'),
(408, 'crm_followup',    '跟进记录',     400, 'MENU', 'ChatDotRound', '/crm/follow-up',       'crm/FollowUpList',        1,0,1,1, 8, 'crm:followup:view',  '启用'),
(409, 'crm_remind',      '跟进提醒',     400, 'MENU', 'Bell',         '/crm/warning',         'crm/CustomerWarningList', 1,0,1,1, 9, 'crm:remind:view',    '启用'),
(410, 'crm_complaint',   '客户投诉',     400, 'MENU', 'Warning',      '/crm/complaint',       'crm/ComplaintList',       1,0,1,1,10, 'crm:complaint:view', '启用'),
(411, 'crm_satisfaction','满意度调查',   400, 'MENU', 'Star',         '/crm/survey',          'crm/SurveyList',          1,0,1,1,11, 'crm:satisfaction:view','启用'),
(412, 'crm_target',      '销售目标',     400, 'MENU', 'Aim',          '/crm/sales-target',    'crm/SalesTargetList',     1,0,1,1,12, 'crm:target:view',    '启用'),
(413, 'crm_report',      'CRM报表中心',  400, 'MENU', 'DataAnalysis', '/crm/report',          'crm/CrmReportCenter',     1,0,1,1,13, 'crm:report:view',    '启用'),
(414, 'crm_manual',      '操作手册',     400, 'MENU', 'Document',     '/crm/help',            'crm/CrmHelp',             1,0,1,1,14, 'crm:manual:view',    '启用')
ON CONFLICT (menu_code) DO NOTHING;

-- ----- 销售管理 (parent=500) -----
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status) VALUES
(501, 'sale_price_strategy','销售价格策略',500, 'MENU', 'PriceTag',    '/sale/price-strategy', 'sale/PriceStrategy',     1,0,1,1, 1, 'sale:price:view',     '启用'),
(502, 'sale_contract',      '销售合同',   500, 'MENU', 'Notebook',    '/sale/contracts',       'sale/SaleContractList',  1,0,1,1, 2, 'sale:contract:view',  '启用'),
(503, 'sale_order',         '销售订单',   500, 'MENU', 'Document',    '/sale/orders',         'sale/SaleOrderList',     1,0,1,1, 3, 'sale:order:view',     '启用'),
(504, 'sale_quote',         '销售报价单', 500, 'MENU', 'Tickets',     '/sale/quotes',         'sale/SaleQuoteList',     1,0,1,1, 4, 'sale:quote:view',     '启用'),
(505, 'sale_delivery',      '销售出库单', 500, 'MENU', 'Van',         '/sale/deliveries',     'sale/SaleDeliveryList',  1,0,1,1, 5, 'sale:delivery:view',  '启用'),
(506, 'sale_return',        '销售退货',   500, 'MENU', 'RefreshLeft', '/sale/returns',        'sale/SaleReturnList',    1,0,1,1, 6, 'sale:return:view',    '启用'),
(507, 'sale_invoice',       '销售发票',   500, 'MENU', 'Memo',        '/sale/invoices',       'sale/SaleInvoiceList',   1,0,1,1, 7, 'sale:invoice:view',   '启用'),
(508, 'sale_report',        '销售分析报表',500, 'MENU', 'DataAnalysis','/sale/report/analysis','sale/SaleReportCenter',  1,0,1,1, 8, 'sale:report:view',    '启用')
ON CONFLICT (menu_code) DO NOTHING;

-- ----- 采购管理 (parent=600) -----
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status) VALUES
(601, 'purchase_price_strategy','采购价格策略',600,'MENU','PriceTag',   '/purchase/price-strategy','purchase/PriceStrategy',  1,0,1,1, 1, 'purchase:price:view',    '启用'),
(602, 'purchase_contract',   '采购合同',     600, 'MENU', 'Notebook',    '/purchase/contracts',    'purchase/PurchaseContract',1,0,1,1, 2, 'purchase:contract:view', '启用'),
(603, 'purchase_order',      '采购订单',     600, 'MENU', 'Document',    '/purchase/orders',       'purchase/PurchaseOrder',  1,0,1,1, 3, 'purchase:order:view',    '启用'),
(604, 'purchase_request',    '采购申请单',   600, 'MENU', 'Tickets',     '/purchase/requests',     'purchase/PurchaseRequest',1,0,1,1, 4, 'purchase:request:view',  '启用'),
(605, 'purchase_receipt',    '采购入库单',   600, 'MENU', 'Box',         '/purchase/inbounds',     'purchase/PurchaseReceipt',1,0,1,1, 5, 'purchase:receipt:view',  '启用'),
(606, 'purchase_return',     '采购退货单',   600, 'MENU', 'RefreshLeft', '/purchase/returns',      'purchase/PurchaseReturn', 1,0,1,1, 6, 'purchase:return:view',   '启用'),
(607, 'purchase_invoice',    '采购发票',     600, 'MENU', 'Memo',        '/purchase/invoices',     'purchase/PurchaseInvoice',1,0,1,1, 7, 'purchase:invoice:view',  '启用'),
(608, 'purchase_report',     '采购分析报表', 600, 'MENU', 'DataAnalysis','/purchase/report/analysis','purchase/PurchaseReport',1,0,1,1, 8, 'purchase:report:view',   '启用')
ON CONFLICT (menu_code) DO NOTHING;

-- ----- 库存管理 (parent=700) -----
-- 2026-09-20 下线 702「仓库管理」/ 703「储位管理」：与「基础资料→仓库档案 / 货位档案」重复，
-- 且这两个页面字段整页错配（点进去整页空白）。已部署环境的清理脚本见 deploy/sql/65_*.sql。
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status) VALUES
(701, 'inventory_current',   '即时库存',     700, 'MENU', 'DataBoard',   '/inventory/current',             'inventory/InventoryCurrent',   1,0,1,1, 1, 'inventory:current:view',   '启用'),
(704, 'inventory_batch',     '批次库存',     700, 'MENU', 'Stamp',       '/inventory/batches',             'inventory/BatchList',          1,0,1,1, 4, 'inventory:batch:view',     '启用'),
(705, 'inventory_ledger',    '库存流水',     700, 'MENU', 'List',        '/inventory/ledger',              'inventory/StockFlowList',      1,0,1,1, 5, 'inventory:ledger:view',    '启用'),
(706, 'other_receipt',       '其他入库单',   700, 'MENU', 'Download',    '/inventory/other-receipts',      'inventory/OtherReceiptList',   1,0,1,1, 6, 'inventory:otherin:view',   '启用'),
(707, 'other_issue',         '其他出库单',   700, 'MENU', 'Upload',      '/inventory/other-issues',        'inventory/OtherIssueList',     1,0,1,1, 7, 'inventory:otherout:view',  '启用'),
(708, 'stock_transfer',      '库存调拨单',   700, 'MENU', 'Sort',        '/inventory/stock-transfers',     'inventory/StockTransferList',  1,0,1,1, 8, 'inventory:transfer:view',  '启用'),
(709, 'stock_check',         '库存盘点单',   700, 'MENU', 'Finished',    '/inventory/stock-checks',        'inventory/StockCheckList',     1,0,1,1, 9, 'inventory:check:view',     '启用'),
(710, 'material_requisition','生产领料单',   700, 'MENU', 'SetUp',       '/inventory/material-requisitions','inventory/MaterialRequisitionList',1,0,1,1,10, 'inventory:requisition:view','启用'),
(711, 'inventory_report',    '库存分析报表', 700, 'MENU', 'DataAnalysis','/inventory/report/analysis',     'inventory/InventoryReportCenter',1,0,1,1,11, 'inventory:report:view',    '启用')
ON CONFLICT (menu_code) DO NOTHING;

-- ----- 生产管理 (parent=800) -----
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status) VALUES
(801, 'bom',                 'BOM清单',       800, 'MENU', 'Cascader',     '/masterdata/bom',              'masterdata/BomList',              1,0,1,1, 1, 'production:bom:view',         '启用'),
(802, 'work_center',         '工作中心',       800, 'MENU', 'OfficeBuilding','/production/work-center',     'production/WorkCenterList',       1,0,1,1, 2, 'production:workcenter:view',   '启用'),
(803, 'workstation',         '工序/工位',      800, 'MENU', 'Coordinate',   '/production/workstation',      'production/WorkstationList',      1,0,1,1, 3, 'production:workstation:view',  '启用'),
(804, 'routing',             '工艺路线',       800, 'MENU', 'Guide',        '/production/routings',         'production/RoutingList',          1,0,1,1, 4, 'production:routing:view',      '启用'),
(805, 'production_order',    '生产工单',       800, 'MENU', 'Document',     '/production/workorders',       'production/WorkOrderList',        1,0,1,1, 5, 'production:order:view',        '启用'),
(806, 'production_receipt',  '生产入库单',     800, 'MENU', 'Box',          '/production/receipts',         'production/ProductionReceiptList',1,0,1,1, 6, 'production:receipt:view',      '启用'),
(807, 'production_return',   '生产退料单',     800, 'MENU', 'RefreshLeft',  '/production/returns',          'production/ProductionReturnList', 1,0,1,1, 7, 'production:return:view',       '启用'),
(808, 'production_supplement','生产补料单',    800, 'MENU', 'CirclePlus',   '/production/supplements',      'production/ProductionSupplementList',  1,0,1,1, 8, 'production:supplement:view',   '启用'),

(810, 'operation_report',    '工序汇报单',     800, 'MENU', 'EditPen',      '/production/operation-reports','production/OperationReportList',      1,0,1,1,10, 'production:opreport:view',     '启用'),
(811, 'mrp_plan',            'MRP运算',        800, 'MENU', 'Cpu',          '/production/mrp',              'production/MrpPlan',                  1,0,1,1,11, 'production:mrp:view',          '启用'),
(812, 'production_assemble', '生产组装单',     800, 'MENU', 'Connection',   '/production/assemblies',       'production/ProductionAssembleList',    1,0,1,1,12, 'production:assemble:view',     '启用'),
(813, 'production_disassemble','生产拆卸单',   800, 'MENU', 'SwitchButton', '/production/disassemblies',    'production/ProductionDisassembleList',1,0,1,1,13, 'production:disassemble:view',  '启用'),
(814, 'production_manual',   '生产操作手册',   800, 'MENU', 'Document',     '/production/manual',           'production/ProductionManual',         1,0,1,1,14, 'production:manual:view',       '启用'),
(815, 'production_report',   '生产分析报表',   800, 'MENU', 'DataAnalysis', '/production/report/analysis',  'production/ProductionReportCenter',   1,0,1,1,15, 'production:report:view',       '启用')
ON CONFLICT (menu_code) DO NOTHING;

-- ----- 委外管理 (parent=900) -----
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status) VALUES
(901, 'subcontract_order',    '委外加工单', 900, 'MENU', 'Document',    '/subcontract/orders',      'subcontract/SubcontractOrderList',    1,0,1,1, 1, 'subcontract:order:view',    '启用'),
(902, 'subcontract_issue',    '委外发料单', 900, 'MENU', 'Upload',      '/subcontract/picks',       'subcontract/SubcontractPickList',     1,0,1,1, 2, 'subcontract:issue:view',    '启用'),
(903, 'subcontract_receipt',  '委外入库单', 900, 'MENU', 'Download',    '/subcontract/inbounds',    'subcontract/SubcontractInboundList',  1,0,1,1, 3, 'subcontract:receipt:view',  '启用'),
(904, 'subcontract_supplement','委外补料单',900, 'MENU', 'CirclePlus',  '/subcontract/supplements', 'subcontract/SubcontractSupplementList',1,0,1,1,4, 'subcontract:supplement:view','启用'),
(905, 'subcontract_return',   '委外退料单', 900, 'MENU', 'RefreshLeft', '/subcontract/returns',     'subcontract/SubcontractReturnList',   1,0,1,1, 5, 'subcontract:return:view',   '启用')
ON CONFLICT (menu_code) DO NOTHING;

-- ----- 财务管理 (parent=1000) -----
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status) VALUES
(1001,'cash_receipt',         '收款单',       1000,'MENU','Wallet',       '/finance/receipt',              'finance/PaymentReceiptList',    1,0,1,1, 1, 'finance:receipt:view',       '启用'),
(1002,'cash_payment',         '付款单',       1000,'MENU','CreditCard',   '/finance/payment',              'finance/PaymentVoucherList',    1,0,1,1, 2, 'finance:payment:view',       '启用'),
(1005,'operating_expense',    '费用分摊',     1000,'MENU','Money',        '/finance/operation-expense',    'finance/OperationExpenseList',  1,0,1,1, 5, 'finance:expense:view',       '启用'),
(1006,'cost_config',          '成本核算配置', 1000,'MENU','SetUp',        '/finance/cost-config',          'finance/CostConfigList',        1,0,1,1, 6, 'finance:costconfig:view',    '启用'),
(1007,'cost_calculation',     '成本核算',     1000,'MENU','Cpu',         '/finance/cost-calculation',     'finance/CostCalculationList',   1,0,1,1, 7, 'finance:costcalc:view',      '启用'),
(1008,'sale_prepayment',      '销售预收款单', 1000,'MENU','Wallet',       '/finance/sale-pre-receipts',    'finance/SalePreReceiptList',    1,0,1,1, 8, 'finance:saleprepay:view',    '启用'),
(1009,'purchase_prepayment',  '采购预付款单', 1000,'MENU','CreditCard',   '/finance/purchase-pre-payments','finance/PurchasePrePaymentList',1,0,1,1, 9, 'finance:purchaseprepay:view','启用'),
(1010,'finance_report',       '财务分析报表', 1000,'MENU','DataAnalysis', '/finance/report/analysis',      'finance/FinanceReportCenter',   1,0,1,1,10, 'finance:report:view',        '启用')
ON CONFLICT (menu_code) DO NOTHING;

-- ----- 系统管理 (parent=1100) -----
INSERT INTO sys_menu (menu_id, menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, is_cache, is_enabled, keepalive, sort_order, permission_code, status)
VALUES (1101, 'system_settings', '设置中心', 1100, 'MENU', 'Setting', '/system/settings', 'system/SettingsCenter', 1, 0, 1, 1, 1, 'system:settings:view', '启用')
ON CONFLICT (menu_code) DO NOTHING;


-- ===========================================================================
-- 第二部分: sys_role_permission 角色权限数据
-- ===========================================================================
-- admin 角色 (role_id=1) 拥有所有菜单的 VIEW 权限
-- 使用 INSERT ... ON CONFLICT DO NOTHING 防止重复

INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT 1, m.menu_id, 'VIEW', 1, 0
FROM sys_menu m
WHERE m.is_enabled = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = 1 AND rp.menu_id = m.menu_id AND rp.permission_type = 'VIEW'
  );


-- ===========================================================================
-- 第三部分: 修复 sys_user_role 表缺失字段
-- ===========================================================================

ALTER TABLE IF EXISTS sys_user_role
ADD COLUMN IF NOT EXISTS assign_date TIMESTAMP;

ALTER TABLE IF EXISTS sys_user_role
ADD COLUMN IF NOT EXISTS assign_by VARCHAR(50);

ALTER TABLE IF EXISTS sys_user_role
ADD COLUMN IF NOT EXISTS data_scope_type VARCHAR(20);

ALTER TABLE IF EXISTS sys_user_role
ADD COLUMN IF NOT EXISTS data_scope_value VARCHAR(500);

-- 回填已有数据
UPDATE sys_user_role
SET assign_date = COALESCE(assign_date, CURRENT_TIMESTAMP),
    assign_by   = COALESCE(assign_by, 'admin'),
    data_scope_type = COALESCE(data_scope_type,
        CASE WHEN role_id = 1 THEN '全部' ELSE '本部门' END)
WHERE assign_date IS NULL;


-- ===========================================================================
-- 第四部分: 清理 unit_of_measure 中的测试脏数据
-- ===========================================================================
-- 删除 U006~U030 的测试数据,只保留5个标准单位 (PCS/BOX/SET/KG/G)
-- 同时清理 init.sql 中的其他标准单位 (M/CM/L/ML/SQM/TON) 也保留

DELETE FROM unit_of_measure
WHERE unit_code LIKE 'U%'
  AND unit_code SIMILAR TO 'U(0[6-9]|[12][0-9]|30)';


-- ===========================================================================
-- 第五部分: 修复 material_code_rule 中的乱码
-- ===========================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'material_code_rule') THEN
        UPDATE material_code_rule
        SET category_name = '原材料'
        WHERE category_name ~ '[^\u4e00-\u9fff^\w^\s^-]'
          AND category_name !~ '^[\w\s\u4e00-\u9fff-]+$';

        INSERT INTO material_code_rule (category_id, category_name, prefix, seq_length, is_enabled, last_seq)
        SELECT mc.category_id, mc.category_name,
               CASE mc.category_name
                   WHEN '原材料'   THEN 'RM'
                   WHEN '半成品'   THEN 'SF'
                   WHEN '成品'     THEN 'FG'
                   WHEN '包材'     THEN 'PM'
                   WHEN '标准件'   THEN 'ST'
                   WHEN '电子件'   THEN 'EL'
                   WHEN '结构件'   THEN 'SC'
                   WHEN '轴承类'   THEN 'BR'
                   WHEN '轴类'     THEN 'AX'
                   WHEN '辅助材料' THEN 'AM'
                   ELSE 'MA'
               END,
               6, 1, 0
        FROM material_category mc
        WHERE mc.category_level = 1
          AND NOT EXISTS (
              SELECT 1 FROM material_code_rule mcr
              WHERE mcr.category_id = mc.category_id
          )
        ON CONFLICT DO NOTHING;
    END IF;
END $$;


-- ===========================================================================
-- 第六部分: 重置序列
-- ===========================================================================
-- 确保序列值不与显式插入的 menu_id 冲突

SELECT setval('sys_menu_menu_id_seq', (SELECT COALESCE(MAX(menu_id), 1) FROM sys_menu));

-- 同步重置 sys_role_permission 序列(如果存在)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'sys_role_permission_permission_id_seq') THEN
        PERFORM setval('sys_role_permission_permission_id_seq',
                       (SELECT COALESCE(MAX(permission_id), 1) FROM sys_role_permission));
    END IF;
END $$;


-- ===========================================================================
-- 验证数据
-- ===========================================================================

DO $$
DECLARE
    v_menu_count    INT;
    v_dir_count     INT;
    v_page_count    INT;
    v_perm_count    INT;
BEGIN
    SELECT COUNT(*) INTO v_menu_count FROM sys_menu;
    SELECT COUNT(*) INTO v_dir_count  FROM sys_menu WHERE menu_type = 'DIR';
    SELECT COUNT(*) INTO v_page_count FROM sys_menu WHERE menu_type = 'MENU';
    SELECT COUNT(*) INTO v_perm_count FROM sys_role_permission WHERE role_id = 1;

    RAISE NOTICE '========== 数据验证结果 ==========';
    RAISE NOTICE 'sys_menu 总数: % (目录: %, 菜单: %)', v_menu_count, v_dir_count, v_page_count;
    RAISE NOTICE 'admin(role_id=1) 权限数: %', v_perm_count;

    IF v_dir_count < 11 THEN
        RAISE WARNING '一级目录数量不足11个,请检查!';
    END IF;

    IF v_perm_count < v_menu_count THEN
        RAISE WARNING 'admin权限数少于菜单数,请检查!';
    END IF;

    RAISE NOTICE '=================================';
END $$;


COMMIT;

-- ===========================================================================
-- 执行完成
-- ===========================================================================
SELECT '云端数据库配置数据补全完成!' AS result;
