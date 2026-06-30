import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/dashboard/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'sale/orders', name: 'SaleOrders', component: () => import('../views/sale/SaleOrderList.vue'), meta: { title: '销售订单' } },
      { path: 'sale/orders/new', name: 'SaleOrderNew', component: () => import('../views/sale/SaleOrderEdit.vue'), meta: { title: '新增订单' } },
      { path: 'sale/orders/:id', name: 'SaleOrderEdit', component: () => import('../views/sale/SaleOrderEdit.vue'), meta: { title: '编辑订单' } },
      { path: 'purchase/orders', name: 'PurchaseOrders', component: () => import('../views/purchase/PurchaseOrderList.vue'), meta: { title: '采购订单' } },
      { path: 'purchase/orders/new', name: 'PurchaseOrderNew', component: () => import('../views/purchase/PurchaseOrderEdit.vue'), meta: { title: '新增采购单' } },
      { path: 'purchase/orders/:id', name: 'PurchaseOrderEdit', component: () => import('../views/purchase/PurchaseOrderEdit.vue'), meta: { title: '编辑采购单' } },
      { path: 'inventory', name: 'Inventory', component: () => import('../views/inventory/InventoryList.vue'), meta: { title: '库存管理' } },
      { path: 'inventory/warehouses', name: 'Warehouses', component: () => import('../views/inventory/WarehouseList.vue'), meta: { title: '仓库管理' } },
      { path: 'inventory/locations', name: 'Locations', component: () => import('../views/inventory/LocationList.vue'), meta: { title: '储位管理' } },
      { path: 'inventory/batches', name: 'Batches', component: () => import('../views/inventory/BatchList.vue'), meta: { title: '批次库存' } },
      { path: 'masterdata/materials', name: 'Materials', component: () => import('../views/masterdata/MaterialList.vue'), meta: { title: '物料档案' } },
      { path: 'masterdata/customers', name: 'Customers', component: () => import('../views/masterdata/CustomerList.vue'), meta: { title: '客户档案' } },
      { path: 'masterdata/bom', name: 'BomList', component: () => import('../views/masterdata/BomList.vue'), meta: { title: 'BOM清单' } },
      { path: 'production/workorders', name: 'WorkOrders', component: () => import('../views/production/WorkOrderList.vue'), meta: { title: '生产工单' } },
      { path: 'finance', name: 'Finance', component: () => import('../views/finance/FinancePage.vue'), meta: { title: '财务管理' } },
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