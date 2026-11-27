/**
 * 单据「接口基路径」登记表（供 03 界面闭环把单据置为已生效、以及 ⑤e 体检后清理使用）
 *
 * 为什么单独一张表：界面闭环（03）只关心怎么在页面上操作，不关心接口路径；
 * 而"把界面建出来的单据提交/审核到已生效"需要按单据调对应接口。这里一处登记，两处复用。
 *
 * 字段说明（全部按源码核实，禁止凭经验填）：
 *   base            接口基路径（前端 src/api 下的 BASE 常量，含 /api 前缀）
 *   noSubmit        该单据没有「提交」接口，草稿可直接审核（生产入库单）
 *   pre             审核前必须先调的动作（收款单/付款单要先「确认」）
 *   approvePath     审核接口末段，默认 approve（销售预收款单是 audit）
 *   approveMethod   审核方法，默认 POST（销售预收款单是 PUT）
 *   note            无接口时的说明（页面已下线等）
 */
export const BILL_API = {
  // ---------- 销售 ----------
  sale_order: { base: '/api/sale/orders' },
  sale_quote: { base: '/api/sale/quotes' },
  sale_contract: { base: '/api/sale/contracts' },
  sale_delivery: { base: '/api/sale/deliveries' },
  sale_return: { base: '/api/sale/returns' },
  sale_invoice: { base: '/api/sale/invoices' },
  // ---------- 采购 ----------
  purchase_request: { base: '/api/purchase/requests' },
  purchase_contract: { base: '/api/purchase/contract' },
  purchase_order: { base: '/api/purchase/orders' },
  purchase_receipt: { base: '/api/purchase/inbounds' },
  purchase_return: { base: '/api/purchase/returns' },
  purchase_invoice: { base: '/api/purchase/invoices' },
  // ---------- 库存 ----------
  other_receipt: { base: '/api/inventory/other-receipts' },
  other_issue: { base: '/api/inventory/other-issues' },
  stock_transfer: { base: '/api/inventory/stock-transfers' },
  stock_check: { base: '/api/inventory/stock-checks' },
  material_requisition: { base: '/api/inventory/material-requisitions' },
  // ---------- 生产 ----------
  production_receipt: { base: '/api/production/receipts', noSubmit: true },
  process_transfer: { base: null, note: '工序转移单已下线（页面重定向到生产工单），无独立接口' },
  production_order: { base: '/api/production/workorders' },
  produce_assemble: { base: '/api/produce/assemble' },
  produce_disassemble: { base: '/api/produce/disassemble' },
  // ---------- 委外 ----------
  // 委外加工单：状态是数字体系（0草稿 1待审核 2已生效 3已下达…）；下游 4 张委外单只允许选“已下达及以后”，
  // 所以置生效后还要补一步「下达」（POST /{id}/release），否则下游下拉永远没有可选项（2026-11-11 实测）。
  subcontract: { base: '/api/subcontract/orders', effectiveValues: ['2', '3'], afterEffective: ['release'] },
  subcontract_pick: { base: '/api/subcontract/picks' },
  subcontract_inbound: { base: '/api/subcontract/inbounds' },
  subcontract_return: { base: '/api/subcontract/returns' },
  subcontract_supplement: { base: '/api/subcontract/supplements' },
  // ---------- 财务 ----------
  sale_pre_receipt: { base: '/api/sale-pre-receipt', approvePath: 'audit', approveMethod: 'PUT' },
  purchase_pre_payment: { base: '/api/purchase-pre-payment' },
  payment_receipt: { base: '/api/finance/receipts', pre: ['confirm'] },
  payment_voucher: { base: '/api/finance/vouchers', pre: ['confirm'] },
  // ---------- 打样 / 其他 ----------
  sample_order: { base: '/api/sample/orders' },
  free_sample_order: { base: '/api/free-sample' },
  bom: { base: '/api/masterdata/bom' }
}
