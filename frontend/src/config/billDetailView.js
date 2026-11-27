// ====================================================================
// 列表页「明细模式」登记表（2026-10-12 铺开批次）
//
// 列表页通用壳按 storageKey 在这里查到配置 → 该页自动获得明细模式能力：
//   列设置抽屉顶部出现「视图模式」，明细列清单来自对应单据明细报表的 /columns
//   （表头字段 + 明细行字段 + 自定义字段 + 行级回写列），零页面代码接入。
//
// 口径（登记表《列表页汇总明细双视图_立项登记表》第三节 3.2 对照表 + 二期用户口径）：
//   回写列按单据关系各配各的；没有下游关系的单据不出回写列（不硬凑）。
//   2026-10-14 二期：剩余 20 页一次全铺（全站带明细行的单据列表页 100% 具备双视图）；
//   工序汇报单已并入工单流程（无独立页面）不登记；库存流水/工艺路线/工作中心/工位/质检模板无明细行概念不接入。
// ====================================================================

export const BILL_DETAIL_VIEW_CONFIGS = {
  // ===== 销售 =====
  SaleDeliveryList: { apiBase: '/report/sale/delivery-detail', linkPrefix: '/sale/deliveries/', exportName: '销售出库明细' },
  SaleInvoiceList: { apiBase: '/report/sale/invoice-detail', linkPrefix: '/sale/invoices/', exportName: '销售发票明细' },
  // ===== 采购 =====
  PurchaseOrderList: { apiBase: '/report/purchase/order-detail', linkPrefix: '/purchase/orders/', exportName: '采购订单明细' },
  PurchaseRequestList: { apiBase: '/report/purchase/request-detail', linkPrefix: '/purchase/requests/', exportName: '采购申请明细' },
  PurchaseContractList: { apiBase: '/report/purchase/contract-detail', linkPrefix: '/purchase/contracts/', exportName: '采购合同明细' },
  PurchaseInboundList: { apiBase: '/report/purchase/inbound-detail', linkPrefix: '/purchase/inbounds/', exportName: '采购入库明细' },
  PurchaseInvoiceList: { apiBase: '/report/purchase/invoice-detail', linkPrefix: '/purchase/invoices/', exportName: '采购发票明细' },
  PurchaseReturnList: { apiBase: '/report/purchase/return-detail', linkPrefix: '/purchase/returns/', exportName: '采购退货明细' },
  // ===== 库存 =====
  MaterialRequisitionList: { apiBase: '/report/inventory/material-requisition-detail', linkPrefix: '/inventory/material-requisitions/edit/', exportName: '生产领料明细' },
  OtherReceiptList: { apiBase: '/report/inventory/other-receipt-detail', linkPrefix: '/inventory/other-receipts/edit/', exportName: '其他入库明细' },
  OtherIssueList: { apiBase: '/report/inventory/other-issue-detail', linkPrefix: '/inventory/other-issues/edit/', exportName: '其他出库明细' },
  StockTransferList: { apiBase: '/report/inventory/stock-transfer-detail', linkPrefix: '/inventory/stock-transfers/edit/', exportName: '库存调拨明细' },
  StockCheckList: { apiBase: '/report/inventory/stock-check-detail', linkPrefix: '/inventory/stock-checks/edit/', exportName: '库存盘点明细' },
  // ===== 生产 =====
  WorkOrderList: { apiBase: '/report/production/order-all-detail', linkPrefix: '/production/workorders/', exportName: '生产工单全明细' },
  ProductionReceiptList: { apiBase: '/report/production/receipt-detail', linkPrefix: '/production/receipts/edit/', exportName: '生产入库明细' },
  ProductionReturnList: { apiBase: '/report/production/material-return-detail', linkPrefix: '/production/returns/edit/', exportName: '生产退料明细' },
  ProductionSupplementList: { apiBase: '/report/production/material-supplement-detail', linkPrefix: '/production/supplements/edit/', exportName: '生产补料明细' },
  // ===== 二期铺开（2026-10-14）：剩余单据一次全铺 =====
  // 销售
  SaleContractList: { apiBase: '/report/sale/contract-detail', linkPrefix: '/sale/contracts/', exportName: '销售合同明细' },
  SaleReturnList: { apiBase: '/report/sale/return-detail', linkPrefix: '/sale/returns/', exportName: '销售退货明细' },
  SaleExchangeList: { apiBase: '/report/sale/exchange-detail', linkPrefix: '/sale/exchanges/', exportName: '销售换货明细' },
  // 售后维修：编辑页两张明细（维修明细 + 配件领用）走合并全明细（2026-11-27 补漏项）
  SaleRepairList: { apiBase: '/report/sale/repair-all-detail', linkPrefix: '/sale/repairs/', exportName: '售后维修全明细' },
  SaleQuoteList: { apiBase: '/report/sale/quote-detail', linkPrefix: '/sale/quotes/', exportName: '销售报价明细' },
  // 委外
  SubcontractList: { apiBase: '/report/subcontract/order-detail', linkPrefix: '/subcontract/orders/', exportName: '委外加工单明细' },
  SubcontractPickList: { apiBase: '/report/subcontract/pick-detail', linkPrefix: '/subcontract/picks/', exportName: '委外发料明细' },
  SubcontractInboundList: { apiBase: '/report/subcontract/inbound-detail', linkPrefix: '/subcontract/inbounds/', exportName: '委外入库明细' },
  SubcontractReturnList: { apiBase: '/report/subcontract/return-detail', linkPrefix: '/subcontract/returns/', exportName: '委外退料明细' },
  SubcontractSupplementList: { apiBase: '/report/subcontract/supplement-detail', linkPrefix: '/subcontract/supplements/', exportName: '委外补料明细' },
  // 生产（组装/拆卸按成品明细层拍平；工序汇报无明细子表=一行一单；工资核算无独立编辑页，单号不可点）
  ProduceAssembleList: { apiBase: '/report/production/assemble-all-detail', linkPrefix: '/production/assemble/', exportName: '组装单全明细' },
  ProduceDisassembleList: { apiBase: '/report/production/disassemble-all-detail', linkPrefix: '/production/disassemble/', exportName: '拆卸单全明细' },
  OperationReportList: { apiBase: '/report/production/operation-report-detail', linkPrefix: '/production/operation-reports/edit/', exportName: '工序汇报明细' },
  MrpPlanList: { apiBase: '/report/production/mrp-plan-detail', linkPrefix: '/production/mrp-plans/edit/', exportName: 'MRP运算计划明细' },
  // 财务
  PaymentReceiptList: { apiBase: '/report/finance/receipt-all-detail', linkPrefix: '/finance/receipt/edit/', exportName: '收款单全明细' },
  PaymentVoucherList: { apiBase: '/report/finance/payment-all-detail', linkPrefix: '/finance/payment/edit/', exportName: '付款单全明细' },
  SalePreReceiptList: { apiBase: '/report/finance/sale-pre-receipt-detail', linkPrefix: '/finance/sale-pre-receipts/edit/', exportName: '销售预收款明细' },
  PurchasePrePaymentList: { apiBase: '/report/finance/purchase-pre-payment-detail', linkPrefix: '/finance/purchase-pre-payments/edit/', exportName: '采购预付款明细' },
  OperationExpenseList: { apiBase: '/report/finance/operation-expense-all-detail', linkPrefix: '/finance/operation-expense/', exportName: '费用分摊单全明细' },
  WageSettlementList: { apiBase: '/report/finance/wage-settlement-detail', linkPrefix: '', exportName: '工序工资核算明细' },
  // 打样
  SampleList: { apiBase: '/report/sample/order-detail', linkPrefix: '/sample/edit/', exportName: '打样单明细' },
  FreeSampleList: { apiBase: '/report/sample/free-sample-detail', linkPrefix: '/freesample/edit/', exportName: '免费样品订单明细' }
}
