// ====================================================================
// 下推关系配置 - 定义各模块单据之间的下推链路与字段映射
// 参照金蝶云星空/用友U8标准: "下推"用于源单据生成下游单据, "选单"用于下游单据选择上游来源
// 字段对齐智能ERP系统-完整表单字段规范文档 (FSourceType/FSourceBillNo/FSourceRowNo)
// ====================================================================
import { BILL_STATUS, APPROVED_CLASS_SET } from '@/utils/bill-status'

// ============== 下推状态要求统一引用常量（禁止硬编码"已生效"等字符串） ==============
const APPROVED = BILL_STATUS.APPROVED
const APPROVED_OR_CONFIRMED = [BILL_STATUS.APPROVED, BILL_STATUS.CONFIRMED]
// 委外加工单提交后状态是「已下达」（不是「已生效」），其下游发料/入库据此放行
const APPROVED_OR_ISSUED = [BILL_STATUS.APPROVED, BILL_STATUS.ISSUED]

/** 判断一个状态是否满足 sourceStatusRequired 要求（支持字符串或数组）*/
export function meetsStatusRequired (statusOfSource, required) {
  if (!required) return true
  const normalizedList = Array.isArray(required) ? required : [required]
  const s = String(statusOfSource ?? '')
  for (const r of normalizedList) {
    // 字符串等值匹配
    if (s === String(r)) return true
  }
  // APPROVED_CLASS_SET 兼容：若只要 "已生效类" 都 OK，传常量 APPROVED_CLASS_SET（通过标志位 isApprovedClass）
  return false
}

// 单据类型枚举 (与路由 name 对齐)
export const DOC_TYPE = {
  // 销售
  SALE_QUOTE:    { key: 'SaleQuote',    name: '销售报价单', prefix: 'QUT',  route: '/sale/quotes',       editRoute: '/sale/quotes',       idField: 'quoteId',    noField: 'quoteNo'    },
  SALE_CONTRACT: { key: 'SaleContract', name: '销售合同',   prefix: 'SC',   route: '/sale/contracts',     editRoute: '/sale/contracts',     idField: 'contractId', noField: 'contractNo' },
  SALE_ORDER:    { key: 'SaleOrder',    name: '销售订单',   prefix: 'SO',   route: '/sale/orders',        editRoute: '/sale/orders',        idField: 'orderId',    noField: 'orderNo'    },
  SALE_DELIVERY: { key: 'SaleDelivery', name: '销售出库单', prefix: 'SOUT', route: '/sale/deliveries',    editRoute: '/sale/deliveries',    idField: 'deliveryId', noField: 'deliveryNo' },
  SALE_RETURN:   { key: 'SaleReturn',   name: '销售退货单', prefix: 'SRT',  route: '/sale/returns',       editRoute: '/sale/returns',       idField: 'returnId',    noField: 'returnNo'    },
  SALE_INVOICE:  { key: 'SaleInvoice',  name: '销售发票',   prefix: 'SINV', route: '/sale/invoices',      editRoute: '/sale/invoices',       idField: 'invoiceId',   noField: 'invoiceNo'   },
  // 采购
  PURCHASE_CONTRACT:{ key: 'PurchaseContract',name: '采购合同',   prefix: 'PC',   route: '/purchase/contracts',editRoute: '/purchase/contracts',idField: 'contractId',noField: 'contractNo'},
  PURCHASE_REQUEST: { key: 'PurchaseRequest', name: '采购申请单', prefix: 'PR',   route: '/purchase/requests', editRoute: '/purchase/requests', idField: 'requestId', noField: 'requestNo' },
  PURCHASE_ORDER:   { key: 'PurchaseOrder',   name: '采购订单',   prefix: 'PO',   route: '/purchase/orders',   editRoute: '/purchase/orders',   idField: 'orderId',   noField: 'orderNo'   },
  PURCHASE_INBOUND: { key: 'PurchaseInbound', name: '采购入库单', prefix: 'PIN',  route: '/purchase/inbounds', editRoute: '/purchase/inbounds', idField: 'receiptId', noField: 'receiptNo' },
  PURCHASE_RETURN:  { key: 'PurchaseReturn',  name: '采购退货单', prefix: 'PRT',  route: '/purchase/returns',   editRoute: '/purchase/returns',   idField: 'returnId',  noField: 'returnNo'   },
  PURCHASE_INVOICE: { key: 'PurchaseInvoice', name: '采购发票',   prefix: 'PINV', route: '/purchase/invoices', editRoute: '/purchase/invoices', idField: 'invoiceId', noField: 'invoiceNo' },
  // 生产
  // 生产工单：详情接口返回的是 WorkOrder 实体，主键/单号字段是 orderId/orderNo
  //（不是 workOrderId/workOrderNo——写错会让"选单"拉详情时传 undefined、单号列恒空白）
  // 编码与后端台账 BillLinkRegistry 对齐：原 PRODUCTION_WORKORDER → PRODUCTION_ORDER（2026-09-20 统一，仅本文件内部引用，无外部消费者）
  PRODUCTION_ORDER:      { key: 'WorkOrders',         name: '生产工单',   prefix: 'MO',     route: '/production/workorders',     editRoute: '/production/workorders',     idField: 'orderId',       noField: 'orderNo'       },
  PRODUCTION_RECEIPT:    { key: 'ProductionReceipts', name: '生产入库单', prefix: 'MOIN',   route: '/production/receipts',         editRoute: '/production/receipts',         idField: 'receiptId',     noField: 'receiptNo'     },
  // 编码与后端台账 BillLinkRegistry 对齐：原 PRODUCTION_PICK → MATERIAL_REQUISITION（2026-09-20 统一，仅本文件内部引用）
  MATERIAL_REQUISITION:  { key: 'MaterialRequisitions', name: '生产领料单', prefix: 'MOPICK', route: '/inventory/material-requisitions', editRoute: '/inventory/material-requisitions', idField: 'requisitionId', noField: 'requisitionNo' },
  OPERATION_REPORT:      { key: 'OperationReport',    name: '工序汇报单', prefix: 'PRP',    route: '/production/operation-reports', editRoute: '/production/operation-reports', idField: 'reportId',  noField: 'reportNo'  },
  SUBCONTRACT_ORDER:     { key: 'SubcontractOrder',   name: '委外加工单', prefix: 'SC-ORDER', route: '/subcontract/orders',         editRoute: '/subcontract/orders',          idField: 'id',         noField: 'orderNo'    },
  SUBCONTRACT_PICK:      { key: 'SubcontractPick',    name: '委外发料单', prefix: 'SPICK',    route: '/subcontract/picks',          editRoute: '/subcontract/picks',           idField: 'id',         noField: 'pickNo'     },
  SUBCONTRACT_INBOUND:   { key: 'SubcontractInbound', name: '委外入库单', prefix: 'SIN',      route: '/subcontract/inbounds',       editRoute: '/subcontract/inbounds',        idField: 'id',         noField: 'inboundNo'  },
  SUBCONTRACT_RETURN:    { key: 'SubcontractReturn',  name: '委外退料单', prefix: 'SRT',      route: '/subcontract/returns',        editRoute: '/subcontract/returns',         idField: 'id',         noField: 'returnNo'   },
  SUBCONTRACT_SUPPLEMENT:{ key: 'SubcontractSupplement', name: '委外补料单', prefix: 'SSUP',  route: '/subcontract/supplements',    editRoute: '/subcontract/supplements',     idField: 'id',         noField: 'supplementNo' },
  MRP_PLAN:              { key: 'MrpPlan',            name: 'MRP计划',    prefix: 'MRP',    route: '/mrp/plans',                  editRoute: '/mrp/plans',                   idField: 'id',         noField: 'planNo'    },
  // 打样
  SAMPLE_ORDER:  { key: 'SampleOrder',  name: '打样单',  prefix: 'SAM',  route: '/sample/orders',  editRoute: '/sample/orders',  idField: 'sampleId',  noField: 'sampleNo'  }
}

// 前端单据类型 key → 后端 BillLinkRegistry 单据类型编码（下推状态接口用）
export const DOC_BACKEND_TYPE = {
  SaleQuote: 'SALE_QUOTE',
  SaleContract: 'SALE_CONTRACT',
  SaleOrder: 'SALE_ORDER',
  SaleDelivery: 'SALE_DELIVERY',
  SaleReturn: 'SALE_RETURN',
  SaleInvoice: 'SALE_INVOICE',
  PurchaseContract: 'PURCHASE_CONTRACT',
  PurchaseRequest: 'PURCHASE_REQUEST',
  PurchaseOrder: 'PURCHASE_ORDER',
  PurchaseInbound: 'PURCHASE_INBOUND',
  PurchaseReturn: 'PURCHASE_RETURN',
  PurchaseInvoice: 'PURCHASE_INVOICE',
  WorkOrders: 'PRODUCTION_ORDER',
  ProductionReceipts: 'PRODUCTION_RECEIPT',
  MaterialRequisitions: 'MATERIAL_REQUISITION',
  OperationReport: 'OPERATION_REPORT',
  SubcontractOrder: 'SUBCONTRACT_ORDER',
  SubcontractPick: 'SUBCONTRACT_DELIVERY',
  SubcontractInbound: 'SUBCONTRACT_RECEIPT',
  SubcontractReturn: 'SUBCONTRACT_RETURN',
  SubcontractSupplement: 'SUBCONTRACT_SUPPLEMENT',
  MrpPlan: 'MRP_PLAN',
  SampleOrder: 'SAMPLE_ORDER'
}

// 下推关系定义
// sourceType: 源单类型 (DOC_TYPE.key)
// targetType: 目标单类型
// sourceStatusRequired: 源单必须处于的状态 (满足才能下推)
// fieldMapper: (sourceDoc) => 目标单表头初始值
// itemMapper:   (sourceItem, sourceDoc) => 目标单明细行
// permCode: 下推按钮权限码
export const PUSH_RELATIONS = [
  // ===== 销售模块 =====
  {
    sourceType: 'SaleQuote',
    targetType: 'SaleOrder',
    sourceStatusRequired: APPROVED,
    permCode: 'sale:quote:push',
    fieldMapper: (s) => ({
      customerId: s.customerId,
      customerName: s.customerName,
      salerId: s.salerId,
      salerName: s.salerName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      paymentTermId: s.paymentTermId,
      paymentTermName: s.paymentTermName,
      deliveryDate: s.deliveryDate,
      // 交易字段补带（2026-10-13 对账）：价格等级此前缺带 → 订单不按价格等级调价
      priceLevelId: s.priceLevelId,
      discountRate: s.discountRate || 0,
      remark: `由报价单 ${s.quoteNo} 下推生成`,
      // 来源信息
      sourceBillType: '销售报价单',
      sourceBillNo: s.quoteNo,
      // 附件携带
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      // 物料主图携带
      materialMainImage: it.materialMainImage || '',
      specification: it.specification,
      unitId: it.unitId,
      unit: it.unit,
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      // 勾稽字段
      sourceId: srcDoc.quoteId,
      sourceItemId: it.quoteItemId,
      sourceBillType: '销售报价单',
      sourceBillNo: srcDoc.quoteNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.quoteId,
      sourceType: 'sell'
    })
  },
  // ===== 销售报价单 → 销售合同 =====
  {
    sourceType: 'SaleQuote',
    targetType: 'SaleContract',
    sourceStatusRequired: APPROVED,
    permCode: 'sale:quote:push',
    fieldMapper: (s) => ({
      customerId: s.customerId,
      customerName: s.customerName,
      customerNo: s.customerNo || '',
      customerShortName: s.customerShortName || '',
      contactPerson: s.contactPerson || '',
      contactPhone: s.contactPhone || '',
      salerId: s.salerId,
      salerName: s.salerName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      paymentTermId: s.paymentTermId,
      paymentTermName: s.paymentTermName,
      deliveryDate: s.deliveryDate,
      // 交易字段补带（2026-10-13 对账）：价格等级此前缺带
      priceLevelId: s.priceLevelId,
      discountRate: s.discountRate || 0,
      remark: `由报价单 ${s.quoteNo} 下推生成`,
      sourceBillType: '销售报价单',
      sourceBillNo: s.quoteNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      materialMainImage: it.materialMainImage || '',
      specification: it.specification,
      unitId: it.unitId,
      unit: it.unit,
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      // 勾稽字段
      sourceId: srcDoc.quoteId,
      sourceItemId: it.quoteItemId,
      sourceBillType: '销售报价单',
      sourceBillNo: srcDoc.quoteNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.quoteId,
      sourceType: 'sell'
    })
  },
  // ===== 销售合同 → 销售订单 =====
  {
    sourceType: 'SaleContract',
    targetType: 'SaleOrder',
    sourceStatusRequired: APPROVED,
    permCode: 'sale:contract:push',
    fieldMapper: (s) => ({
      customerId: s.customerId,
      customerName: s.customerName,
      salerId: s.salerId,
      salerName: s.salerName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      paymentTermId: s.paymentTermId,
      paymentTermName: s.paymentTermName,
      deliveryDate: s.deliveryDate,
      // 交易字段补带（2026-10-13 对账）：价格等级/部门/定金此前缺带
      priceLevelId: s.priceLevelId,
      departmentId: s.departmentId,
      depositAmount: s.depositAmount,
      discountRate: s.discountRate || 0,
      remark: `由销售合同 ${s.contractNo} 下推生成`,
      sourceBillType: '销售合同',
      sourceBillNo: s.contractNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      materialMainImage: it.materialMainImage || '',
      specification: it.specification,
      unitId: it.unitId,
      unit: it.unit,
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      // 勾稽字段
      sourceId: srcDoc.contractId,
      sourceItemId: it.contractItemId,
      sourceBillType: '销售合同',
      sourceBillNo: srcDoc.contractNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.contractId,
      sourceType: 'sell'
    })
  },
  // ===== 销售合同 → 采购合同 =====
  // 口径（2026-09-15 用户指令）：两边表头不一致，只推「单据信息」「交易信息」两段的同义字段 + 物料明细；
  // 客户信息段不推（采购合同的供应商必须用户自选）、销售员不映射到采购员、目标单合同号由自身编号规则生成。
  {
    sourceType: 'SaleContract',
    targetType: 'PurchaseContract',
    sourceStatusRequired: APPROVED,
    permCode: 'sale:contract:push',
    fieldMapper: (s) => ({
      // ---- 单据信息（合同号不带：目标单自动编号，防重号）----
      contractDate: s.contractDate,
      // 合同类型值域不同：销售侧的"销售合同"在采购侧下拉不存在，改写为"采购合同"；框架协议/年度合同原样带
      contractType: s.contractType === '销售合同' ? '采购合同' : (s.contractType || ''),
      validityStart: s.validityStart,
      validityEnd: s.validityEnd,
      deliveryDate: s.deliveryDate,
      // ---- 交易信息（只取两边同义字段）----
      currencyId: s.currencyId,
      currencyCode: s.currencyCode || '',
      exchangeRate: s.exchangeRate,
      warehouseId: s.warehouseId,
      // 是否含税：销售侧存 '是'/'否'，采购侧界面同为 '是'/'否'（保存时由 sanitizeContract 转 1/0）
      isTax: s.isTax,
      taxCalcMethod: s.taxCalcMethod,
      discountRate: s.discountRate || 0,
      // 交易/联系字段补带（2026-10-13 用户拍板）：付款条件/运输方式/收货地址/联系人/电话——
      // 原"采购合同无对应字段"的理由已过时（字段 2026-09-20 已补齐），经用户确认改为带上；
      // 两条不变口径：供应商必须自选、销售员不推送到采购员
      paymentTermId: s.paymentTermId,
      transportMethod: s.transportMethod,
      receiveAddress: s.receiveAddress,
      contactPerson: s.contactPerson,
      contactPhone: s.contactPhone,
      // 备注沿用全站惯例，同时承担可追溯
      remark: `由销售合同 ${s.contractNo} 下推生成`,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      specification: it.specification,
      unitId: it.unitId,
      unit: it.unit,
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      // 折扣率不带：采购合同后端金额口径为 数量×单价(+税)，不参与折扣计算，带了会出现"有折扣率但金额未打折"的假象
      // 金额/税额/价税合计一律不带：由目标单后端 calculateItemAmount 重算
      deliveryDate: it.deliveryDate || null,
      remark: it.remark || '',
      // 勾稽字段（来源单号 → 采购合同明细"来源单号"列；选单时 SourcePicker 会按行覆盖为各自源单）
      sourceBillType: '销售合同',
      sourceBillNo: srcDoc.contractNo,
      sourceLineNo: it.sortNo,
      sourceDocId: srcDoc.contractId
    })
  },
  // ===== 打样单 → 销售订单 =====
  {
    sourceType: 'SampleOrder',
    targetType: 'SaleOrder',
    sourceStatusRequired: APPROVED_OR_CONFIRMED,
    permCode: 'sample:order:push',
    fieldMapper: (s) => ({
      customerId: s.customerId,
      customerName: s.customerName,
      contactPerson: s.contactPerson,
      contactPhone: s.contactPhone,
      departmentId: s.departmentId,
      // 计税方式显式携带（2026-10-13 对账）：打样单不在"计税方式随单下推"通用兜底的 12 类
      // 销售/采购单据清单里，不显式带则订单表头回落设置中心默认
      taxCalcMethod: s.taxCalcMethod || undefined,
      deliveryDate: s.demandDate ? s.demandDate : null,
      remark: `由打样单 ${s.sampleNo} 下推生成`,
      sourceBillType: '打样单',
      sourceBillNo: s.sampleNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => {
      const matId = it.childMaterialId || it.materialId
      const matCode = it.childMaterialCode || it.materialCode || ''
      const matName = it.childMaterialName || it.materialName || ''
      const qty = it.baseQty || it.sampleQty || 1
      return {
        materialId: matId,
        materialCode: matCode,
        materialName: matName,
        specification: it.specification || '',
        unitId: it.unitId,
        unit: '',
        quantity: qty,
        unitPrice: it.unitPrice || 0,
        taxRate: 13,
        discountRate: 0,
        // 勾稽字段
        sourceId: srcDoc.sampleId,
        sourceItemId: it.sampleItemId,
        sourceBillType: '打样单',
        sourceBillNo: srcDoc.sampleNo,
        sourceLineNo: it.lineNo,
        sourceDocId: srcDoc.sampleId,
        sourceType: 'sell'
      }
    }
  },
  {
    sourceType: 'SaleOrder',
    targetType: 'SaleDelivery',
    sourceStatusRequired: APPROVED,
    permCode: 'sale:order:push',
    fieldMapper: (s) => ({
      customerId: s.customerId,
      customerName: s.customerName,
      warehouseId: s.warehouseId,
      warehouseName: s.warehouseName,
      salerId: s.salerId,
      salerName: s.salerName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      orderNo: s.orderNo,
      sourceId: s.orderId,
      deliveryDate: new Date().toISOString().slice(0, 10),
      // 交易字段补带（2026-10-13 对账）：价格等级/付款条件/折扣率此前缺带
      priceLevelId: s.priceLevelId,
      paymentTermId: s.paymentTermId,
      discountRate: s.discountRate || 0,
      // 发货收款管控随单带值（2026-10-19）：出库单展示源头订单选定的管控模式，出库环节只读
      paymentControl: s.paymentControl,
      contactPerson: s.contactPerson || '',
      contactPhone: s.contactPhone || '',
      remark: `由销售订单 ${s.orderNo} 下推生成`,
      sourceBillType: '销售订单',
      sourceBillNo: s.orderNo,
      // 附件携带
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      // 物料主图携带
      materialMainImage: it.materialMainImage || '',
      specification: it.specification,
      unitId: it.unitId,
      unit: it.unit,
      quantity: it.quantity,  // 实际应减已出库数量, 简化处理
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      // 计税方式随行携带（2026-10-13 对账）：出库明细是行级计税方式（表头无此字段），
      // 源单（订单）是表头驱动 → 用源单表头计税方式写到出库每一行。
      // 值域转换：出库行下拉选项是「价内税/价外税/不计税」，订单表头是「含税价/不含税价/不计税」，
      // 不转换则下拉匹配不到选项、显示空白
      taxCalcMethod: srcDoc.taxCalcMethod === '含税价' ? '价内税'
        : (srcDoc.taxCalcMethod === '不计税' ? '不计税' : '价外税'),
      // 勾稽字段
      sourceId: srcDoc.orderId,
      sourceItemId: it.orderItemId,
      sourceBillType: '销售订单',
      sourceBillNo: srcDoc.orderNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.orderId,
      sourceType: 'sell',
      // 替换发货（2026-11-27）：原货品 + 销售预指定指示，供出库明细展开
      sourceMaterialId: it.materialId,
      sourceMaterialCode: it.materialCode,
      sourceMaterialName: it.materialName,
      substitutePlans: it.substitutePlans || []
    })
  },
  {
    sourceType: 'SaleOrder',
    targetType: 'SaleInvoice',
    sourceStatusRequired: APPROVED,
    permCode: 'sale:order:push',
    fieldMapper: (s) => ({
      customerId: s.customerId,
      customerName: s.customerName,
      warehouseId: s.warehouseId,
      warehouseName: s.warehouseName,
      salerId: s.salerId,
      salerName: s.salerName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      // 交易信息补带（2026-10-13）：价格等级/付款条件/表头折扣率此前缺带 → 下推后为空，
      // 价格等级不调价、付款条件算不出账期与预付；客户档案静态信息（税号/开户行/银行账号/地址）
      // 源单实体没有，由目标单编辑页按下推带入的客户自动补齐（见 SaleInvoiceEdit onMounted）
      priceLevelId: s.priceLevelId,
      paymentTermId: s.paymentTermId,
      discountRate: s.discountRate || 0,
      sourceId: s.orderId,
      invoiceDate: new Date().toISOString().slice(0, 10),
      invoiceType: '增值税专用发票',
      remark: `由销售订单 ${s.orderNo} 下推生成`,
      sourceBillType: '销售订单',
      sourceBillNo: s.orderNo,
      // 附件携带
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      // 物料主图携带
      materialMainImage: it.materialMainImage || '',
      specification: it.specification,
      unitId: it.unitId,
      unit: it.unit,
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      // 勾稽字段
      sourceId: srcDoc.orderId,
      sourceItemId: it.orderItemId,
      sourceBillType: '销售订单',
      sourceBillNo: srcDoc.orderNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.orderId,
      sourceType: 'sell'
    })
  },
  {
    sourceType: 'SaleDelivery',
    targetType: 'SaleReturn',
    sourceStatusRequired: APPROVED,
    permCode: 'sale:delivery:push',
    fieldMapper: (s) => ({
      customerId: s.customerId,
      customerName: s.customerName,
      warehouseId: s.warehouseId,
      warehouseName: s.warehouseName,
      salerId: s.salerId,
      salerName: s.salerName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      deliveryNo: s.deliveryNo,
      sourceId: s.deliveryId,
      returnDate: new Date().toISOString().slice(0, 10),
      // 交易字段补带（2026-10-13 对账）：价格等级/付款条件/折扣率此前缺带；
      // 计税方式：出库是行级、退货是表头级 → 取出库第一行计税方式写到退货表头。
      // 值域转换：出库行是「价内税/价外税」旧值域，退货表头下拉是「含税价/不含税价」新值域
      priceLevelId: s.priceLevelId,
      paymentTermId: s.paymentTermId,
      discountRate: s.discountRate || 0,
      taxCalcMethod: s.items && s.items[0] && s.items[0].taxCalcMethod
        ? (s.items[0].taxCalcMethod === '价内税' || s.items[0].taxCalcMethod === '含税价' ? '含税价'
          : (s.items[0].taxCalcMethod === '不计税' ? '不计税' : '不含税价'))
        : undefined,
      remark: `由销售出库单 ${s.deliveryNo} 下推生成`,
      sourceBillType: '销售出库单',
      sourceBillNo: s.deliveryNo,
      // 附件携带
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      // 物料主图携带
      materialMainImage: it.materialMainImage || '',
      specification: it.specification,
      unitId: it.unitId,
      unit: it.unit,
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      // 计税方式随行携带（2026-10-13 对账）：出库明细是行级计税方式
      taxCalcMethod: it.taxCalcMethod || undefined,
      // 勾稽字段
      sourceId: srcDoc.deliveryId,
      sourceItemId: it.deliveryItemId,
      sourceBillType: '销售出库单',
      sourceBillNo: srcDoc.deliveryNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.deliveryId,
      sourceType: 'sell'
    })
  },
  {
    sourceType: 'SaleDelivery',
    targetType: 'SaleInvoice',
    sourceStatusRequired: APPROVED,
    permCode: 'sale:delivery:push',
    fieldMapper: (s) => ({
      customerId: s.customerId,
      customerName: s.customerName,
      salerId: s.salerId,
      salerName: s.salerName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      // 交易信息补带（2026-10-13 对账）：价格等级/付款条件/折扣率此前缺带；
      // 计税方式：出库是行级、发票是表头级 → 取出库第一行计税方式写到发票表头。
      // （出库表头无计税方式字段，"计税方式随单下推"通用兜底对本链路不生效）
      // 值域转换：出库行是「价内税/价外税」旧值域，发票表头下拉是「含税价/不含税价」新值域
      priceLevelId: s.priceLevelId,
      paymentTermId: s.paymentTermId,
      discountRate: s.discountRate || 0,
      taxCalcMethod: s.items && s.items[0] && s.items[0].taxCalcMethod
        ? (s.items[0].taxCalcMethod === '价内税' || s.items[0].taxCalcMethod === '含税价' ? '含税价'
          : (s.items[0].taxCalcMethod === '不计税' ? '不计税' : '不含税价'))
        : undefined,
      deliveryNo: s.deliveryNo,
      sourceId: s.deliveryId,
      invoiceDate: new Date().toISOString().slice(0, 10),
      invoiceType: '增值税专用发票',
      remark: `由销售出库单 ${s.deliveryNo} 下推生成`,
      sourceBillType: '销售出库单',
      sourceBillNo: s.deliveryNo,
      // 附件携带
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      // 物料主图携带
      materialMainImage: it.materialMainImage || '',
      specification: it.specification,
      unitId: it.unitId,
      unit: it.unit,
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      // 勾稽字段
      sourceId: srcDoc.deliveryId,
      sourceItemId: it.deliveryItemId,
      sourceBillType: '销售出库单',
      sourceBillNo: srcDoc.deliveryNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.deliveryId,
      sourceType: 'sell'
    })
  },
  {
    sourceType: 'SaleReturn',
    targetType: 'SaleInvoice',
    sourceStatusRequired: APPROVED,
    permCode: 'sale:return:push',
    fieldMapper: (s) => ({
      customerId: s.customerId,
      customerName: s.customerName,
      salerId: s.salerId,
      salerName: s.salerName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      // 交易信息补带（2026-10-13）：同销售订单→发票链路，价格等级/付款条件/表头折扣率缺带修复
      priceLevelId: s.priceLevelId,
      paymentTermId: s.paymentTermId,
      discountRate: s.discountRate || 0,
      sourceId: s.returnId,
      invoiceDate: new Date().toISOString().slice(0, 10),
      invoiceType: '增值税专用发票',
      remark: `由销售退货单 ${s.returnNo} 下推生成(红字)`,
      sourceBillType: '销售退货单',
      sourceBillNo: s.returnNo,
      // 由销售退货单下推必须是红字发票；后端保存时对标记为空默认 0(蓝字)，
      // 且只有红字发票才跳过源单互斥校验，故此处必须显式置 1（口径与采购退货下推一致）
      isRedInvoice: 1,
      // 附件携带
      attachmentUrls: s.attachmentUrls || ''
    }),
    // 【下推前拦截】退货审核时系统已自动生成红字发票并回写"已开票数量"；
    // 全部明细行剩余可开票数量 ≤ 0 时禁止下推并提示，避免跳到空白发票页再被后端校验拦截
    fullyInvoicedTip: '该销售退货单已全部开票（退货审核时系统已自动生成红字发票），无需重复下推开票',
    /** 剩余可开票数量 = 退货数量 - 已开票数量（未返回已开票数量字段时 = 全量，与旧行为一致） */
    remainingQtyOf: (it) => Math.max(0, (it.quantity || 0) - (it.turnedInvoiceQuantity || 0)),
    itemMapper: (it, srcDoc) => {
      // 只带"剩余可开票数量"（退货审核自动生成红字发票后，已开票数量已占满 → 剩余 0 的行不带入）
      const remain = Math.max(0, (it.quantity || 0) - (it.turnedInvoiceQuantity || 0))
      if (remain <= 0) return null
      return {
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      // 物料主图携带
      materialMainImage: it.materialMainImage || '',
      specification: it.specification,
      unitId: it.unitId,
      unit: it.unit,
      quantity: remain,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      // 勾稽字段
      sourceId: srcDoc.returnId,
      sourceItemId: it.returnItemId,
      sourceBillType: '销售退货单',
      sourceBillNo: srcDoc.returnNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.returnId,
      sourceType: 'sell'
      }
    }
  },

  // ===== 采购申请单 → 采购合同 =====
  {
    sourceType: 'PurchaseRequest',
    targetType: 'PurchaseContract',
    sourceStatusRequired: APPROVED,
    permCode: 'purchase:request:push',
    fieldMapper: (s) => ({
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      contactPerson: s.contactPerson || '',
      contactPhone: s.contactPhone || '',
      buyerId: s.buyerId,
      buyerName: s.buyerName || '',
      currencyId: s.currencyId,
      currencyCode: s.currencyCode || '',
      exchangeRate: s.exchangeRate || 1.0,
      paymentMethod: s.paymentMethod || '',
      warehouseId: s.warehouseId,
      receiveAddress: s.receiveAddress || '',
      deliveryDate: s.expectedDeliveryDate || s.deliveryDate,
      // 交易字段补带（2026-10-13 对账）：付款条件/部门此前缺带
      paymentTermId: s.paymentTermId,
      departmentId: s.departmentId,
      remark: `由采购申请单 ${s.requestNo} 下推生成`,
      sourceBillType: '采购申请单',
      sourceBillNo: s.requestNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      materialMainImage: it.materialMainImage || '',
      specification: it.model || it.specification || '',
      unit: it.unit || '',
      quantity: it.approvedQty ?? it.applyQty ?? 0,
      unitPrice: it.unitPrice || 0,
      taxRate: it.taxRate || 13,
      discountRate: it.discountRate || 0,
      deliveryDate: it.demandDate || null,
      remark: it.rowRemark || '',
      // 勾稽字段
      sourceBillType: '采购申请单',
      sourceBillNo: srcDoc.requestNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.requestId,
      sourceType: 'buy'
    })
  },
  // ===== 采购合同 → 采购订单 =====
  {
    sourceType: 'PurchaseContract',
    targetType: 'PurchaseOrder',
    sourceStatusRequired: APPROVED,
    permCode: 'purchase:contract:push',
    fieldMapper: (s) => ({
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      contactPerson: s.contactPerson,
      contactPhone: s.contactPhone,
      buyer: s.buyerName || '',
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      paymentMethod: s.paymentMethod,
      deliveryDate: s.deliveryDate,
      receiveAddress: s.receiveAddress,
      // 交易字段补带（2026-10-13 对账）：付款条件/折扣率/部门此前缺带；
      // 原映射里的 warehouseId 已删：采购订单页面没有仓库字段（入库时才指定），带了也存不下
      paymentTermId: s.paymentTermId,
      discountRate: s.discountRate || 0,
      departmentId: s.departmentId,
      sourceId: s.contractId,
      remark: `由采购合同 ${s.contractNo} 下推生成`,
      sourceBillType: '采购合同',
      sourceBillNo: s.contractNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      materialMainImage: it.materialMainImage || '',
      specification: it.specification || it.model || '',
      unit: it.unit || '',
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      // 勾稽字段
      sourceId: srcDoc.contractId,
      sourceItemId: it.contractItemId,
      sourceBillType: '采购合同',
      sourceBillNo: srcDoc.contractNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.contractId,
      sourceType: 'buy'
    })
  },

  // ===== 采购模块完整链路 =====
  // 采购申请单 → 采购订单
  {
    sourceType: 'PurchaseRequest',
    targetType: 'PurchaseOrder',
    sourceStatusRequired: APPROVED,
    permCode: 'purchase:request:push',
    fieldMapper: (s) => ({
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      buyer: s.buyerName || '',
      currencyId: s.currencyId,
      currencyCode: s.currencyCode || '',
      exchangeRate: s.exchangeRate || 1.0,
      deliveryDate: s.expectedDeliveryDate || s.deliveryDate,
      // 交易字段补带（2026-10-13 对账）：付款条件/结算方式/部门此前缺带；
      // 原映射里的 warehouseId 已删：采购订单页面没有仓库字段，带了也存不下
      paymentTermId: s.paymentTermId,
      settleModeId: s.settleModeId,
      departmentId: s.departmentId,
      remark: `由采购申请单 ${s.requestNo} 下推生成`,
      sourceBillType: '采购申请单',
      sourceBillNo: s.requestNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      materialMainImage: it.materialMainImage || '',
      specification: it.model || it.specification || '',
      unit: it.unit || '',
      quantity: it.approvedQty ?? it.applyQty ?? 0,
      unitPrice: it.unitPrice || 0,
      taxRate: it.taxRate || 13,
      discountRate: it.discountRate || 0,
      deliveryDate: it.demandDate || null,
      remark: it.rowRemark || '',
      sourceId: srcDoc.requestId,
      sourceItemId: it.requestItemId,
      sourceBillType: '采购申请单',
      sourceBillNo: srcDoc.requestNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.requestId,
      sourceType: 'buy'
    })
  },
  // 采购订单 → 采购入库单
  {
    sourceType: 'PurchaseOrder',
    targetType: 'PurchaseInbound',
    sourceStatusRequired: APPROVED,
    permCode: 'purchase:order:push',
    fieldMapper: (s) => ({
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      warehouseId: s.warehouseId,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      orderId: s.orderId,
      orderNo: s.orderNo,
      inboundDate: new Date().toISOString().slice(0, 10),
      // 采购员/部门补带（2026-10-13 对账）：入库单需要知道是谁采的、哪个部门（字段名不同：订单 departmentId → 入库 deptId）
      buyerId: s.buyerId,
      buyerName: s.buyerName || '',
      deptId: s.departmentId,
      deptName: s.deptName || '',
      remark: `由采购订单 ${s.orderNo} 下推生成`,
      sourceBillType: '采购订单',
      sourceBillNo: s.orderNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      materialMainImage: it.materialMainImage || '',
      specification: it.specification || it.model || '',
      unit: it.unit || '',
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      sourceId: srcDoc.orderId,
      sourceItemId: it.orderItemId,
      sourceBillType: '采购订单',
      sourceBillNo: srcDoc.orderNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.orderId,
      sourceType: 'buy'
    })
  },
  // 采购订单 → 采购发票
  {
    sourceType: 'PurchaseOrder',
    targetType: 'PurchaseInvoice',
    sourceStatusRequired: APPROVED,
    permCode: 'purchase:order:push',
    fieldMapper: (s) => ({
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      sourceId: s.orderId,
      invoiceDate: new Date().toISOString().slice(0, 10),
      invoiceType: '增值税专用发票',
      // 采购员/折扣率补带（2026-10-13 对账）：发票需要采购员追溯
      buyerName: s.buyerName || '',
      discountRate: s.discountRate || 0,
      remark: `由采购订单 ${s.orderNo} 下推生成`,
      sourceBillType: '采购订单',
      sourceBillNo: s.orderNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      materialMainImage: it.materialMainImage || '',
      specification: it.specification || it.model || '',
      unit: it.unit || '',
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      sourceId: srcDoc.orderId,
      sourceItemId: it.orderItemId,
      sourceBillType: '采购订单',
      sourceBillNo: srcDoc.orderNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.orderId,
      sourceType: 'buy'
    })
  },
  // 采购入库单 → 采购退货单
  {
    sourceType: 'PurchaseInbound',
    targetType: 'PurchaseReturn',
    sourceStatusRequired: APPROVED,
    permCode: 'purchase:inbound:push',
    fieldMapper: (s) => ({
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      warehouseId: s.warehouseId,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      inboundNo: s.receiptNo,
      sourceId: s.receiptId,
      returnDate: new Date().toISOString().slice(0, 10),
      // 采购员/部门补带（2026-10-13 对账）：入库与退货字段同名（deptId/deptName）可直接携带
      buyerId: s.buyerId,
      buyerName: s.buyerName || '',
      deptId: s.deptId,
      deptName: s.deptName || '',
      remark: `由采购入库单 ${s.inboundNo} 下推生成`,
      sourceBillType: '采购入库单',
      sourceBillNo: s.inboundNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      materialMainImage: it.materialMainImage || '',
      specification: it.specification || it.model || '',
      unit: it.unit || '',
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      sourceId: srcDoc.receiptId,
      sourceItemId: it.receiptItemId,
      sourceBillType: '采购入库单',
      sourceBillNo: srcDoc.receiptNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.receiptId,
      sourceType: 'buy'
    })
  },
  // 采购入库单 → 采购发票
  {
    sourceType: 'PurchaseInbound',
    targetType: 'PurchaseInvoice',
    sourceStatusRequired: APPROVED,
    permCode: 'purchase:inbound:push',
    fieldMapper: (s) => ({
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      inboundNo: s.receiptNo,
      sourceId: s.receiptId,
      invoiceDate: new Date().toISOString().slice(0, 10),
      invoiceType: '增值税专用发票',
      // 采购员补带（2026-10-13 对账）：发票需要采购员追溯
      buyerName: s.buyerName || '',
      remark: `由采购入库单 ${s.receiptNo} 下推生成`,
      sourceBillType: '采购入库单',
      sourceBillNo: s.receiptNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      materialMainImage: it.materialMainImage || '',
      specification: it.specification || it.model || '',
      unit: it.unit || '',
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      sourceId: srcDoc.receiptId,
      sourceItemId: it.receiptItemId,
      sourceBillType: '采购入库单',
      sourceBillNo: srcDoc.receiptNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.receiptId,
      sourceType: 'buy'
    })
  },
  // 采购退货单 → 采购发票(红字)
  {
    sourceType: 'PurchaseReturn',
    targetType: 'PurchaseInvoice',
    sourceStatusRequired: APPROVED,
    permCode: 'purchase:return:push',
    fieldMapper: (s) => ({
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      currencyId: s.currencyId,
      currencyCode: s.currencyCode,
      exchangeRate: s.exchangeRate,
      sourceId: s.returnId,
      invoiceDate: new Date().toISOString().slice(0, 10),
      invoiceType: '增值税专用发票',
      // 采购员补带（2026-10-13 对账）：发票需要采购员追溯
      buyerName: s.buyerName || '',
      remark: `由采购退货单 ${s.returnNo} 下推生成(红字)`,
      sourceBillType: '采购退货单',
      sourceBillNo: s.returnNo,
      // 由采购退货单下推必须是红字发票；后端 saveInvoiceWithItems 对 isRedInvoice 为空时默认 0(蓝字)，
      // 且只有 isRedInvoice=1 才校验"源单类型必须为采购退货单"，故此处必须显式置 1
      isRedInvoice: 1,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      materialMainImage: it.materialMainImage || '',
      specification: it.specification || it.model || '',
      unit: it.unit || '',
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      taxRate: it.taxRate,
      discountRate: it.discountRate || 0,
      sourceId: srcDoc.returnId,
      sourceItemId: it.returnItemId,
      sourceBillType: '采购退货单',
      sourceBillNo: srcDoc.returnNo,
      sourceLineNo: it.lineNo,
      sourceDocId: srcDoc.returnId,
      sourceType: 'buy'
    })
  },

  // ===== 生产模块: 来源单 → 生产工单 =====
  // 销售订单 → 生产工单 (选单模式: 支持在工单新建对话框中选销售订单明细行)
  {
    sourceType: 'SaleOrder',
    targetType: 'WorkOrders',
    sourceStatusRequired: APPROVED,
    permCode: 'sale:order:push',
    apiPath: (s, item) => item
      ? `/api/production/workorders/from-sale-order/${s.orderId}/${item.orderItemId}`
      : null,
    itemRequired: true,
    itemLabel: '请选择要下推的销售订单明细行',
    fieldMapper: (s) => ({
      remark: `由销售订单 ${s.orderNo} 下推生成`,
      sourceType: '销售订单',
      sourceNo: s.orderNo,
      salesOrderNo: s.orderNo
    }),
    itemMapper: (it) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      quantity: it.quantity,
      unitId: it.unitId,
      // 【2026-10-19 防重复下推】带出「已下推生产数量」：下游（生产工单编辑页）按
      // 剩余可下推量 = 订单数量 − 已下推生产数量 预填，已推完的行自动跳过
      turnedProduceQuantity: it.turnedProduceQuantity,
      // 来源行主键：工单成品行要落 source_item_id，后端保存守卫据此校验剩余量并回写
      orderItemId: it.orderItemId || it.id
    })
  },
  // 【2026-10-09】销售订单 → MRP计划：MRP 运算页「选择来源物料」用通用选单组件取销售订单及其物料明细
  // （与后端 BillLinkRegistry 已登记的「销售订单 → MRP计划」来源去向关系一致；此前只差前端这条选单登记）
  {
    sourceType: 'SaleOrder',
    targetType: 'MrpPlan',
    sourceStatusRequired: APPROVED,
    permCode: 'mrp:plan:push',
    fieldMapper: (s) => ({
      sourceBillType: '销售订单',
      sourceBillNo: s.orderNo,
      remark: `由销售订单 ${s.orderNo} 进入MRP运算`
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      specification: it.specification,
      quantity: it.quantity,
      unitId: it.unitId,
      orderId: srcDoc.orderId,
      orderNo: srcDoc.orderNo,
      orderItemId: it.orderItemId || it.itemId,
      deliveryDate: it.deliveryDate || it.delivery_date
    })
  },
  // MRP计划 → 生产工单 (按计划明细行下推)
  {
    sourceType: 'MrpPlan',
    targetType: 'WorkOrders',
    sourceStatusRequired: null,
    permCode: 'mrp:plan:push',
    apiPath: (s, item) => item
      ? `/api/production/workorders/from-mrp/${item.id}`
      : null,
    itemRequired: true,
    itemLabel: '请选择要下推的MRP计划明细行(仅生产建议)',
    fieldMapper: (s) => ({
      remark: `由MRP计划 ${s.planNo} 下推生成`,
      sourceType: 'MRP',
      sourceNo: s.planNo
    }),
    itemMapper: (it) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName
    })
  },

  // ===== 生产模块: 生产工单 → 下游单据 =====
  // 生产工单 → 生产领料单
  {
    sourceType: 'WorkOrders',
    targetType: 'MaterialRequisitions',
    sourceStatusRequired: APPROVED,
    permCode: 'production:workorder:push',
    apiPath: (s) => `/api/production/workorders/${s.orderId}/push-material-requisition`,
    fieldMapper: (s) => ({
      billType: 'PICK',
      workOrderNo: s.orderNo,
      warehouseId: s.warehouseId,
      sourceBillType: '生产工单',
      sourceBillNo: s.orderNo,
      remark: `由生产工单 ${s.orderNo} 下推生成`
    }),
    itemMapper: (it, srcDoc) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      specification: it.specification,
      unitId: it.unitId,
      quantity: it.requiredQuantity,
      // 单价取工单用料的单位成本（与后端 pushToMaterialRequisition 同口径）
      unitPrice: it.unitCost,
      stockId: it.stockId,
      binId: it.binId,
      workOrderLineNo: it.lineNo,
      sourceBillType: '生产工单',
      sourceBillNo: srcDoc.orderNo,
      sourceLineNo: it.lineNo
    })
  },
  // 生产工单 → 生产入库单
  {
    sourceType: 'WorkOrders',
    targetType: 'ProductionReceipts',
    sourceStatusRequired: APPROVED,
    permCode: 'production:workorder:push',
    apiPath: (s) => `/api/production/workorders/${s.orderId}/push-production-receipt`,
    fieldMapper: (s) => ({
      warehouseId: s.warehouseId,
      productionOrderId: s.orderId,
      productionOrderNo: s.orderNo,
      workOrderNo: s.orderNo,
      sourceBillType: '生产工单',
      sourceBillNo: s.orderNo,
      remark: `由生产工单 ${s.orderNo} 下推生成`
    }),
    itemMapper: (it) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      specification: it.model,
      unitId: it.unitId,
      // 携带工单成品行号：入库单审核按该行号把合格数量回写 work_order_product.completed_quantity
      workOrderLineNo: it.lineNo
    })
  },
  // ===== 委外模块: 完整链路 =====
  // 委外加工单 → 委外发料单
  {
    sourceType: 'SubcontractOrder',
    targetType: 'SubcontractPick',
    sourceStatusRequired: APPROVED_OR_ISSUED,
    permCode: 'subcontract:order:push',
    fieldMapper: (s) => ({
      orderId: s.id,
      orderNo: s.orderNo,
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      remark: `由委外加工单 ${s.orderNo} 下推生成`,
      sourceBillType: '委外加工单',
      sourceBillNo: s.orderNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it) => ({
      orderItemId: it.id,
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      specification: it.specification || it.model,
      unitId: it.unitId,
      quantity: it.quantity
    })
  },
  // 委外加工单 → 委外入库单
  {
    sourceType: 'SubcontractOrder',
    targetType: 'SubcontractInbound',
    sourceStatusRequired: APPROVED_OR_ISSUED,
    permCode: 'subcontract:order:push',
    fieldMapper: (s) => ({
      orderId: s.id,
      orderNo: s.orderNo,
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      processingFee: s.totalAmount,
      remark: `由委外加工单 ${s.orderNo} 下推生成`,
      sourceBillType: '委外加工单',
      sourceBillNo: s.orderNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it) => ({
      orderItemId: it.id,
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      specification: it.specification || it.model,
      unitId: it.unitId,
      quantity: it.quantity,
      unitPrice: it.unitPrice
    })
  },
  // 委外发料单 → 委外退料单
  {
    sourceType: 'SubcontractPick',
    targetType: 'SubcontractReturn',
    sourceStatusRequired: APPROVED,
    permCode: 'subcontract:pick:push',
    fieldMapper: (s) => ({
      orderId: s.orderId,
      orderNo: s.orderNo,
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      pickId: s.id,
      pickNo: s.pickNo,
      remark: `由委外发料单 ${s.pickNo} 下推生成`,
      sourceBillType: '委外发料单',
      sourceBillNo: s.pickNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      specification: it.specification || it.model,
      unitId: it.unitId,
      quantity: it.quantity
    })
  },
  // 委外发料单 → 委外补料单
  {
    sourceType: 'SubcontractPick',
    targetType: 'SubcontractSupplement',
    sourceStatusRequired: APPROVED,
    permCode: 'subcontract:pick:push',
    fieldMapper: (s) => ({
      orderId: s.orderId,
      orderNo: s.orderNo,
      supplierId: s.supplierId,
      supplierName: s.supplierName,
      pickId: s.id,
      pickNo: s.pickNo,
      remark: `由委外发料单 ${s.pickNo} 下推生成`,
      sourceBillType: '委外发料单',
      sourceBillNo: s.pickNo,
      attachmentUrls: s.attachmentUrls || ''
    }),
    itemMapper: (it) => ({
      materialId: it.materialId,
      materialCode: it.materialCode,
      materialName: it.materialName,
      specification: it.specification || it.model,
      unitId: it.unitId,
      quantity: it.quantity
    })
  }
]

// 根据 sourceType 查询所有可下推的目标类型
export function getPushTargets(sourceType) {
  return PUSH_RELATIONS.filter(r => r.sourceType === sourceType)
}

// 根据 targetType 查询所有可选的源单类型
export function getSourceTypes(targetType) {
  return PUSH_RELATIONS.filter(r => r.targetType === targetType)
}

// 根据源单类型 + 目标单类型 获取下推关系配置
export function getRelation(sourceType, targetType) {
  return PUSH_RELATIONS.find(r => r.sourceType === sourceType && r.targetType === targetType)
}

// 获取单据类型定义
export function getDocType(typeKey) {
  return Object.values(DOC_TYPE).find(t => t.key === typeKey)
}
