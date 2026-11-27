/**
 * 单据类型 → 编辑页路由（全站唯一一份）
 *
 * 用途：任何「单号列 / 来源去向面板 / 通知跳转」需要点开一张单据时，都用这里解析路由。
 * 为什么要抽公共：此前这份映射只写在 BillLinkPanel.vue 内部，列表页（如库存流水）各自
 * 硬编码或干脆没接，导致"单号看起来能点、实际点不动"（用户 2026-09-20 反馈）。
 *
 * key = 后端「单据关联注册表」的单据类型编码（BillLinkRegistry 的 docType）；
 * value = id => 编辑页路由（已核对 router/index.js；无独立编辑页者不列入，解析为 null 即不可跳）。
 */
export const BILL_EDIT_ROUTE = {
  // 销售
  SALE_QUOTE: id => `/sale/quotes/${id}`,
  SALE_CONTRACT: id => `/sale/contracts/${id}`,
  SALE_ORDER: id => `/sale/orders/${id}`,
  SALE_DELIVERY: id => `/sale/deliveries/${id}`,
  SALE_INVOICE: id => `/sale/invoices/${id}`,
  SALE_RETURN: id => `/sale/returns/${id}`,
  // 2026-11-18：销售换货单（来源去向面板/通知点击可跳转）
  SALE_EXCHANGE: id => `/sale/exchanges/${id}`,
  // 2026-11-18：售后维修单
  SALE_REPAIR: id => `/sale/repairs/${id}`,
  // 采购
  PURCHASE_REQUEST: id => `/purchase/requests/${id}`,
  PURCHASE_ORDER: id => `/purchase/orders/${id}`,
  PURCHASE_CONTRACT: id => `/purchase/contracts/${id}`,
  PURCHASE_INBOUND: id => `/purchase/inbounds/${id}`,
  PURCHASE_RETURN: id => `/purchase/returns/${id}`,
  PURCHASE_INVOICE: id => `/purchase/invoices/${id}`,
  // 库存
  OTHER_ISSUE: id => `/inventory/other-issues/edit/${id}`,
  OTHER_RECEIPT: id => `/inventory/other-receipts/edit/${id}`,
  MATERIAL_REQUISITION: id => `/inventory/material-requisitions/edit/${id}`,
  STOCK_TRANSFER: id => `/inventory/stock-transfers/edit/${id}`,
  STOCK_CHECK: id => `/inventory/stock-checks/edit/${id}`,
  // 生产
  PRODUCTION_ORDER: id => `/production/workorders/${id}`,
  WORK_ORDER: id => `/production/workorders/${id}`,          // 审批/通知口径的生产工单（与 PRODUCTION_ORDER 同页）
  PRODUCTION_RECEIPT: id => `/production/receipts/edit/${id}`,

  OPERATION_REPORT: id => `/production/operation-reports/edit/${id}`,
  MRP_PLAN: id => `/production/mrp-plans/edit/${id}`,
  // 生产退料 / 补料：审批与通知用的是 MATERIAL_RETURN / MATERIAL_SUPPLEMENT 两个编码
  // （数据落在 material_requisition 表，但页面是「生产退料单 / 生产补料单」，不能跳领料单页）
  MATERIAL_RETURN: id => `/production/returns/edit/${id}`,
  MATERIAL_SUPPLEMENT: id => `/production/supplements/edit/${id}`,
  // 生产组装单 / 生产拆卸单
  PRODUCE_ASSEMBLE: id => `/production/assemble/${id}`,
  PRODUCE_DISASSEMBLE: id => `/production/disassemble/${id}`,
  // 委外
  SUBCONTRACT_ORDER: id => `/subcontract/orders/${id}`,
  SUBCONTRACT_DELIVERY: id => `/subcontract/picks/${id}`,
  SUBCONTRACT_RECEIPT: id => `/subcontract/inbounds/${id}`,
  SUBCONTRACT_SUPPLEMENT: id => `/subcontract/supplements/${id}`,
  SUBCONTRACT_RETURN: id => `/subcontract/returns/${id}`,
  // 样品
  SAMPLE_ORDER: id => `/sample/edit/${id}`,
  FREE_SAMPLE: id => `/freesample/edit/${id}`,
  // 财务
  PAYMENT_RECEIPT: id => `/finance/receipt/edit/${id}`,
  PAYMENT_VOUCHER: id => `/finance/payment/edit/${id}`,
  SALE_PRE_RECEIPT: id => `/finance/sale-pre-receipts/edit/${id}`,
  PURCHASE_PRE_PAYMENT: id => `/finance/purchase-pre-payments/edit/${id}`,
  OPERATION_EXPENSE: id => `/finance/operation-expense/${id}`,
  // 基础资料
  BOM: id => `/masterdata/bom/${id}`
}

/** 该单据类型是否有独立编辑页（无页面者单号不可点） */
export function canJumpBill (docType) {
  return typeof BILL_EDIT_ROUTE[docType] === 'function'
}

/** 解析编辑页路由；类型不支持或 id 为空时返回 null */
export function resolveBillRoute (docType, id) {
  const build = BILL_EDIT_ROUTE[docType]
  if (!build || id === undefined || id === null || id === '') return null
  try {
    return build(id)
  } catch (e) {
    return null
  }
}
