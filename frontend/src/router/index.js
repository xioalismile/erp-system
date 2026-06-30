import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      // 工作台
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/dashboard/Dashboard.vue'), meta: { title: '工作台' } },

      // ========== 基础资料 ==========
      { path: 'basedata/unit', name: 'Unit', component: () => import('../views/basedata/UnitList.vue'), meta: { title: '计量单位' } },
      { path: 'basedata/currency', name: 'Currency', component: () => import('../views/basedata/CurrencyList.vue'), meta: { title: '币种' } },
      { path: 'basedata/settle', name: 'Settle', component: () => import('../views/basedata/SettleList.vue'), meta: { title: '结算方式' } },
      { path: 'basedata/payment-term', name: 'PaymentTerm', component: () => import('../views/basedata/PaymentTermList.vue'), meta: { title: '付款条件' } },
      { path: 'basedata/price-level', name: 'PriceLevel', component: () => import('../views/basedata/PriceLevelList.vue'), meta: { title: '价格等级' } },
      { path: 'basedata/department', name: 'Department', component: () => import('../views/basedata/DepartmentList.vue'), meta: { title: '部门档案' } },
      { path: 'basedata/employee', name: 'Employee', component: () => import('../views/basedata/EmployeeList.vue'), meta: { title: '员工档案' } },
      { path: 'basedata/warehouse', name: 'Warehouse', component: () => import('../views/basedata/WarehouseList.vue'), meta: { title: '仓库档案' } },
      { path: 'basedata/location', name: 'Location', component: () => import('../views/basedata/LocationList.vue'), meta: { title: '货位档案' } },
      { path: 'basedata/material-category', name: 'MaterialCategory', component: () => import('../views/basedata/MaterialCategoryList.vue'), meta: { title: '物料分类' } },
      { path: 'basedata/material', name: 'Material', component: () => import('../views/basedata/MaterialList.vue'), meta: { title: '物料档案' } },
      { path: 'basedata/customer', name: 'Customer', component: () => import('../views/basedata/CustomerList.vue'), meta: { title: '客户档案' } },
      { path: 'basedata/supplier', name: 'Supplier', component: () => import('../views/basedata/SupplierList.vue'), meta: { title: '供应商档案' } },
      { path: 'basedata/account', name: 'Account', component: () => import('../views/basedata/AccountList.vue'), meta: { title: '会计科目' } },

      // ========== 销售管理 ==========
      { path: 'sale/orders', name: 'SaleOrders', component: () => import('../views/sale/SaleOrderList.vue'), meta: { title: '销售订单' } },
      { path: 'sale/orders/new', name: 'SaleOrderNew', component: () => import('../views/sale/SaleOrderEdit.vue'), meta: { title: '新增订单' } },
      { path: 'sale/orders/:id', name: 'SaleOrderEdit', component: () => import('../views/sale/SaleOrderEdit.vue'), meta: { title: '编辑订单' } },

      // ========== 采购管理 ==========
      { path: 'purchase/orders', name: 'PurchaseOrders', component: () => import('../views/purchase/PurchaseOrderList.vue'), meta: { title: '采购订单' } },
      { path: 'purchase/orders/new', name: 'PurchaseOrderNew', component: () => import('../views/purchase/PurchaseOrderEdit.vue'), meta: { title: '新增采购单' } },
      { path: 'purchase/orders/:id', name: 'PurchaseOrderEdit', component: () => import('../views/purchase/PurchaseOrderEdit.vue'), meta: { title: '编辑采购单' } },

      // ========== 库存管理 ==========
      { path: 'inventory', name: 'Inventory', component: () => import('../views/inventory/InventoryList.vue'), meta: { title: '库存管理' } },
      { path: 'inventory/current', name: 'InventoryCurrent', component: () => import('../views/inventory/InventoryList.vue'), meta: { title: '即时库存' } },
      { path: 'inventory/warehouses', name: 'Warehouses', component: () => import('../views/inventory/WarehouseList.vue'), meta: { title: '仓库管理' } },
      { path: 'inventory/locations', name: 'Locations', component: () => import('../views/inventory/LocationList.vue'), meta: { title: '储位管理' } },
      { path: 'inventory/batches', name: 'Batches', component: () => import('../views/inventory/BatchList.vue'), meta: { title: '批次库存' } },
      { path: 'inventory/ledger', name: 'InventoryLedger', component: () => import('../views/inventory/InventoryList.vue'), meta: { title: '库存流水' } },
      { path: 'inventory/other-receipts', name: 'OtherReceipts', component: () => import('../views/inventory/OtherReceiptList.vue'), meta: { title: '其他入库单' } },
      { path: 'inventory/other-issues', name: 'OtherIssues', component: () => import('../views/inventory/OtherIssueList.vue'), meta: { title: '其他出库单' } },
      { path: 'inventory/stock-transfers', name: 'StockTransfers', component: () => import('../views/inventory/StockTransferList.vue'), meta: { title: '库存调拨单' } },
      { path: 'inventory/stock-checks', name: 'StockChecks', component: () => import('../views/inventory/StockCheckList.vue'), meta: { title: '库存盘点单' } },
      { path: 'inventory/material-requisitions', name: 'MaterialRequisitions', component: () => import('../views/inventory/MaterialRequisitionList.vue'), meta: { title: '生产领料单' } },

      // ========== 生产管理 ==========
      { path: 'masterdata/bom', name: 'BomList', component: () => import('../views/masterdata/BomList.vue'), meta: { title: 'BOM清单' } },
      { path: 'production/work-center', name: 'WorkCenters', component: () => import('../views/production/WorkCenterList.vue'), meta: { title: '工作中心' } },
      { path: 'production/workstation', name: 'Workstations', component: () => import('../views/production/WorkstationList.vue'), meta: { title: '工序/工位' } },
      { path: 'production/routings', name: 'Routings', component: () => import('../views/production/RoutingList.vue'), meta: { title: '工艺路线' } },
      { path: 'production/workorders', name: 'WorkOrders', component: () => import('../views/production/WorkOrderList.vue'), meta: { title: '生产工单' } },
      { path: 'production/receipts', name: 'ProductionReceipts', component: () => import('../views/production/ProductionReceiptList.vue'), meta: { title: '生产入库单' } },
      { path: 'production/mrp', name: 'Mrp', component: () => import('../views/production/MrpList.vue'), meta: { title: 'MRP运算' } },

      // ========== 财务管理 ==========
      { path: 'finance', name: 'Finance', component: () => import('../views/finance/FinancePage.vue'), meta: { title: '财务管理' } },
      { path: 'finance/receivable', name: 'FinanceReceivable', component: () => import('../views/finance/FinancePage.vue'), meta: { title: '应收账款' } },
      { path: 'finance/payable', name: 'FinancePayable', component: () => import('../views/finance/FinancePage.vue'), meta: { title: '应付账款' } },
      { path: 'finance/receipt', name: 'FinanceReceipt', component: () => import('../views/finance/FinancePage.vue'), meta: { title: '收款单' } },
      { path: 'finance/payment', name: 'FinancePayment', component: () => import('../views/finance/FinancePage.vue'), meta: { title: '付款单' } },
      { path: 'finance/vouchers', name: 'Vouchers', component: () => import('../views/finance/VoucherList.vue'), meta: { title: '会计凭证' } },
      { path: 'finance/balance', name: 'AccountBalance', component: () => import('../views/finance/AccountBalanceList.vue'), meta: { title: '科目余额表' } },

      // ========== 系统管理 ==========
      { path: 'system/user', name: 'SysUser', component: () => import('../views/system/UserList.vue'), meta: { title: '用户管理' } },
      { path: 'system/role', name: 'SysRole', component: () => import('../views/system/RoleList.vue'), meta: { title: '角色管理' } },
      { path: 'system/menu', name: 'SysMenu', component: () => import('../views/system/MenuList.vue'), meta: { title: '菜单管理' } },
      { path: 'system/dict', name: 'SysDict', component: () => import('../views/system/DictList.vue'), meta: { title: '数据字典' } },
      { path: 'system/log', name: 'SysLog', component: () => import('../views/system/LogList.vue'), meta: { title: '操作日志' } },
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
