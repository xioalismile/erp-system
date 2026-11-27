package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 采购模块 6 个单据明细报表的列配置。
 *
 * <p><b>列名来源</b>：全部取自各实体类的 {@code @TableField} 映射（逐字实查，非推测）。
 * 采购模块有几个**很容易抄错**的列名，已在下方逐条标注：
 * <ul>
 *   <li>采购入库单：日期列是 {@code receipt_date}（Java 字段叫 inDate）；仓库列是 {@code warehouse_id}（Java 字段叫 stockId）；
 *       行规格列列名是 {@code specification}（Java 字段名叫 model）—— 列名与 Java 字段名**方向相反**，最容易抄错</li>
 *   <li>采购发票：主表金额列是 {@code total_amount}（Java 字段叫 amount）、{@code net_amount}（Java 字段叫 taxTotalAmount）；
 *       税号列是 {@code tax_no}（Java 字段叫 supplierTaxNo）；来源类型列是 {@code source_bill_type}
 *       （只有申请单/入库单才叫 {@code source_type}，退货/发票/订单均为 {@code source_bill_type}）</li>
 *   <li>采购申请单：数量列是 {@code quantity}（Java 字段叫 applyQty）；明细备注列是 {@code remark}（Java 字段叫 rowRemark）</li>
 *   <li>采购合同：只有 {@code supplier_no}/{@code supplier_short_name}，**没有** {@code supplier_name}；也没有任何 {@code source_*} 列</li>
 *   <li>合同/订单明细**没有** {@code bin_id}/{@code bin_name} 列，不得 JOIN location；入库/退货明细才有储位</li>
 * </ul>
 *
 * <p><b>三个一致性硬口径</b>（与销售三个明细报表完全一致）：
 * <ol>
 *   <li>默认可见列 7 列，顺序 = 单号 / 对方单位名称 / 日期 / 业务员 / 商品名称 / 商品编号 / 规格</li>
 *   <li>默认勾选搜索条件 = 同样这 7 项（商品名称/编号/规格拆成 3 个独立条件）</li>
 *   <li>日期条件标记 defaultCurrentMonth（前端预置本月 1 日 ~ 今天）</li>
 * </ol>
 *
 * <p><b>名称列取值策略</b>：采购表普遍带冗余名称列（supplier_name/buyer_name/dept_name/stock_name），
 * 但**实测有空白**（如 purchase_order.dept_name 全空、purchase_receipt.stock_name 全空）。
 * 故统一写成 {@code COALESCE(冗余列, 主档列)}，空白时自动回落到主档，避免报表出现大片空白。
 */
public final class PurchaseBillDetailReportConfigs {

    private PurchaseBillDetailReportConfigs() {
    }

    private static final String JOIN_SUPPLIER = "LEFT JOIN supplier s ON s.supplier_id = o.supplier_id";
    private static final String JOIN_UNIT = "LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id";
    private static final String JOIN_DEPT = "LEFT JOIN department dp ON dp.dept_id = o.department_id";
    private static final String JOIN_CURRENCY = "LEFT JOIN currency cu ON cu.currency_id = o.currency_id";
    private static final String JOIN_PAYMENT_TERM = "LEFT JOIN payment_term pt ON pt.payment_term_id = o.payment_term_id";
    private static final String JOIN_SETTLE = "LEFT JOIN settlement_mode sm ON sm.settle_mode_id = o.settle_mode_id";
    private static final String JOIN_DOC_WAREHOUSE = "LEFT JOIN warehouse w ON w.stock_id = o.warehouse_id";
    private static final String JOIN_ITEM_WAREHOUSE = "LEFT JOIN warehouse iw ON iw.stock_id = i.stock_id";
    private static final String JOIN_ITEM_BIN = "LEFT JOIN location b ON b.bin_id = i.bin_id";

    /** 统一的"明细三件套"（商品名称/编号/规格）默认列 —— 三个报表必须一模一样，故抽成方法 */
    private static void addItemMaterialTriple(BillDetailReportConfig c, String specExpr) {
        c.add(new Col("lineNo", "行号", G_ITEM, "int", "i.line_no", false, null, true));
        c.add(new Col("materialName", "商品名称", G_ITEM, "text", "i.material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("materialCode", "商品编号", G_ITEM, "text", "i.material_code", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格", G_ITEM, "text", specExpr, true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", "u.unit_name", false, null, false));
    }

    /** 采购单据共用的合计口径（明细级 + 单据级） */
    private static void applyTotals(BillDetailReportConfig c, String... docTotalKeys) {
        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        for (String k : docTotalKeys) c.docTotalKeys.add(k);
    }

    // ==================================================================
    // 1. 采购申请单明细报表
    // ==================================================================
    public static BillDetailReportConfig requestConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "purchase_request";
        c.itemTable = "purchase_request_item";
        c.itemFkColumn = "request_id";
        c.docIdColumn = "request_id";
        c.itemIdColumn = "request_item_id";
        c.cfEntityDoc = "purchase_request";
        c.cfEntityItem = "purchase_request_item";
        c.docNoExpr = "o.request_no";
        // 采购申请单没有 buyer_id，业务归属是申请人 applicant_id（实查 PurchaseRequestServiceImpl）
        c.dataScopeOwnerColumn = "applicant_id";
        c.docDateFilterKey = "requestDate";
        c.defaultOrderBy = " ORDER BY o.request_date DESC NULLS LAST, o.request_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.applicant_id");
        c.extraJoins.add(JOIN_DEPT);
        c.extraJoins.add(JOIN_SETTLE);
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");
        c.extraJoins.add("LEFT JOIN supplier ss ON ss.supplier_id = i.suggested_supplier_id");

        c.add(new Col("requestNo", "申请单号", G_DOC, "text", "o.request_no", true, "left", true)).filter("text", true);
        // 申请单的"对方单位"在明细行上（建议供应商），故本条件的比较列落在 i 上
        c.add(new Col("suggestedSupplierName", "建议供应商", G_DOC, "text",
                "COALESCE(i.suggested_supplier_name, ss.supplier_name)", true, null, false))
                .select("supplier", "long", "i.suggested_supplier_id", true);
        c.add(new Col("requestDate", "申请日期", G_DOC, "date", "o.request_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("applierName", "申请人", G_DOC, "text", "COALESCE(o.applier_name, e.employee_name)", true, null, true))
                .select("employee", "long", "o.applicant_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("applyType", "申请类型", G_DOC, "text", "o.apply_type", false, null, false));
        c.add(new Col("deptName", "申请部门", G_DOC, "text", "COALESCE(o.apply_dept_name, dp.dept_name)", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("demandDate", "需求日期", G_DOC, "date", "o.demand_date", false, null, true));
        c.add(new Col("totalQty", "申请总数量", G_DOC, "number", "o.total_qty", false, null, true));
        c.add(new Col("isUrgent", "是否加急", G_DOC, "text", "o.is_urgent", false, null, false));
        c.add(new Col("settleModeName", "结算方式", G_DOC, "text", "sm.settle_mode_name", false, null, false))
                .select("settleMode", "long", "o.settle_mode_id", false);
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("isClosed", "是否已结案", G_DOC, "int", "o.is_closed", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("busStatus", "业务状态", G_DOC, "int", "o.bus_status", false, null, false));
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c, "i.specification");   // 规格列列名统一是 specification（注意实体 Java 字段名叫 model，别被字段名带偏）
        c.add(new Col("applyQty", "申请数量", G_ITEM, "number", "i.quantity", false, null, true));  // Java 字段叫 applyQty，列名是 quantity
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("approvedQty", "核定数量", G_ITEM, "number", "i.approved_qty", false, null, true));
        c.add(new Col("suggestedSupplierNameItem", "建议供应商（明细）", G_ITEM, "text",
                "COALESCE(i.suggested_supplier_name, ss.supplier_name)", false, null, false));
        c.add(new Col("itemDemandDate", "需求日期（明细）", G_ITEM, "date", "i.demand_date", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("productionDate", "生产日期", G_ITEM, "date", "i.production_date", false, null, false));
        c.add(new Col("expiryDate", "有效期至", G_ITEM, "date", "i.expiry_date", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceRowNo", "来源行号", G_ITEM, "int", "i.source_row_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));
        // 申请单没有金额列，故合计行只保留数量（itemTotalKeys 里其它 key 在此单据不存在时会跳过）
        c.itemTotalKeys.add("quantity");
        c.docTotalKeys.add("totalQty");
        return c;
    }

    // ==================================================================
    // 2. 采购合同明细报表
    // ==================================================================
    public static BillDetailReportConfig contractConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "purchase_contract";
        c.itemTable = "purchase_contract_item";
        c.itemFkColumn = "contract_id";
        c.docIdColumn = "contract_id";
        c.itemIdColumn = "contract_item_id";
        c.cfEntityDoc = "purchase_contract";
        c.cfEntityItem = "purchase_contract_item";
        c.docNoExpr = "o.contract_no";
        c.dataScopeOwnerColumn = "buyer_id";
        c.docDateFilterKey = "contractDate";
        c.defaultOrderBy = " ORDER BY o.contract_date DESC NULLS LAST, o.contract_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.buyer_id");
        c.extraJoins.add(JOIN_DEPT);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add(JOIN_PAYMENT_TERM);
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        // 注意：purchase_contract_item 没有 bin_id/bin_name 列（实查 information_schema），
        // 故这里**不能**加 JOIN_ITEM_BIN，否则整条 SQL 报「字段 i.bin_id 不存在」。合同明细也没有"储位"列。

        c.add(new Col("contractNo", "合同编号", G_DOC, "text", "o.contract_no", true, "left", true)).filter("text", true);
        // 合同表没有 supplier_name，只有 supplier_short_name → 全名从 supplier 主档取
        c.add(new Col("supplierName", "供应商名称", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_short_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("contractDate", "合同日期", G_DOC, "date", "o.contract_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("buyerName", "采购员", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.buyer_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("contractType", "合同类型", G_DOC, "text", "o.contract_type", false, null, false));
        c.add(new Col("supplierNo", "供应商编号", G_DOC, "text", "o.supplier_no", false, null, false));
        c.add(new Col("supplierShortName", "供应商简称", G_DOC, "text", "o.supplier_short_name", false, null, false));
        c.add(new Col("validityStart", "有效期起", G_DOC, "date", "o.validity_start", false, null, true));
        c.add(new Col("validityEnd", "有效期止", G_DOC, "date", "o.validity_end", false, null, true));
        c.add(new Col("contactPerson", "联系人", G_DOC, "text", "o.contact_person", false, null, false));
        c.add(new Col("contactPhone", "联系电话", G_DOC, "text", "o.contact_phone", false, null, false));
        c.add(new Col("deptName", "采购部门", G_DOC, "text", "dp.dept_name", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true));
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("taxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("discountRate", "整单折扣率%", G_DOC, "number", "o.discount_rate", false, null, true));
        c.add(new Col("discountAmount", "折扣额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true));
        c.add(new Col("netAmount", "价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true));
        c.add(new Col("depositAmount", "定金金额（整单）", G_DOC, "amount", "o.deposit_amount", false, null, true));
        c.add(new Col("deliveryDate", "交货日期", G_DOC, "date", "o.delivery_date", false, null, true));
        c.add(new Col("paymentTermName", "付款条件", G_DOC, "text", "pt.payment_term_name", false, null, false));
        c.add(new Col("paymentMethod", "付款方式", G_DOC, "text", "o.payment_method", false, null, false))
                .select("paymentMethod", "string", "o.payment_method", false);
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", false, null, false))
                .select("warehouse", "long", "o.warehouse_id", false);
        c.add(new Col("receiveAddress", "收货地址", G_DOC, "text", "o.receive_address", false, null, false));
        c.add(new Col("transportMethod", "运输方式", G_DOC, "text", "o.transport_method", false, null, false))
                .select("transportMethod", "string", "o.transport_method", false);
        c.add(new Col("isTax", "是否含税", G_DOC, "text", "o.is_tax", false, null, false));
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("isInvoiced", "是否已开票", G_DOC, "text", "o.is_invoiced", false, null, false));
        c.add(new Col("logisticsCompany", "物流公司", G_DOC, "text", "o.logistics_company", false, null, false));
        c.add(new Col("logisticsNo", "物流单号", G_DOC, "text", "o.logistics_no", false, null, false));
        c.add(new Col("invoiceType", "发票类型", G_DOC, "text", "o.invoice_type", false, null, false));
        c.add(new Col("projectNo", "项目号", G_DOC, "text", "o.project_no", false, null, false));
        c.add(new Col("supplierOrderNo", "供应商订单号", G_DOC, "text", "o.supplier_order_no", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
        c.add(new Col("baseTotalAmount", "本位币合计金额", G_DOC, "amount", "o.base_total_amount", false, null, true));
        c.add(new Col("baseTaxAmount", "本位币税额", G_DOC, "amount", "o.base_tax_amount", false, null, true));
        c.add(new Col("baseNetAmount", "本位币价税合计", G_DOC, "amount", "o.base_net_amount", false, null, true));

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c, "i.specification");
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        // 【2026-10-14 第二批】合同的已入库/已开票改「合同→订单→入库/发票」两跳实时聚合：
        // purchase_contract_item.received_quantity / invoiced_quantity 全系统只有初始化为 0，没有任何回写点（死列）。
        c.add(new Col("receivedQuantity", "已入库数量", G_ITEM, "number",
                RowWritebackSql.purchaseContractInboundQty("i.contract_item_id", "i.material_code"), false, null, true));
        c.add(new Col("invoicedQuantity", "已开票数量", G_ITEM, "number",
                RowWritebackSql.purchaseContractInvoiceQty("i.contract_item_id", "i.material_code"), false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        // 2026-11-14：编辑页明细有含税价/不含税价，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("itemDiscountRate", "明细折扣率%", G_ITEM, "number", "i.discount_rate", false, null, true));
        c.add(new Col("itemDiscountAmount", "明细折扣额", G_ITEM, "amount", "i.discount_amount", false, null, true));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("itemTaxAmount", "明细税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", "i.net_amount", false, null, true));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("itemDeliveryDate", "明细交货日期", G_ITEM, "date", "i.delivery_date", false, null, false));
        c.add(new Col("deliveryPlace", "交货地点", G_ITEM, "text", "i.delivery_place", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceLineNo", "来源行号", G_ITEM, "int", "i.source_line_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        applyTotals(c, "totalAmount", "discountAmount", "taxAmount", "netAmount", "depositAmount");
        // 报表中心默认视图不变：新增列只在列设置里供用户勾选
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax"));
        return c;
    }

    // ==================================================================
    // 3. 采购订单明细报表
    // ==================================================================
    public static BillDetailReportConfig orderConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "purchase_order";
        c.itemTable = "purchase_order_item";
        c.itemFkColumn = "order_id";
        c.docIdColumn = "order_id";
        c.itemIdColumn = "order_item_id";
        c.cfEntityDoc = "purchase_order";
        c.cfEntityItem = "purchase_order_item";
        c.docNoExpr = "o.order_no";
        c.dataScopeOwnerColumn = "buyer_id";   // 实查 PurchaseOrderServiceImpl: applyTo(wrapper, "buyer_id")
        c.docDateFilterKey = "orderDate";
        c.defaultOrderBy = " ORDER BY o.order_date DESC NULLS LAST, o.order_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.buyer_id");
        c.extraJoins.add(JOIN_DEPT);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add(JOIN_PAYMENT_TERM);
        c.extraJoins.add(JOIN_SETTLE);
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        // 注意：purchase_order_item 没有 bin_id/bin_name 列（实查 information_schema），不能加 JOIN_ITEM_BIN。
        // 订单明细的"储位"为 null（订单阶段还不定储位），合并不显示该项。

        c.add(new Col("orderNo", "采购订单号", G_DOC, "text", "o.order_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商名称", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("orderDate", "采购日期", G_DOC, "date", "o.order_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("buyerName", "采购员", G_DOC, "text",
                "COALESCE(NULLIF(o.buyer_name, ''), e.employee_name)", true, null, true))
                .select("employee", "long", "o.buyer_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        // 2026-11-14：补「计税方式」（默认隐藏，列设置可勾）
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("contactPerson", "联系人", G_DOC, "text", "o.contact_person", false, null, false));
        c.add(new Col("contactPhone", "联系电话", G_DOC, "text", "o.contact_phone", false, null, false));
        c.add(new Col("deptName", "采购部门", G_DOC, "text",
                "COALESCE(NULLIF(o.dept_name, ''), dp.dept_name)", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true));
        c.add(new Col("docTaxRate", "税率%（整单）", G_DOC, "number", "o.tax_rate", false, null, false));
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("discountRate", "整单折扣率%", G_DOC, "number", "o.discount_rate", false, null, true));
        c.add(new Col("discountAmount", "折扣额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true));
        c.add(new Col("taxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("netAmount", "价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true));
        c.add(new Col("prepaymentAmount", "预付款金额（整单）", G_DOC, "amount", "o.prepayment_amount", false, null, true));
        c.add(new Col("prepaidRate", "预付款比例%", G_DOC, "number", "o.prepaid_rate", false, null, true));
        c.add(new Col("deliveryDate", "交货日期", G_DOC, "date", "o.delivery_date", false, null, true));
        c.add(new Col("deliveryPlace", "交货地点", G_DOC, "text", "o.delivery_place", false, null, false));
        c.add(new Col("paymentTermName", "付款条件", G_DOC, "text", "pt.payment_term_name", false, null, false));
        c.add(new Col("settleModeName", "结算方式", G_DOC, "text", "sm.settle_mode_name", false, null, false))
                .select("settleMode", "long", "o.settle_mode_id", false);
        c.add(new Col("accountName", "采购账户", G_DOC, "text", "o.account_name", false, null, false));
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("requestNo", "请购单号", G_DOC, "text", "o.request_no", false, null, false));
        c.add(new Col("isClosed", "是否已结案", G_DOC, "int", "o.is_closed", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("busStatus", "业务状态", G_DOC, "int", "o.bus_status", false, null, false));
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
        c.add(new Col("baseTotalAmount", "本位币合计金额", G_DOC, "amount", "o.base_total_amount", false, null, true));
        c.add(new Col("baseTaxAmount", "本位币税额", G_DOC, "amount", "o.base_tax_amount", false, null, true));
        c.add(new Col("baseNetAmount", "本位币价税合计", G_DOC, "amount", "o.base_net_amount", false, null, true));

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c, "i.specification");
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        // 【2026-10-14 修复】已入/已开与行级回写列改为「下游单据明细实时聚合」：
        // 原读落库列 received_quantity/invoiced_quantity（无实时回写点保障 + 演示假值会假报入库/开票），
        // 与销售订单同批、同口径（已生效类下游计入数量、已作废剔除、草稿/待审核不计）。
        c.add(new Col("receivedQuantity", "已入库数量", G_ITEM, "number",
                RowWritebackSql.purchaseInboundQty("i.order_item_id", "o.order_no", "i.material_code"), false, null, true));
        c.add(new Col("invoicedQuantity", "已开票数量", G_ITEM, "number",
                RowWritebackSql.purchaseInvoiceQty("i.line_no", "o.order_no", "i.material_code"), false, null, true));
        // 行级回写展示列（2026-10-12 列表页明细模式；2026-10-14 起取数改为下游实时聚合），
        // 登记 reportHiddenKeys 保证既有"采购订单明细报表"默认视图不变。付款无行级落库字段 → 不硬凑。）
        c.add(new Col("rowInboundStatus", "入库情况", G_ITEM, "text",
                "'已入 ' || " + RowWritebackSql.purchaseInboundQty("i.order_item_id", "o.order_no", "i.material_code")
                        + " || ' / ' || COALESCE(i.quantity,0)", false, null, false));
        c.add(new Col("rowInvoiceStatus", "发票情况", G_ITEM, "text",
                "'已开 ' || " + RowWritebackSql.purchaseInvoiceQty("i.line_no", "o.order_no", "i.material_code")
                        + " || ' / ' || COALESCE(i.quantity,0)", false, null, false));
        c.reportHiddenKeys.addAll(java.util.List.of("rowInboundStatus", "rowInvoiceStatus"));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        // 2026-11-14：编辑页明细有含税价/不含税价，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("taxType", "税种", G_ITEM, "int", "i.tax_type", false, null, false));
        c.add(new Col("itemDiscountRate", "明细折扣率%", G_ITEM, "number", "i.discount_rate", false, null, true));
        c.add(new Col("itemDiscountAmount", "明细折扣额", G_ITEM, "amount", "i.discount_amount", false, null, true));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("avgCost", "成本", G_ITEM, "number", "i.avg_cost", false, null, false));
        c.add(new Col("itemTaxAmount", "明细税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", "i.net_amount", false, null, true));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("itemDeliveryDate", "明细交货日期", G_ITEM, "date", "i.delivery_date", false, null, false));
        c.add(new Col("itemDeliveryPlace", "明细交货地点", G_ITEM, "text", "i.delivery_place", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("productionDate", "生产日期", G_ITEM, "date", "i.production_date", false, null, false));
        c.add(new Col("expiryDate", "有效期至", G_ITEM, "date", "i.expiry_date", false, null, false));
        // 【2026-10-14 第二批】已转入库/已转退货改下游单据明细实时聚合（原读落库列：演示假值会假报入库/退货）
        c.add(new Col("turnedInputQuantity", "已转入库数量", G_ITEM, "number",
                RowWritebackSql.purchaseInboundQty("i.order_item_id", "o.order_no", "i.material_code"), false, null, false));
        c.add(new Col("turnedReturnQuantity", "已转退货数量", G_ITEM, "number",
                RowWritebackSql.purchaseReturnQty("i.order_item_id", "o.order_no", "i.material_code"), false, null, false));
        c.add(new Col("requestLineNo", "请购单行号", G_ITEM, "int", "i.request_line_no", false, null, false));
        c.add(new Col("detailCloseStatus", "明细结案状态", G_ITEM, "int", "i.detail_close_status", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceLineNo", "来源行号", G_ITEM, "int", "i.source_line_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));
        c.add(new Col("baseUnitPrice", "本位币单价", G_ITEM, "number", "i.base_unit_price", false, null, false));
        c.add(new Col("baseAmount", "本位币金额", G_ITEM, "amount", "i.base_amount", false, null, false));
        c.add(new Col("itemBaseTaxAmount", "明细本位币税额", G_ITEM, "amount", "i.base_tax_amount", false, null, false));
        c.add(new Col("itemBaseNetAmount", "明细本位币价税合计", G_ITEM, "amount", "i.base_net_amount", false, null, false));

        applyTotals(c, "totalAmount", "discountAmount", "taxAmount", "netAmount", "prepaymentAmount");
        // 报表中心默认视图不变：新增列只在列设置里供用户勾选
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax", "taxCalcMethod"));
        return c;
    }

    // ==================================================================
    // 4. 采购入库单明细报表（表名是 purchase_receipt，不是 purchase_inbound）
    // ==================================================================
    public static BillDetailReportConfig inboundConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "purchase_receipt";
        c.itemTable = "purchase_receipt_item";
        c.itemFkColumn = "receipt_id";
        c.docIdColumn = "receipt_id";
        c.itemIdColumn = "receipt_item_id";
        c.cfEntityDoc = "purchase_inbound";
        c.cfEntityItem = "purchase_inbound_item";
        c.docNoExpr = "o.receipt_no";
        c.dataScopeOwnerColumn = "buyer_id";
        c.docDateFilterKey = "receiptDate";
        c.defaultOrderBy = " ORDER BY o.receipt_date DESC NULLS LAST, o.receipt_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.buyer_id");
        c.extraJoins.add(JOIN_DEPT);
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");
        c.extraJoins.add(JOIN_ITEM_BIN);
        // 2026-11-14：入库以明细行仓库为准（编辑页 2026-11-07 起），明细模式同步显示明细仓库
        c.extraJoins.add("LEFT JOIN warehouse iw ON iw.stock_id = i.stock_id");

        c.add(new Col("receiptNo", "入库单号", G_DOC, "text", "o.receipt_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商名称", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        // 日期列名是 receipt_date（实体 Java 字段叫 inDate）
        c.add(new Col("receiptDate", "入库日期", G_DOC, "date", "o.receipt_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("buyerName", "采购员", G_DOC, "text",
                "COALESCE(NULLIF(o.buyer_name, ''), e.employee_name)", true, null, true))
                .select("employee", "long", "o.buyer_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        // 2026-11-14：补「计税方式」（默认隐藏，列设置可勾）
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("deptName", "采购部门", G_DOC, "text",
                "COALESCE(NULLIF(o.dept_name, ''), dp.dept_name)", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("warehouseName", "仓库", G_DOC, "text",
                "COALESCE(NULLIF(o.stock_name, ''), w.stock_name)", false, null, false))
                .select("warehouse", "long", "o.warehouse_id", false);
        c.add(new Col("sourceType", "来源类型", G_DOC, "text", "o.source_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("deliveryNo", "送货单号", G_DOC, "text", "o.delivery_no", false, null, false));
        c.add(new Col("inspectionNo", "检验单号", G_DOC, "text", "o.inspection_no", false, null, false));
        // 2026-11-27 明细列设置补单据字段（采购其他单据都有「审批状态」、入库单缺）
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("isLotControl", "是否批次管理", G_DOC, "text", "o.is_lot_control", false, null, false));
        c.add(new Col("isClosed", "是否已结案", G_DOC, "int", "o.is_closed", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("expectedDeliveryDate", "预计到货日期", G_DOC, "date", "o.expected_delivery_date", false, null, false));
        c.add(new Col("deliveryAddress", "收货地址", G_DOC, "text", "o.delivery_address", false, null, false));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
        c.add(new Col("baseTotalAmount", "本位币合计金额", G_DOC, "amount", "o.base_total_amount", false, null, true));
        c.add(new Col("baseTaxAmount", "本位币税额", G_DOC, "amount", "o.base_tax_amount", false, null, true));
        c.add(new Col("baseNetAmount", "本位币价税合计", G_DOC, "amount", "o.base_net_amount", false, null, true));

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c, "i.specification");   // 规格列列名是 specification（实体 Java 字段名叫 model）
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        // 2026-11-14：编辑页明细有含税价/不含税价，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("avgCost", "成本", G_ITEM, "number", "i.avg_cost", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        // 2026-11-14：入库明细仓库/质检三列与编辑页对齐（默认隐藏，列设置可勾）
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("qualifiedQuantity", "合格数量", G_ITEM, "number", "i.qualified_quantity", false, null, false));
        c.add(new Col("rejectedQuantity", "不合格数量", G_ITEM, "number", "i.rejected_quantity", false, null, false));
        c.add(new Col("handlingType", "处理方式", G_ITEM, "text", "i.handling_type", false, null, false));
        c.add(new Col("serialNo", "序列号", G_ITEM, "text", "i.serial_no", false, null, false));
        c.add(new Col("productionDate", "生产日期", G_ITEM, "date", "i.production_date", false, null, false));
        c.add(new Col("expiryDate", "有效期至", G_ITEM, "date", "i.expiry_date", false, null, false));
        c.add(new Col("binName", "储位", G_ITEM, "text", "COALESCE(NULLIF(i.bin_name, ''), b.bin_name)", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceRowNo", "来源行号", G_ITEM, "int", "i.source_row_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));
        c.add(new Col("baseUnitPrice", "本位币单价", G_ITEM, "number", "i.base_unit_price", false, null, false));
        c.add(new Col("baseAmount", "本位币金额", G_ITEM, "amount", "i.base_amount", false, null, false));
        c.add(new Col("itemBaseTaxAmount", "明细本位币税额", G_ITEM, "amount", "i.base_tax_amount", false, null, false));
        c.add(new Col("itemBaseNetAmount", "明细本位币价税合计", G_ITEM, "amount", "i.base_net_amount", false, null, false));

        // 入库单明细没有折扣/税额列 → 只对存在的列做合计
        c.itemTotalKeys.addAll(java.util.List.of("quantity", "amount"));
        c.docTotalKeys.add("totalAmount");
        // 报表中心默认视图不变：新增列只在列设置里供用户勾选
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax", "taxCalcMethod",
                "itemWarehouseName", "qualifiedQuantity", "rejectedQuantity", "handlingType"));
        return c;
    }

    // ==================================================================
    // 5. 采购退货单明细报表
    // ==================================================================
    public static BillDetailReportConfig returnConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "purchase_return";
        c.itemTable = "purchase_return_item";
        c.itemFkColumn = "return_id";
        c.docIdColumn = "return_id";
        c.itemIdColumn = "return_item_id";
        c.cfEntityDoc = "purchase_return";
        c.cfEntityItem = "purchase_return_item";
        c.docNoExpr = "o.return_no";
        c.dataScopeOwnerColumn = "buyer_id";
        c.docDateFilterKey = "returnDate";
        c.defaultOrderBy = " ORDER BY o.return_date DESC NULLS LAST, o.return_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.buyer_id");
        c.extraJoins.add(JOIN_DEPT);
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");
        c.extraJoins.add(JOIN_ITEM_BIN);

        c.add(new Col("returnNo", "退货单号", G_DOC, "text", "o.return_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商名称", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("returnDate", "退货日期", G_DOC, "date", "o.return_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("buyerName", "采购员", G_DOC, "text",
                "COALESCE(NULLIF(o.buyer_name, ''), e.employee_name)", true, null, true))
                .select("employee", "long", "o.buyer_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        // 2026-11-14：补「计税方式」（默认隐藏，列设置可勾）
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("deptName", "采购部门", G_DOC, "text",
                "COALESCE(NULLIF(o.dept_name, ''), dp.dept_name)", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("warehouseName", "仓库", G_DOC, "text",
                "COALESCE(NULLIF(o.stock_name, ''), w.stock_name)", false, null, false))
                .select("warehouse", "long", "o.warehouse_id", false);
        c.add(new Col("returnType", "退货类型", G_DOC, "text", "o.return_type", false, null, false));
        c.add(new Col("returnReason", "退货原因", G_DOC, "text", "o.return_reason", false, null, false));
        // 2026-11-27 明细列设置补单据字段（采购其他单据都有「审批状态」、退货单缺）
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("sourceType", "来源类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("isLotControl", "是否批次管理", G_DOC, "text", "o.is_lot_control", false, null, false));
        c.add(new Col("isClosed", "是否已结案", G_DOC, "int", "o.is_closed", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("deliveryAddress", "收货地址", G_DOC, "text", "o.delivery_address", false, null, false));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
        c.add(new Col("baseTotalAmount", "本位币合计金额", G_DOC, "amount", "o.base_total_amount", false, null, true));
        c.add(new Col("baseTaxAmount", "本位币税额", G_DOC, "amount", "o.base_tax_amount", false, null, true));
        c.add(new Col("baseNetAmount", "本位币价税合计", G_DOC, "amount", "o.base_net_amount", false, null, true));

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c, "i.specification");   // 规格列列名是 specification（实体 Java 字段名叫 model）
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        // 2026-11-14：编辑页明细有含税价/不含税价，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("binName", "储位", G_ITEM, "text", "COALESCE(NULLIF(i.bin_name, ''), b.bin_name)", false, null, false));
        c.add(new Col("productionDate", "生产日期", G_ITEM, "date", "i.production_date", false, null, false));
        c.add(new Col("expiryDate", "有效期至", G_ITEM, "date", "i.expiry_date", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceRowNo", "来源行号", G_ITEM, "int", "i.source_row_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "amount"));
        c.docTotalKeys.add("totalAmount");
        // 报表中心默认视图不变：新增列只在列设置里供用户勾选
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax", "taxCalcMethod",
                "itemWarehouseName", "qualifiedQuantity", "rejectedQuantity", "handlingType"));
        return c;
    }

    // ==================================================================
    // 6. 采购发票明细报表
    // ==================================================================
    public static BillDetailReportConfig invoiceConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "purchase_invoice";
        c.itemTable = "purchase_invoice_item";
        c.itemFkColumn = "invoice_id";
        c.docIdColumn = "invoice_id";
        c.itemIdColumn = "invoice_item_id";
        c.cfEntityDoc = "purchase_invoice";
        c.cfEntityItem = "purchase_invoice_item";
        c.docNoExpr = "o.invoice_no";
        // 采购发票主表没有 buyer_id（只有 supplier_id）→ 无业务归属维度，仅按制单人过滤
        c.dataScopeOwnerColumn = null;
        c.docDateFilterKey = "invoiceDate";
        c.defaultOrderBy = " ORDER BY o.invoice_date DESC NULLS LAST, o.invoice_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("invoiceNo", "发票号码", G_DOC, "text", "o.invoice_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商名称", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("invoiceDate", "开票日期", G_DOC, "date", "o.invoice_date", true, null, true)).filter("dateRange", true);
        // 发票没有采购员字段 → 用制单人占住"业务员"这一位（保持 7 项搜索条件结构一致）
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", true, null, true)).filter("text", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        // 2026-11-14：补「计税方式」（默认隐藏，列设置可勾）
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("invoiceType", "发票类型", G_DOC, "text", "o.invoice_type", false, null, false));
        c.add(new Col("supplierTaxNo", "供应商税号", G_DOC, "text", "o.tax_no", false, null, false));
        c.add(new Col("invoiceCode", "发票代码", G_DOC, "text", "o.invoice_code", false, null, false));
        c.add(new Col("invoiceNumber", "发票号码(税务)", G_DOC, "text", "o.invoice_number", false, null, false));
        c.add(new Col("deliveryAddress", "收货地址", G_DOC, "text", "o.delivery_address", false, null, false));
        // 主表金额列名特殊：total_amount 对应 Java 字段 amount；net_amount 对应 taxTotalAmount
        c.add(new Col("totalAmount", "开票金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("taxAmount", "开票税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("netAmount", "开票价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true));
        c.add(new Col("sourceType", "来源类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("isRedInvoice", "红蓝字", G_DOC, "int", "o.is_red_invoice", false, null, false))
                .select("isRedInvoice", "int", "o.is_red_invoice", false);
        c.add(new Col("isMatch", "是否已勾稽", G_DOC, "int", "o.is_match", false, null, false));
        // 2026-11-27 明细列设置补单据字段（采购其他单据都有「审批状态」、发票缺）
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("isClosed", "是否已结案", G_DOC, "int", "o.is_closed", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
        c.add(new Col("baseTotalAmount", "本位币合计金额", G_DOC, "amount", "o.base_total_amount", false, null, true));
        c.add(new Col("baseTaxAmount", "本位币税额", G_DOC, "amount", "o.base_tax_amount", false, null, true));
        c.add(new Col("baseNetAmount", "本位币价税合计", G_DOC, "amount", "o.base_net_amount", false, null, true));

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c, "i.specification");   // 规格列列名是 specification（实体 Java 字段名叫 model）
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        // 2026-11-14：编辑页明细有含税价/不含税价，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("amount", "开票金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("itemTaxAmount", "明细开票税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("taxCategory", "税收分类", G_ITEM, "text", "i.tax_category", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("productionDate", "生产日期", G_ITEM, "date", "i.production_date", false, null, false));
        c.add(new Col("expiryDate", "有效期至", G_ITEM, "date", "i.expiry_date", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceRowNo", "来源行号", G_ITEM, "int", "i.source_row_no", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "amount", "itemTaxAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "taxAmount", "netAmount"));
        // 报表中心默认视图不变：新增列只在列设置里供用户勾选
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax", "taxCalcMethod"));
        return c;
    }
}
