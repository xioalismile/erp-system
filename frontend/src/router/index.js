import { createRouter, createWebHistory } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useTagsViewStore } from '@/stores/tagsView'
import { isAddPagePath } from '@/utils/addPage'
import { HIDDEN_BILL_ROUTE_PREFIXES } from '@/config/hiddenBills'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/tenant-select', component: () => import('../views/TenantSelect.vue') },
  { path: '/403', component: () => import('../views/Forbidden.vue') },
  // 移动端（微信小程序 web-view / 手机浏览器）：扫码 → 工序操作，全屏无侧边栏
  { path: '/m/scan', name: 'MobileScan', component: () => import('../views/mobile/MobileScan.vue'), meta: { title: '扫码' } },
  { path: '/m/operation/:id', name: 'MobileOperation', component: () => import('../views/mobile/MobileOperation.vue'), meta: { title: '工序操作' } },
  { path: '/redirect/:path(.*)*', component: () => import('../views/Redirect.vue') },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      // 工作台（固定标签，不可关闭）
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/dashboard/Dashboard.vue'), meta: { title: '工作台', affix: true } },

      // ========== 数据看板 ==========
      { path: 'databoard/finance', name: 'DataBoardFinance', component: () => import('../views/databoard/FinanceBoard.vue'), meta: { title: '财务看板' } },
      { path: 'databoard/sale', name: 'DataBoardSale', component: () => import('../views/databoard/SaleBoard.vue'), meta: { title: '销售看板' } },
      { path: 'databoard/inventory', name: 'DataBoardInventory', component: () => import('../views/databoard/InventoryBoard.vue'), meta: { title: '仓储看板' } },
      { path: 'databoard/operation', name: 'DataBoardOperation', component: () => import('../views/databoard/OperationBoard.vue'), meta: { title: '经营看板' } },
      // SPC质量看板 + 看板页设计器（2026-11-05）
      // 2026-11-06：独立「自定义看板」列表页已下线，自定义能力并入四张现有看板
      { path: 'databoard/spc', name: 'DataBoardSpc', component: () => import('../views/databoard/SpcBoard.vue'), meta: { title: 'SPC质量看板' } },
      { path: 'databoard/custom/designer', name: 'DataBoardCustomDesigner', component: () => import('../views/databoard/CustomBoardDesigner.vue'), meta: { title: '自定义看板设计器' } },

      // ========== 基础资料 ==========
      { path: 'basedata/help', name: 'BaseDataHelp', component: () => import('../views/basedata/BaseDataHelp.vue'), meta: { title: '基础资料操作手册' } },
      { path: 'basedata/unit', name: 'Unit', component: () => import('../views/basedata/UnitList.vue'), meta: { title: '计量单位' } },
      { path: 'basedata/currency', name: 'Currency', component: () => import('../views/basedata/CurrencyList.vue'), meta: { title: '币种' } },
      { path: 'basedata/settle', name: 'Settle', component: () => import('../views/basedata/SettleList.vue'), meta: { title: '结算方式' } },
      { path: 'basedata/bank-account', name: 'BasedataBankAccount', component: () => import('../views/finance/BankAccountList.vue'), meta: { title: '银行账户' } },
      { path: 'basedata/price-level', name: 'PriceLevel', component: () => import('../views/basedata/PriceLevelList.vue'), meta: { title: '价格等级' } },
      { path: 'basedata/department', name: 'Department', component: () => import('../views/basedata/DepartmentList.vue'), meta: { title: '部门档案' } },
      { path: 'basedata/employee', name: 'Employee', component: () => import('../views/basedata/EmployeeList.vue'), meta: { title: '员工档案' } },
      { path: 'basedata/warehouse', name: 'Warehouse', component: () => import('../views/basedata/WarehouseList.vue'), meta: { title: '仓库档案' } },
      { path: 'basedata/location', name: 'Location', component: () => import('../views/basedata/LocationList.vue'), meta: { title: '货位档案' } },
      { path: 'basedata/material-category', name: 'MaterialCategory', component: () => import('../views/basedata/MaterialCategoryList.vue'), meta: { title: '物料分类' } },
      { path: 'basedata/material', name: 'Material', component: () => import('../views/basedata/MaterialList.vue'), meta: { title: '物料档案' } },
      { path: 'basedata/material/new', name: 'MaterialNew', component: () => import('../views/basedata/MaterialEdit.vue'), meta: { title: '新增物料' } },
      { path: 'basedata/material/edit/:id', name: 'MaterialEdit', component: () => import('../views/basedata/MaterialEdit.vue'), meta: { title: '编辑物料' } },
      { path: 'basedata/customer', name: 'Customer', component: () => import('../views/basedata/CustomerList.vue'), meta: { title: '客户档案' } },
      { path: 'basedata/supplier', name: 'Supplier', component: () => import('../views/basedata/SupplierList.vue'), meta: { title: '供应商档案' } },
      { path: 'basedata/account', name: 'Account', component: () => import('../views/basedata/AccountList.vue'), meta: { title: '会计科目' } },
      { path: 'basedata/material-code-rule', name: 'MaterialCodeRule', component: () => import('../views/basedata/MaterialCodeRuleList.vue'), meta: { title: '物料编码规则' } },

      // 基础资料 - 新增/编辑独立页面路由
      { path: 'basedata/unit/new', name: 'UnitNew', component: () => import('../views/basedata/UnitEdit.vue'), meta: { title: '新增计量单位' } },
      { path: 'basedata/unit/edit/:id', name: 'UnitEdit', component: () => import('../views/basedata/UnitEdit.vue'), meta: { title: '编辑计量单位' } },
      { path: 'basedata/currency/new', name: 'CurrencyNew', component: () => import('../views/basedata/CurrencyEdit.vue'), meta: { title: '新增币种' } },
      { path: 'basedata/currency/edit/:id', name: 'CurrencyEdit', component: () => import('../views/basedata/CurrencyEdit.vue'), meta: { title: '编辑币种' } },
      { path: 'basedata/settle/new', name: 'SettleNew', component: () => import('../views/basedata/SettleEdit.vue'), meta: { title: '新增结算方式' } },
      { path: 'basedata/settle/edit/:id', name: 'SettleEdit', component: () => import('../views/basedata/SettleEdit.vue'), meta: { title: '编辑结算方式' } },
      { path: 'basedata/bank-account/new', name: 'BasedataBankAccountNew', component: () => import('../views/finance/BankAccountEdit.vue'), meta: { title: '新增银行账户' } },
      { path: 'basedata/bank-account/edit/:id', name: 'BasedataBankAccountEdit', component: () => import('../views/finance/BankAccountEdit.vue'), meta: { title: '编辑银行账户' } },
      { path: 'basedata/price-level/new', name: 'PriceLevelNew', component: () => import('../views/basedata/PriceLevelEdit.vue'), meta: { title: '新增价格等级' } },
      { path: 'basedata/price-level/edit/:id', name: 'PriceLevelEdit', component: () => import('../views/basedata/PriceLevelEdit.vue'), meta: { title: '编辑价格等级' } },
      { path: 'basedata/department/new', name: 'DepartmentNew', component: () => import('../views/basedata/DepartmentEdit.vue'), meta: { title: '新增部门档案' } },
      { path: 'basedata/department/edit/:id', name: 'DepartmentEdit', component: () => import('../views/basedata/DepartmentEdit.vue'), meta: { title: '编辑部门档案' } },
      { path: 'basedata/employee/new', name: 'EmployeeNew', component: () => import('../views/basedata/EmployeeEdit.vue'), meta: { title: '新增员工档案' } },
      { path: 'basedata/employee/edit/:id', name: 'EmployeeEdit', component: () => import('../views/basedata/EmployeeEdit.vue'), meta: { title: '编辑员工档案' } },
      { path: 'basedata/warehouse/new', name: 'WarehouseNew', component: () => import('../views/basedata/WarehouseEdit.vue'), meta: { title: '新增仓库档案' } },
      { path: 'basedata/warehouse/edit/:id', name: 'WarehouseEdit', component: () => import('../views/basedata/WarehouseEdit.vue'), meta: { title: '编辑仓库档案' } },
      { path: 'basedata/location/new', name: 'LocationNew', component: () => import('../views/basedata/LocationEdit.vue'), meta: { title: '新增货位档案' } },
      { path: 'basedata/location/edit/:id', name: 'LocationEdit', component: () => import('../views/basedata/LocationEdit.vue'), meta: { title: '编辑货位档案' } },
      { path: 'basedata/material-category/new', name: 'MaterialCategoryNew', component: () => import('../views/basedata/MaterialCategoryEdit.vue'), meta: { title: '新增物料分类' } },
      { path: 'basedata/material-category/edit/:id', name: 'MaterialCategoryEdit', component: () => import('../views/basedata/MaterialCategoryEdit.vue'), meta: { title: '编辑物料分类' } },
      { path: 'basedata/customer/new', name: 'CustomerNew', component: () => import('../views/basedata/CustomerEdit.vue'), meta: { title: '新增客户档案' } },
      { path: 'basedata/customer/edit/:id', name: 'CustomerEdit', component: () => import('../views/basedata/CustomerEdit.vue'), meta: { title: '编辑客户档案' } },
      { path: 'basedata/supplier/new', name: 'SupplierNew', component: () => import('../views/basedata/SupplierEdit.vue'), meta: { title: '新增供应商档案' } },
      { path: 'basedata/supplier/edit/:id', name: 'SupplierEdit', component: () => import('../views/basedata/SupplierEdit.vue'), meta: { title: '编辑供应商档案' } },
      { path: 'basedata/account/new', name: 'AccountNew', component: () => import('../views/basedata/AccountEdit.vue'), meta: { title: '新增会计科目' } },
      { path: 'basedata/account/edit/:id', name: 'AccountEdit', component: () => import('../views/basedata/AccountEdit.vue'), meta: { title: '编辑会计科目' } },

      // ========== 获客管理 ==========
      { path: 'acquisition/task', name: 'AcquisitionTask', component: () => import('../views/acquisition/AcquisitionTaskList.vue'), meta: { title: '获客任务' } },
      { path: 'acquisition/result', name: 'AcquisitionResult', component: () => import('../views/acquisition/AcquisitionResultList.vue'), meta: { title: '采集结果审核' } },
      { path: 'acquisition/login', name: 'AcquisitionLogin', component: () => import('../views/acquisition/AcquisitionLogin.vue'), meta: { title: '企查查登录' } },
      { path: 'acquisition/help', name: 'AcquisitionHelp', component: () => import('../views/acquisition/AcquisitionHelp.vue'), meta: { title: '获客管理操作手册' } },

      // ========== 平台管理：客户管理（多租户）—— 自营+客户包都开放 ==========
      // 构建期开关：VITE_PLATFORM_ADMIN=true 时注册路由；未设置则动态 import 不可达 → 不进产物
      ...(import.meta.env.VITE_PLATFORM_ADMIN === 'true' ? [
        { path: 'admin/tenant/manage', name: 'TenantManage', component: () => import('../views/admin/TenantManage.vue'), meta: { title: '客户管理' } }
      ] : []),

      // ========== 平台管理：客户升级部署 —— 仅自营环境（客户包不设置 VITE_DEPLOY_ADMIN，防覆盖/删除用户数据） ==========
      ...(import.meta.env.VITE_DEPLOY_ADMIN === 'true' ? [
        { path: 'admin/deploy/manage', name: 'DeployManage', component: () => import('../views/admin/DeployManage.vue'), meta: { title: '客户升级部署' } }
      ] : []),

      // ========== AI 数据使用政策 ==========
      { path: 'system/ai-data-policy', name: 'AiDataPolicy', component: () => import('../views/system/AiDataPolicy.vue'), meta: { title: 'AI数据使用政策' } },

      // ========== AI 用量管理 ==========
      { path: 'system/ai-token-usage', name: 'AiTokenUsage', component: () => import('../views/system/AiTokenUsage.vue'), meta: { title: 'AI用量管理' } },

      // ========== AI知识库管理 ==========
      { path: 'system/ai-knowledge', name: 'AiKnowledge', component: () => import('../views/system/AiKnowledge.vue'), meta: { title: 'AI知识库管理' } },

      // ========== 客户管理 ==========
      { path: 'crm/dashboard', name: 'CrmDashboard', component: () => import('../views/crm/CrmDashboard.vue'), meta: { title: 'CRM仪表盘' } },
      { path: 'crm/customer', name: 'CrmCustomer', component: () => import('../views/crm/CrmCustomerList.vue'), meta: { title: '我的私海' } },
      { path: 'crm/private-pool', redirect: { name: 'CrmCustomer' } },
      { path: 'crm/pool', name: 'CrmPool', component: () => import('../views/crm/CustomerPoolList.vue'), meta: { title: '公海池' } },
      { path: 'crm/lead', name: 'CrmLead', component: () => import('../views/crm/LeadList.vue'), meta: { title: '销售线索' } },
      { path: 'crm/opportunity', name: 'CrmOpportunity', component: () => import('../views/crm/OpportunityList.vue'), meta: { title: '商机管理' } },
      { path: 'crm/funnel', name: 'CrmFunnel', component: () => import('../views/crm/SalesFunnelBoard.vue'), meta: { title: '销售漏斗' } },
      { path: 'crm/contact', name: 'CrmContact', component: () => import('../views/crm/ContactList.vue'), meta: { title: '联系人管理' } },
      { path: 'crm/sales-activity', name: 'CrmSalesActivity', component: () => import('../views/crm/SalesActivity.vue'), meta: { title: '销售过程' } },
      { path: 'crm/follow-up', name: 'CrmFollowUp', component: () => import('../views/crm/FollowUpList.vue'), meta: { title: '跟进记录' } },
      { path: 'crm/warning', name: 'CrmWarning', component: () => import('../views/crm/CustomerWarningList.vue'), meta: { title: '跟进提醒' } },
      { path: 'crm/complaint', name: 'CrmComplaint', component: () => import('../views/crm/ComplaintList.vue'), meta: { title: '客户投诉' } },
      { path: 'crm/survey', name: 'CrmSurvey', component: () => import('../views/crm/SurveyList.vue'), meta: { title: '满意度调查' } },
      { path: 'crm/sales-target', name: 'CrmSalesTarget', component: () => import('../views/crm/SalesTargetList.vue'), meta: { title: '销售目标' } },
      { path: 'crm/report', name: 'CrmReportCenter', component: () => import('../views/crm/CrmReportCenter.vue'), meta: { title: 'CRM报表中心' } },
      { path: 'crm/help', name: 'CrmHelp', component: () => import('../views/crm/CrmHelp.vue'), meta: { title: '操作手册' } },

      // ========== 销售管理 ==========
      { path: 'sale/price-strategy', name: 'SalePriceStrategy', component: () => import('../views/price/SalePriceStrategyList.vue'), meta: { title: '销售价格策略' } },
      { path: 'sale/price-strategy/new', name: 'SalePriceStrategyNew', component: () => import('../views/price/SalePriceStrategyEdit.vue'), meta: { title: '新增销售价格策略' } },
      { path: 'sale/price-strategy/:id', name: 'SalePriceStrategyEdit', component: () => import('../views/price/SalePriceStrategyEdit.vue'), meta: { title: '编辑销售价格策略' } },
      { path: 'sale/contracts', name: 'SaleContracts', component: () => import('../views/sale/SaleContractList.vue'), meta: { title: '销售合同' } },
      { path: 'sale/contracts/new', name: 'SaleContractNew', component: () => import('../views/sale/SaleContractEdit.vue'), meta: { title: '新增合同' } },
      { path: 'sale/contracts/:id', name: 'SaleContractEdit', component: () => import('../views/sale/SaleContractEdit.vue'), meta: { title: '编辑合同' } },
      { path: 'sale/orders', name: 'SaleOrders', component: () => import('../views/sale/SaleOrderList.vue'), meta: { title: '销售订单' } },
      { path: 'sale/orders/new', name: 'SaleOrderNew', component: () => import('../views/sale/SaleOrderEdit.vue'), meta: { title: '新增订单' } },
      { path: 'sale/orders/:id', name: 'SaleOrderEdit', component: () => import('../views/sale/SaleOrderEdit.vue'), meta: { title: '编辑订单' } },
      { path: 'sale/quotes', name: 'SaleQuotes', component: () => import('../views/sale/SaleQuoteList.vue'), meta: { title: '销售报价单' } },
      { path: 'sale/quotes/new', name: 'SaleQuoteNew', component: () => import('../views/sale/SaleQuoteEdit.vue'), meta: { title: '新增报价单' } },
      { path: 'sale/quotes/:id', name: 'SaleQuoteEdit', component: () => import('../views/sale/SaleQuoteEdit.vue'), meta: { title: '编辑报价单' } },
      { path: 'sale/deliveries', name: 'SaleDeliveries', component: () => import('../views/sale/SaleDeliveryList.vue'), meta: { title: '销售出库单' } },
      { path: 'sale/deliveries/new', name: 'SaleDeliveryNew', component: () => import('../views/sale/SaleDeliveryEdit.vue'), meta: { title: '新增出库单' } },
      { path: 'sale/deliveries/:id', name: 'SaleDeliveryEdit', component: () => import('../views/sale/SaleDeliveryEdit.vue'), meta: { title: '编辑出库单' } },
      { path: 'sale/returns', name: 'SaleReturns', component: () => import('../views/sale/SaleReturnList.vue'), meta: { title: '销售退货' } },
      { path: 'sale/returns/new', name: 'SaleReturnNew', component: () => import('../views/sale/SaleReturnEdit.vue'), meta: { title: '新增退货' } },
      { path: 'sale/returns/:id', name: 'SaleReturnEditPage', component: () => import('../views/sale/SaleReturnEdit.vue'), meta: { title: '编辑退货' } },
      { path: 'sale/exchanges', name: 'SaleExchanges', component: () => import('../views/sale/SaleExchangeList.vue'), meta: { title: '销售换货单' } },
      { path: 'sale/exchanges/:id', name: 'SaleExchangeEdit', component: () => import('../views/sale/SaleExchangeEdit.vue'), meta: { title: '编辑换货单' } },
      // 售后维修单
      { path: 'sale/repairs', name: 'SaleRepairs', component: () => import('../views/sale/SaleRepairList.vue'), meta: { title: '售后维修单' } },
      { path: 'sale/repairs/new', name: 'SaleRepairNew', component: () => import('../views/sale/SaleRepairEdit.vue'), meta: { title: '新增售后维修单' } },
      { path: 'sale/repairs/:id', name: 'SaleRepairEdit', component: () => import('../views/sale/SaleRepairEdit.vue'), meta: { title: '编辑售后维修单' } },
      // 物流监控看板
      { path: 'logistics/dashboard', name: 'LogisticsDashboard', component: () => import('../views/logistics/LogisticsDashboard.vue'), meta: { title: '物流监控看板' } },
      { path: 'sale/invoices', name: 'SaleInvoices', component: () => import('../views/sale/SaleInvoiceList.vue'), meta: { title: '销售发票' } },
      { path: 'sale/invoices/new', name: 'SaleInvoiceNew', component: () => import('../views/sale/SaleInvoiceEdit.vue'), meta: { title: '新增发票' } },
      { path: 'sale/invoices/:id', name: 'SaleInvoiceEditPage', component: () => import('../views/sale/SaleInvoiceEdit.vue'), meta: { title: '编辑发票' } },
      // 销售报表 - 分析报表
      { path: 'sale/report/analysis', name: 'SaleReportAnalysis', component: () => import('../views/sale/SaleReportCenter.vue'), meta: { title: '销售分析报表' } },
      // 销售单据明细报表（2026-09-22 新增）：一行 = 一条商品明细
      { path: 'sale/report/sale-order-detail', name: 'SaleOrderDetailReport', component: () => import('../views/sale/SaleOrderDetailReport.vue'), meta: { title: '销售订单明细报表' } },
      { path: 'sale/report/sale-delivery-detail', name: 'SaleDeliveryDetailReport', component: () => import('../views/sale/SaleDeliveryDetailReport.vue'), meta: { title: '销售出库明细报表' } },
      { path: 'sale/report/sale-invoice-detail', name: 'SaleInvoiceDetailReport', component: () => import('../views/sale/SaleInvoiceDetailReport.vue'), meta: { title: '销售发票明细报表' } },
      // 销售发票报表
      { path: 'sale/report/invoice', name: 'SaleInvoiceReport', component: () => import('../views/sale/SaleInvoiceReport.vue'), meta: { title: '销售发票报表' } },
      { path: 'sale/report/customer-statement', name: 'SaleReportCustomerStatement', component: () => import('../views/report/CustomerStatement.vue'), meta: { title: '客户对账单' } },
      { path: 'sale/help', name: 'SaleHelp', component: () => import('../views/sale/SaleHelp.vue'), meta: { title: '销售管理操作手册' } },

      // ========== 采购管理 ==========
      { path: 'purchase/price-strategy', name: 'PurchasePriceStrategy', component: () => import('../views/price/PurchasePriceStrategyList.vue'), meta: { title: '采购价格策略' } },
      { path: 'purchase/price-strategy/new', name: 'PurchasePriceStrategyNew', component: () => import('../views/price/PurchasePriceStrategyEdit.vue'), meta: { title: '新增采购价格策略' } },
      { path: 'purchase/price-strategy/:id', name: 'PurchasePriceStrategyEdit', component: () => import('../views/price/PurchasePriceStrategyEdit.vue'), meta: { title: '编辑采购价格策略' } },
      { path: 'purchase/contracts', name: 'PurchaseContracts', component: () => import('../views/purchase/PurchaseContractList.vue'), meta: { title: '采购合同' } },
      { path: 'purchase/contracts/new', name: 'PurchaseContractNew', component: () => import('../views/purchase/PurchaseContractEdit.vue'), meta: { title: '新增采购合同' } },
      { path: 'purchase/contracts/:id', name: 'PurchaseContractEdit', component: () => import('../views/purchase/PurchaseContractEdit.vue'), meta: { title: '编辑采购合同' } },
      { path: 'purchase/orders', name: 'PurchaseOrders', component: () => import('../views/purchase/PurchaseOrderList.vue'), meta: { title: '采购订单' } },
      { path: 'purchase/orders/new', name: 'PurchaseOrderNew', component: () => import('../views/purchase/PurchaseOrderEdit.vue'), meta: { title: '新增采购单' } },
      { path: 'purchase/orders/:id', name: 'PurchaseOrderEdit', component: () => import('../views/purchase/PurchaseOrderEdit.vue'), meta: { title: '编辑采购单' } },
      // 采购申请单
      { path: 'purchase/requests', name: 'PurchaseRequests', component: () => import('../views/purchase/PurchaseRequestList.vue'), meta: { title: '采购申请单' } },
      { path: 'purchase/requests/new', name: 'PurchaseRequestNew', component: () => import('../views/purchase/PurchaseRequestEdit.vue'), meta: { title: '新增采购申请单' } },
      { path: 'purchase/requests/:id', name: 'PurchaseRequestEdit', component: () => import('../views/purchase/PurchaseRequestEdit.vue'), meta: { title: '编辑采购申请单' } },
      // 采购入库单
      { path: 'purchase/inbounds', name: 'PurchaseInbounds', component: () => import('../views/purchase/PurchaseInboundList.vue'), meta: { title: '采购入库单' } },
      { path: 'purchase/inbounds/new', name: 'PurchaseInboundNew', component: () => import('../views/purchase/PurchaseInboundEdit.vue'), meta: { title: '新增采购入库单' } },
      { path: 'purchase/inbounds/:id', name: 'PurchaseInboundEdit', component: () => import('../views/purchase/PurchaseInboundEdit.vue'), meta: { title: '编辑采购入库单' } },
      // 采购退货单
      { path: 'purchase/returns', name: 'PurchaseReturns', component: () => import('../views/purchase/PurchaseReturnList.vue'), meta: { title: '采购退货单' } },
      { path: 'purchase/returns/new', name: 'PurchaseReturnNew', component: () => import('../views/purchase/PurchaseReturnEdit.vue'), meta: { title: '新增采购退货单' } },
      { path: 'purchase/returns/:id', name: 'PurchaseReturnEdit', component: () => import('../views/purchase/PurchaseReturnEdit.vue'), meta: { title: '编辑采购退货单' } },
      // 采购发票
      { path: 'purchase/invoices', name: 'PurchaseInvoices', component: () => import('../views/purchase/PurchaseInvoiceList.vue'), meta: { title: '采购发票' } },
      { path: 'purchase/invoices/new', name: 'PurchaseInvoiceNew', component: () => import('../views/purchase/PurchaseInvoiceEdit.vue'), meta: { title: '新增采购发票' } },
      { path: 'purchase/invoices/:id', name: 'PurchaseInvoiceEdit', component: () => import('../views/purchase/PurchaseInvoiceEdit.vue'), meta: { title: '编辑采购发票' } },
      // 采购报表 - 分析报表
      { path: 'purchase/report/analysis', name: 'PurchaseReportAnalysis', component: () => import('../views/purchase/PurchaseReportCenter.vue'), meta: { title: '采购分析报表' } },
      // 采购单据明细报表（2026-09-22 新增，与销售明细报表同一套通用引擎/页面组件）：一行 = 一条商品明细
      { path: 'purchase/report/purchase-request-detail', name: 'PurchaseRequestDetailReport', component: () => import('../views/purchase/PurchaseRequestDetailReport.vue'), meta: { title: '采购申请单明细报表' } },
      { path: 'purchase/report/purchase-contract-detail', name: 'PurchaseContractDetailReport', component: () => import('../views/purchase/PurchaseContractDetailReport.vue'), meta: { title: '采购合同明细报表' } },
      { path: 'purchase/report/purchase-order-detail', name: 'PurchaseOrderDetailReport', component: () => import('../views/purchase/PurchaseOrderDetailReport.vue'), meta: { title: '采购订单明细报表' } },
      { path: 'purchase/report/purchase-inbound-detail', name: 'PurchaseInboundDetailReport', component: () => import('../views/purchase/PurchaseInboundDetailReport.vue'), meta: { title: '采购入库单明细报表' } },
      { path: 'purchase/report/purchase-return-detail', name: 'PurchaseReturnDetailReport', component: () => import('../views/purchase/PurchaseReturnDetailReport.vue'), meta: { title: '采购退货单明细报表' } },
      { path: 'purchase/report/purchase-invoice-detail', name: 'PurchaseInvoiceDetailReport', component: () => import('../views/purchase/PurchaseInvoiceDetailReport.vue'), meta: { title: '采购发票明细报表' } },
      // 采购发票报表
      { path: 'purchase/report/invoice', name: 'PurchaseInvoiceReport', component: () => import('../views/purchase/PurchaseInvoiceReport.vue'), meta: { title: '采购发票报表' } },
      { path: 'purchase/report/supplier-statement', name: 'PurchaseReportSupplierStatement', component: () => import('../views/report/SupplierStatement.vue'), meta: { title: '供应商对账单' } },
      { path: 'purchase/help', name: 'PurchaseHelp', component: () => import('../views/purchase/PurchaseHelp.vue'), meta: { title: '采购管理操作手册' } },

      // ========== 库存管理 ==========
      { path: 'inventory', name: 'Inventory', component: () => import('../views/inventory/InventoryList.vue'), meta: { title: '库存管理' } },
      { path: 'inventory/current', name: 'InventoryCurrent', component: () => import('../views/inventory/InventoryList.vue'), meta: { title: '即时库存' } },
      // 2026-09-20 下线 inventory/warehouses、inventory/locations：与基础资料→仓库档案/货位档案重复，
      // 且原页面字段整页错配（对应 .vue 已删除）。按守则04 · 2.7.2-5 保留重定向兼容：
      // 老收藏夹 / 历史通知链接不落空白页，直接进入正确的档案页。菜单清理见 deploy/sql/65_*.sql。
      { path: 'inventory/warehouses', redirect: '/basedata/warehouse' },
      { path: 'inventory/locations', redirect: '/basedata/location' },
      { path: 'inventory/batches', name: 'Batches', component: () => import('../views/inventory/BatchList.vue'), meta: { title: '批次库存' } },
      { path: 'inventory/ledger', name: 'InventoryLedger', component: () => import('../views/inventory/StockFlowList.vue'), meta: { title: '库存流水' } },
      { path: 'inventory/other-receipts', name: 'OtherReceipts', component: () => import('../views/inventory/OtherReceiptList.vue'), meta: { title: '其他入库单' } },
      { path: 'inventory/other-issues', name: 'OtherIssues', component: () => import('../views/inventory/OtherIssueList.vue'), meta: { title: '其他出库单' } },
      { path: 'inventory/other-issues/edit', name: 'OtherIssueNew', component: () => import('../views/inventory/OtherIssueEdit.vue'), meta: { title: '新增其他出库单' } },
      { path: 'inventory/other-issues/edit/:id', name: 'OtherIssueEdit', component: () => import('../views/inventory/OtherIssueEdit.vue'), meta: { title: '编辑其他出库单' } },
      { path: 'inventory/stock-transfers', name: 'StockTransfers', component: () => import('../views/inventory/StockTransferList.vue'), meta: { title: '库存调拨单' } },
      { path: 'inventory/stock-transfers/edit', name: 'StockTransferNew', component: () => import('../views/inventory/StockTransferEdit.vue'), meta: { title: '新增库存调拨单' } },
      { path: 'inventory/stock-transfers/edit/:id', name: 'StockTransferEdit', component: () => import('../views/inventory/StockTransferEdit.vue'), meta: { title: '编辑库存调拨单' } },
      { path: 'inventory/stock-checks', name: 'StockChecks', component: () => import('../views/inventory/StockCheckList.vue'), meta: { title: '库存盘点单' } },
      { path: 'inventory/stock-checks/edit', name: 'StockCheckNew', component: () => import('../views/inventory/StockCheckEdit.vue'), meta: { title: '新增库存盘点单' } },
      { path: 'inventory/stock-checks/edit/:id', name: 'StockCheckEdit', component: () => import('../views/inventory/StockCheckEdit.vue'), meta: { title: '编辑库存盘点单' } },
      { path: 'inventory/other-receipts/edit', name: 'OtherReceiptNew', component: () => import('../views/inventory/OtherReceiptEdit.vue'), meta: { title: '新增其他入库单' } },
      { path: 'inventory/other-receipts/edit/:id', name: 'OtherReceiptEdit', component: () => import('../views/inventory/OtherReceiptEdit.vue'), meta: { title: '编辑其他入库单' } },
      { path: 'inventory/material-requisitions', name: 'MaterialRequisitions', component: () => import('../views/inventory/MaterialRequisitionList.vue'), meta: { title: '生产领料单' } },
      { path: 'inventory/material-requisitions/edit', name: 'MaterialRequisitionNew', component: () => import('../views/inventory/MaterialRequisitionEdit.vue'), meta: { title: '新增生产领料单' } },
      { path: 'inventory/material-requisitions/edit/:id', name: 'MaterialRequisitionEdit', component: () => import('../views/inventory/MaterialRequisitionEdit.vue'), meta: { title: '编辑生产领料单' } },
      // 库存报表 - 分析报表
      { path: 'inventory/report/analysis', name: 'InventoryReportAnalysis', component: () => import('../views/inventory/InventoryReportCenter.vue'), meta: { title: '库存分析报表' } },
      // 库存报表 - 单据明细报表（5 个；与销售/采购明细报表共用同一个通用组件）
      { path: 'inventory/report/other-receipt-detail', name: 'OtherReceiptDetailReport', component: () => import('../views/inventory/OtherReceiptDetailReport.vue'), meta: { title: '其他入库单明细报表' } },
      { path: 'inventory/report/other-issue-detail', name: 'OtherIssueDetailReport', component: () => import('../views/inventory/OtherIssueDetailReport.vue'), meta: { title: '其他出库单明细报表' } },
      { path: 'inventory/report/stock-transfer-detail', name: 'StockTransferDetailReport', component: () => import('../views/inventory/StockTransferDetailReport.vue'), meta: { title: '库存调拨单明细报表' } },
      { path: 'inventory/report/stock-check-detail', name: 'StockCheckDetailReport', component: () => import('../views/inventory/StockCheckDetailReport.vue'), meta: { title: '库存盘点单明细报表' } },
      { path: 'inventory/report/material-requisition-detail', name: 'MaterialRequisitionDetailReport', component: () => import('../views/inventory/MaterialRequisitionDetailReport.vue'), meta: { title: '生产领料单明细报表' } },
      { path: 'inventory/help', name: 'InventoryHelp', component: () => import('../views/inventory/InventoryHelp.vue'), meta: { title: '库存管理操作手册' } },

      // ========== 生产管理 ==========
      { path: 'masterdata/bom', name: 'BomList', component: () => import('../views/masterdata/BomList.vue'), meta: { title: 'BOM清单' } },
      { path: 'masterdata/bom/new', name: 'BomNew', component: () => import('../views/masterdata/BomEdit.vue'), meta: { title: '新增BOM' } },
      { path: 'masterdata/bom/:id', name: 'BomEdit', component: () => import('../views/masterdata/BomEdit.vue'), meta: { title: '编辑BOM' } },
      // 基础资料 → 质检模板（第 12 期）：route_path 与 SQL deploy/sql/123_qc.sql / menuConfig.js 严格一致
      { path: 'masterdata/qc-template', name: 'QcTemplateList', component: () => import('../views/masterdata/QcTemplateList.vue'), meta: { title: '质检模板' } },
      { path: 'production/work-center', name: 'WorkCenters', component: () => import('../views/production/WorkCenterList.vue'), meta: { title: '工作中心' } },
      { path: 'production/workstation', name: 'Workstations', component: () => import('../views/production/WorkstationList.vue'), meta: { title: '工序/工位' } },
      { path: 'production/routings', name: 'Routings', component: () => import('../views/production/RoutingList.vue'), meta: { title: '工艺路线' } },
      { path: 'production/workorders', name: 'WorkOrders', component: () => import('../views/production/WorkOrderList.vue'), meta: { title: '生产工单' } },
      { path: 'production/workorders/new', name: 'WorkOrderNew', component: () => import('../views/production/WorkOrderEdit.vue'), meta: { title: '新增生产工单' } },
      { path: 'production/workorders/:id', name: 'WorkOrderDetail', component: () => import('../views/production/WorkOrderEdit.vue'), meta: { title: '工单详情' } },
      { path: 'production/schedule-board', name: 'ProductionScheduleBoard', component: () => import('../views/production/ProductionScheduleBoard.vue'), meta: { title: '车间排程看板' } },
      // 第 16 期：车间进度跟踪从排程看板拆出为独立页；route_path 与 sys_menu / menuConfig.js 严格一致
      { path: 'production/progress-tracking', name: 'ProductionProgressTracking', component: () => import('../views/production/ProductionProgressTracking.vue'), meta: { title: '车间进度跟踪' } },
      { path: 'production/receipts', name: 'ProductionReceipts', component: () => import('../views/production/ProductionReceiptList.vue'), meta: { title: '生产入库单' } },
      // 化妆品批记录完整闭环（2026-11-06）：批记录中心（总查询门面）+ 批次追溯树
      { path: 'production/batch-record-center', name: 'BatchRecordCenter', component: () => import('../views/production/BatchRecordCenter.vue'), meta: { title: '批记录中心' } },
      { path: 'production/batch-record-center/trace/:batchId', name: 'BatchTraceView', component: () => import('../views/production/BatchTraceView.vue'), meta: { title: '批次追溯', hidden: true } },
      { path: 'production/returns', name: 'ProductionReturns', component: () => import('../views/production/ProductionReturnList.vue'), meta: { title: '生产退料单' } },
      { path: 'production/supplements', name: 'ProductionSupplements', component: () => import('../views/production/ProductionSupplementList.vue'), meta: { title: '生产补料单' } },
      { path: 'production/supplements/edit', name: 'ProductionSupplementNew', component: () => import('../views/production/ProductionSupplementEdit.vue'), meta: { title: '新增生产补料单' } },
      { path: 'production/supplements/edit/:id', name: 'ProductionSupplementEdit', component: () => import('../views/production/ProductionSupplementEdit.vue'), meta: { title: '编辑生产补料单' } },
      { path: 'production/receipts/edit', name: 'ProductionReceiptNew', component: () => import('../views/production/ProductionReceiptEdit.vue'), meta: { title: '新增生产入库单' } },
      { path: 'production/receipts/edit/:id', name: 'ProductionReceiptEdit', component: () => import('../views/production/ProductionReceiptEdit.vue'), meta: { title: '编辑生产入库单' } },
      { path: 'production/returns/edit', name: 'ProductionReturnNew', component: () => import('../views/production/ProductionReturnEdit.vue'), meta: { title: '新增生产退料单' } },
      { path: 'production/returns/edit/:id', name: 'ProductionReturnEdit', component: () => import('../views/production/ProductionReturnEdit.vue'), meta: { title: '编辑生产退料单' } },



      
      
      // 2026-10-25 用户口径：界面只保留「生产工单 / 工艺路线 / 进度跟踪」，工序汇报单界面下线；
      // 页面代码与后端接口保留（去掉注释即可恢复），旧路径统一跳「生产工单」，老收藏夹/通知链接不 404
      // { path: 'production/operation-report', name: 'OperationReportList', component: () => import('../views/production/OperationReportList.vue'), meta: { title: '工序汇报单' } },
      { path: 'production/operation-report', redirect: '/production/workorders' },
      // 2026-09-20 入口改造：/production/mrp = MRP计划单据列表（菜单入口）；原 5 步卡片向导降级为「新增」页 /production/mrp/new
      { path: 'production/mrp', name: 'Mrp', component: () => import('../views/production/MrpPlanList.vue'), meta: { title: 'MRP运算' } },
      { path: 'production/mrp/new', name: 'MrpWizard', component: () => import('../views/production/MrpList.vue'), meta: { title: 'MRP运算' } },
      // 旧地址兼容（老收藏夹/通知链接不 404）
      { path: 'production/mrp-plan', redirect: '/production/mrp' },
      // 生产模块 - 新增/编辑独立页面路由
      // 工序汇报单编辑页同步下线（2026-10-25）：扫码报工改为工序页内直接填数量，旧路径跳「生产工单」
      // { path: 'production/operation-reports/edit', name: 'OperationReportNew', component: () => import('../views/production/OperationReportEdit.vue'), meta: { title: '新增报工单' } },
      // { path: 'production/operation-reports/edit/:id', name: 'OperationReportEdit', component: () => import('../views/production/OperationReportEdit.vue'), meta: { title: '编辑报工单' } },
      { path: 'production/operation-reports/edit', redirect: '/production/workorders' },
      { path: 'production/operation-reports/edit/:id', redirect: '/production/workorders' },



      { path: 'production/mrp-plans/edit', name: 'MrpPlanNew', component: () => import('../views/production/MrpPlanEdit.vue'), meta: { title: '新增MRP计划单' } },
      { path: 'production/mrp-plans/edit/:id', name: 'MrpPlanEdit', component: () => import('../views/production/MrpPlanEdit.vue'), meta: { title: '编辑MRP计划单' } },
      // 生产组装单
      { path: 'production/assemble', name: 'ProduceAssembleList', component: () => import('../views/production/ProduceAssembleList.vue'), meta: { title: '生产组装单' } },
      { path: 'production/assemble/new', name: 'ProduceAssembleNew', component: () => import('../views/production/ProduceAssembleEdit.vue'), meta: { title: '新增组装单' } },
      { path: 'production/assemble/:id', name: 'ProduceAssembleEdit', component: () => import('../views/production/ProduceAssembleEdit.vue'), meta: { title: '组装单详情' } },
      // 生产拆卸单
      { path: 'production/disassemble', name: 'ProduceDisassembleList', component: () => import('../views/production/ProduceDisassembleList.vue'), meta: { title: '生产拆卸单' } },
      { path: 'production/disassemble/new', name: 'ProduceDisassembleNew', component: () => import('../views/production/ProduceDisassembleEdit.vue'), meta: { title: '新增拆卸单' } },
      { path: 'production/disassemble/:id', name: 'ProduceDisassembleEdit', component: () => import('../views/production/ProduceDisassembleEdit.vue'), meta: { title: '拆卸单详情' } },
      { path: 'production/help', name: 'ProductionHelp', component: () => import('../views/production/ProductionHelp.vue'), meta: { title: '生产操作手册' } },
      // 生产报表 - 分析报表
      { path: 'production/report/analysis', name: 'ProductionReportAnalysis', component: () => import('../views/production/ProductionReportCenter.vue'), meta: { title: '生产分析报表' } },
      // 生产报表 - 工序质检报表（route_path 与 sys_menu / menuConfig.js 严格一致，供经营看板质量块穿透）
      { path: 'production/qc-record', name: 'QcRecordReport', component: () => import('../views/production/QcRecordReport.vue'), meta: { title: '生产工序质检报表' } },
      // 生产报表 - 台账报表（标准台账已下线，改用自定义报表）

      // ========== 设备管理 ==========
      // route_path 与 sys_menu / menuConfig.js 严格一致（deploy/sql/184_device_management.sql）
      { path: 'device/ledger', name: 'DeviceLedger', component: () => import('../views/device/DeviceLedgerList.vue'), meta: { title: '设备台账' } },
      { path: 'device/access', name: 'DeviceAccess', component: () => import('../views/device/DeviceAccess.vue'), meta: { title: '接入管理' } },
      { path: 'device/debug', name: 'DeviceDebug', component: () => import('../views/device/DeviceDebug.vue'), meta: { title: '接入调试台' } },
      { path: 'device/monitor', name: 'DeviceMonitor', component: () => import('../views/device/DeviceStagePlaceholder.vue'), meta: { title: '实时监控' } },
      { path: 'device/task', name: 'DeviceTaskList', component: () => import('../views/device/DeviceTaskList.vue'), meta: { title: '设备任务' } },
      { path: 'device/report-confirm', name: 'DeviceReportConfirmList', component: () => import('../views/device/DeviceReportConfirmList.vue'), meta: { title: '报工待确认' } },
      { path: 'device/unmatched', name: 'DeviceUnmatchedList', component: () => import('../views/device/DeviceUnmatchedList.vue'), meta: { title: '未关联数据' } },
      { path: 'device/alarm', name: 'DeviceAlarmList', component: () => import('../views/device/DeviceStagePlaceholder.vue'), meta: { title: '报警中心' } },
      { path: 'device/inspection', name: 'DeviceInspectionTemplateList', component: () => import('../views/device/DeviceStagePlaceholder.vue'), meta: { title: '点检管理' } },
      { path: 'device/maintenance', name: 'DeviceMaintenancePlanList', component: () => import('../views/device/DeviceStagePlaceholder.vue'), meta: { title: '保养管理' } },
      { path: 'device/repair', name: 'DeviceRepairList', component: () => import('../views/device/DeviceStagePlaceholder.vue'), meta: { title: '维修工单' } },
      { path: 'device/spare', name: 'DeviceSpareList', component: () => import('../views/device/DeviceStagePlaceholder.vue'), meta: { title: '备件管理' } },
      { path: 'device/report', name: 'DeviceReport', component: () => import('../views/device/DeviceStagePlaceholder.vue'), meta: { title: '设备报表' } },
      { path: 'device/help', name: 'DeviceHelp', component: () => import('../views/device/DeviceStagePlaceholder.vue'), meta: { title: '操作手册' } },

      // ========== 委外管理 ==========
      { path: 'subcontract/orders', name: 'SubcontractOrderList', component: () => import('../views/production/SubcontractList.vue'), meta: { title: '委外加工单' } },
      { path: 'subcontract/orders/new', name: 'SubcontractOrderNew', component: () => import('../views/subcontract/SubcontractOrderEdit.vue'), meta: { title: '新增委外加工单' } },
      { path: 'subcontract/orders/:id', name: 'SubcontractOrderEdit', component: () => import('../views/subcontract/SubcontractOrderEdit.vue'), meta: { title: '编辑委外加工单' } },
      { path: 'subcontract/picks', name: 'SubcontractPickList', component: () => import('../views/subcontract/SubcontractPickList.vue'), meta: { title: '委外发料单' } },
      { path: 'subcontract/picks/new', name: 'SubcontractPickNew', component: () => import('../views/subcontract/SubcontractPickEdit.vue'), meta: { title: '新增委外发料单' } },
      { path: 'subcontract/picks/:id', name: 'SubcontractPickEdit', component: () => import('../views/subcontract/SubcontractPickEdit.vue'), meta: { title: '编辑委外发料单' } },
      { path: 'subcontract/inbounds', name: 'SubcontractInboundList', component: () => import('../views/subcontract/SubcontractInboundList.vue'), meta: { title: '委外入库单' } },
      { path: 'subcontract/inbounds/new', name: 'SubcontractInboundNew', component: () => import('../views/subcontract/SubcontractInboundEdit.vue'), meta: { title: '新增委外入库单' } },
      { path: 'subcontract/inbounds/:id', name: 'SubcontractInboundEdit', component: () => import('../views/subcontract/SubcontractInboundEdit.vue'), meta: { title: '编辑委外入库单' } },
      { path: 'subcontract/supplements', name: 'SubcontractSupplementList', component: () => import('../views/subcontract/SubcontractSupplementList.vue'), meta: { title: '委外补料单' } },
      { path: 'subcontract/supplements/new', name: 'SubcontractSupplementNew', component: () => import('../views/subcontract/SubcontractSupplementEdit.vue'), meta: { title: '新增委外补料单' } },
      { path: 'subcontract/supplements/:id', name: 'SubcontractSupplementEdit', component: () => import('../views/subcontract/SubcontractSupplementEdit.vue'), meta: { title: '编辑委外补料单' } },
      { path: 'subcontract/returns', name: 'SubcontractReturnList', component: () => import('../views/subcontract/SubcontractReturnList.vue'), meta: { title: '委外退料单' } },
      { path: 'subcontract/returns/new', name: 'SubcontractReturnNew', component: () => import('../views/subcontract/SubcontractReturnEdit.vue'), meta: { title: '新增委外退料单' } },
      { path: 'subcontract/returns/:id', name: 'SubcontractReturnEdit', component: () => import('../views/subcontract/SubcontractReturnEdit.vue'), meta: { title: '编辑委外退料单' } },
      { path: 'subcontract/help', name: 'SubcontractHelp', component: () => import('../views/subcontract/SubcontractHelp.vue'), meta: { title: '委外操作手册' } },

      { path: 'sample/list', name: 'SampleList', component: () => import('../views/sample/SampleList.vue'), meta: { title: '打样管理' } },
      { path: 'sample/edit', name: 'SampleNew', component: () => import('../views/sample/SampleEdit.vue'), meta: { title: '新增打样单' } },
      { path: 'sample/edit/:id', name: 'SampleEdit', component: () => import('../views/sample/SampleEdit.vue'), meta: { title: '编辑打样单' } },
      { path: 'freesample/list', name: 'FreeSampleList', component: () => import('../views/freesample/FreeSampleList.vue'), meta: { title: '免费样品' } },
      { path: 'freesample/edit/:id?', name: 'FreeSampleEdit', component: () => import('../views/freesample/FreeSampleEdit.vue'), meta: { title: '编辑样品单' } },

      // ========== 财务管理 ==========
      { path: 'finance', name: 'Finance', component: () => import('../views/finance/FinancePage.vue'), meta: { title: '财务管理' } },
      // 旧账户维护入口已迁移到基础资料 /basedata/bank-account，保留3条兼容跳转（旧收藏夹/历史单据跳转不失效）
      { path: 'finance/account', redirect: '/basedata/bank-account' },
      { path: 'finance/account/new', redirect: '/basedata/bank-account/new' },
      { path: 'finance/account/edit/:id', redirect: '/basedata/bank-account/edit/:id' },
      { path: 'finance/receipt', name: 'FinanceReceipt', component: () => import('../views/finance/PaymentReceiptList.vue'), meta: { title: '收款单' } },
      { path: 'finance/receipt/new', name: 'FinanceReceiptNew', component: () => import('../views/finance/PaymentReceiptEdit.vue'), meta: { title: '新增收款单' } },
      { path: 'finance/receipt/edit/:id', name: 'FinanceReceiptEdit', component: () => import('../views/finance/PaymentReceiptEdit.vue'), meta: { title: '编辑收款单' } },
      { path: 'finance/payment', name: 'FinancePayment', component: () => import('../views/finance/PaymentVoucherList.vue'), meta: { title: '付款单' } },
      { path: 'finance/payment/new', name: 'FinancePaymentNew', component: () => import('../views/finance/PaymentVoucherEdit.vue'), meta: { title: '新增付款单' } },
      { path: 'finance/payment/edit/:id', name: 'FinancePaymentEdit', component: () => import('../views/finance/PaymentVoucherEdit.vue'), meta: { title: '编辑付款单' } },
      { path: 'finance/operation-expense', name: 'OperationExpense', component: () => import('../views/finance/OperationExpenseList.vue'), meta: { title: '费用分摊' } },
      { path: 'finance/operation-expense/new', name: 'OperationExpenseNew', component: () => import('../views/finance/OperationExpenseEdit.vue'), meta: { title: '新增费用分摊' } },
      { path: 'finance/operation-expense/:id', name: 'OperationExpenseEditPage', component: () => import('../views/finance/OperationExpenseEdit.vue'), meta: { title: '编辑费用分摊' } },
      { path: 'finance/cost-config', name: 'CostConfig', component: () => import('../views/finance/CostConfigList.vue'), meta: { title: '成本核算配置' } },
      { path: 'finance/cost-calculation', name: 'CostCalculation', component: () => import('../views/finance/CostCalculationList.vue'), meta: { title: '成本核算' } },
      // 2026-09-20 边界收紧：删除「新增成本核算单」入口（成本核算只能按期间归集已生效单据生成核算单，不提供人工凭空建单）
      { path: 'finance/cost-calculation/edit/:id', name: 'CostCalculationEdit', component: () => import('../views/finance/CostCalculationEdit.vue'), meta: { title: '编辑成本核算单' } },
      // 工序工资核算（2026-09-27 新增）：按已生效工序汇报单归集计件/计时工资
      { path: 'finance/wage-settlement', name: 'WageSettlement', component: () => import('../views/finance/WageSettlementList.vue'), meta: { title: '工序工资核算' } },
      // 销售预收款单
      { path: 'finance/sale-pre-receipts', name: 'SalePreReceiptList', component: () => import('../views/finance/SalePreReceiptList.vue'), meta: { title: '销售预收款单' } },
      { path: 'finance/sale-pre-receipts/new', name: 'SalePreReceiptNew', component: () => import('../views/finance/SalePreReceiptEdit.vue'), meta: { title: '新增销售预收款单' } },
      { path: 'finance/sale-pre-receipts/edit/:id', name: 'SalePreReceiptEdit', component: () => import('../views/finance/SalePreReceiptEdit.vue'), meta: { title: '编辑销售预收款单' } },
      // 采购预付款单
      { path: 'finance/purchase-pre-payments', name: 'PurchasePrePaymentList', component: () => import('../views/finance/PurchasePrePaymentList.vue'), meta: { title: '采购预付款单' } },
      { path: 'finance/purchase-pre-payments/new', name: 'PurchasePrePaymentNew', component: () => import('../views/finance/PurchasePrePaymentEdit.vue'), meta: { title: '新增采购预付款单' } },
      { path: 'finance/purchase-pre-payments/edit/:id', name: 'PurchasePrePaymentEdit', component: () => import('../views/finance/PurchasePrePaymentEdit.vue'), meta: { title: '编辑采购预付款单' } },
      // 财务报表 - 分析报表
      { path: 'finance/report/analysis', name: 'FinanceReportAnalysis', component: () => import('../views/finance/FinanceReportCenter.vue'), meta: { title: '财务分析报表' } },
      // 财务报表 - 台账报表
      { path: 'finance/report/ar-summary', name: 'FinanceArSummary', component: () => import('../views/finance/report/ArSummary.vue'), meta: { title: '应收账款汇总表' } },
      { path: 'finance/report/ap-summary', name: 'FinanceApSummary', component: () => import('../views/finance/report/ApSummary.vue'), meta: { title: '应付账款汇总表' } },
      { path: 'finance/help', name: 'FinancesHelp', component: () => import('../views/finance/FinancesHelp.vue'), meta: { title: '财务操作手册' } },

      // ========== 自定义报表（数据源=标准报表+全部单据表，明细模式+主从关联+基础资料关联） ==========
      { path: 'report/custom', name: 'CustomReportList', component: () => import('../views/report/CustomReportList.vue'), meta: { title: '自定义报表' } },
      { path: 'report/custom/designer', name: 'CustomReportDesigner', component: () => import('../views/report/CustomReportDesigner.vue'), meta: { title: '报表设计器' } },
      { path: 'report/custom/run/:id', name: 'CustomReportRun', component: () => import('../views/report/CustomReportRun.vue'), meta: { title: '自定义报表运行' } },

      // ========== 自定义仪表盘（批次3：多个报表组合一页网格展示） ==========
      { path: 'report/dashboard', name: 'CustomDashboardList', component: () => import('../views/report/CustomDashboardList.vue'), meta: { title: '仪表盘' } },
      { path: 'report/dashboard/designer', name: 'CustomDashboardDesigner', component: () => import('../views/report/CustomDashboardDesigner.vue'), meta: { title: '仪表盘设计器' } },
      { path: 'report/dashboard/view/:id', name: 'CustomDashboardView', component: () => import('../views/report/CustomDashboardView.vue'), meta: { title: '仪表盘查看' } },

      // ========== 电商平台（亚马逊北美站 + 拼多多国内站） ==========
      // 构建期开关（2026-10-11）：VITE_HIDE_ECOMMERCE=true 时整组物理剔除（客户包不进产物，
      // 输 URL 也不可达；写法与平台管理的构建期排除同口径——直接比较环境变量，Rollup 才能折叠剔除）。
      // 恢复 = 删掉 .env.customer 里该变量重新构建。
      ...(import.meta.env.VITE_HIDE_ECOMMERCE === 'true' ? [] : [
        { path: 'ecommerce/shop', name: 'EcomShop', component: () => import('../views/ecommerce/EcomShopList.vue'), meta: { title: '店铺授权' } },
        { path: 'ecommerce/orders', name: 'EcomOrders', component: () => import('../views/ecommerce/PlatformOrderList.vue'), meta: { title: '平台订单' } },
        { path: 'ecommerce/mapping', name: 'EcomMapping', component: () => import('../views/ecommerce/ProductMappingList.vue'), meta: { title: '商品映射' } },
      ]),

      // ========== 系统管理 ==========
      { path: 'system/user', redirect: '/system/settings' },
      { path: 'system/role', redirect: '/system/settings' },
      { path: 'system/employee-perm', redirect: '/system/settings' },
      { path: 'system/dict', redirect: '/system/settings' },
      { path: 'system/log', redirect: '/system/settings' },
      { path: 'system/sys-config', redirect: '/system/settings' },
      { path: 'system/settings', name: 'SysSettings', component: () => import('../views/system/SettingsCenter.vue'), meta: { title: '设置中心' } },
      // 消息中心（2026-09-22）：铃铛「查看全部」的落地页，非菜单入口（不加菜单 = 不需要菜单权限初始化）
      { path: 'system/message-center', name: 'MessageCenter', component: () => import('../views/system/MessageCenter.vue'), meta: { title: '消息中心' } },
      { path: 'system/print-template', redirect: '/system/settings' },
      { path: 'system/field-permission', redirect: '/system/settings' },
      { path: 'system/serial-no-config', redirect: '/system/settings' },
      { path: 'system/custom-field', redirect: '/system/settings' },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// ===== 防无限重定向保险丝 =====
// vue-router 自带的死循环保护仅在 DEV 生效（内部判断 process.env.NODE_ENV !== 'production'，
// 生产构建被编译掉），生产环境守卫若对同一目标反复 next(x) 会形成无限微任务循环，整页卡死无响应。
// 保险丝逻辑：守卫连续 6 次被导向同一路径仍未完成导航（正常操作不可能出现，只有重定向循环会这样），
// 强制 next(false) 中止导航，让页面停留在当前路由。
let _fusePath = ''
let _fuseCount = 0

/** 重开确认框是否正在显示（防止连点「新增」弹出多个确认框） */
let _addReopenPrompting = false

/**
 * 新增页"重开"处理
 * 修复问题：二次点「新增」不刷新，表单里残留上一次填的物料信息。
 *
 * 根因：keep-alive 以 route.fullPath 作缓存 key，新增页 fullPath 恒定（/xxx/new、/xxx/edit），
 *      二次点「新增」命中同一缓存 key → 复用上次组件实例 → onMounted 不再执行。
 *
 * 规则：
 *   1) 首次打开该新增页（标签不存在）→ 不干预，走原流程（天然空白）
 *   2) 点标签页切回（带"点标签切换"标记）→ 不干预，保留已填草稿（历史需求）
 *   3) 从列表页点「新增」再次进入已打开的新增页 → 判定为"重开"：
 *      · 旧页有未保存内容 → 先弹确认框；点「取消」中断导航，旧草稿保留
 *      · 确认 / 旧页无内容 → 重开序号 +1（MainLayout 的 keep-alive key 变化 → 挂载全新实例）
 *
 * @returns {boolean} true = 本次导航已由本函数接管（异步 next）；false = 继续走原守卫逻辑
 */
function handleAddPageReopen(to, next) {
  if (!isAddPagePath(to.path)) return false
  let tagsStore
  try {
    tagsStore = useTagsViewStore()
  } catch (_) {
    return false // Pinia 未就绪（异常场景）→ 不干预，保持旧行为
  }
  if (!tagsStore || !Array.isArray(tagsStore.visitedViews)) return false
  // 只处理"该新增页已经打开过"的场景（首次打开天然是空白页，无需干预）
  if (!tagsStore.visitedViews.some(v => v.fullPath === to.fullPath)) return false
  // 点标签页切回：保留草稿，不重建
  if (tagsStore.consumeTabActivate(to.fullPath)) return false
  if (_addReopenPrompting) {
    // 上一个确认框还没处理完，忽略这次重复点击，避免多个确认框叠加
    next(false)
    return true
  }
  const reopen = () => {
    tagsStore.bumpViewNonce(to.fullPath)
    tagsStore.clearDirty(to.fullPath)
    next()
  }
  // 旧新增页没有用户输入 → 直接重开空白页，不打扰用户
  if (!tagsStore.isDirty(to.fullPath)) {
    reopen()
    return true
  }
  _addReopenPrompting = true
  const meta = router.resolve(to.fullPath).meta || {}
  const title = meta.title || '新增页'
  ElMessageBox.confirm(
    `「${title}」还有未保存的内容，重新打开会丢失这些内容。是否放弃并重新打开？`,
    '未保存的新增内容',
    {
      type: 'warning',
      confirmButtonText: '放弃并重新打开',
      cancelButtonText: '取消',
      closeOnClickModal: false
    }
  ).then(() => {
    reopen()
  }).catch(() => {
    // 取消：中断本次导航（停留在当前页），旧标签与草稿都保留，用户可继续填写
    next(false)
  }).finally(() => {
    _addReopenPrompting = false
  })
  return true
}

router.beforeEach((to, from, next) => {
  // 用 fullPath 计数：重定向循环的特征是完全相同的目标（含 query）连续出现；
  // 正常的同路径不同 query 导航（如设置中心 ?group= 深链接）每次 fullPath 不同，不会误触发
  if (to.fullPath === _fusePath) {
    _fuseCount++
    if (_fuseCount > 5) {
      console.error(`[Router] 检测到无限重定向（连续 ${_fuseCount} 次跳往 ${to.fullPath}），已强制中止导航`)
      _fusePath = ''
      _fuseCount = 0
      next(false)
      return
    }
  } else {
    _fusePath = to.fullPath
    _fuseCount = 1
  }
  // ---- 新增页保存后跳到本单据编辑页（/xxx/edit → /xxx/edit/:id）----
  // 保存成功时业务页会 router.replace 到带单号的地址，刚才那个「新增页」实例仍留在 keep-alive 里。
  // 标签栏上的旧标签点回去会复用它（form 里还带着已保存的单据），再保存就写回旧单。
  // 这里把它的重开序号 +1：旧实例的缓存 key 作废，点回去只会得到一张新的空白新增页。
  if (from.path && from.path !== to.path && isAddPagePath(from.path) && to.path.startsWith(from.path + '/')) {
    try { useTagsViewStore().bumpViewNonce(from.fullPath) } catch (_) {}
  }
  // ---- 新增页重开处理（二次点「新增」必须给全新空白页）----
  if (handleAddPageReopen(to, next)) return
  // 开发环境跳过登录验证（但仍保持登录后访问登录页的重定向逻辑，避免混乱）
  const token = localStorage.getItem('token')
  // ---- 白名单（匿名页）：无 token 也能直接访问 ----
  const WHITE = ['/login', '/tenant-select', '/403', '/404']
  const inWhite = WHITE.some(p => to.path === p || to.path.startsWith(p + '/'))

  // ---- 入口页归一化：/index.html 这类"不对应任何真实页面"的路径，统一送回工作台 ----
  // 背景（8cloud 已实测）：部署入口 / 浏览器收藏常写成 /index.html（该服务器根路径 / 是官网宣传页），
  // 而 SPA 在 /index.html 下启动时 window.location.pathname 就是 /index.html —— 它没有匹配到任何路由。
  // 下面的权限守卫把"不在 menuRoutePaths 里的路径"一律判为无权 → 任何账号登录后都会被踢到 /403
  // （用户实报：登录后提示 403，点「返回工作台」才恢复正常）。
  // 现归一：未匹配到任何路由 = 不是系统里的页面 → 有登录态回工作台，未登录回登录页。
  if (!to.matched.length) {
    next(token ? '/dashboard' : '/login')
    return
  }

  // ---- 分支 1：有 token 还访问登录/租户选择页 → 直接跳工作台（金蝶/用友标准行为） ----
  // 例外：登录/切换公司流程写入了待选租户（pendingTenants）时放行租户选择页，
  // 否则 tenant-select 永远进不去，选租户后的 /auth/userinfo 权限同步永远不会执行
  if (token && (to.path === '/login' || to.path === '/tenant-select')) {
    if (to.path === '/tenant-select') {
      try {
        const pending = JSON.parse(localStorage.getItem('pendingTenants') || '[]')
        if (Array.isArray(pending) && pending.length > 0) { next(); return }
      } catch (_) {}
    }
    next('/dashboard')
    return
  }

  // ---- 分支 2：开发环境跳过登录验证（按原有约定）----
  // 2026-09-29 收紧：这条"跳过"的本意是跳过**权限/菜单校验**（本地菜单与权限数据常和线上不一致，
  // 不该把人拦成 403），并不包含"没有登录态也放行"。原写法无条件 next()，导致本地无 token 直接
  // 打开「/」或工作台时，工作台/消息铃铛/工序待办/小助手配置等组件照常挂载、抢跑十几个业务接口
  // （全部 401），又被 401 拦截器整页硬跳回登录页 —— 表现为"打开首页闪一下 + 一串 401 报错"。
  // 现与生产环境对齐：无 token 且目标不在白名单 → 先回登录页，不再挂载业务页面。
  // （有 token 时行为完全不变：仍然跳过菜单/权限拦截，不会出现 403。）
  if (import.meta.env.DEV) {
    if (!inWhite && !token) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
    next()
    return
  }

  // ---- 分支 3：非白名单 + 无 token → 跳登录（带上原路径便于登录后回跳）----
  if (!inWhite && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  // 模块隐藏拦截：根据环境变量隐藏指定模块（即使 admin 也不能访问）
  if (token) {
    const hiddenStr = import.meta.env.VITE_HIDDEN_MODULES || ''
    const hiddenModules = hiddenStr.split(',').map(s => s.trim()).filter(Boolean)
    if (hiddenModules.length > 0) {
      // 模块 key 对应路径前缀映射
      const modulePathMap = {
        'acquisition': '/acquisition',
        'crm': '/crm',
        'sample': '/sample',
        'freesample': '/freesample',
        'subcontract': '/subcontract',
        'finance': '/finance',
        'platform': '/admin',
        'device': '/device'
      }
      for (const moduleKey of hiddenModules) {
        const pathPrefix = modulePathMap[moduleKey]
        if (pathPrefix && to.path.startsWith(pathPrefix)) {
          next('/dashboard')
          return
        }
      }
    }
    // 客户包隐藏单据拦截（2026-11-23）：VITE_HIDDEN_BILLS 里的单据，admin 也不能进（直接跳工作台）
    if (HIDDEN_BILL_ROUTE_PREFIXES.length > 0
      && HIDDEN_BILL_ROUTE_PREFIXES.some(p => to.path === p || to.path.startsWith(p + '/'))) {
      next('/dashboard')
      return
    }
  }
  // 权限拦截：使用 menuRoutePaths 控制访问（基于版本+角色的双重检查）
  if (token) {
    // 首页根路径 /dashboard 是登录后默认落地页，所有有权登录的用户都应当能进入
    // （即使 72 条 menuRoutePaths 里写的是具体子看板 /crm/dashboard 等，也要豁免这个根路径，
    // 否则进入系统立即被拦到 403 等于"登录后卡死"）
    if (to.path === '/dashboard') {
      next()
      return
    }
    // /403 /404 是守卫自身的兜底跳转目标，必须永远可达：
    // 若不豁免，无权限用户跳 /403 后守卫会再次拦截又跳 /403 → 无限重定向。
    // vue-router 的死循环保护仅在 DEV 生效（生产构建中被编译掉），生产环境会直接卡死整页。
    if (to.path === '/403' || to.path === '/404') {
      next()
      return
    }
    // 消息中心（2026-09-22）：所有登录用户都可进入 —— 它是铃铛「查看全部」的落地页，
    // 内容本身已按"当前账号"在服务端过滤；若走下面的 /system 前缀判定，销售账号会被拦成 403。
    if (to.path === '/system/message-center') {
      next()
      return
    }
    // 优先使用 menuRoutePaths（后端已根据版本和角色过滤）
    const menuRoutePaths = JSON.parse(localStorage.getItem('menuRoutePaths') || '[]')
    const roles = JSON.parse(localStorage.getItem('roles') || '[]')
    const isSuperAdmin = roles.includes('SUPER_ADMIN')
    
    // 如果有 menuRoutePaths，使用它来检查权限
    if (menuRoutePaths.length > 0) {
      // 构建一个可访问的路径前缀集合
      // menuRoutePaths 可能包含：/sale/order, /crm/lead 等完整路径
      // 我们需要检查 to.path 的前缀是否在 menuRoutePaths 中
      const allowedPathPrefixes = new Set()
      for (const rp of menuRoutePaths) {
        // 添加完整路径
        allowedPathPrefixes.add(rp)
        // 添加一级路径前缀（如 /sale/order → /sale）
        const parts = rp.split('/')
        if (parts.length >= 2) {
          allowedPathPrefixes.add('/' + parts[1])
        }
      }
      
      // 检查当前路径是否在允许列表中
      const currentPath = to.path
      let hasAccess = allowedPathPrefixes.has(currentPath)
      
      // 检查路径的各级前缀
      if (!hasAccess) {
        const pathParts = currentPath.split('/').filter(Boolean)
        let prefix = ''
        for (let i = 0; i < pathParts.length; i++) {
          prefix += '/' + pathParts[i]
          if (allowedPathPrefixes.has(prefix)) {
            hasAccess = true
            break
          }
        }
      }
      
      // 超级管理员可以访问平台管理模块
      if (!hasAccess && isSuperAdmin && to.path.startsWith('/admin')) {
        hasAccess = true
      }
      
      // 系统设置特殊处理
      if (!hasAccess && to.path.startsWith('/system')) {
        // 检查是否有 /system/settings 权限或 ADMIN 角色
        if (menuRoutePaths.includes('/system/settings') || roles.includes('ADMIN') || isSuperAdmin) {
          hasAccess = true
        }
      }
      
      if (!hasAccess) {
        next('/403')
        return
      }
    } else {
      // 降级：如果没有 menuRoutePaths，使用 roles 检查（兼容旧数据）
      // 但不再允许管理员全量放行
      // SUPER_ADMIN 与后端口径统一：平台超管 = 平台管理 + 业务全权限，全放行
      if (isSuperAdmin) {
        // 超管直接放行，走 next()
      } else if (roles.length > 0) {
        const map = {
          '/sale':         ['SALES', 'SALES_MANAGER', 'ADMIN'],
          '/quote':        ['SALES', 'SALES_MANAGER', 'ADMIN'],
          '/crm':          ['SALES', 'SALES_MANAGER', 'ADMIN'],
          '/acquisition':  ['SALES', 'SALES_MANAGER', 'ADMIN'],
          '/logistics':    ['SALES', 'SALES_MANAGER', 'ADMIN'],
          '/purchase':     ['PURCHASE', 'PURCHASE_MANAGER', 'ADMIN'],
          '/inventory':    ['WAREHOUSE', 'WAREHOUSE_MANAGER', 'PURCHASE', 'PURCHASE_MANAGER', 'SALES', 'SALES_MANAGER', 'PRODUCTION', 'PRODUCTION_MANAGER', 'FINANCE', 'FINANCE_MANAGER', 'ADMIN'],
          '/production':   ['PRODUCTION', 'PRODUCTION_MANAGER', 'ADMIN'],
          '/subcontract':  ['PRODUCTION', 'PRODUCTION_MANAGER', 'ADMIN'],
          '/sample':       ['SALES', 'SALES_MANAGER', 'PRODUCTION', 'PRODUCTION_MANAGER', 'ADMIN'],
          '/freesample':   ['SALES', 'SALES_MANAGER', 'PRODUCTION', 'PRODUCTION_MANAGER', 'ADMIN'],
          '/finance':      ['FINANCE', 'FINANCE_MANAGER', 'ADMIN'],
          '/databoard':    ['FINANCE', 'FINANCE_MANAGER', 'SALES_MANAGER', 'WAREHOUSE_MANAGER', 'PURCHASE_MANAGER', 'PRODUCTION_MANAGER', 'PRODUCTION', 'ADMIN'],
          '/system':       ['ADMIN'],
          '/admin':        ['SUPER_ADMIN']
        }
        for (const prefix in map) {
          if (to.path.startsWith(prefix)) {
            const allow = map[prefix]
            const ok = roles.some(r => allow.includes(r))
            if (!ok) {
              next('/403')
              return
            }
            break
          }
        }
      }
    }
  }
  next()
})

/**
 * 动态注入组件 name：在异步组件解析完成后、导航确认前，
 * 给未显式声明 name 的组件定义对象设置 name = route.name。
 * 这样 keep-alive 的 :include="cachedViewNames"（存的是 route.name）才能正确匹配并缓存组件实例，
 * 切换标签时保留填写的数据与滚动位置。
 */
router.beforeResolve((to) => {
  to.matched.forEach(record => {
    const comp = record.components && record.components.default
    if (comp && typeof comp === 'object') {
      // 异步组件可能是 { __name, ... } 或已解析的组件定义对象
      if (!comp.name && to.name) {
        comp.name = to.name
      }
    }
  })
})

/**
 * document.title 与系统名称联动：
 * - 优先读 localStorage 缓存的 appConfig.system.name（登录页无 token 也能用）
 * - 兜底为默认"智能ERP"
 * - 如果当前路由 meta.title 有值就拼接："页面名 - 系统名"，否则直接显示系统名
 */
function readCachedSystemName() {
  try {
    const raw = localStorage.getItem('erp_app_config_v1')
    if (!raw) return ''
    const obj = JSON.parse(raw)
    if (obj && typeof obj['system.name'] === 'string' && obj['system.name'].trim()) {
      return obj['system.name'].trim()
    }
  } catch (_) {}
  return ''
}

function updateDocTitle(to) {
  const sysName = readCachedSystemName() || '智能ERP'
  const pageTitle = (to && to.meta && to.meta.title) ? to.meta.title : ''
  document.title = pageTitle ? `${pageTitle} - ${sysName}` : sysName
}

router.afterEach((to) => {
  updateDocTitle(to)
})

export default router
