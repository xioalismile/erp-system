package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 委外模块 5 个单据明细报表的列配置（加工单 / 发料单 / 入库单 / 退料单 / 补料单）。
 *
 * <p><b>列名来源</b>：全部取自各实体类的 {@code @TableName}/{@code @TableField} 映射（逐字实查，非推测）。
 * 委外模块有几个与销售/采购**不一样**的结构点，已在下方逐条标注：
 * <ul>
 *   <li>主表主键是 {@code id}（**不是** order_id/pick_id 等）；明细外键才是 order_id/pick_id/inbound_id/return_id/supplement_id；
 *       故 docIdColumn/itemIdColumn 均为 {@code id}，不可照抄采购模板的 "xxx_id"</li>
 *   <li>明细表**没有** {@code line_no} 行号列（全模块 5 张明细表均无）→ 默认排序改用 {@code i.id ASC}，
 *       且**不得**定义 key=lineNo 的列（引擎 orderBySql 见到该 key 会拼 i.line_no → 列不存在直接报错，
 *       同 ProductionBillDetailReportConfigs 旧结构表的坑）</li>
 *   <li>创建人列是 {@code create_by}（**不是** created_by），已把 dataScopeCreatedByColumn 逐个改掉</li>
 *   <li>状态列 {@code status} 是**数字**（各单据枚举不同，见各 CASE 常量注释）→ **不能**用
 *       select("status","string",…)（拿中文比 integer 会报 SQL 错），照 ProductionBillDetailReportConfigs
 *       旧结构表先例：statusText 用 CASE 转中文做**列显示**，另给 statusCode 原值列；
 *       **筛选**用 select("status","int","o.status",…) 按整数精确比较（2026-10-14 列表页筛选条件自定义起，
 *       列表页明细模式以 f_status 下发数字状态值）</li>
 *   <li>发料/退料/补料明细表是**纯数量单据**，无任何金额/单价列 → 金额类合计用恒 0 表达式列兜底
 *       （保证统计卡依赖的 KEY_ITEM_NET_AMOUNT 必有取数，见 {@link #ZERO_AMOUNT_EXPR}）；
 *       入库明细的金额性质列是 {@code processing_fee}（加工费）</li>
 *   <li>发料/入库/退料/补料明细单位只有 {@code unit} 文本列（无 unit_id，不 JOIN unit_of_measure）；
 *       只有加工单明细带 unit_id</li>
 *   <li>包名是 com.erp.subcontract.returnn（return 是 Java 关键字，多了一个 n）</li>
 * </ul>
 *
 * <p><b>默认筛选口径</b>（5 张一致）：单号 text（默认勾选）/ 日期 dateRange（默认勾选+本月）/
 * 供应商下拉（默认勾选）/ 制单人 text（占"业务员"位，采购发票先例）/ 状态数字精确下拉（不默认勾选；
 * 下拉的选项清单由列表页按各单状态机覆写，引擎侧静态枚举仅作占位）。</p>
 */
public final class SubcontractBillDetailReportConfigs {

    private SubcontractBillDetailReportConfigs() {
    }

    /** 供应商主档（实查 Supplier.java @TableName("supplier")，主键 supplier_id / 名称 supplier_name） */
    private static final String JOIN_SUPPLIER = "LEFT JOIN supplier s ON s.supplier_id = o.supplier_id";
    /** 明细图片列数据源：物料主档图片串（5 张明细表均带 material_id；实查 Material.java） */
    private static final String JOIN_MATERIAL_IMG = "LEFT JOIN material m ON m.material_id = i.material_id";
    /** 明细仓库：委外明细外键列名是 warehouse_id，指向 warehouse 主键 stock_id（实查 Warehouse.java） */
    private static final String JOIN_ITEM_WAREHOUSE = "LEFT JOIN warehouse iw ON iw.stock_id = i.warehouse_id";
    /** 仅加工单明细带 unit_id（实查 SubcontractOrderItem.java） */
    private static final String JOIN_UNIT_ORDER = "LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id";

    /**
     * 发料/退料/补料明细表无任何金额、单价列（纯数量单据）→ 造恒为 0 的表达式金额列，
     * 保证引擎统计卡依赖的 {@link BillDetailReportConfig#KEY_ITEM_NET_AMOUNT} 必有取数（SUM 结果恒 0，符合业务：发料不产生金额）。
     */
    private static final String ZERO_AMOUNT_EXPR = "(COALESCE(i.quantity,0) * 0)";

    // ==================== 数字状态 → 中文（各单据枚举不同，实查 SubcontractHelp.vue / 各 Controller 注释） ====================
    /** 加工单 8 态：0 草稿 / 1 待审核 / 2 已生效 / 3 已下达 / 4 执行中 / 5 已完工 / 6 已结案 / 7 已作废 */
    private static final String ORDER_STATUS_TEXT =
            "CASE o.status WHEN 0 THEN '草稿' WHEN 1 THEN '待审核' WHEN 2 THEN '已生效' WHEN 3 THEN '已下达' "
                    + "WHEN 4 THEN '执行中' WHEN 5 THEN '已完工' WHEN 6 THEN '已结案' WHEN 7 THEN '已作废' ELSE o.status::text END";
    /** 发料单 4 态：0 草稿 / 1 待审核 / 2 已生效 / 3 已出库 */
    private static final String PICK_STATUS_TEXT =
            "CASE o.status WHEN 0 THEN '草稿' WHEN 1 THEN '待审核' WHEN 2 THEN '已生效' WHEN 3 THEN '已出库' ELSE o.status::text END";
    /** 入库单 3 态：0 草稿 / 1 待审核 / 2 已生效 */
    private static final String INBOUND_STATUS_TEXT =
            "CASE o.status WHEN 0 THEN '草稿' WHEN 1 THEN '待审核' WHEN 2 THEN '已生效' ELSE o.status::text END";
    /** 退料单 4 态：0 草稿 / 1 待审核 / 2 已生效 / 3 已入库 */
    private static final String RETURN_STATUS_TEXT =
            "CASE o.status WHEN 0 THEN '草稿' WHEN 1 THEN '待审核' WHEN 2 THEN '已生效' WHEN 3 THEN '已入库' ELSE o.status::text END";
    /** 补料单 4 态：0 草稿 / 1 待审核 / 2 已生效 / 3 已出库（与发料单同枚举） */
    private static final String SUPPLEMENT_STATUS_TEXT = PICK_STATUS_TEXT;

    /**
     * 委外 5 张单据共用的表头尾列（审批/制单/备注/附件等）。
     * 注意创建人列是 create_by（实查各主表实体 @TableField(value = "create_by")）。
     *
     * <p>状态筛选口径（2026-10-14 列表页筛选条件自定义 · 委外模块批次）：
     * 委外 5 单状态列都是<b>数字</b>（各单据枚举见上方 CASE 常量），不能用 select("status","string",…)
     * 拿中文比 integer 列（ProductionBillDetailReportConfigs 旧结构表已踩过）。
     * 这里用 {@code select("status","int","o.status",…)}：列表页明细模式把页面数字状态值
     * 以 f_status 下发后按整数精确比较（coerce 失败的非法值在引擎内忽略）。
     * 列显示仍是 CASE 中文（display 与筛选表达式分离）。</p>
     */
    private static void addCommonDocTail(BillDetailReportConfig c, String statusTextExpr) {
        c.add(new Col("status", "单据状态", G_DOC, "status", statusTextExpr, false, null, true))
                .select("status", "int", "o.status", false);
        c.add(new Col("statusCode", "状态码", G_DOC, "int", "o.status", false, null, false));
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.create_by", true, null, true))
                .filter("text", true);
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.create_time", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
    }

    /** 委外明细共用的"图片 + 商品三件套"（无行号列，图片列放明细组最前，照销售订单先例） */
    private static void addItemMaterialTriple(BillDetailReportConfig c, String unitNameExpr) {
        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        c.add(new Col("materialName", "商品名称", G_ITEM, "text", "i.material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("materialCode", "商品编号", G_ITEM, "text", "i.material_code", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", unitNameExpr, false, null, false));
    }

    // ==================================================================
    // 1. 委外加工单明细报表
    // ==================================================================
    public static BillDetailReportConfig orderConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "subcontract_order";
        c.itemTable = "subcontract_order_item";
        c.itemFkColumn = "order_id";
        c.docIdColumn = "id";            // 委外主表主键是 id（实查 @TableId(value = "id")），不是 order_id
        c.itemIdColumn = "id";
        c.cfEntityDoc = "subcontract_order";
        c.cfEntityItem = "subcontract_order_item";
        c.docNoExpr = "o.order_no";
        // 主表无员工维度字段（实查 SubcontractOrder.java：只有 supplier_id，无 buyer/handler/saler）→ 无业务归属维度
        c.dataScopeOwnerColumn = null;
        c.dataScopeCreatedByColumn = "create_by";   // 实查：委外主表创建人列是 create_by，不是 created_by
        c.docDateFilterKey = "orderDate";
        // 明细无 line_no → 按 i.id 排（等同插入顺序）
        c.defaultOrderBy = " ORDER BY o.order_date DESC NULLS LAST, o.order_no DESC NULLS LAST, i.id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add(JOIN_UNIT_ORDER);
        c.extraJoins.add(JOIN_MATERIAL_IMG);

        c.add(new Col("orderNo", "委外单号", G_DOC, "text", "o.order_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("orderDate", "委外日期", G_DOC, "date", "o.order_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("processItem", "委外工序号", G_DOC, "text", "o.process_item", false, null, false));
        // 表头物料/单位（编辑页「物料信息」区）；key 加 doc 前缀避免与明细列冲突
        c.add(new Col("docMaterialName", "委外物料", G_DOC, "text", "o.material_name", false, null, false));
        c.add(new Col("docMaterialCode", "委外物料编号", G_DOC, "text", "o.material_code", false, null, false));
        c.add(new Col("docUnitName", "单位（整单）", G_DOC, "text", "o.unit_name", false, null, false));
        c.add(new Col("totalQuantity", "委外数量", G_DOC, "number", "o.total_quantity", false, null, true));
        c.add(new Col("finishedQuantity", "已完工数量", G_DOC, "number", "o.finished_quantity", false, null, true));
        c.add(new Col("docUnitPrice", "加工单价（整单）", G_DOC, "number", "o.unit_price", false, null, true));
        c.add(new Col("totalAmount", "加工费总额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("docTaxRate", "税率%（整单）", G_DOC, "number", "o.tax_rate", false, null, true));
        c.add(new Col("taxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("netAmount", "价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true));
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "o.currency_code", false, null, false));
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true));
        c.add(new Col("baseTotalAmount", "本位币加工费", G_DOC, "amount", "o.base_total_amount", false, null, true));
        c.add(new Col("baseTaxAmount", "本位币税额", G_DOC, "amount", "o.base_tax_amount", false, null, true));
        c.add(new Col("baseNetAmount", "本位币价税合计", G_DOC, "amount", "o.base_net_amount", false, null, true));
        c.add(new Col("expectDate", "计划开工日期", G_DOC, "date", "o.expect_date", false, null, true));
        c.add(new Col("actualDate", "计划完工日期", G_DOC, "date", "o.actual_date", false, null, true));
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        addCommonDocTail(c, ORDER_STATUS_TEXT);

        addItemMaterialTriple(c, "COALESCE(NULLIF(i.unit, ''), u.unit_name)");
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("finishedQty", "已完工数量（明细）", G_ITEM, "number", "i.finished_qty", false, null, true));
        c.add(new Col("actualQty", "实发数量", G_ITEM, "number", "i.actual_qty", false, null, true));
        c.add(new Col("supplyType", "供应方式", G_ITEM, "text", "i.supply_type", false, null, false));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        c.add(new Col("priceWithTax", "含税单价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税单价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("itemTaxAmount", "明细税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", "i.net_amount", false, null, true));
        c.add(new Col("detailCloseStatus", "明细结案状态", G_ITEM, "int", "i.detail_close_status", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceBillType", "明细来源类型", G_ITEM, "text", "i.source_bill_type", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "finishedQty", "actualQty", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalQuantity", "finishedQuantity", "totalAmount", "taxAmount", "netAmount"));
        return c;
    }

    // ==================================================================
    // 2. 委外发料单明细报表
    // ==================================================================
    public static BillDetailReportConfig pickConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "subcontract_pick";
        c.itemTable = "subcontract_pick_item";
        c.itemFkColumn = "pick_id";
        c.docIdColumn = "id";
        c.itemIdColumn = "id";
        c.cfEntityDoc = "subcontract_pick";
        c.cfEntityItem = "subcontract_pick_item";
        c.docNoExpr = "o.pick_no";
        c.dataScopeOwnerColumn = null;              // 主表无员工维度字段（实查 SubcontractPick.java）
        c.dataScopeCreatedByColumn = "create_by";
        c.docDateFilterKey = "pickDate";
        c.defaultOrderBy = " ORDER BY o.pick_date DESC NULLS LAST, o.pick_no DESC NULLS LAST, i.id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_MATERIAL_IMG);

        c.add(new Col("pickNo", "领料单号", G_DOC, "text", "o.pick_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("pickDate", "领料日期", G_DOC, "date", "o.pick_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("orderNo", "委外单号", G_DOC, "text", "o.order_no", false, null, false));
        c.add(new Col("totalQuantity", "总数量（整单）", G_DOC, "number", "o.total_quantity", false, null, true));
        addCommonDocTail(c, PICK_STATUS_TEXT);

        // 发料明细无 unit_id → 单位直接取 i.unit 文本（实查 SubcontractPickItem.java）
        addItemMaterialTriple(c, "i.unit");
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text",
                "COALESCE(NULLIF(i.warehouse_name, ''), iw.stock_name)", false, null, false))
                .select("warehouse", "long", "i.warehouse_id", false);
        // 纯数量单据（明细无金额/单价列）→ 恒 0 表达式兜底（见 ZERO_AMOUNT_EXPR 注释）
        c.add(new Col("amount", "金额", G_ITEM, "amount", ZERO_AMOUNT_EXPR, false, null, false));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", ZERO_AMOUNT_EXPR, false, null, true));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceBillType", "明细来源类型", G_ITEM, "text", "i.source_bill_type", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemNetAmount"));
        c.docTotalKeys.add("totalQuantity");
        return c;
    }

    // ==================================================================
    // 3. 委外入库单明细报表
    // ==================================================================
    public static BillDetailReportConfig inboundConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "subcontract_inbound";
        c.itemTable = "subcontract_inbound_item";
        c.itemFkColumn = "inbound_id";
        c.docIdColumn = "id";
        c.itemIdColumn = "id";
        c.cfEntityDoc = "subcontract_inbound";
        c.cfEntityItem = "subcontract_inbound_item";
        c.docNoExpr = "o.inbound_no";
        c.dataScopeOwnerColumn = null;              // 主表无员工维度字段（实查 SubcontractInbound.java）
        c.dataScopeCreatedByColumn = "create_by";
        c.docDateFilterKey = "inboundDate";
        c.defaultOrderBy = " ORDER BY o.inbound_date DESC NULLS LAST, o.inbound_no DESC NULLS LAST, i.id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_MATERIAL_IMG);

        c.add(new Col("inboundNo", "入库单号", G_DOC, "text", "o.inbound_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("inboundDate", "入库日期", G_DOC, "date", "o.inbound_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("orderNo", "委外单号", G_DOC, "text", "o.order_no", false, null, false));
        c.add(new Col("totalQuantity", "总数量（整单）", G_DOC, "number", "o.total_quantity", false, null, true));
        c.add(new Col("processingFee", "加工费（整单）", G_DOC, "amount", "o.processing_fee", false, null, true));
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        addCommonDocTail(c, INBOUND_STATUS_TEXT);

        addItemMaterialTriple(c, "i.unit");
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text",
                "COALESCE(NULLIF(i.warehouse_name, ''), iw.stock_name)", false, null, false))
                .select("warehouse", "long", "i.warehouse_id", false);
        // 明细金额性质列是 processing_fee（实查 SubcontractInboundItem.java，编辑页标签「加工费」）
        c.add(new Col("itemProcessingFee", "明细加工费", G_ITEM, "amount", "i.processing_fee", false, null, true));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "COALESCE(i.processing_fee,0)", false, null, false));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", "COALESCE(i.processing_fee,0)", false, null, true));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceBillType", "明细来源类型", G_ITEM, "text", "i.source_bill_type", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemProcessingFee", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalQuantity", "processingFee"));
        return c;
    }

    // ==================================================================
    // 4. 委外退料单明细报表（注意包名 com.erp.subcontract.returnn）
    // ==================================================================
    public static BillDetailReportConfig returnConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "subcontract_return";
        c.itemTable = "subcontract_return_item";
        c.itemFkColumn = "return_id";
        c.docIdColumn = "id";
        c.itemIdColumn = "id";
        c.cfEntityDoc = "subcontract_return";
        c.cfEntityItem = "subcontract_return_item";
        c.docNoExpr = "o.return_no";
        c.dataScopeOwnerColumn = null;              // 主表无员工维度字段（实查 SubcontractReturn.java）
        c.dataScopeCreatedByColumn = "create_by";
        c.docDateFilterKey = "returnDate";
        c.defaultOrderBy = " ORDER BY o.return_date DESC NULLS LAST, o.return_no DESC NULLS LAST, i.id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_MATERIAL_IMG);

        c.add(new Col("returnNo", "退料单号", G_DOC, "text", "o.return_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("returnDate", "退料日期", G_DOC, "date", "o.return_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("orderNo", "委外单号", G_DOC, "text", "o.order_no", false, null, false));
        c.add(new Col("pickNo", "来源领料单号", G_DOC, "text", "o.pick_no", false, null, false));
        c.add(new Col("totalQuantity", "总数量（整单）", G_DOC, "number", "o.total_quantity", false, null, true));
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        addCommonDocTail(c, RETURN_STATUS_TEXT);

        addItemMaterialTriple(c, "i.unit");
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text",
                "COALESCE(NULLIF(i.warehouse_name, ''), iw.stock_name)", false, null, false))
                .select("warehouse", "long", "i.warehouse_id", false);
        c.add(new Col("amount", "金额", G_ITEM, "amount", ZERO_AMOUNT_EXPR, false, null, false));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", ZERO_AMOUNT_EXPR, false, null, true));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceBillType", "明细来源类型", G_ITEM, "text", "i.source_bill_type", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemNetAmount"));
        c.docTotalKeys.add("totalQuantity");
        return c;
    }

    // ==================================================================
    // 5. 委外补料单明细报表
    // ==================================================================
    public static BillDetailReportConfig supplementConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "subcontract_supplement";
        c.itemTable = "subcontract_supplement_item";
        c.itemFkColumn = "supplement_id";
        c.docIdColumn = "id";
        c.itemIdColumn = "id";
        c.cfEntityDoc = "subcontract_supplement";
        c.cfEntityItem = "subcontract_supplement_item";
        c.docNoExpr = "o.supplement_no";
        c.dataScopeOwnerColumn = null;              // 主表无员工维度字段（实查 SubcontractSupplement.java）
        c.dataScopeCreatedByColumn = "create_by";
        c.docDateFilterKey = "supplementDate";
        c.defaultOrderBy = " ORDER BY o.supplement_date DESC NULLS LAST, o.supplement_no DESC NULLS LAST, i.id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_MATERIAL_IMG);

        c.add(new Col("supplementNo", "补料单号", G_DOC, "text", "o.supplement_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("supplementDate", "补料日期", G_DOC, "date", "o.supplement_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("orderNo", "委外单号", G_DOC, "text", "o.order_no", false, null, false));
        c.add(new Col("pickNo", "来源领料单号", G_DOC, "text", "o.pick_no", false, null, false));
        c.add(new Col("totalQuantity", "总数量（整单）", G_DOC, "number", "o.total_quantity", false, null, true));
        c.add(new Col("reason", "补料原因", G_DOC, "text", "o.reason", false, null, false));
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        addCommonDocTail(c, SUPPLEMENT_STATUS_TEXT);

        addItemMaterialTriple(c, "i.unit");
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text",
                "COALESCE(NULLIF(i.warehouse_name, ''), iw.stock_name)", false, null, false))
                .select("warehouse", "long", "i.warehouse_id", false);
        c.add(new Col("amount", "金额", G_ITEM, "amount", ZERO_AMOUNT_EXPR, false, null, false));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", ZERO_AMOUNT_EXPR, false, null, true));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceBillType", "明细来源类型", G_ITEM, "text", "i.source_bill_type", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemNetAmount"));
        c.docTotalKeys.add("totalQuantity");
        return c;
    }
}
