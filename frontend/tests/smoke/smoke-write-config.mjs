/**
 * 端到端闭环：每张单据的"页面操作脚本配置"
 *
 * 铁律（立项登记表 R7）：本文件里每一条配置**都必须有源码证据注释**，
 * 禁止凭经验填写。项目没有任何 snake_case↔camelCase 转换层，
 * 字段名写错会表现为"明细为空"，正是"明细不持久化"的重灾区。
 *
 * 未在此登记的"有明细表的单据"一律进入 PENDING，由 03 用例显式报告为「未覆盖」，
 * 绝不静默跳过、也绝不伪造通过。
 */

/**
 * 已登记的单据（字段名与按钮文案均已核对源码）
 * 结构说明：
 *   module        归属模块（决定用哪个冒烟角色登录）
 *   listPath      列表页路由
 *   newPath       新增页路由
 *   detailPath(id) 详情页路由
 *   headerSelects 新增页必填的 el-select 表头字段（按 el-form-item 的 label 文本定位）
 *   headerDates   必填的日期字段（未给默认值时填今天）
 *   detail        明细区操作参数
 *   evidence      源码证据（文件:行号）
 */
export const BILLS = [
  {
    key: 'sale_order',
    name: '销售订单',
    module: 'sale',
    listPath: '/sale/orders',
    newPath: '/sale/orders/new',
    detailPath: (id) => `/sale/orders/${id}`,
    /** 保存成功后 URL 应匹配的形状（用于断言"保存后回写了 id"，抓 D6） */
    detailUrlPattern: '\\/sale\\/orders\\/\\d+',
    /** 表头"单据编号"字段的 label（用于读单号） */
    codeLabel: '订单编号',
    headerSelects: ['客户', '销售员', '币种'],
    headerDates: ['订单日期'],
    detail: {
      addButtonText: '添加明细',
      materialPlaceholder: '选择物料',
      materialHeader: '商品名称',
      qtyHeader: '数量',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/sale/SaleOrderEdit.vue L1025-L1031  必填 rules: orderDate/customerId/salerId/currencyId',
      'src/views/sale/SaleOrderEdit.vue L265          “添加明细”按钮 @click="addItem"',
      'src/views/sale/SaleOrderEdit.vue L278          明细绑定 :data="form.items"',
      'src/views/sale/SaleOrderEdit.vue L287          物料下拉 placeholder="选择物料"',
      'src/views/sale/SaleOrderEdit.vue L761-L763     明细列定义：{ prop: "materialId", label: "商品名称" }',
      'src/views/sale/SaleOrderEdit.vue L352-L356     “数量”列 el-input-number v-model="row.quantity"',
      'src/views/sale/SaleOrderEdit.vue L1244-L1266   addItem() 行字段：materialId/quantity/unitPrice/...'
    ]
  },
  {
    key: 'sale_quote',
    name: '销售报价单',
    module: 'sale',
    listPath: '/sale/quotes',
    newPath: '/sale/quotes/new',
    detailUrlPattern: '\\/sale\\/quotes\\/\\d+',
    headerSelects: ['客户', '销售员', '币种'],
    headerDates: ['报价日期'],
    detail: { addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3] },
    evidence: [
      'src/views/sale/SaleQuoteEdit.vue L828-L833   必填 rules: quoteDate/customerId/salerId/currencyId',
      'src/views/sale/SaleQuoteEdit.vue L68         “报价日期”表单项',
      'src/views/sale/SaleQuoteEdit.vue L250-L252   “添加明细”按钮',
      'src/views/sale/SaleQuoteEdit.vue L263        明细表 :data="form.items"（包在 .table-wrap 内，L256）',
      'src/views/sale/SaleQuoteEdit.vue L646/L649   明细列 label：商品名称 / 商品编号 / 数量',
      'src/views/sale/SaleQuoteEdit.vue L14         单号元素 <span class="order-no">',
      'src/router/index.js                          路由 sale/quotes、sale/quotes/new、sale/quotes/:id'
    ]
  },
  {
    key: 'sale_contract',
    name: '销售合同',
    module: 'sale',
    listPath: '/sale/contracts',
    newPath: '/sale/contracts/new',
    detailUrlPattern: '\\/sale\\/contracts\\/\\d+',
    headerSelects: ['客户', '销售员', '币种'],
    headerDates: ['合同日期'],
    detail: { addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3] },
    evidence: [
      'src/views/sale/SaleContractEdit.vue L908-L913 必填 rules: contractDate/customerId/salerId/currencyId（warehouseId 非必填）',
      'src/views/sale/SaleContractEdit.vue L68       “合同日期”表单项',
      'src/views/sale/SaleContractEdit.vue L329-L331 “添加明细”按钮',
      'src/views/sale/SaleContractEdit.vue L342      明细表 :data="form.items"（.table-wrap 在 L335）',
      'src/views/sale/SaleContractEdit.vue L721/L724 明细列 label：商品名称 / 商品编号 / 数量',
      'src/views/sale/SaleContractEdit.vue L14       单号元素 <span class="order-no">',
      'src/router/index.js                           路由 sale/contracts、sale/contracts/new、sale/contracts/:id'
    ]
  },
  {
    key: 'sale_delivery',
    name: '销售出库单',
    module: 'sale',
    listPath: '/sale/deliveries',
    newPath: '/sale/deliveries/new',
    detailUrlPattern: '\\/sale\\/deliveries\\/\\d+',
    headerSelects: ['客户', '仓库', '销售员', '币种'],
    headerDates: ['出库日期'],
    detail: {
      addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3],
      // 【设置中心-单价校验】"销售出库单第1行单价不能为0" —— 实测拦住保存，必须补填
      fillNumbers: [{ header: '单价', value: 100 }]
    },
    evidence: [
      'src/views/sale/SaleDeliveryEdit.vue L996-L1002 必填 rules: deliveryDate/customerId/warehouseId/salerId/currencyId（仓库也为必填）',
      'src/views/sale/SaleDeliveryEdit.vue L68/L134  “出库日期”“仓库(required)”表单项',
      'src/views/sale/SaleDeliveryEdit.vue L260-L262 “添加明细”按钮',
      'src/views/sale/SaleDeliveryEdit.vue L273      明细表 :data="form.items"（.table-wrap 在 L266）',
      'src/views/sale/SaleDeliveryEdit.vue L705/L709 明细列 label：商品名称 / 商品编号 / 数量',
      'src/views/sale/SaleDeliveryEdit.vue L14       单号元素 <span class="order-no">',
      'src/router/index.js                           路由 sale/deliveries、sale/deliveries/new、sale/deliveries/:id'
    ]
  },
  {
    key: 'sale_return',
    name: '销售退货',
    module: 'sale',
    listPath: '/sale/returns',
    newPath: '/sale/returns/new',
    detailUrlPattern: '\\/sale\\/returns\\/\\d+',
    headerSelects: ['客户', '销售员', '币种'],
    headerDates: ['退货日期'],
    detail: { addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3] },
    evidence: [
      'src/views/sale/SaleReturnEdit.vue L848-L854   必填 rules: returnDate/customerId/salerId/currencyId（warehouseId 非必填）',
      'src/views/sale/SaleReturnEdit.vue L68         “退货日期”表单项',
      'src/views/sale/SaleReturnEdit.vue L249-L251   “添加明细”按钮',
      'src/views/sale/SaleReturnEdit.vue L262        明细表 :data="form.items"（.table-wrap 在 L255）',
      'src/views/sale/SaleReturnEdit.vue L671/L674   明细列 label：商品名称 / 商品编号 / 数量',
      'src/views/sale/SaleReturnEdit.vue L14         单号元素 <span class="order-no">',
      'src/router/index.js                           路由 sale/returns、sale/returns/new、sale/returns/:id'
    ]
  },
  {
    key: 'sale_invoice',
    name: '销售发票',
    module: 'sale',
    listPath: '/sale/invoices',
    newPath: '/sale/invoices/new',
    detailUrlPattern: '\\/sale\\/invoices\\/\\d+',
    headerSelects: ['客户', '发票类型', '销售员', '币种'],
    headerDates: ['开票日期'],
    detail: { addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3] },
    evidence: [
      'src/views/sale/SaleInvoiceEdit.vue L798-L804 必填 rules: invoiceDate/customerId/invoiceType/salerId/currencyId',
      'src/views/sale/SaleInvoiceEdit.vue L67/L70   “开票日期”“发票类型(required)”表单项',
      'src/views/sale/SaleInvoiceEdit.vue L237-L239 “添加明细”按钮',
      'src/views/sale/SaleInvoiceEdit.vue L250      明细表 :data="form.items"（.table-wrap 在 L243）',
      'src/views/sale/SaleInvoiceEdit.vue L632/L635 明细列 label：商品名称 / 商品编号 / 数量',
      'src/views/sale/SaleInvoiceEdit.vue L14       单号元素 <span class="order-no">',
      'src/router/index.js                          路由 sale/invoices、sale/invoices/new、sale/invoices/:id'
    ]
  },

  // ================= 第 2 批：采购模块 6 张 =================
  {
    key: 'purchase_request',
    name: '采购申请单',
    module: 'purchase',
    listPath: '/purchase/requests',
    newPath: '/purchase/requests/new',
    detailUrlPattern: '\\/purchase\\/requests\\/\\d+',
    headerSelects: ['申请部门', '申请人', '申请类型'],
    headerDates: ['申请日期', '需求日期'],
    detail: {
      addButtonText: '添加明细',
      materialHeader: '商品名称',
      // 2026-11-11：本页明细列名是「申请数量」（applyQty），不是「数量」；
      // 用「数量」匹配会命中只读的可用数量列（没有输入框）→ 误报“数量单元格没有输入框”。
      qtyHeader: '申请数量',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/purchase/PurchaseRequestEdit.vue L792-L797 必填 rules: requestDate/departmentId/applicantId/applyType/demandDate（两个日期）',
      'src/views/purchase/PurchaseRequestEdit.vue L69/L72/L75/L97/L102 表单项：申请日期/需求日期/申请类型/申请部门/申请人',
      'src/views/purchase/PurchaseRequestEdit.vue L224-L226 “添加明细”按钮',
      'src/views/purchase/PurchaseRequestEdit.vue L237 明细表 :data="form.items"（.detail-subtable-header 在 L231）',
      'src/views/purchase/PurchaseRequestEdit.vue L241/L265 明细列：materialId / quantity',
      'src/views/purchase/PurchaseRequestEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L154-L156 路由 purchase/requests、purchase/requests/new、purchase/requests/:id'
    ]
  },
  {
    key: 'purchase_contract',
    name: '采购合同',
    module: 'purchase',
    listPath: '/purchase/contracts',
    newPath: '/purchase/contracts/new',
    detailUrlPattern: '\\/purchase\\/contracts\\/\\d+',
    headerSelects: ['供应商', '采购员', '币种'],
    headerDates: ['合同日期'],
    detail: { addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3] },
    evidence: [
      'src/views/purchase/PurchaseContractEdit.vue L813-L818 必填 rules: contractDate/supplierId/buyerId/currencyId（warehouseId 非必填）',
      'src/views/purchase/PurchaseContractEdit.vue L68/L96/L122/L127 表单项：合同日期/供应商/采购员/币种',
      'src/views/purchase/PurchaseContractEdit.vue L262-L264 “添加明细”按钮',
      'src/views/purchase/PurchaseContractEdit.vue L275 明细表 :data="form.items"（.detail-subtable-header 在 L269）',
      'src/views/purchase/PurchaseContractEdit.vue L279/L306 明细列：materialId / quantity',
      'src/views/purchase/PurchaseContractEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L147-L149 路由 purchase/contracts、purchase/contracts/new、purchase/contracts/:id'
    ]
  },
  {
    key: 'purchase_order',
    name: '采购订单',
    module: 'purchase',
    listPath: '/purchase/orders',
    newPath: '/purchase/orders/new',
    detailUrlPattern: '\\/purchase\\/orders\\/\\d+',
    headerSelects: ['供应商', '采购员', '币种'],
    headerDates: ['订单日期'],
    detail: { addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3] },
    evidence: [
      'src/views/purchase/PurchaseOrderEdit.vue L918-L923 必填 rules: orderDate/supplierId/buyerName/currencyId',
      'src/views/purchase/PurchaseOrderEdit.vue L68/L83/L109/L114 表单项：订单日期/供应商/采购员/币种',
      'src/views/purchase/PurchaseOrderEdit.vue L238-L240 “添加明细”按钮',
      'src/views/purchase/PurchaseOrderEdit.vue L251 明细表 :data="form.items"（.detail-subtable-header 在 L245）',
      'src/views/purchase/PurchaseOrderEdit.vue L255/L289 明细列：materialId / quantity',
      'src/views/purchase/PurchaseOrderEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L150-L152 路由 purchase/orders、purchase/orders/new、purchase/orders/:id'
    ]
  },
  {
    key: 'purchase_receipt',
    name: '采购入库单',
    module: 'purchase',
    listPath: '/purchase/inbounds',
    newPath: '/purchase/inbounds/new',
    detailUrlPattern: '\\/purchase\\/inbounds\\/\\d+',
    headerSelects: ['供应商', '仓库', '采购员', '币种'],
    headerDates: ['入库日期'],
    detail: {
      addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3],
      // 入库单进库存成本，销售出库单已有【单价校验】先例，这里一并补填以防同类校验拦保存
      fillNumbers: [{ header: '单价', value: 100 }]
    },
    evidence: [
      'src/views/purchase/PurchaseInboundEdit.vue L860-L865 必填 rules: inDate/supplierId/stockId/buyerId/currencyId（仓库必填）',
      'src/views/purchase/PurchaseInboundEdit.vue L67/L85/L114/L124/L132 表单项：入库日期/供应商/采购员/币种/仓库',
      'src/views/purchase/PurchaseInboundEdit.vue L242-L244 “添加明细”按钮',
      'src/views/purchase/PurchaseInboundEdit.vue L255 明细表 :data="form.items"（.detail-subtable-header 在 L249）',
      'src/views/purchase/PurchaseInboundEdit.vue L259/L286 明细列：materialId / quantity',
      'src/views/purchase/PurchaseInboundEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L158-L160 路由 purchase/inbounds、purchase/inbounds/new、purchase/inbounds/:id'
    ]
  },
  {
    key: 'purchase_return',
    name: '采购退货单',
    module: 'purchase',
    listPath: '/purchase/returns',
    newPath: '/purchase/returns/new',
    detailUrlPattern: '\\/purchase\\/returns\\/\\d+',
    headerSelects: ['供应商', '仓库', '退货类型', '采购员', '币种'],
    headerDates: ['退货日期'],
    // returnReason 是必填"文本框"，只填下拉/日期会被必填校验拦住保存
    headerTexts: [{ label: '退货原因', value: 'E2E冒烟-质量问题退货' }],
    detail: {
      addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3],
      fillNumbers: [{ header: '单价', value: 100 }]
    },
    evidence: [
      'src/views/purchase/PurchaseReturnEdit.vue L860-L867 必填 rules: returnDate/supplierId/stockId/returnType/buyerId/currencyId/returnReason',
      'src/views/purchase/PurchaseReturnEdit.vue L68/L71/L92/L118/L128/L133 表单项：退货日期/退货类型/供应商/采购员/仓库/币种',
      'src/views/purchase/PurchaseReturnEdit.vue L246-L248 “添加明细”按钮',
      'src/views/purchase/PurchaseReturnEdit.vue L259 明细表 :data="form.items"（.detail-subtable-header 在 L253）',
      'src/views/purchase/PurchaseReturnEdit.vue L263/L290 明细列：materialId / quantity',
      'src/views/purchase/PurchaseReturnEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L162-L164 路由 purchase/returns、purchase/returns/new、purchase/returns/:id'
    ]
  },
  {
    key: 'purchase_invoice',
    name: '采购发票',
    module: 'purchase',
    listPath: '/purchase/invoices',
    newPath: '/purchase/invoices/new',
    detailUrlPattern: '\\/purchase\\/invoices\\/\\d+',
    headerSelects: ['发票类型', '供应商', '币种'],
    headerDates: ['发票日期'],
    // 实测（2026-09-14）：发票类型选「专票」时，源码 PurchaseInvoiceEdit.vue L837-L848 的
    // supplierTaxNo validator 会强制「专票必须填写供应商税号」，不填则保存被拦（不是缺陷）。
    headerTexts: [{ label: '供应商税号', value: '91440101MA5ABCDE1X' }],
    detail: { addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3] },
    evidence: [
      'src/views/purchase/PurchaseInvoiceEdit.vue L832-L837 必填 rules: invoiceType/invoiceDate/supplierId/currencyId（supplierTaxNo 仅 validator，是否必填待实测确认）',
      'src/views/purchase/PurchaseInvoiceEdit.vue L68/L74/L99/L128 表单项：发票类型/发票日期/供应商/币种',
      'src/views/purchase/PurchaseInvoiceEdit.vue L238-L240 “添加明细”按钮',
      'src/views/purchase/PurchaseInvoiceEdit.vue L251 明细表 :data="form.items"（.detail-subtable-header 在 L245）',
      'src/views/purchase/PurchaseInvoiceEdit.vue L255/L282 明细列：materialId / quantity',
      'src/views/purchase/PurchaseInvoiceEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L166-L168 路由 purchase/invoices、purchase/invoices/new、purchase/invoices/:id'
    ]
  },

  // ================= 第 3 批：库存模块 5 张 =================
  // 注意：库存模块路由结构与销售/采购不同 —— 是 /edit（新增）与 /edit/:id（编辑），没有 /new。
  {
    key: 'other_receipt',
    name: '其他入库单',
    module: 'inventory',
    listPath: '/inventory/other-receipts',
    newPath: '/inventory/other-receipts/edit',
    detailUrlPattern: '\\/inventory\\/other-receipts\\/edit\\/\\d+',
    headerSelects: ['入库类型', '入库仓库', '币种'],
    headerDates: ['入库日期'],
    detail: {
      addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3],
      fillNumbers: [{ header: '单价', value: 100 }]
    },
    evidence: [
      'src/views/inventory/OtherReceiptEdit.vue L722-L727 必填 rules: receiptDate/receiptType/warehouseId/currencyId',
      'src/views/inventory/OtherReceiptEdit.vue L94/L97/L119/L124 表单项：入库日期/入库类型/入库仓库(注意标签是「入库仓库」不是「仓库」)/币种',
      'src/views/inventory/OtherReceiptEdit.vue L241-L243 “添加明细”按钮',
      'src/views/inventory/OtherReceiptEdit.vue L254 明细表 :data="form.items"（.detail-subtable-header 在 L248）',
      'src/views/inventory/OtherReceiptEdit.vue L258/L285 明细列：materialId / quantity',
      'src/views/inventory/OtherReceiptEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L193-L194 路由 inventory/other-receipts、.../edit、.../edit/:id'
    ]
  },
  {
    key: 'other_issue',
    name: '其他出库单',
    module: 'inventory',
    listPath: '/inventory/other-issues',
    newPath: '/inventory/other-issues/edit',
    detailUrlPattern: '\\/inventory\\/other-issues\\/edit\\/\\d+',
    headerSelects: ['出库类型', '出库仓库', '币种'],
    headerDates: ['出库日期'],
    detail: {
      addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3],
      // 与销售出库单同理：出库类单据可能有【单价校验】，先补填以防拦保存
      fillNumbers: [{ header: '单价', value: 100 }]
    },
    evidence: [
      'src/views/inventory/OtherIssueEdit.vue L790-L795 必填 rules: issueDate/issueType/warehouseId/currencyId',
      'src/views/inventory/OtherIssueEdit.vue L67/L70/L97/L102 表单项：出库日期/出库类型/出库仓库(标签是「出库仓库」)/币种',
      'src/views/inventory/OtherIssueEdit.vue L228-L230 “添加明细”按钮',
      'src/views/inventory/OtherIssueEdit.vue L241 明细表 :data="form.items"（.detail-subtable-header 在 L235）',
      'src/views/inventory/OtherIssueEdit.vue 明细列为硬编码：L255 「商品名称」、L282 「数量」（不用 col.prop 模板）',
      'src/views/inventory/OtherIssueEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L185-L186 路由 inventory/other-issues、.../edit、.../edit/:id'
    ]
  },
  {
    key: 'stock_transfer',
    name: '调拨单',
    module: 'inventory',
    listPath: '/inventory/stock-transfers',
    newPath: '/inventory/stock-transfers/edit',
    detailUrlPattern: '\\/inventory\\/stock-transfers\\/edit\\/\\d+',
    headerSelects: ['调拨类型', '调出仓库', '调入仓库', '币种'],
    // toStockId 带 validator（要求调入≠调出），两个都取第一项必然校验失败 → 调入仓库取第二项
    pickSecondOptions: ['调入仓库'],
    headerDates: ['调拨日期'],
    detail: { addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3] },
    evidence: [
      'src/views/inventory/StockTransferEdit.vue L789-L796 必填 rules: transferDate/transferType/fromStockId/toStockId(含 validator)',
      'src/views/inventory/StockTransferEdit.vue L67/L70/L90/L95/L100 表单项：调拨日期/调拨类型/调出仓库/调入仓库/币种',
      'src/views/inventory/StockTransferEdit.vue L227-L229 “添加明细”按钮',
      'src/views/inventory/StockTransferEdit.vue L240 明细表 :data="form.items"（.detail-subtable-header 在 L234）',
      'src/views/inventory/StockTransferEdit.vue 明细列为硬编码：L254 「商品名称」、L281 「数量」',
      'src/views/inventory/StockTransferEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L188-L189 路由 inventory/stock-transfers、.../edit、.../edit/:id'
    ]
  },
  {
    key: 'stock_check',
    name: '盘点单',
    module: 'inventory',
    listPath: '/inventory/stock-checks',
    newPath: '/inventory/stock-checks/edit',
    detailUrlPattern: '\\/inventory\\/stock-checks\\/edit\\/\\d+',
    headerSelects: ['盘点类型', '仓库', '币种'],
    headerDates: ['盘点日期'],
    // 盘点单没有「数量」列，可编辑的实盘列是 checkQuantity，列标签实证为「盘点数量」（源码 L614）；
    // 2026-11-11：该页进入时自带若干空行，故声明 allowPreRows，只填本次新增的行。
    detail: { addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '盘点数量', qtyValues: [2, 3], allowPreRows: true },
    evidence: [
      'src/views/inventory/StockCheckEdit.vue L727-L732 必填 rules: checkDate/stockType/warehouseId/currencyId',
      'src/views/inventory/StockCheckEdit.vue L66/L69/L90/L95 表单项：盘点日期/盘点类型/仓库/币种',
      'src/views/inventory/StockCheckEdit.vue L220-L222 “添加明细”按钮',
      'src/views/inventory/StockCheckEdit.vue L233 明细表 :data="form.items"（.detail-subtable-header 在 L227）',
      'src/views/inventory/StockCheckEdit.vue L613-L614 明细列配置：bookQuantity=账面数量、checkQuantity=盘点数量',
      'src/views/inventory/StockCheckEdit.vue L288-L290 盘点数量列用 el-input-number，:disabled="readonly"',
      'src/views/inventory/StockCheckEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L191-L192 路由 inventory/stock-checks、.../edit、.../edit/:id'
    ]
  },
  {
    key: 'material_requisition',
    name: '领料单',
    module: 'inventory',
    listPath: '/inventory/material-requisitions',
    newPath: '/inventory/material-requisitions/edit',
    detailUrlPattern: '\\/inventory\\/material-requisitions\\/edit\\/\\d+',
    headerSelects: ['仓库', '币种'],
    headerDates: ['领料日期'],
    detail: { addButtonText: '添加明细', materialHeader: '商品名称', qtyHeader: '数量', qtyValues: [2, 3] },
    evidence: [
      'src/views/inventory/MaterialRequisitionEdit.vue L737-L741 必填 rules: requisitionDate/warehouseId/currencyId',
      'src/views/inventory/MaterialRequisitionEdit.vue L66/L86/L96 表单项：领料日期/仓库/币种',
      'src/views/inventory/MaterialRequisitionEdit.vue L213-L215 “添加明细”按钮（:disabled="readonly"）',
      'src/views/inventory/MaterialRequisitionEdit.vue L226 明细表 :data="form.items"（.detail-subtable-header 在 L220）',
      'src/views/inventory/MaterialRequisitionEdit.vue L230/L264 明细列：materialId / quantity',
      'src/views/inventory/MaterialRequisitionEdit.vue L14 单号元素 <span class="order-no">',
      'src/router/index.js L196-L197 路由 inventory/material-requisitions、.../edit、.../edit/:id'
    ]
  },

  // ================= 第二批（2026-09-20）：逐页核对后并入的 17 张 =================
  // 核对方式：逐个 Edit.vue 读 rules / 明细列 / addItem()，并与后端 Item 实体逐字段比对。
  // 特别注意 detail.tableHint —— 双明细表页面靠它锁定"要操作的那张表"（见 03 用例 normalizeDetailAnchor）。

  // ---------- 生产模块 ----------
  {
    key: 'production_receipt',
    name: '生产入库单',
    module: 'production',
    listPath: '/production/receipts',
    newPath: '/production/receipts/edit',
    detailUrlPattern: '\\/production\\/receipts\\/edit\\/\\d+',
    codeLabel: '入库单号',
    headerSelects: ['仓库', '币别'],
    headerDates: ['入库日期'],
    detail: {
      addButtonText: '添加明细',
      materialHeader: '商品名称',
      qtyHeader: '数量',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/production/ProductionReceiptEdit.vue L474-L477 必填 rules: receiptDate/warehouseId/currencyId',
      'src/views/production/ProductionReceiptEdit.vue L172-L174 「添加明细」按钮 @click="addItem"',
      'src/views/production/ProductionReceiptEdit.vue L154-L167 BillItemsTable :items="form.items"（锚点齐全）',
      'src/views/production/ProductionReceiptEdit.vue L560-L582 addItem() 行字段：materialId/quantity/...',
      'backend production/entity/ProductionReceiptItem.java L26-L27/L41-L42 materialId/quantity（camelCase 一致）',
      'src/router/index.js L215/L220/L221 路由 production/receipts、.../edit、.../edit/:id'
    ]
  },
  // 2026-11-11：工序转移单已下线（页面重定向到生产工单），从闭环登记移入 PENDING_BILLS。
  {
    key: 'production_order',
    name: '生产工单',
    module: 'production',
    listPath: '/production/workorders',
    newPath: '/production/workorders/new',
    detailUrlPattern: '\\/production\\/workorders\\/\\d+',
    codeLabel: '工单编号',
    // 2026-11-11 修正：本页表头没有「仓库」控件——仓库只出现在**原料明细**的列里（cell-warehouseName），
    // 而成品明细单独存在时原料表是空的，强行找列必然误报。
    // 该页后端保存时对空仓库有兜底（WorkOrderServiceImpl 取启用仓库中 stock_id 最小者），
    // 故闭环用例不填仓库，交给系统默认。
    headerSelects: [],
    headerDates: ['工单日期'],
    // ⚠ 双明细表：成品明细（products）+ 原料明细（materials）。本配置针对**成品明细**，
    //   tableHint 用"成品编码"帮助 normalizeDetailAnchor 锁定正确的表。
    detail: {
      addButtonText: '添加成品',
      materialHeader: '成品编码',
      qtyHeader: '计划数量',
      tableHint: '成品编码',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/production/WorkOrderEdit.vue L960-L963 必填 rules: orderDate/warehouseId',
      'src/views/production/WorkOrderEdit.vue L449-L451 「添加成品」按钮 @click="addProductRow"',
      'src/views/production/WorkOrderEdit.vue L453 成品明细表 :data="productList"（= workOrder.products）',
      'src/views/production/WorkOrderEdit.vue L469/L471 列「成品编码」内 el-select v-model="row.materialId"',
      'src/views/production/WorkOrderEdit.vue L498-L503 列「计划数量」v-model="row.quantity"',
      'src/views/production/WorkOrderEdit.vue L1273-L1294 addProductRow() 行字段：materialId/quantity/...',
      '⚠ WorkOrderEdit.vue 全文无 .detail-subtable-header（靠 normalizeDetailAnchor 的 ③ 手写明细分支兜底）',
      'src/router/index.js L211/L212/L213 路由 production/workorders、.../new、.../:id'
    ]
  },
  {
    key: 'produce_assemble',
    name: '组装单',
    module: 'production',
    listPath: '/production/assemble',
    newPath: '/production/assemble/new',
    detailUrlPattern: '\\/production\\/assemble\\/\\d+',
    codeLabel: '单据编号',
    headerSelects: ['成品仓库', '原料仓库'],
    headerDates: ['单据日期'],
    // ⚠ 双明细表（成品明细 produce_assemble_detail + 原料明细 produce_assemble_bom），本配置针对成品明细。
    // ⚠ 该页前后端字段为 snake_case（实体带 @JsonNaming(SnakeCaseStrategy)），前端直接读写 product_id/quantity。
    detail: {
      addButtonText: '添加成品',
      materialHeader: '物料编码',
      qtyHeader: '数量',
      tableHint: '物料编码',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/production/ProduceAssembleEdit.vue L622-L624 必填 rules: wh_id / wh_id_bom',
      'src/views/production/ProduceAssembleEdit.vue L242-L244 「添加成品」按钮 @click="addProductRow"',
      'src/views/production/ProduceAssembleEdit.vue L248 表 :data="form.produce_assemble_detail"',
      'src/views/production/ProduceAssembleEdit.vue L250/L252 列「物料编码」el-select v-model="row.product_id"',
      'src/views/production/ProduceAssembleEdit.vue L264-L266 列「数量」v-model="row.quantity"',
      'backend produce/assemble/entity/ProduceAssemble.java L17 @JsonNaming(SnakeCase) + L156-L162 produceAssembleDetail/produceAssembleBom',
      'backend produce/assemble/entity/ProduceAssembleDetail.java L27-L28 product_id / L51-L52 quantity',
      'src/router/index.js L239/L240/L241 路由 production/assemble、.../new、.../:id'
    ]
  },
  {
    key: 'produce_disassemble',
    name: '拆卸单',
    module: 'production',
    listPath: '/production/disassemble',
    newPath: '/production/disassemble/new',
    detailUrlPattern: '\\/production\\/disassemble\\/\\d+',
    codeLabel: '单据编号',
    headerSelects: ['成品仓库', '原料仓库'],
    headerDates: ['单据日期'],
    detail: {
      addButtonText: '添加成品',
      materialHeader: '物料编码',
      qtyHeader: '数量',
      tableHint: '物料编码',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/production/ProduceDisassembleEdit.vue L611-L613 必填 rules: wh_id / wh_id_bom',
      'src/views/production/ProduceDisassembleEdit.vue L242-L244 「添加成品」按钮 @click="addProductRow"',
      'src/views/production/ProduceDisassembleEdit.vue L248 表 :data="form.produce_disassemble_detail"',
      'src/views/production/ProduceDisassembleEdit.vue L250/L252 列「物料编码」v-model="row.product_id"；L264-L266 列「数量」',
      'backend produce/assemble/entity/ProduceDisassemble.java L17 SnakeCase + L156-L162',
      'src/router/index.js L243/L244/L245 路由 production/disassemble、.../new、.../:id'
    ]
  },

  // ---------- 委外模块 ----------
  {
    key: 'subcontract',
    name: '委外加工单',
    module: 'subcontract',
    listPath: '/subcontract/orders',
    newPath: '/subcontract/orders/new',
    detailUrlPattern: '\\/subcontract\\/orders\\/\\d+',
    codeLabel: '委外单号',
    headerSelects: ['物料', '委外供应商'],
    headerDates: ['委外日期', '计划开工日期', '计划完工日期'],
    // ⚠ 明细数组是 form.lines（不是 form.items），数量列 UI 名是「应发数量」。
    // ⚠ 该页是手写明细（无 .detail-subtable-header），靠 normalizeDetailAnchor 的 ③ 分支兜底。
    // 2026-11-11：该页进入时自带空行，声明 allowPreRows，只填本次新增的行。
    detail: {
      addButtonText: '添加明细',
      materialHeader: '商品名称',
      qtyHeader: '应发数量',
      qtyValues: [2, 3],
      allowPreRows: true
    },
    evidence: [
      'src/views/subcontract/SubcontractOrderEdit.vue L479-L487 必填 rules: orderDate/materialId/supplierId/planStartDate/planFinishDate/qty/unitPrice',
      'src/views/subcontract/SubcontractOrderEdit.vue L167-L169 「添加明细」按钮 @click="addLine"',
      'src/views/subcontract/SubcontractOrderEdit.vue L173-L174 明细表 :data="form.lines"（手写 .table-wrap，无子表头锚点）',
      'src/views/subcontract/SubcontractOrderEdit.vue L176 列「商品名称」；L210 列「应发数量」v-model="row.baseQty"',
      'src/views/subcontract/SubcontractOrderEdit.vue L596-L605 onSave 手工映射：quantity←baseQty、finishedQty←actualQty（存在一层手写映射）',
      'backend subcontract/order/entity/SubcontractOrder.java L132-L133 items；SubcontractOrderItem.java L34-L38 quantity/finished_qty',
      'src/router/index.js L252-L254 路由 subcontract/orders、.../new、.../:id'
    ]
  },
  {
    key: 'subcontract_pick',
    name: '委外发料单',
    module: 'subcontract',
    listPath: '/subcontract/picks',
    newPath: '/subcontract/picks/new',
    detailUrlPattern: '\\/subcontract\\/picks\\/\\d+',
    codeLabel: '领料单号',
    headerSelects: ['委外加工单'],
    headerDates: ['领料日期'],
    detail: {
      addButtonText: '添加明细',
      materialHeader: '商品名称',
      qtyHeader: '数量',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/subcontract/SubcontractPickEdit.vue L344-L347 必填 rules: pickDate / orderId',
      'src/views/subcontract/SubcontractPickEdit.vue L132-L134 「添加明细」按钮 @click="addLine"',
      'src/views/subcontract/SubcontractPickEdit.vue L116-L144 明细走 BillItemsTable（锚点齐全）',
      'src/views/subcontract/SubcontractPickEdit.vue L254-L263 detailColumns：materialId「商品名称」、quantity「数量」',
      '⚠ 明细行前端上送 unitName/unitId，后端 SubcontractPickItem 只有 unit（单位不落库，不影响 materialId/quantity）',
      '⚠ 表头「委外加工单」下拉只列 status≥3（已下达及以后）的委外单（L723-L725）→ 前置数据依赖',
      'src/router/index.js L255-L257 路由'
    ]
  },
  {
    key: 'subcontract_inbound',
    name: '委外入库单',
    module: 'subcontract',
    listPath: '/subcontract/inbounds',
    newPath: '/subcontract/inbounds/new',
    detailUrlPattern: '\\/subcontract\\/inbounds\\/\\d+',
    codeLabel: '入库单号',
    headerSelects: ['委外加工单'],
    headerDates: ['入库日期'],
    detail: {
      addButtonText: '添加明细',
      materialHeader: '商品名称',
      qtyHeader: '数量',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/subcontract/SubcontractInboundEdit.vue L345-L348 必填 rules: inboundDate / orderId',
      'src/views/subcontract/SubcontractInboundEdit.vue L131-L133 「添加明细」按钮 @click="addLine"',
      'src/views/subcontract/SubcontractInboundEdit.vue L251-L261 detailColumns：materialId「商品名称」、quantity「数量」、processingFee「加工费」',
      'backend subcontract/inbound/entity/SubcontractInboundItem.java L34 unit / L37 quantity / L47 processing_fee',
      '⚠ 表头「委外加工单」下拉只列 status≥3 的委外单（L724-L726）→ 前置数据依赖',
      'src/router/index.js L258-L260 路由'
    ]
  },
  {
    key: 'subcontract_return',
    name: '委外退料单',
    module: 'subcontract',
    listPath: '/subcontract/returns',
    newPath: '/subcontract/returns/new',
    detailUrlPattern: '\\/subcontract\\/returns\\/\\d+',
    codeLabel: '退料单号',
    headerSelects: ['委外加工单'],
    headerDates: ['退料日期'],
    detail: {
      addButtonText: '添加明细',
      materialHeader: '商品名称',
      qtyHeader: '数量',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/subcontract/SubcontractReturnEdit.vue L343-L346 必填 rules: returnDate / orderId',
      'src/views/subcontract/SubcontractReturnEdit.vue L131-L133 「添加明细」按钮 @click="addLine"',
      'src/views/subcontract/SubcontractReturnEdit.vue L251-L260 detailColumns：materialId「商品名称」、quantity「数量」',
      'src/views/subcontract/SubcontractReturnEdit.vue L452 上送 unit（与后端一致）',
      '⚠ 表头「委外加工单」下拉只列 status≥3 的委外单（L744-L746）→ 前置数据依赖',
      'src/router/index.js L264-L266 路由'
    ]
  },
  {
    key: 'subcontract_supplement',
    name: '委外补料单',
    module: 'subcontract',
    listPath: '/subcontract/supplements',
    newPath: '/subcontract/supplements/new',
    detailUrlPattern: '\\/subcontract\\/supplements\\/\\d+',
    codeLabel: '补料单号',
    headerSelects: ['委外加工单'],
    headerDates: ['补料日期'],
    // 表头有一条必填文本域「补料原因」（非下拉非日期），不填会被必填校验拦住
    headerTexts: [{ label: '补料原因', value: 'E2E冒烟-补料原因' }],
    detail: {
      addButtonText: '添加明细',
      materialHeader: '商品名称',
      qtyHeader: '数量',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/subcontract/SubcontractSupplementEdit.vue L348-L352 必填 rules: supplementDate / orderId / reason',
      'src/views/subcontract/SubcontractSupplementEdit.vue L105 必填「补料原因」prop="reason"（textarea）',
      'src/views/subcontract/SubcontractSupplementEdit.vue L134-L136 「添加明细」按钮 @click="addLine"',
      'src/views/subcontract/SubcontractSupplementEdit.vue L254-L263 detailColumns：materialId「商品名称」、quantity「数量」',
      '⚠ 明细行前端上送 unitName/unitId，后端 SubcontractSupplementItem 只有 unit',
      '⚠ 表头「委外加工单」下拉只列 status≥3 的委外单（L752-L754）→ 前置数据依赖',
      'src/router/index.js L261-L263 路由'
    ]
  },

  // ---------- 财务模块 ----------
  // 说明：财务单据明细**没有物料/数量列**，明细由通用组件 BillItemsTable 渲染；
  //      materialHeader 指向明细里唯一的下拉列「账户」，qtyHeader 指向唯一的可填金额列。
  {
    key: 'sale_pre_receipt',
    name: '销售预收款单',
    module: 'finance',
    listPath: '/finance/sale-pre-receipts',
    newPath: '/finance/sale-pre-receipts/new',
    detailUrlPattern: '\\/finance\\/sale-pre-receipts\\/edit\\/\\d+',
    codeLabel: '单号',
    // 必填的 customerId 是"只读输入框 + 弹窗选择器"而非 el-select，测试无法自动填 → 留空（保存走宽松草稿分支）
    headerSelects: [],
    // 2026-11-11：客户是“只读框 + 放大镜弹窗”，由 headerPickers 走弹窗双击选择；
    // 不填客户时提交会被必填校验拦住，曾被误判成“提交按钮点了没反应”。
    headerPickers: ['客户'],
    headerDates: ['收款日期'],
    detail: {
      addButtonText: '添加明细',
      materialHeader: '账户',
      qtyHeader: '收款金额',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/finance/SalePreReceiptEdit.vue L536-L540 必填 rules: receiptDate(日期)/customerId(只读+弹窗 L89-L95)/amount',
      'src/views/finance/SalePreReceiptEdit.vue L161-L163 「添加明细」按钮；L148 :items="form.items"',
      'src/views/finance/SalePreReceiptEdit.vue L432-L442 明细列：L433「账户」(唯一 el-select)、L435「收款金额」',
      'backend finance/prepayment/sale/entity/SalePreReceiptItem.java L28-L38 落库列：account_id/account_name/account_no/amount',
      '⚠ 明细「账户」下拉依赖 bank_account 基础数据，为空则填不了',
      'src/router/index.js L295-L297 路由'
    ]
  },
  {
    key: 'purchase_pre_payment',
    name: '采购预付款单',
    module: 'finance',
    listPath: '/finance/purchase-pre-payments',
    newPath: '/finance/purchase-pre-payments/new',
    detailUrlPattern: '\\/finance\\/purchase-pre-payments\\/edit\\/\\d+',
    codeLabel: '单号',
    headerSelects: [],
    // 2026-11-11：供应商是“只读框 + 放大镜弹窗”，由 headerPickers 走弹窗双击选择
    headerPickers: ['供应商'],
    headerDates: ['付款日期'],
    detail: {
      addButtonText: '添加明细',
      materialHeader: '账户',
      qtyHeader: '付款金额',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/finance/PurchasePrePaymentEdit.vue L534-L538 必填 rules: paymentDate/supplierId(只读+弹窗)/amount',
      'src/views/finance/PurchasePrePaymentEdit.vue L161-L163 「添加明细」按钮；L148 :items="form.items"',
      'src/views/finance/PurchasePrePaymentEdit.vue L432-L442 明细列：L433「账户」、L435「付款金额」',
      'backend finance/prepayment/purchase/entity/PurchasePrePaymentItem.java L28-L38 落库列：account_id/account_name/account_no/amount',
      'src/router/index.js L299-L301 路由'
    ]
  },
  {
    key: 'payment_receipt',
    name: '收款单',
    module: 'finance',
    listPath: '/finance/receipt',
    newPath: '/finance/receipt/new',
    detailUrlPattern: '\\/finance\\/receipt\\/edit\\/\\d+',
    codeLabel: '单号',
    headerSelects: ['客户', '币种'],
    headerDates: ['收款日期'],
    detail: {
      addButtonText: '添加明细',
      materialHeader: '账户',
      // 明细列真实名是「金额」（PaymentReceiptEdit.vue detailColumns：类型/账户/预收单号/银行账号/金额/备注/收款本位币）
      qtyHeader: '金额',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/finance/PaymentReceiptEdit.vue L934-L938 必填 rules: receiptDate/customerId(el-select L85-L89)/currencyId(el-select L112-L116)',
      'src/views/finance/PaymentReceiptEdit.vue L164-L166 「添加明细」按钮；L149 :items="combinedDetailRows"（底层仍是 form.items）',
      'src/views/finance/PaymentReceiptEdit.vue L699-L705 明细列：L700「账户」、L702「收款金额」',
      'backend finance/entity/PaymentReceiptItem.java L24-L45（@TableName("cash_receipt_item")）',
      'src/router/index.js L281-L283 路由 finance/receipt、.../new、.../edit/:id'
    ]
  },
  {
    key: 'payment_voucher',
    name: '付款单',
    module: 'finance',
    listPath: '/finance/payment',
    newPath: '/finance/payment/new',
    detailUrlPattern: '\\/finance\\/payment\\/edit\\/\\d+',
    codeLabel: '单号',
    headerSelects: ['供应商', '币种'],
    headerDates: ['付款日期'],
    detail: {
      addButtonText: '添加明细',
      materialHeader: '账户',
      qtyHeader: '金额',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/finance/PaymentVoucherEdit.vue L722-L726 必填 rules: paymentDate/supplierId(el-select)/currencyId(el-select)',
      'src/views/finance/PaymentVoucherEdit.vue L164-L166 「添加明细」按钮；L149 :items="combinedPayRows"',
      'src/views/finance/PaymentVoucherEdit.vue L601-L609 明细列：L603「账户」、L606「金额」',
      'backend finance/entity/PaymentVoucherItem.java L24-L45（@TableName("cash_payment_item")）',
      'src/router/index.js L284-L286 路由 finance/payment、.../new、.../edit/:id'
    ]
  },

  // ---------- 打样管理 + BOM ----------
  {
    key: 'sample_order',
    name: '打样单',
    module: 'sample',
    listPath: '/sample/list',
    newPath: '/sample/edit',
    detailUrlPattern: '\\/sample\\/edit\\/\\d+',
    codeLabel: '打样单号',
    headerSelects: ['样品名称', '样品类型', '打样阶段', '单位', '优先级'],
    headerDates: ['需求交期', '计划完成'],
    // ⚠ 该页是手写明细（.table-wrap 内无 .detail-subtable-header），靠 normalizeDetailAnchor ③ 分支兜底
    detail: {
      addButtonText: '添加子件',
      materialHeader: '子件物料',
      qtyHeader: '基本用量',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/sample/SampleEdit.vue L544-L553 必填 rules: sampleName/sampleType/sampleStage/unitId/sampleQty/demandDate/planFinishDate/priority',
      'src/views/sample/SampleEdit.vue L204-L206 「添加子件」按钮 @click="addItem"',
      'src/views/sample/SampleEdit.vue L210-L211 明细表 .table-wrap > el-table :data="form.items"（无子表头锚点）',
      'src/views/sample/SampleEdit.vue L213/L215 列「子件物料」v-model="row.childMaterialId"；L246/L248 列「基本用量」v-model="row.baseQty"',
      'backend sample/entity/SampleOrderItem.java L26-L27 childMaterialId / L42-L43 baseQty（与前端逐字一致）',
      'src/router/index.js L269-L271 路由 sample/list、sample/edit、sample/edit/:id'
    ]
  },
  {
    key: 'free_sample_order',
    name: '免费样品单',
    module: 'sample',
    listPath: '/freesample/list',
    newPath: '/freesample/edit',
    // 详情是 query 形式：/freesample/edit?id=N（FreeSampleList L242）
    detailUrlPattern: '\\/freesample\\/edit\\?id=\\d+',
    codeLabel: '单号',
    headerSelects: ['客户', '仓库'],
    headerDates: ['日期'],
    detail: {
      // 界面按钮文案是「+ 添加行」，用子串「添加行」匹配
      addButtonText: '添加行',
      materialHeader: '商品名称',
      qtyHeader: '数量',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/freesample/FreeSampleEdit.vue L510-L514 必填 rules: customerId/sampleDate/warehouseId',
      'src/views/freesample/FreeSampleEdit.vue L162 「+ 添加行」按钮 @click="addItem"',
      'src/views/freesample/FreeSampleEdit.vue L171 明细表 el-table :data="form.items"（有子表头、无 .table-wrap 外层）',
      'src/views/freesample/FreeSampleEdit.vue L173/L175 列「商品名称」v-model="row.materialId"；L185/L187 列「数量」v-model="row.quantity"',
      'backend freesample/entity/FreeSampleOrderItem.java L26-L27 materialId / L41-L42 quantity（一致）',
      'src/router/index.js L272-L273 路由 freesample/list、freesample/edit/:id?'
    ]
  },
  {
    key: 'bom',
    name: 'BOM',
    module: 'basedata',
    listPath: '/masterdata/bom',
    newPath: '/masterdata/bom/new',
    detailUrlPattern: '\\/masterdata\\/bom\\/\\d+',
    codeLabel: '单据编号',
    // 母件物料下拉只列"成品/半成品"（BomEdit L677-L679）→ 前置数据依赖
    headerSelects: ['母件物料', '基本单位'],
    headerDates: ['单据日期'],
    detail: {
      addButtonText: '添加子件',
      materialHeader: '子件物料',
      qtyHeader: '用量',
      qtyValues: [2, 3]
    },
    evidence: [
      'src/views/masterdata/BomEdit.vue L764-L769 必填 rules: bomDate/materialId/unitId/quantity',
      'src/views/masterdata/BomEdit.vue L227-L229 「添加子件」按钮 @click="addItem"',
      'src/views/masterdata/BomEdit.vue L237 明细表 el-table :data="form.items"（有子表头、无 .table-wrap 外层）',
      'src/views/masterdata/BomEdit.vue L265/L268 列「子件物料」v-model="row.materialId"；L316/L318 列「用量」v-model="row.quantity"',
      'backend masterdata/entity/BomItem.java L37-L38 materialId / L72-L73 quantity（一致）',
      'src/router/index.js L205-L207 路由 masterdata/bom、masterdata/bom/new、masterdata/bom/:id'
    ]
  }
]

/**
 * 有明细表、但**无法用本套闭环模型覆盖**的单据。
 *
 * 口径（2026-09-20 更新）：原清单里 18 张"尚未逐页核对字段"的单据，已全部逐页核对完毕，
 * 其中 17 张移入 BILLS；只剩下面这 1 张，且原因是**功能形态不匹配**（不是"还没做"）。
 * 这里保留登记是为了让报告如实列出"未覆盖"，绝不静默跳过、也绝不伪造通过。
 */
export const PENDING_BILLS = [
  {
    name: 'MRP计划',
    table: 'mrp_plan',
    reason: '运算结果单：明细由 MRP 运算生成，页面无「添加明细」按钮（MrpPlanEdit.vue L193-L213 明细表只读），'
      + '不符合"加 2 行明细→回读→改数量→再回读"的闭环模型；列表页「新增MRP计划」进入的是 5 步向导（MrpList.vue），'
      + '并非打开空白单据。它的正确验证方式是「跑一次 MRP 运算→校验生成结果」，属另一类用例，本期如实列为未覆盖。'
  },
  {
    name: '工序转移单',
    table: 'process_transfer',
    reason: '该单据已下线：路由重定向到生产工单，编辑页既无明细表容器也无「保存」按钮，'
      + '不符合可建单闭环模型（2026-11-11 实测）。保留登记以如实列出未覆盖，不做闭环。'
  }
]
