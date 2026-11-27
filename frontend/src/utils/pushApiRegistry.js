// ====================================================================
// 下推/选单 API 注册表 - 集中管理各单据的 list/get 调用
// 避免在通用组件中硬编码 import, 各业务 API 在此注册
// ====================================================================
import request from '@/utils/request'
// 销售模块 API
import { listSaleQuote, getSaleQuote } from '@/api/sale/saleQuote'
import { listSaleContract, getSaleContract } from '@/api/sale/saleContract'
import { listSaleOrder, getSaleOrder } from '@/api/sale/saleOrder'
import { listSaleDelivery, getSaleDelivery } from '@/api/sale/saleDelivery'
import { listSaleReturn, getSaleReturn } from '@/api/sale/saleReturn'
import { listSaleInvoice, getSaleInvoice } from '@/api/sale/saleInvoice'
// 打样模块 API
import { pageSample, getSample } from '@/api/sample/sample'
// 采购模块 API
import { listPurchaseContract, getPurchaseContract } from '@/api/purchase/purchaseContract'
import { listPurchaseRequest, getPurchaseRequest } from '@/api/purchase/request'
import { listPurchaseInbound, getPurchaseInbound } from '@/api/purchase/inbound'
import { listPurchaseReturn, getPurchaseReturn } from '@/api/purchase/return'
import { listPurchaseInvoice, getPurchaseInvoice } from '@/api/purchase/invoice'

// 采购订单 API 文件缺失, 用 request 兜底
function listPurchaseOrder(params) { return request.get('/purchase/orders', { params }) }
function getPurchaseOrder(id) { return request.get(`/purchase/orders/${id}`) }

// 委外模块 API
import { listSubcontract, getSubcontract, pageSubcontractPick, getSubcontractPick, pageSubcontractInbound, getSubcontractInbound } from '@/api/production/subcontract'

// 生产工单 API - 用 request 兜底
function listWorkOrder(params) { return request.get('/production/workorders', { params }) }
function getWorkOrder(id) { return request.get(`/production/workorders/${id}`) }

// 按单据类型 key 注册 list/get
export const PUSH_API_REGISTRY = {
  // 销售
  SaleQuote:    { list: listSaleQuote,    get: getSaleQuote,    base: '/sale/quotes' },
  SaleContract: { list: listSaleContract, get: getSaleContract, base: '/sale/contracts' },
  SaleOrder:    { list: listSaleOrder,    get: getSaleOrder,    base: '/sale/orders' },
  SaleDelivery: { list: listSaleDelivery, get: getSaleDelivery, base: '/sale/deliveries' },
  SaleReturn:   { list: listSaleReturn,   get: getSaleReturn,   base: '/sale/returns' },
  SaleInvoice:  { list: listSaleInvoice,  get: getSaleInvoice,  base: '/sale/invoices' },
  // 打样
  SampleOrder:  { list: pageSample,     get: getSample,      base: '/sample/orders' },
  // 采购
  PurchaseContract:{ list: listPurchaseContract, get: getPurchaseContract, base: '/purchase/contracts' },
  PurchaseRequest: { list: listPurchaseRequest, get: getPurchaseRequest, base: '/purchase/requests' },
  PurchaseOrder:   { list: listPurchaseOrder,   get: getPurchaseOrder,   base: '/purchase/orders' },
  PurchaseInbound: { list: listPurchaseInbound, get: getPurchaseInbound, base: '/purchase/inbounds' },
  PurchaseReturn:  { list: listPurchaseReturn,  get: getPurchaseReturn,  base: '/purchase/returns' },
  PurchaseInvoice: { list: listPurchaseInvoice, get: getPurchaseInvoice, base: '/purchase/invoices' },
  // 生产
  WorkOrders:      { list: listWorkOrder,        get: getWorkOrder,        base: '/production/workorders' },
  // 委外
  SubcontractOrder:   { list: listSubcontract,        get: getSubcontract,        base: '/subcontract/orders' },
  SubcontractPick:    { list: pageSubcontractPick,    get: getSubcontractPick,    base: '/subcontract/picks' },
  SubcontractInbound: { list: pageSubcontractInbound, get: getSubcontractInbound, base: '/subcontract/inbounds' }
}

// 获取某单据类型的列表 API
export function getListApi(typeKey) {
  return PUSH_API_REGISTRY[typeKey]?.list
}

// 获取某单据类型的详情 API
export function getGetApi(typeKey) {
  return PUSH_API_REGISTRY[typeKey]?.get
}
