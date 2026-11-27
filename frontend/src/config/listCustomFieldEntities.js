/**
 * 「单据列表 · 自定义字段列」页标识 → 业务对象/主键 映射（唯一事实来源）
 *
 * 背景：设置中心自定义字段支持「列表显示」开关（is_list_show=1）。基础资料（CrudTable）
 * 早已按它出列；但单据列表（BillListPage）此前只做了"按自定义字段搜索"，没有把勾了
 * 「列表显示」的字段渲染成列 —— 开关看着有、实际不生效。
 *
 * 本模块把"哪个列表页对应哪个业务对象、以及该对象主键在行数据里的字段名"集中登记，
 * 由 useColumnSetting（追加列定义）与 BillListPage（回填字段值）共同使用，避免各页手写。
 *
 * 口径：
 *  - 仅登记"已接入自定义字段"的单据对象（与后端 CustomFieldServiceImpl.ENTITY_TABLE_MAP 一致）。
 *  - 已在页面里自行处理自定义列（如库存台账 InventoryList/BatchList/StockFlowList）不登记，避免重复出列。
 *  - idKey = 该对象主键在前端行数据里的字段名（= Java 实体 @TableId 属性名）；写错会导致"列在、值空"。
 */
import { ref } from 'vue'
import { loadCustomFieldDefinitions } from '@/composables/useCustomField'

export const LIST_CUSTOM_FIELD_ENTITIES = {
  // 销售管理
  SaleQuoteList: { entity: 'sale_quote', idKey: 'quoteId' },
  SaleContractList: { entity: 'sale_contract', idKey: 'contractId' },
  SaleOrderList: { entity: 'sale_order', idKey: 'orderId' },
  SaleDeliveryList: { entity: 'sale_delivery', idKey: 'deliveryId' },
  SaleReturnList: { entity: 'sale_return', idKey: 'returnId' },
  SaleExchangeList: { entity: 'sale_exchange', idKey: 'exchangeId' },
  SaleRepairList: { entity: 'sale_repair', idKey: 'repairId' },
  SaleInvoiceList: { entity: 'sale_invoice', idKey: 'invoiceId' },
  // 采购管理
  PurchaseRequestList: { entity: 'purchase_request', idKey: 'requestId' },
  PurchaseOrderList: { entity: 'purchase_order', idKey: 'orderId' },
  PurchaseInboundList: { entity: 'purchase_inbound', idKey: 'receiptId' },
  PurchaseReturnList: { entity: 'purchase_return', idKey: 'returnId' },
  PurchaseContractList: { entity: 'purchase_contract', idKey: 'contractId' },
  PurchaseInvoiceList: { entity: 'purchase_invoice', idKey: 'invoiceId' },
  // 财务·预付/预收（2026-11-27 补登记：后端自定义字段映射已存在，列表此前未接）
  PurchasePrePaymentList: { entity: 'purchase_pre_payment', idKey: 'id' },
  SalePreReceiptList: { entity: 'sale_pre_receipt', idKey: 'id' },
  // 库存管理
  StockTransferList: { entity: 'stock_transfer', idKey: 'transferId' },
  OtherReceiptList: { entity: 'other_receipt', idKey: 'receiptId' },
  OtherIssueList: { entity: 'other_issue', idKey: 'issueId' },
  MaterialRequisitionList: { entity: 'material_requisition', idKey: 'requisitionId' },
  // 生产退料/补料与领料共用 material_requisition 表（自定义字段定义同源；2026-11-27 补登记）
  ProductionReturnList: { entity: 'material_requisition', idKey: 'requisitionId' },
  ProductionSupplementList: { entity: 'material_requisition', idKey: 'requisitionId' },
  StockCheckList: { entity: 'stock_check', idKey: 'checkId' },
  // 生产管理
  ProduceAssembleList: { entity: 'produce_assemble', idKey: 'id' },
  ProduceDisassembleList: { entity: 'produce_disassemble', idKey: 'id' },
  WorkOrderList: { entity: 'production_order', idKey: 'orderId' },
  ProductionReceiptList: { entity: 'production_receipt', idKey: 'receiptId' },
  OperationReportList: { entity: 'operation_report', idKey: 'reportId' },
  // 委外管理（主键均为 id）
  SubcontractList: { entity: 'subcontract_order', idKey: 'id' },
  SubcontractPickList: { entity: 'subcontract_pick', idKey: 'id' },
  SubcontractInboundList: { entity: 'subcontract_inbound', idKey: 'id' },
  SubcontractReturnList: { entity: 'subcontract_return', idKey: 'id' },
  SubcontractSupplementList: { entity: 'subcontract_supplement', idKey: 'id' },
  // 打样管理
  SampleList: { entity: 'sample_order', idKey: 'sampleId' },
  FreeSampleList: { entity: 'free_sample_order', idKey: 'orderId' },
  // 财务·收付款（2026-11-27 补登记：后端自定义字段映射已存在，列表此前未接）
  PaymentReceiptList: { entity: 'cash_receipt', idKey: 'receiptId' },
  PaymentVoucherList: { entity: 'cash_payment', idKey: 'paymentId' }
}

export function resolveListCustomField(pageKey) {
  return pageKey ? (LIST_CUSTOM_FIELD_ENTITIES[pageKey] || null) : null
}

/** entity → 自定义列 ref 缓存（同一对象只加载一次，避免同一页两处各拉一遍） */
const columnCache = {}

/**
 * 返回该列表页的「列表显示」自定义字段列定义（响应式、异步填充）。
 * 页面/壳未登记的返回空数组，页面行为与改动前完全一致（零回归）。
 */
export function listCustomColumnsRef(pageKey) {
  const target = resolveListCustomField(pageKey)
  if (!target) return ref([])
  if (columnCache[target.entity]) return columnCache[target.entity]
  const cols = ref([])
  columnCache[target.entity] = cols
  loadCustomFieldDefinitions(target.entity)
    .then(res => {
      const raw = Array.from((res && res.fieldMap) ? res.fieldMap.values() : [])
      cols.value = raw
        .filter(f => f && f.isListShow === 1)
        .map(f => ({
          prop: f.fieldKey,
          label: f.fieldLabel,
          minWidth: 140,
          customField: true, // 标记：由通用机制追加的自定义列
          defaultHidden: false
        }))
    })
    .catch(() => { /* 静默降级：拿不到定义就不出列，不影响页面 */ })
  return cols
}
