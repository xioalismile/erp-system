package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 生产模块第二批 4 个单据明细报表的列配置（列表页「明细模式」取数用）：
 * 组装单 / 拆卸单 / 工序汇报单 / MRP 运算计划。
 *
 * <p><b>列名来源</b>：实体 {@code @TableField} 注解 + 库内 DDL 逐表实查，非推测——
 * 组装/拆卸见 {@code db/migration/13_produce_assemble_disassemble.sql}（创建人列是 creator、创建/修改时间是
 * ctime/utime，<b>没有</b> created_at 族列）、{@code db/21_bill_status_unify.sql}（结案/作废列）、
 * {@code db/90_*approval_status.sql}、{@code db/140_header_tax_calc_method.sql}、{@code db/139_item_price_tax_columns.sql}；
 * 工序汇报见 {@code database/schema/05_production.sql}、{@code 08_field_fix.sql}、
 * {@code 11_production_module_fix.sql}；
 * MRP 见 {@code schema/mrp_schema.sql} + {@code erp_update_20260718_mrp_linkage.sql}。
 *
 * <p><b>双层子表判定</b>（组装/拆卸）：编辑页有两张明细卡 ——
 * 「成品明细」({@code produce_assemble_detail} / {@code produce_disassemble_detail}，单据金额 = 成品金额合计，
 * 为明细主口径，本配置取这层) 与「BOM原料明细」({@code *_bom}，一期已有独立的用料明细报表)。
 *
 * <p><b>各单据要点</b>：
 * <ul>
 *   <li>组装/拆卸：旧结构表 —— 状态是数字（0 草稿/1 待审核/2 已生效/7 已作废）→ 用 CASE 转中文列，
 *       <b>绝不能</b>对它用 {@code select("status",…)}；明细商品字段是 {@code product_code}/{@code product_name}；
 *       <b>没有 {@code line_no}</b>，行号用 {@code row_id} 且 key 不得叫 lineNo
 *       （引擎排序见到 key=lineNo 就拼 {@code i.line_no}，该列不存在会直接炸）；行级数据权限列是 {@code creator}。</li>
 *   <li>工序汇报单：<b>无子表</b>（{@code itemTable} 保持 null，一行 = 一张单，全部列放单据信息组，不造商品明细组）；
 *       状态是中文文本，可正常 select；行级数据权限列 {@code created_by}。</li>
 *   <li>MRP 运算计划：计划状态是数字（0 草稿/1 待审核/2 已生效/3 已执行/7 已作废，与后端状态文案映射逐字一致）
 *       → 用 CASE 转中文；创建人列是 {@code create_by}（行级数据权限同口径）；明细物料列 {@code material_id}
 *       → JOIN 物料主档出图片列；明细<b>没有 {@code line_no}</b>，行号用 {@code id} 且 key 不得叫 lineNo。</li>
 * </ul>
 *
 * <p><b>中文名口径</b>：以各编辑页标签为准（组装/拆卸 = 物料编码/物料名称/规格型号/主单位数量…；
 * MRP = 方案编号/方案名称/运算日期/毛需求/净需求…；工序汇报 = 汇报单号/汇报日期/报工数量…）。
 */
public final class ProductionExtraBillDetailReportConfigs {

    private ProductionExtraBillDetailReportConfigs() {
    }

    // ==================== JOIN 片段（全部按实体字段实查后拼装） ====================
    private static final String JOIN_CURRENCY = "LEFT JOIN currency cu ON cu.currency_id = o.currency_id";
    private static final String JOIN_UNIT = "LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id";
    /** 旧结构单据头仓库（列名 wh_id / wh_id_bom，warehouse 主键是 stock_id） */
    private static final String JOIN_ASM_DOC_WAREHOUSE = "LEFT JOIN warehouse w ON w.stock_id = o.wh_id";
    private static final String JOIN_ASM_ITEM_WAREHOUSE = "LEFT JOIN warehouse iw ON iw.stock_id = i.wh_id";
    private static final String JOIN_ASM_ITEM_STORAGE = "LEFT JOIN location b ON b.bin_id = i.storage_id";
    /** 组装/拆卸明细的物料列叫 product_id（值即物料主键 material_id，编辑页选物料时回填）→ 物料主档出图片 */
    private static final String JOIN_ASM_ITEM_MATERIAL = "LEFT JOIN material m ON m.material_id = i.product_id";
    /** MRP 明细物料 → 物料主档出图片 */
    private static final String JOIN_MRP_ITEM_MATERIAL = "LEFT JOIN material m ON m.material_id = i.material_id";

    /** 旧结构单据状态（数字 → 中文；取值口径与前端组装/拆卸列表页状态映射逐字一致，与一期相同） */
    private static final String ASM_STATUS_TEXT =
            "CASE o.status WHEN 0 THEN '草稿' WHEN 1 THEN '待审核' WHEN 2 THEN '已生效' WHEN 7 THEN '已作废' "
                    + "ELSE o.status::text END";

    /** MRP 计划状态（数字 → 中文；与后端 planStatusText 映射逐字一致：0草稿/1待审核/2已生效/3已执行/7已作废） */
    private static final String MRP_STATUS_TEXT =
            "CASE o.plan_status WHEN 0 THEN '草稿' WHEN 1 THEN '待审核' WHEN 2 THEN '已生效' WHEN 3 THEN '已执行' WHEN 7 THEN '已作废' "
                    + "ELSE o.plan_status::text END";

    // ==================================================================
    // 1~2. 组装单 / 拆卸单明细报表（旧结构表；编辑页明细主口径 = 成品明细层）
    // ==================================================================

    /** 旧结构单据头列（组装/拆卸表结构一致，共用；列名全部实查 13 号迁移 + 21/90/139/140 号补列） */
    private static void addExtraAssembleDocColumns(BillDetailReportConfig c) {
        c.add(new Col("serialNo", "单据编号", G_DOC, "text", "o.serial_no", true, "left", true)).filter("text", true);
        c.add(new Col("whName", "成品仓库", G_DOC, "text", "COALESCE(NULLIF(o.wh_name, ''), w.stock_name)", true, null, true))
                .select("warehouse", "long", "o.wh_id", true);
        c.add(new Col("whNameBom", "原料仓库", G_DOC, "text", "o.wh_name_bom", true, null, true))
                .select("warehouse", "long", "o.wh_id_bom", false);
        c.add(new Col("dateline", "单据日期", G_DOC, "date", "(o.dateline)::date", true, null, true)).filter("dateRange", true);
        c.add(new Col("dept", "生产部门", G_DOC, "text", "o.dept", true, null, false));
        // 旧结构表创建人列是 creator（编辑页「单据追溯」里叫创建人）
        c.add(new Col("createdBy", "创建人", G_DOC, "text", "o.creator", true, null, true)).filter("text", true);
        // 状态是数字 → 筛选用「CASE 转中文后的表达式」做等值（2026-10-14 筛选条件自定义：text 模糊升级为下拉，
        // 选项 = 引擎 status 静态枚举中文；filterExpr 不能写裸 integer 列 o.status——中文比 integer 列会报 SQL 错）
        c.add(new Col("statusText", "单据状态", G_DOC, "status", ASM_STATUS_TEXT, false, null, true))
                .select("status", "string", ASM_STATUS_TEXT, false);
        c.add(new Col("statusCode", "状态码", G_DOC, "int", "o.status", false, null, false));
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("isTax", "是否含税", G_DOC, "text", "o.is_tax", false, null, false));
        c.add(new Col("taxType", "税种", G_DOC, "int", "o.tax_type", false, null, false));
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("currencyRate", "汇率", G_DOC, "number", "o.currency_rate", false, null, true));
        c.add(new Col("docAmount", "单据金额（整单）", G_DOC, "amount", "o.amount", false, null, true));
        c.add(new Col("docBaseAmount", "本位币金额（整单）", G_DOC, "amount", "o.base_amount", false, null, true));
        c.add(new Col("docTaxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("docTaxRate", "税率%（整单）", G_DOC, "number", "o.tax_rate", false, null, true));
        c.add(new Col("salesmanStr", "业务员", G_DOC, "text", "o.salesman_str", false, null, false));
        c.add(new Col("flowStatusName", "流程状态", G_DOC, "text", "o.flow_status_name", false, null, false));
        c.add(new Col("flowOperator", "流程操作人", G_DOC, "text", "o.flow_operator", false, null, false));
        c.add(new Col("sourceType", "来源类型", G_DOC, "text", "o.source_type", false, null, false));
        c.add(new Col("printNum", "打印次数", G_DOC, "int", "o.print_num", false, null, false));
        c.add(new Col("notes", "单据备注", G_DOC, "text", "o.notes", false, null, false));
        c.add(new Col("images", "图片", G_DOC, "text", "o.images", false, null, false));
        c.add(new Col("files", "附件", G_DOC, "text", "o.files", false, null, false));
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("closedBy", "结案人", G_DOC, "text", "o.closed_by", false, null, false));
        c.add(new Col("closedAt", "结案时间", G_DOC, "datetime", "o.closed_at", false, null, false));
        c.add(new Col("closedReason", "结案原因", G_DOC, "text", "o.closed_reason", false, null, false));
        c.add(new Col("billBeforeStatus", "作废前状态", G_DOC, "text", "o.bill_before_status", false, null, false));
        c.add(new Col("voidedBy", "作废人", G_DOC, "text", "o.voided_by", false, null, false));
        c.add(new Col("voidedAt", "作废时间", G_DOC, "datetime", "o.voided_at", false, null, false));
        c.add(new Col("voidReason", "作废原因", G_DOC, "text", "o.void_reason", false, null, false));
        // 旧结构表的时间/修改人列是 ctime / modifier / utime（编辑页「单据追溯」四件套之一）
        c.add(new Col("ctime", "创建时间", G_DOC, "datetime", "o.ctime", false, null, true));
        c.add(new Col("modifier", "修改人", G_DOC, "text", "o.modifier", false, null, false));
        c.add(new Col("utime", "修改时间", G_DOC, "datetime", "o.utime", false, null, false));
    }

    /**
     * 旧结构明细列（编辑页「成品明细」卡片的列清单）。
     *
     * <p>⚠ 两个坑：① 商品字段是 {@code product_code}/{@code product_name}；
     * ② <b>没有 {@code line_no}</b>，行号列用 {@code row_id}，且 key 必须<b>不叫 lineNo</b>
     * （引擎排序见到 key=lineNo 会拼 {@code i.line_no} → 列不存在直接报错）。
     */
    private static void addExtraAssembleItemColumns(BillDetailReportConfig c) {
        c.add(new Col("rowNo", "行号", G_ITEM, "int", "i.row_id", false, null, true));
        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        c.add(new Col("materialName", "物料名称", G_ITEM, "text", "i.product_name", true, null, true))
                .filter("text", true);
        c.add(new Col("materialCode", "物料编码", G_ITEM, "text", "i.product_code", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格型号", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", "COALESCE(NULLIF(i.unit, ''), u.unit_name)", false, null, false));
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("basicQuantity", "主单位数量", G_ITEM, "number", "i.basic_quantity", false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.price", false, null, true));
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("itemTaxAmount", "税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("itemWhName", "仓库", G_ITEM, "text", "COALESCE(NULLIF(i.wh_name, ''), iw.stock_name)", false, null, false));
        c.add(new Col("binName", "储位", G_ITEM, "text", "COALESCE(NULLIF(i.storage_name, ''), b.bin_name)", false, null, false));
        c.add(new Col("basicUnit", "基本单位", G_ITEM, "text", "i.basic_unit", false, null, false));
        c.add(new Col("sortOrder", "排序号", G_ITEM, "int", "i.sort_order", false, null, false));
        c.add(new Col("itemSourceType", "明细来源类型", G_ITEM, "text", "i.source_type", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_serial_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.notes", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "basicQuantity", "amount", "itemTaxAmount"));
        c.docTotalKeys.addAll(java.util.List.of("docAmount", "docBaseAmount", "docTaxAmount"));
    }

    /** 组装 / 拆卸两份报表的公共骨架（主表 + 明细表只有表名/外键不同，其余完全一致） */
    private static BillDetailReportConfig assembleExtraFamily(String mainTable, String itemFk,
                                                              String itemTable, String cfDoc, String cfItem) {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = mainTable;
        c.itemTable = itemTable;
        c.itemFkColumn = itemFk;
        c.docIdColumn = "id";                    // ⚠ 旧结构主表主键就叫 id
        c.itemIdColumn = "id";                   // ⚠ 明细主键也叫 id
        c.cfEntityDoc = cfDoc;
        c.cfEntityItem = cfItem;
        c.docNoExpr = "o.serial_no";
        c.dataScopeOwnerColumn = null;           // 实查两单据 ServiceImpl：applyIfLimited(wrapper, null, "creator")
        c.dataScopeCreatedByColumn = "creator";  // ⚠ 旧结构表创建人列叫 creator，不是 created_by
        c.docDateFilterKey = "dateline";
        // ⚠ 不能写成 i.line_no（该表没有 line_no 列），用 row_id
        c.defaultOrderBy = " ORDER BY o.dateline DESC NULLS LAST, o.serial_no DESC NULLS LAST, i.row_id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_ASM_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add(JOIN_ASM_ITEM_MATERIAL);
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add(JOIN_ASM_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_ASM_ITEM_STORAGE);

        addExtraAssembleDocColumns(c);
        addExtraAssembleItemColumns(c);
        return c;
    }

    /** 1. 组装单明细报表（明细口径 = 编辑页「成品明细（入库）」层，单据金额即成品金额合计） */
    public static BillDetailReportConfig assembleConfig() {
        return assembleExtraFamily("produce_assemble", "assemble_id", "produce_assemble_detail",
                "produce_assemble", "produce_assemble_detail");
    }

    /** 2. 拆卸单明细报表（明细口径 = 编辑页「成品明细（出库）」层） */
    public static BillDetailReportConfig disassembleConfig() {
        return assembleExtraFamily("produce_disassemble", "disassemble_id", "produce_disassemble_detail",
                "produce_disassemble", "produce_disassemble_detail");
    }

    // ==================================================================
    // 3. 工序汇报单明细报表（operation_report；无子表：一行 = 一张单）
    // ==================================================================

    /**
     * 工序汇报单无子表（编辑页整页都是表头字段，库里虽有 operation_report_item 但业务不落子表行），
     * {@code itemTable} 保持 null，引擎自动走「一行一单」模式；全部列放单据信息组，不造商品明细组。
     */
    public static BillDetailReportConfig operationReportConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "operation_report";
        c.itemTable = null;                      // ⚠ 无子表：明细模式 = 一行一单
        c.docIdColumn = "report_id";
        c.cfEntityDoc = "operation_report";
        c.cfEntityItem = null;
        c.docNoExpr = "o.report_no";
        c.dataScopeOwnerColumn = null;           // 实查 OperationReportServiceImpl: applyIfLimited(wrapper, null, "created_by")
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "reportDate";
        // ⚠ 无子表：默认排序不得引用 i 别名
        c.defaultOrderBy = " ORDER BY o.report_date DESC NULLS LAST, o.report_no DESC NULLS LAST ";

        c.add(new Col("reportNo", "汇报单号", G_DOC, "text", "o.report_no", true, "left", true)).filter("text", true);
        c.add(new Col("reportDate", "汇报日期", G_DOC, "date", "o.report_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", true, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("productionOrderNo", "工单号", G_DOC, "text", "o.production_order_no", true, null, true))
                .filter("text", false);
        c.add(new Col("processNo", "工序号", G_DOC, "text", "o.process_no", true, null, false)).filter("text", false);
        c.add(new Col("operationName", "工序名称", G_DOC, "text", "o.operation_name", true, null, false));
        c.add(new Col("workCenterName", "工作中心", G_DOC, "text", "o.work_center_name", true, null, false));
        c.add(new Col("employeeName", "操作工", G_DOC, "text", "o.employee_name", true, null, true))
                .select("employee", "long", "o.employee_id", false);
        c.add(new Col("reportType", "汇报类型", G_DOC, "text", "o.report_type", false, null, false));
        // 统计卡口径：key=quantity 营业上取「报工数量」（总数量），引擎统计卡与合计行自动对上
        c.add(new Col("quantity", "报工数量", G_DOC, "number", "o.total_quantity", false, null, true));
        c.add(new Col("qualifiedQuantity", "合格数量", G_DOC, "number", "o.qualified_quantity", false, null, true));
        c.add(new Col("rejectedQuantity", "报废数量", G_DOC, "number", "o.rejected_quantity", false, null, true));
        c.add(new Col("workHours", "实际工时", G_DOC, "number", "o.work_hours", false, null, true));
        c.add(new Col("overtimeHours", "加班工时", G_DOC, "number", "o.overtime_hours", false, null, false));
        c.add(new Col("shiftType", "班次", G_DOC, "text", "o.shift_type", false, null, false));
        c.add(new Col("equipmentId", "使用设备", G_DOC, "text", "o.equipment_id", false, null, false));
        c.add(new Col("remark", "备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("sourceBillNo", "来源单据号", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("closedBy", "结案人", G_DOC, "text", "o.closed_by", false, null, false));
        c.add(new Col("closedAt", "结案时间", G_DOC, "datetime", "o.closed_at", false, null, false));
        c.add(new Col("closedReason", "结案原因", G_DOC, "text", "o.closed_reason", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("updatedBy", "修改人", G_DOC, "text", "o.updated_by", false, null, false));
        c.add(new Col("updatedAt", "修改时间", G_DOC, "datetime", "o.updated_at", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));

        // 无子表：明细级合计留空（引擎按单统计），单据级只放数量/工时类
        c.docTotalKeys.addAll(java.util.List.of("quantity", "qualifiedQuantity", "rejectedQuantity", "workHours"));
        return c;
    }

    // ==================================================================
    // 4. MRP 运算计划明细报表（mrp_plan / mrp_plan_item）
    // ==================================================================

    /** MRP 计划单据头列（编辑页标签：方案编号/方案名称/运算日期/需求来源/计划展望期(天)/时界(天)…） */
    private static void addMrpPlanDocColumns(BillDetailReportConfig c) {
        c.add(new Col("planNo", "方案编号", G_DOC, "text", "o.plan_no", true, "left", true)).filter("text", true);
        c.add(new Col("planName", "方案名称", G_DOC, "text", "o.plan_name", true, null, true)).filter("text", false);
        c.add(new Col("planDate", "运算日期", G_DOC, "date", "o.plan_date", true, null, true)).filter("dateRange", true);
        // 计划状态是数字 → 筛选用「CASE 转中文后的表达式」做等值（2026-10-14 筛选条件自定义：text 模糊升级为下拉，
        // 选项 = 引擎 status 静态枚举中文；filterExpr 不能写裸 integer 列 o.plan_status——中文比 integer 列会报 SQL 错）
        c.add(new Col("statusText", "单据状态", G_DOC, "status", MRP_STATUS_TEXT, false, null, true))
                .select("status", "string", MRP_STATUS_TEXT, false);
        c.add(new Col("planStatusCode", "计划状态码", G_DOC, "int", "o.plan_status", false, null, false));
        c.add(new Col("demandSource", "需求来源", G_DOC, "text", "o.demand_source", true, null, false))
                .filter("text", false);
        c.add(new Col("planHorizon", "计划展望期(天)", G_DOC, "int", "o.plan_horizon", false, null, false));
        c.add(new Col("timeFenceDays", "时界(天)", G_DOC, "int", "o.time_fence_days", false, null, false));
        c.add(new Col("totalNetDemand", "净需求总量（整单）", G_DOC, "number", "o.total_net_demand", false, null, true));
        c.add(new Col("totalPurchaseSuggestion", "采购建议总量（整单）", G_DOC, "number", "o.total_purchase_suggestion", false, null, true));
        c.add(new Col("totalProduceSuggestion", "生产建议总量（整单）", G_DOC, "number", "o.total_produce_suggestion", false, null, true));
        c.add(new Col("lotSizeType", "批量规则", G_DOC, "text", "o.lot_size_type", false, null, false));
        c.add(new Col("lotSize", "固定批量", G_DOC, "number", "o.lot_size", false, null, true));
        c.add(new Col("minOrderQty", "最小订货量", G_DOC, "number", "o.min_order_qty", false, null, true));
        c.add(new Col("includeSo", "包含销售订单", G_DOC, "int", "o.include_so", false, null, false));
        c.add(new Col("includeForecast", "包含预测", G_DOC, "int", "o.include_forecast", false, null, false));
        c.add(new Col("includeSafetyStock", "考虑安全库存", G_DOC, "int", "o.include_safety_stock", false, null, false));
        c.add(new Col("includeOpenPo", "考虑在途采购", G_DOC, "int", "o.include_open_po", false, null, false));
        c.add(new Col("includeOpenMo", "考虑在制工单", G_DOC, "int", "o.include_open_mo", false, null, false));
        c.add(new Col("includeOnhandStock", "考虑现有库存", G_DOC, "int", "o.include_onhand_stock", false, null, false));
        c.add(new Col("remark", "备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("closedBy", "结案人", G_DOC, "text", "o.closed_by", false, null, false));
        c.add(new Col("closedAt", "结案时间", G_DOC, "datetime", "o.closed_at", false, null, false));
        c.add(new Col("closedReason", "结案原因", G_DOC, "text", "o.closed_reason", false, null, false));
        c.add(new Col("billBeforeStatus", "作废前状态", G_DOC, "int", "o.bill_before_status", false, null, false));
        c.add(new Col("voidedBy", "作废人", G_DOC, "text", "o.voided_by", false, null, false));
        c.add(new Col("voidedAt", "作废时间", G_DOC, "datetime", "o.voided_at", false, null, false));
        c.add(new Col("voidReason", "作废原因", G_DOC, "text", "o.void_reason", false, null, false));
        // mrp_plan 的创建人/时间列是 create_by / create_time / update_time（无 updated_by 列，实查实体与 DDL）
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.create_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.create_time", false, null, true));
        c.add(new Col("updatedAt", "修改时间", G_DOC, "datetime", "o.update_time", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
    }

    /** MRP 计划明细列（编辑页明细行：物料编码/物料名称/规格/毛需求/可用库存/安全库存/净需求/计划数量/建议类型/建议日期） */
    private static void addMrpPlanItemColumns(BillDetailReportConfig c) {
        // ⚠ mrp_plan_item 没有 line_no 列：行号用 i.id，且 key 必须**不叫 lineNo**
        c.add(new Col("rowNo", "行号", G_ITEM, "int", "i.id", false, null, true));
        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        c.add(new Col("materialName", "物料名称", G_ITEM, "text", "i.material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("materialCode", "物料编码", G_ITEM, "text", "i.material_code", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", "i.unit", false, null, false));
        // 统计卡口径：key=quantity 取明细「计划数量」
        c.add(new Col("quantity", "计划数量", G_ITEM, "number", "i.plan_quantity", false, null, true));
        c.add(new Col("grossDemand", "毛需求", G_ITEM, "number", "i.gross_demand", false, null, true));
        c.add(new Col("availableStock", "可用库存", G_ITEM, "number", "i.available_stock", false, null, true));
        c.add(new Col("safetyStock", "安全库存", G_ITEM, "number", "i.safety_stock", false, null, true));
        c.add(new Col("netDemand", "净需求", G_ITEM, "number", "i.net_demand", false, null, true));
        c.add(new Col("suggestType", "建议类型", G_ITEM, "text", "i.suggest_type", false, null, false));
        c.add(new Col("leadTime", "提前期(天)", G_ITEM, "int", "i.lead_time", false, null, false));
        c.add(new Col("suggestDate", "建议日期", G_ITEM, "date", "i.suggest_date", false, null, false));
        c.add(new Col("saleOrderNo", "来源销售订单号", G_ITEM, "text", "i.sale_order_no", false, null, false));
        c.add(new Col("level", "层级", G_ITEM, "int", "i.level", false, null, false));
        c.add(new Col("itemRemark", "备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "grossDemand", "netDemand"));
        c.docTotalKeys.addAll(java.util.List.of("totalNetDemand", "totalPurchaseSuggestion", "totalProduceSuggestion"));
    }

    /** 5. MRP 运算计划明细报表 */
    public static BillDetailReportConfig mrpPlanConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "mrp_plan";
        c.itemTable = "mrp_plan_item";
        c.itemFkColumn = "plan_id";
        c.docIdColumn = "id";
        c.itemIdColumn = "id";
        c.cfEntityDoc = "mrp_plan";
        c.cfEntityItem = "mrp_plan_item";
        c.docNoExpr = "o.plan_no";
        c.dataScopeOwnerColumn = null;           // 实查 MrpServiceImpl: applyIfLimited(wrapper, null, "create_by")
        c.dataScopeCreatedByColumn = "create_by"; // ⚠ mrp_plan 创建人列叫 create_by，不是 created_by
        c.docDateFilterKey = "planDate";
        // ⚠ mrp_plan_item 没有 line_no 列，用 i.id 排序
        c.defaultOrderBy = " ORDER BY o.plan_date DESC NULLS LAST, o.plan_no DESC NULLS LAST, i.id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_MRP_ITEM_MATERIAL);

        addMrpPlanDocColumns(c);
        addMrpPlanItemColumns(c);
        return c;
    }
}
