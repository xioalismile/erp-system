-- ============================================================
-- 144_version_split.sql
-- 版本划分重构（2026-10-10）：
--   ① 标准版新增「财务」子集（应收/应付/预收/预付/收款/付款/成本/报表）
--   ② 三版本口径重划（标准版 / CRM版 / 旗舰版）
--   ③ 版本隔离收口：不属于本版本的菜单/设置项一律不打标记为「通用」
-- 规则：version 取 STANDARD / CRM / ULTIMATE；NULL = 三档通用
--   菜单可见性 = (version IS NULL OR version='STANDARD') / +CRM / 全量(ULTIMATE)
-- 幂等：按 route_path 直接赋版本，重复执行结果一致
-- 执行范围：主库 + 所有 active 租户独立库 + 租户模板库（每库各跑一次）
-- ============================================================
BEGIN;

-- 0) 先清空“本不应带版本”的历史标记，避免旧数据残留（仅针对下方会被显式重设的路径）
--    说明：全部路径都在下面四个分组里被显式赋值，故这里无需额外清理。

-- ---------- ① 标准版（含标准版及以上）----------
UPDATE sys_menu SET version='STANDARD' WHERE route_path IN (
  -- 工作台
  '/dashboard',
  -- 基础资料
  '/basedata','/basedata/unit','/basedata/currency','/basedata/settle','/basedata/bank-account',
  '/basedata/price-level','/basedata/department','/basedata/employee','/basedata/warehouse',
  '/basedata/location','/basedata/material-category','/basedata/material','/basedata/customer',
  '/basedata/supplier','/basedata/payment-term','/basedata/help',
  -- 销售管理
  '/sale','/sale/orders','/sale/contracts','/sale/quotes','/sale/deliveries','/sale/returns','/sale/invoices',
  '/sale/report/analysis','/sale/report/sale-order-detail','/sale/report/sale-delivery-detail',
  '/sale/report/sale-invoice-detail','/sale/report/customer-statement','/sale/help',
  '/logistics/dashboard',
  -- 采购管理
  '/purchase','/purchase/requests','/purchase/contracts','/purchase/orders','/purchase/inbounds',
  '/purchase/returns','/purchase/invoices','/purchase/report/analysis','/purchase/report/purchase-request-detail',
  '/purchase/report/purchase-contract-detail','/purchase/report/purchase-order-detail',
  '/purchase/report/purchase-inbound-detail','/purchase/report/purchase-return-detail',
  '/purchase/report/purchase-invoice-detail','/purchase/report/supplier-statement','/purchase/help',
  -- 库存管理（不含生产领料单/生产领料单明细报表 —— 归旗舰版）
  '/inventory','/inventory/current','/inventory/batches','/inventory/ledger','/inventory/other-receipts',
  '/inventory/other-issues','/inventory/stock-transfers','/inventory/stock-checks','/inventory/help',
  '/inventory/report/analysis','/inventory/report/other-receipt-detail','/inventory/report/other-issue-detail',
  '/inventory/report/stock-transfer-detail','/inventory/report/stock-check-detail',
  -- 财务（本次新增进标准版：预收/预付/收款/付款/成本核算/报表）
  '/finance','/finance/sale-pre-receipts','/finance/purchase-pre-payments','/finance/receipt','/finance/payment',
  '/finance/cost-calculation','/finance/help',
  '/finance/report/analysis','/finance/report/ar-summary','/finance/report/ap-summary',
  -- 数据看板（按数据来源：销售/仓储/财务 三块）
  '/databoard/finance','/databoard/sale','/databoard/inventory',
  -- 设置中心（业务组里属于标准版的部分）
  '/system/settings?item=inventory-warning','/system/settings?item=sale-price',
  '/system/settings?item=purchase-price','/system/settings?item=commission-rate',
  '/system/settings?item=logistics-config'
);

-- ---------- ② CRM版 ----------
UPDATE sys_menu SET version='CRM' WHERE route_path IN (
  '/crm','/crm/dashboard','/crm/customer','/crm/pool','/crm/lead','/crm/opportunity','/crm/funnel',
  '/crm/contact','/crm/follow-up','/crm/warning','/crm/complaint','/crm/survey','/crm/sales-target',
  '/crm/sales-activity','/crm/report','/crm/help',
  '/acquisition','/acquisition/task','/acquisition/result','/acquisition/login','/acquisition/help',
  '/system/settings?item=crm-warning'
);

-- ---------- ③ 旗舰版 ----------
UPDATE sys_menu SET version='ULTIMATE' WHERE route_path IN (
  -- 平台管理
  '/admin','/admin/tenant/manage','/admin/deploy/manage',
  -- 生产管理（含组装/拆卸/车间进度跟踪/工序质检报表）
  '/production','/production/routings','/production/mrp','/production/workorders','/production/schedule-board',
  '/production/progress-tracking','/production/assemble','/production/disassemble','/production/returns',
  '/production/supplements','/production/receipts','/production/help',
  '/production/report/analysis','/production/qc-record',
  -- 委外管理
  '/subcontract','/subcontract/orders','/subcontract/picks','/subcontract/inbounds','/subcontract/supplements',
  '/subcontract/returns','/subcontract/help',
  -- 打样管理
  '/sample','/sample/list','/freesample/list',
  -- BOM / 质检模板
  '/masterdata/bom','/masterdata/qc-template',
  -- 库存下的生产领料单及其报表
  '/inventory/material-requisitions','/inventory/report/material-requisition-detail',
  -- 财务里未纳入标准版的部分
  '/finance/operation-expense','/finance/cost-config','/finance/wage-settlement',
  -- 数据看板·经营看板（含生产/质检/人力）
  '/databoard/operation'
);

-- ---------- ④ 通用（三档都可见）----------
UPDATE sys_menu SET version=NULL WHERE route_path IN (
  '/databoard',
  '/report','/report/custom','/report/dashboard',
  '/system/settings','/system/ai/tenant-key','/system/ai/tenant-quota','/system/ai/user-quota',
  '/system/settings?item=basic-info','/system/settings?item=sys-param','/system/settings?item=role-perm',
  '/system/settings?item=employee-perm','/system/settings?item=data-maintain','/system/settings?item=i18n-manager',
  '/system/settings?item=menu-name','/system/settings?item=theme-setting','/system/settings?item=ui-size',
  '/system/settings?item=notification-setting','/system/settings?item=serial-no','/system/settings?item=approval',
  '/system/settings?item=batch-no','/system/settings?item=print-setting','/system/settings?item=custom-field',
  '/system/settings?item=operate-log',
  '/system/settings?item=ai-overview','/system/settings?item=ai-assistant-settings',
  '/system/settings?item=ai-disclaimer','/system/settings?item=ai-data-policy',
  '/system/settings?item=ai-tool-permission','/system/settings?item=ai-token-usage',
  '/system/settings?item=ai-knowledge'
);

COMMIT;

-- 复核（人工核对用）：
-- SELECT COALESCE(version,'(通用)') AS v, count(*) FROM sys_menu
--  WHERE route_path IS NOT NULL GROUP BY version ORDER BY 1;
