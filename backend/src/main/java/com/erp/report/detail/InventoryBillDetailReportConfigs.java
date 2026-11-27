package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 库存模块 5 个单据明细报表的列配置。
 *
 * <p><b>列名来源</b>：全部取自实库 {@code information_schema.columns}（2026-09-22 逐表实查，非推测）。
 * 库存模块最容易抄错的地方已在下方标注：
 * <ul>
 *   <li>仓库主档主键是 {@code stock_id}、名称是 {@code stock_name}（表 {@code warehouse}）——
 *       单据上的仓库列叫 {@code warehouse_id}，明细上的叫 {@code stock_id}，**两者列名不同**</li>
 *   <li>库存调拨：单据上是 {@code from_stock_id}/{@code to_stock_id}，明细上是
 *       {@code from_stock_id}/{@code to_stock_id} + {@code from_bin_id}/{@code to_bin_id}；
 *       <b>调拨明细没有 stock_id 列</b>，别抄其他单据的写法</li>
 *   <li>盘点明细没有税率/折扣/价税合计，改的是 {@code book_*}（账面）/ {@code check_*}（实盘）/
 *       {@code profit_*}/{@code loss_*}（盘盈盘亏）</li>
 *   <li>领料明细列是 {@code issued_quantity}（已发数量）、{@code source_line_no}（来源行号）</li>
 * </ul>
 *
 * <p><b>三个一致性硬口径</b>（与销售/采购明细报表完全一致）：
 * <ol>
 *   <li>默认可见列 7 列，顺序 = 单号 / 对方单位名称 / 日期 / 业务员 / 商品名称 / 商品编号 / 规格</li>
 *   <li>默认勾选搜索条件 = 同样这 7 项（商品名称/编号/规格拆成 3 个独立条件）</li>
 *   <li>日期条件标记 defaultCurrentMonth（前端预置本月 1 日 ~ 今天）</li>
 * </ol>
 *
 * <p><b>「对方单位名称」这一位怎么落</b>：库存单据是**内部单据，没有往来单位**，
 * 故按业务语义取"这张单最该先看清的那个对象"：
 * <ul>
 *   <li>其他入库 / 其他出库 / 库存盘点 → 取<b>仓库</b>（{@code warehouseName}）</li>
 *   <li>库存调拨 → 取<b>调出仓库</b>（{@code fromStockName}），调入仓库另作可选列</li>
 *   <li>生产领料 → 取<b>领用部门</b>（{@code deptName}），领料人占"业务员"位</li>
 * </ul>
 * 全字段都在列设置里，用户可自行换列，此处只定默认。
 *
 * <p><b>行级数据权限</b>：实查 5 个 Service 都是
 * {@code dataScopeResolver.applyIfLimited(wrapper, null, "created_by")} ——
 * <b>无业务归属列（员工维度），只有制单人（用户名维度）</b>，故 {@code dataScopeOwnerColumn = null}。
 */
public final class InventoryBillDetailReportConfigs {

    private InventoryBillDetailReportConfigs() {
    }

    private static final String JOIN_UNIT = "LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id";
    private static final String JOIN_CURRENCY = "LEFT JOIN currency cu ON cu.currency_id = o.currency_id";
    private static final String JOIN_DEPT = "LEFT JOIN department dp ON dp.dept_id = o.department_id";
    /** 单据头仓库（{@code warehouse} 主键是 stock_id，单据上的列叫 warehouse_id） */
    private static final String JOIN_DOC_WAREHOUSE = "LEFT JOIN warehouse w ON w.stock_id = o.warehouse_id";
    /** 明细仓库（明细上的列叫 stock_id） */
    private static final String JOIN_ITEM_WAREHOUSE = "LEFT JOIN warehouse iw ON iw.stock_id = i.stock_id";
    private static final String JOIN_ITEM_BIN = "LEFT JOIN location b ON b.bin_id = i.bin_id";
    /** 经手人（库存单据字段名是 handler_id，不是 buyder/saler） */
    private static final String JOIN_HANDLER = "LEFT JOIN employee e ON e.employee_id = o.handler_id";
    /** 领料人（领料单字段名是 employee_id） */
    private static final String JOIN_REQUISITION_EMPLOYEE = "LEFT JOIN employee e ON e.employee_id = o.employee_id";
    /** 调拨：单据头调出/调入仓库 */
    private static final String JOIN_DOC_FROM_WAREHOUSE = "LEFT JOIN warehouse fw ON fw.stock_id = o.from_stock_id";
    private static final String JOIN_DOC_TO_WAREHOUSE = "LEFT JOIN warehouse tw ON tw.stock_id = o.to_stock_id";
    /** 调拨：明细行调出/调入仓库 + 储位（调拨明细没有 stock_id，必须走这里的四个别名） */
    private static final String JOIN_ITEM_FROM_WAREHOUSE = "LEFT JOIN warehouse ifw ON ifw.stock_id = i.from_stock_id";
    private static final String JOIN_ITEM_TO_WAREHOUSE = "LEFT JOIN warehouse itw ON itw.stock_id = i.to_stock_id";
    private static final String JOIN_ITEM_FROM_BIN = "LEFT JOIN location ifb ON ifb.bin_id = i.from_bin_id";
    private static final String JOIN_ITEM_TO_BIN = "LEFT JOIN location itb ON itb.bin_id = i.to_bin_id";

    /** 统一的"明细三件套"（商品名称/编号/规格）默认列 —— 5 个报表必须一模一样 */
    private static void addItemMaterialTriple(BillDetailReportConfig c) {
        c.add(new Col("lineNo", "行号", G_ITEM, "int", "i.line_no", false, null, true));
        c.add(new Col("materialName", "商品名称", G_ITEM, "text", "i.material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("materialCode", "商品编号", G_ITEM, "text", "i.material_code", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", "u.unit_name", false, null, false));
    }

    /** 单据头公共列（5 个库存单据都有：状态/审批/结案/作废/制单/备注/组织/附件） */
    private static void addCommonDocTail(BillDetailReportConfig c, boolean withCurrency) {
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("busStatus", "业务状态", G_DOC, "int", "o.bus_status", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("closedBy", "结案人", G_DOC, "text", "o.closed_by", false, null, false));
        c.add(new Col("closedAt", "结案时间", G_DOC, "datetime", "o.closed_at", false, null, false));
        c.add(new Col("closedReason", "结案原因", G_DOC, "text", "o.closed_reason", false, null, false));
        c.add(new Col("billBeforeStatus", "作废前状态", G_DOC, "text", "o.bill_before_status", false, null, false));
        c.add(new Col("voidedBy", "作废人", G_DOC, "text", "o.voided_by", false, null, false));
        c.add(new Col("voidedAt", "作废时间", G_DOC, "datetime", "o.voided_at", false, null, false));
        c.add(new Col("voidReason", "作废原因", G_DOC, "text", "o.void_reason", false, null, false));
        if (withCurrency) {
            c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                    .select("currency", "long", "o.currency_id", false);
            c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true));
        }
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("updatedBy", "修改人", G_DOC, "text", "o.updated_by", false, null, false));
        c.add(new Col("updatedAt", "修改时间", G_DOC, "datetime", "o.updated_at", false, null, false));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
    }

    /** 单据级金额三件套（其他出入库/调拨都有 discount_amount / tax_amount / net_amount） */
    private static void addDocAmounts(BillDetailReportConfig c) {
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("discountAmount", "折扣额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true));
        c.add(new Col("taxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("netAmount", "价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true));
    }

    /** 明细级金额/税（其他出入库/调拨明细都有 tax_rate/discount_rate/discount_amount/tax_amount/net_amount） */
    private static void addItemAmounts(BillDetailReportConfig c) {
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        // 2026-11-14：编辑页明细有含税价/不含税价，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("avgCost", "成本", G_ITEM, "number", "i.avg_cost", false, null, false));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("itemDiscountRate", "明细折扣率%", G_ITEM, "number", "i.discount_rate", false, null, true));
        c.add(new Col("itemDiscountAmount", "明细折扣额", G_ITEM, "amount", "i.discount_amount", false, null, true));
        c.add(new Col("itemTaxAmount", "明细税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", "i.net_amount", false, null, true));
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax"));
    }

    // ==================================================================
    // 1. 其他入库单明细报表（表名 other_receipt / other_receipt_item）
    // ==================================================================
    public static BillDetailReportConfig otherReceiptConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "other_receipt";
        c.itemTable = "other_receipt_item";
        c.itemFkColumn = "receipt_id";
        c.docIdColumn = "receipt_id";
        c.itemIdColumn = "receipt_item_id";
        c.cfEntityDoc = "other_receipt";
        c.cfEntityItem = "other_receipt_item";
        c.docNoExpr = "o.receipt_no";
        c.dataScopeOwnerColumn = null;              // 实查 OtherReceiptServiceImpl: applyIfLimited(wrapper, null, "created_by")
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "receiptDate";
        c.defaultOrderBy = " ORDER BY o.receipt_date DESC NULLS LAST, o.receipt_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_HANDLER);
        c.extraJoins.add(JOIN_CURRENCY);   // 公共尾列里有 cu.currency_code，必须同时 join cu
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_ITEM_BIN);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("receiptNo", "入库单号", G_DOC, "text", "o.receipt_no", true, "left", true)).filter("text", true);
        // 内部单据无往来单位 → 第 2 位取仓库（本单最该先看清的对象）
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", true, null, true))
                .select("warehouse", "long", "o.warehouse_id", true);
        c.add(new Col("receiptDate", "入库日期", G_DOC, "date", "o.receipt_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("handlerName", "经手人", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.handler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("receiptType", "入库类型", G_DOC, "text", "o.receipt_type", false, null, false));
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        addDocAmounts(c);
        addCommonDocTail(c, true);

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c);
        addItemAmounts(c);
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("binName", "储位", G_ITEM, "text", "b.bin_name", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("productionDate", "生产日期", G_ITEM, "date", "i.production_date", false, null, false));
        c.add(new Col("expiryDate", "有效期至", G_ITEM, "date", "i.expiry_date", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "discountAmount", "taxAmount", "netAmount"));
        return c;
    }

    // ==================================================================
    // 2. 其他出库单明细报表（other_issue / other_issue_item）
    // ==================================================================
    public static BillDetailReportConfig otherIssueConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "other_issue";
        c.itemTable = "other_issue_item";
        c.itemFkColumn = "issue_id";
        c.docIdColumn = "issue_id";
        c.itemIdColumn = "issue_item_id";
        c.cfEntityDoc = "other_issue";
        c.cfEntityItem = "other_issue_item";
        c.docNoExpr = "o.issue_no";
        c.dataScopeOwnerColumn = null;              // 实查 OtherIssueServiceImpl: applyIfLimited(wrapper, null, "created_by")
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "issueDate";
        c.defaultOrderBy = " ORDER BY o.issue_date DESC NULLS LAST, o.issue_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_HANDLER);
        c.extraJoins.add(JOIN_DEPT);                // other_issue 有 department_id（other_receipt 没有）
        c.extraJoins.add(JOIN_CURRENCY);   // 公共尾列里有 cu.currency_code，必须同时 join cu
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_ITEM_BIN);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("issueNo", "出库单号", G_DOC, "text", "o.issue_no", true, "left", true)).filter("text", true);
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", true, null, true))
                .select("warehouse", "long", "o.warehouse_id", true);
        c.add(new Col("issueDate", "出库日期", G_DOC, "date", "o.issue_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("handlerName", "经手人", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.handler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("issueType", "出库类型", G_DOC, "text", "o.issue_type", false, null, false));
        c.add(new Col("deptName", "领用部门", G_DOC, "text", "dp.dept_name", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        addDocAmounts(c);
        addCommonDocTail(c, true);

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c);
        addItemAmounts(c);
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("binName", "储位", G_ITEM, "text", "b.bin_name", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("projectCode", "项目号", G_ITEM, "text", "i.project_code", false, null, false));
        c.add(new Col("itemSourceBillType", "明细来源类型", G_ITEM, "text", "i.source_bill_type", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "discountAmount", "taxAmount", "netAmount"));
        return c;
    }

    // ==================================================================
    // 3. 库存调拨单明细报表（stock_transfer / stock_transfer_item）
    //    ⚠ 调拨明细没有 stock_id 列，只有 from_stock_id / to_stock_id / from_bin_id / to_bin_id
    // ==================================================================
    public static BillDetailReportConfig stockTransferConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "stock_transfer";
        c.itemTable = "stock_transfer_item";
        c.itemFkColumn = "transfer_id";
        c.docIdColumn = "transfer_id";
        c.itemIdColumn = "transfer_item_id";
        c.cfEntityDoc = "stock_transfer";
        c.cfEntityItem = "stock_transfer_item";
        c.docNoExpr = "o.transfer_no";
        c.dataScopeOwnerColumn = null;              // 实查 StockTransferServiceImpl: applyIfLimited(wrapper, null, "created_by")
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "transferDate";
        c.defaultOrderBy = " ORDER BY o.transfer_date DESC NULLS LAST, o.transfer_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_DOC_FROM_WAREHOUSE);
        c.extraJoins.add(JOIN_DOC_TO_WAREHOUSE);
        c.extraJoins.add(JOIN_HANDLER);
        c.extraJoins.add(JOIN_CURRENCY);   // 公共尾列里有 cu.currency_code，必须同时 join cu
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add(JOIN_ITEM_FROM_WAREHOUSE);
        c.extraJoins.add(JOIN_ITEM_TO_WAREHOUSE);
        c.extraJoins.add(JOIN_ITEM_FROM_BIN);
        c.extraJoins.add(JOIN_ITEM_TO_BIN);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("transferNo", "调拨单号", G_DOC, "text", "o.transfer_no", true, "left", true)).filter("text", true);
        // 内部单据无往来单位 → 第 2 位取「调出仓库」（货从这里出去），调入仓库另作可选列
        c.add(new Col("fromStockName", "调出仓库", G_DOC, "text", "fw.stock_name", true, null, true))
                .select("warehouse", "long", "o.from_stock_id", true);
        c.add(new Col("transferDate", "调拨日期", G_DOC, "date", "o.transfer_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("handlerName", "经手人", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.handler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("toStockName", "调入仓库", G_DOC, "text", "tw.stock_name", false, null, false))
                .select("warehouse", "long", "o.to_stock_id", false);
        c.add(new Col("transferType", "调拨类型", G_DOC, "text", "o.transfer_type", false, null, false));
        addDocAmounts(c);
        addCommonDocTail(c, true);

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c);
        addItemAmounts(c);
        c.add(new Col("itemFromStockName", "明细调出仓库", G_ITEM, "text", "ifw.stock_name", false, null, false))
                .select("warehouse", "long", "i.from_stock_id", false);
        c.add(new Col("itemToStockName", "明细调入仓库", G_ITEM, "text", "itw.stock_name", false, null, false))
                .select("warehouse", "long", "i.to_stock_id", false);
        c.add(new Col("fromBinName", "调出储位", G_ITEM, "text", "ifb.bin_name", false, null, false));
        c.add(new Col("toBinName", "调入储位", G_ITEM, "text", "itb.bin_name", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("productionDate", "生产日期", G_ITEM, "date", "i.production_date", false, null, false));
        c.add(new Col("expiryDate", "有效期至", G_ITEM, "date", "i.expiry_date", false, null, false));
        c.add(new Col("itemSourceBillType", "明细来源类型", G_ITEM, "text", "i.source_bill_type", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "discountAmount", "taxAmount", "netAmount"));
        return c;
    }

    // ==================================================================
    // 4. 库存盘点单明细报表（stock_check / stock_check_item）
    //    ⚠ 盘点明细没有税率/折扣/价税合计 → 合计口径改走 book_*/check_*/profit_*/loss_*
    // ==================================================================
    public static BillDetailReportConfig stockCheckConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "stock_check";
        c.itemTable = "stock_check_item";
        c.itemFkColumn = "check_id";
        c.docIdColumn = "check_id";
        c.itemIdColumn = "check_item_id";
        c.cfEntityDoc = "stock_check";
        c.cfEntityItem = "stock_check_item";
        c.docNoExpr = "o.check_no";
        c.dataScopeOwnerColumn = null;              // 实查 StockCheckServiceImpl: applyIfLimited(wrapper, null, "created_by")
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "checkDate";
        c.defaultOrderBy = " ORDER BY o.check_date DESC NULLS LAST, o.check_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_HANDLER);
        c.extraJoins.add(JOIN_CURRENCY);   // 公共尾列里有 cu.currency_code，必须同时 join cu
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_ITEM_BIN);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("checkNo", "盘点单号", G_DOC, "text", "o.check_no", true, "left", true)).filter("text", true);
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", true, null, true))
                .select("warehouse", "long", "o.warehouse_id", true);
        c.add(new Col("checkDate", "盘点日期", G_DOC, "date", "o.check_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("handlerName", "经手人", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.handler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("stockType", "盘点类型", G_DOC, "text", "o.stock_type", false, null, false));
        c.add(new Col("docBookAmount", "账面金额（整单）", G_DOC, "amount", "o.book_amount", false, null, true));
        c.add(new Col("docCheckAmount", "实盘金额（整单）", G_DOC, "amount", "o.check_amount", false, null, true));
        c.add(new Col("profitLossAmount", "盘盈盘亏金额（整单）", G_DOC, "amount", "o.profit_loss_amount", false, null, true));
        addCommonDocTail(c, true);

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c);
        c.add(new Col("bookQuantity", "账面数量", G_ITEM, "number", "i.book_quantity", false, null, true));
        c.add(new Col("bookAmount", "账面金额", G_ITEM, "amount", "i.book_amount", false, null, true));
        c.add(new Col("checkQuantity", "实盘数量", G_ITEM, "number", "i.check_quantity", false, null, true));
        c.add(new Col("checkAmount", "实盘金额", G_ITEM, "amount", "i.check_amount", false, null, true));
        c.add(new Col("profitQuantity", "盘盈数量", G_ITEM, "number", "i.profit_quantity", false, null, true));
        c.add(new Col("profitAmount", "盘盈金额", G_ITEM, "amount", "i.profit_amount", false, null, true));
        c.add(new Col("lossQuantity", "盘亏数量", G_ITEM, "number", "i.loss_quantity", false, null, true));
        c.add(new Col("lossAmount", "盘亏金额", G_ITEM, "amount", "i.loss_amount", false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        c.add(new Col("avgCost", "成本", G_ITEM, "number", "i.avg_cost", false, null, false));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("binName", "储位", G_ITEM, "text", "b.bin_name", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("checkResult", "盘点结果", G_ITEM, "text", "i.check_result", false, null, false));
        c.add(new Col("profitLossReason", "盈亏原因", G_ITEM, "text", "i.profit_loss_reason", false, null, false));
        c.add(new Col("reason", "差异原因", G_ITEM, "text", "i.reason", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        // 盘点单没有 quantity / itemNetAmount → 统计卡只会有「单据张数 / 明细行数」，由引擎按列存在性自动裁剪
        c.itemTotalKeys.addAll(java.util.List.of("bookQuantity", "bookAmount", "checkQuantity", "checkAmount",
                "profitQuantity", "profitAmount", "lossQuantity", "lossAmount"));
        c.docTotalKeys.addAll(java.util.List.of("docBookAmount", "docCheckAmount", "profitLossAmount"));
        return c;
    }

    // ==================================================================
    // 5. 生产领料单明细报表（material_requisition，bill_type='PICK'；同一张表还有退料/补料）
    // ==================================================================
    public static BillDetailReportConfig materialRequisitionConfig() {
        return materialRequisitionVariant("PICK", "领料");
    }

    /**
     * {@code material_requisition} 一张表承载 领料(PICK) / 退料(RETURN) / 补料(SUPPLEMENT) 三种单据。
     * 三者的表结构与列清单**完全一致**，只有「单据类型」和单号/日期/人员/数量的中文叫法不同 ——
     * 故抽成本方法共用一份列清单，避免复制三份近似代码（复制必然随时间跑偏）。
     * 生产模块的退料/补料明细报表直接复用本方法，
     * 见 {@link ProductionBillDetailReportConfigs#materialReturnConfig()} / {@link ProductionBillDetailReportConfigs#materialSupplementConfig()}。
     *
     * <p><b>billType 必须走下方白名单校验</b>：它会被拼进 {@code fixedWhere}（固定条件），
     * 而 fixedWhere 的设计前提是"只允许常量、禁止用户输入"，所以这里必须堵死任意字符串。
     */
    public static BillDetailReportConfig materialRequisitionVariant(String billType, String prefix) {
        if (!java.util.Set.of("PICK", "RETURN", "SUPPLEMENT").contains(billType)) {
            throw new IllegalArgumentException("material_requisition 只支持 PICK/RETURN/SUPPLEMENT 三种单据类型: " + billType);
        }
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "material_requisition";
        c.itemTable = "material_requisition_item";
        c.itemFkColumn = "requisition_id";
        c.docIdColumn = "requisition_id";
        c.itemIdColumn = "requisition_item_id";
        c.cfEntityDoc = "material_requisition";
        c.cfEntityItem = "material_requisition_item";
        c.docNoExpr = "o.requisition_no";
        c.dataScopeOwnerColumn = null;              // 实查 MaterialRequisitionServiceImpl: applyIfLimited(wrapper, null, "created_by")
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "requisitionDate";
        // 关键：一张表承载 领料/退料/补料，必须按类型隔离，否则三种单据会混在同一张报表里
        c.fixedWhere.add("o.bill_type = '" + billType + "'");
        c.defaultOrderBy = " ORDER BY o.requisition_date DESC NULLS LAST, o.requisition_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_REQUISITION_EMPLOYEE);
        c.extraJoins.add(JOIN_DEPT);
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_CURRENCY);   // 公共尾列里有 cu.currency_code，必须同时 join cu
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_ITEM_BIN);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("requisitionNo", prefix + "单号", G_DOC, "text", "o.requisition_no", true, "left", true)).filter("text", true);
        // 内部单据无往来单位 → 第 2 位取「部门」
        c.add(new Col("deptName", "部门", G_DOC, "text", "dp.dept_name", true, null, true))
                .select("department", "long", "o.department_id", true);
        c.add(new Col("requisitionDate", prefix + "日期", G_DOC, "date", "o.requisition_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("employeeName", prefix + "人", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.employee_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("billType", "单据类型", G_DOC, "text", "o.bill_type", false, null, false));
        c.add(new Col("workOrderNo", "生产工单号", G_DOC, "text", "o.work_order_no", false, null, false));
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", false, null, false))
                .select("warehouse", "long", "o.warehouse_id", false);
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        addCommonDocTail(c, true);

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c);
        c.add(new Col("quantity", prefix + "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("issuedQuantity", "已发数量", G_ITEM, "number", "i.issued_quantity", false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("avgCost", "成本", G_ITEM, "number", "i.avg_cost", false, null, false));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("binName", "储位", G_ITEM, "text", "b.bin_name", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("workOrderLineNo", "工单行号", G_ITEM, "int", "i.work_order_line_no", false, null, false));
        c.add(new Col("itemSourceBillType", "明细来源类型", G_ITEM, "text", "i.source_bill_type", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceLineNo", "来源行号", G_ITEM, "int", "i.source_line_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "amount"));
        c.docTotalKeys.add("totalAmount");
        return c;
    }
}
