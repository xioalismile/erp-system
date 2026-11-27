// 页面 storageKey → 字段权限 page_key（= 单据主表名）。
// 由《单据与页面登记》/静态体检表推导；新增单据在此自动获得字段权限，不再逐页传 field-perm-key。
export const BILL_PAGE_PERM_KEYS = {
  FreeSampleList: 'free_sample_order',
  MaterialRequisitionList: 'material_requisition',
  MrpPlanList: 'mrp_plan',
  OperationExpenseList: 'operation_expense',
  OperationReportList: 'operation_report',
  OtherIssueList: 'other_issue',
  OtherReceiptList: 'other_receipt',
  PaymentReceiptList: 'cash_receipt',
  PaymentVoucherList: 'cash_payment',
  ProduceAssembleList: 'produce_assemble',
  ProduceDisassembleList: 'produce_disassemble',
  ProductionReceiptList: 'production_receipt',
  ProductionReturnList: 'material_requisition',
  ProductionSupplementList: 'material_requisition',
  PurchaseContractList: 'purchase_contract',
  PurchaseInboundList: 'purchase_receipt',
  PurchaseInvoiceList: 'purchase_invoice',
  PurchaseOrderList: 'purchase_order',
  PurchasePrePaymentList: 'purchase_pre_payment',
  PurchaseRequestList: 'purchase_request',
  PurchaseReturnList: 'purchase_return',
  SaleContractList: 'sale_contract',
  SaleDeliveryList: 'sale_delivery',
  SaleExchangeList: 'sale_exchange',
  SaleInvoiceList: 'sale_invoice',
  SaleOrderList: 'sale_order',
  SalePreReceiptList: 'sale_pre_receipt',
  SaleQuoteList: 'sale_quote',
  SaleRepairList: 'sale_repair',
  SaleReturnList: 'sale_return',
  SampleList: 'sample_order',
  StockCheckList: 'stock_check',
  StockTransferList: 'stock_transfer',
  SubcontractInboundList: 'subcontract_inbound',
  SubcontractList: 'subcontract_order',
  SubcontractPickList: 'subcontract_pick',
  SubcontractReturnList: 'subcontract_return',
  SubcontractSupplementList: 'subcontract_supplement',
  WageSettlementList: 'wage_settlement',
  WorkOrderList: 'production_order',
}

/** 支持明细列设置页键（storageKey::detail）；未登记返回空串 */
export function permKeyOfPage(pageKey) {
  if (!pageKey) return ''
  const base = String(pageKey).replace(/::detail$/, '')
  return BILL_PAGE_PERM_KEYS[base] || ''
}
