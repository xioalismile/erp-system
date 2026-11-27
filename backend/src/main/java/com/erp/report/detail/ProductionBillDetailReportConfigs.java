package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 生产模块 9 个单据明细报表的列配置。
 *
 * <p><b>列名来源</b>：全部取自实库 {@code information_schema.columns}（2026-09-22 逐表实查，非推测）。
 *
 * <p>生产模块有两套**完全不同的表结构**，抄错一处就整条 SQL 报错，务必按下面的分流理解：
 * <ol>
 *   <li><b>新结构单据</b>（{@code production_order} / {@code production_receipt} 及其明细）：
 *       列名与其它模块同风格（{@code order_no} / {@code line_no} / {@code created_by} / {@code stock_id}），
 *       状态是**中文文本**。</li>
 *   <li><b>旧结构单据</b>（{@code produce_assemble} / {@code produce_disassemble} 及其 {@code _detail} / {@code _bom}）：
 *       沿用老系统列名，坑最多：
 *       <ul>
 *         <li>单号是 {@code serial_no}、单据日期是 {@code dateline}（timestamp）、单据备注是 {@code notes}（不是 remark）</li>
 *         <li><b>创建人列叫 {@code creator}，不是 {@code created_by}</b></li>
 *         <li><b>状态是数字</b> 0 草稿 / 1 待审核 / 2 已生效 / 7 已作废（与前端列表页 statusMap 一致），
 *             故这里额外给一个 {@code statusText} 列用 CASE 转中文；<b>绝不能</b>对它用 {@code select("status","string",…)}
 *             —— 那会拿中文去和 integer 列比较直接报 SQL 错</li>
 *         <li>明细的**商品字段是 {@code product_code}/{@code product_name}**（不是 material_*）；单位名在 {@code unit}</li>
 *         <li><b>明细没有 {@code line_no} 列</b>（只有 {@code row_id}/{@code sort_order}）→ 行号列 key 必须**不叫 lineNo**：
 *             引擎的 {@code orderBySql} 见到 key=lineNo 就会拼 {@code i.line_no}，列不存在会直接炸</li>
 *         <li>储位列是 {@code storage_id}/{@code storage_name}（不是 bin_id）</li>
 *         <li>主表**没有 {@code org_code}、没有 {@code bus_status}、没有 {@code approved_by/at}**</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p><b>三个一致性硬口径</b>（与销售/采购/库存明细报表完全一致）：
 * 默认可见 7 列 = 单号 / 对方单位 / 日期 / 业务员 / 商品名称 / 商品编号 / 规格；
 * 默认勾选搜索条件 = 同样 7 项；日期条件标记 defaultCurrentMonth。
 *
 * <p><b>「对方单位名称」这一位怎么落</b>：生产单据也是内部单据、无往来单位 ——
 * 工单取<b>生产部门</b>、生产入库取<b>仓库</b>、组装/拆卸取<b>仓库</b>。
 *
 * <p><b>行级数据权限</b>：实查各 Service —— 工单/生产入库 {@code applyIfLimited(wrapper, null, "created_by")}；
 * 组装/拆卸单 {@code applyIfLimited(wrapper, null, "creator")}（旧结构表列名不同）。两者都**无业务归属列**。
 */
public final class ProductionBillDetailReportConfigs {

    private ProductionBillDetailReportConfigs() {
    }

    private static final String JOIN_UNIT = "LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id";
    private static final String JOIN_DEPT = "LEFT JOIN department dp ON dp.dept_id = o.department_id";
    /** 工单负责人（字段名 manager_id） */
    private static final String JOIN_MANAGER = "LEFT JOIN employee e ON e.employee_id = o.manager_id";
    /** 生产入库经手人（字段名 handler_id） */
    private static final String JOIN_HANDLER = "LEFT JOIN employee e ON e.employee_id = o.handler_id";
    private static final String JOIN_CURRENCY = "LEFT JOIN currency cu ON cu.currency_id = o.currency_id";
    /** 单据头仓库（warehouse 主键是 stock_id，单据上的列叫 warehouse_id） */
    private static final String JOIN_DOC_WAREHOUSE = "LEFT JOIN warehouse w ON w.stock_id = o.warehouse_id";
    /** 明细仓库：工单原料/生产入库明细的列叫 stock_id */
    private static final String JOIN_ITEM_WAREHOUSE = "LEFT JOIN warehouse iw ON iw.stock_id = i.stock_id";
    /** 明细仓库：工单成品明细的列叫 warehouse_id（与原料明细不同！） */
    private static final String JOIN_ITEM_WAREHOUSE_BY_WHID = "LEFT JOIN warehouse iw ON iw.stock_id = i.warehouse_id";
    private static final String JOIN_ITEM_BIN = "LEFT JOIN location b ON b.bin_id = i.bin_id";
    /** 旧结构单据头仓库（列名 wh_id） */
    private static final String JOIN_ASM_DOC_WAREHOUSE = "LEFT JOIN warehouse w ON w.stock_id = o.wh_id";
    /** 旧结构明细仓库（列名 wh_id） */
    private static final String JOIN_ASM_ITEM_WAREHOUSE = "LEFT JOIN warehouse iw ON iw.stock_id = i.wh_id";
    /** 旧结构明细储位（列名 storage_id） */
    private static final String JOIN_ASM_ITEM_STORAGE = "LEFT JOIN location b ON b.bin_id = i.storage_id";

    /** 旧结构单据状态（数字 → 中文；取值口径与前端 组装/拆卸列表页 statusMap 逐字一致） */
    private static final String ASM_STATUS_TEXT =
            "CASE o.status WHEN 0 THEN '草稿' WHEN 1 THEN '待审核' WHEN 2 THEN '已生效' WHEN 7 THEN '已作废' "
                    + "ELSE o.status::text END";

    /** 明细三件套（商品名称/编号/规格）—— 5 个报表必须一模一样，故抽成方法 */
    private static void addItemMaterialTriple(BillDetailReportConfig c, String unitNameExpr) {
        c.add(new Col("lineNo", "行号", G_ITEM, "int", "i.line_no", false, null, true));
        c.add(new Col("materialName", "商品名称", G_ITEM, "text", "i.material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("materialCode", "商品编号", G_ITEM, "text", "i.material_code", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", unitNameExpr, false, null, false));
    }

    /**
     * 新结构单据的**公共**尾列（生产工单 / 生产入库单都有这些列）。
     *
     * <p>刻意不接收"要不要含币种 / 要不要含业务状态"这类开关：
     * 带分支的写法会让「列名静态校验」把未启用的列也算成已引用（生产批次实测踩到），
     * 从而掩盖真实错列名。需要额外列的单据（生产入库单有币种与业务状态、工单没有）
     * 请在调用处**显式补列**。
     */
    private static void addCommonDocTail(BillDetailReportConfig c) {
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("closedBy", "结案人", G_DOC, "text", "o.closed_by", false, null, false));
        c.add(new Col("closedAt", "结案时间", G_DOC, "datetime", "o.closed_at", false, null, false));
        c.add(new Col("closedReason", "结案原因", G_DOC, "text", "o.closed_reason", false, null, false));
        c.add(new Col("billBeforeStatus", "作废前状态", G_DOC, "text", "o.bill_before_status", false, null, false));
        c.add(new Col("voidedBy", "作废人", G_DOC, "text", "o.voided_by", false, null, false));
        c.add(new Col("voidedAt", "作废时间", G_DOC, "datetime", "o.voided_at", false, null, false));
        c.add(new Col("voidReason", "作废原因", G_DOC, "text", "o.void_reason", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("updatedBy", "修改人", G_DOC, "text", "o.updated_by", false, null, false));
        c.add(new Col("updatedAt", "修改时间", G_DOC, "datetime", "o.updated_at", false, null, false));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
    }

    /**
     * 生产工单单据头列（工单原料报表 / 工单成品报表**共用**，两者单据头完全一致）。
     * 注意：工单表没有 currency_id / bus_status / total_amount。
     */
    private static void addWorkOrderDocColumns(BillDetailReportConfig c) {
        c.add(new Col("orderNo", "工单号", G_DOC, "text", "o.order_no", true, "left", true)).filter("text", true);
        // 内部单据无往来单位 → 第 2 位取「生产部门」
        c.add(new Col("deptName", "生产部门", G_DOC, "text", "dp.dept_name", true, null, true))
                .select("department", "long", "o.department_id", true);
        c.add(new Col("orderDate", "工单日期", G_DOC, "date", "o.order_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("managerName", "负责人", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.manager_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("orderType", "工单类型", G_DOC, "text", "o.order_type", false, null, false));
        c.add(new Col("docMaterialCode", "工单产品编号", G_DOC, "text", "o.material_code", false, null, true));
        c.add(new Col("docMaterialName", "工单产品名称", G_DOC, "text", "o.material_name", false, null, true));
        c.add(new Col("docModel", "工单产品规格", G_DOC, "text", "o.model", false, null, false));
        c.add(new Col("docSpec", "工单产品规格(备用列)", G_DOC, "text", "o.spec", false, null, false));
        c.add(new Col("unitCode", "单位编码", G_DOC, "text", "o.unit_code", false, null, false));
        c.add(new Col("bomCode", "BOM编号", G_DOC, "text", "o.bom_code", false, null, false));
        c.add(new Col("bomVersion", "BOM版本", G_DOC, "text", "o.bom_version", false, null, false));
        c.add(new Col("routingCode", "工艺路线编号", G_DOC, "text", "o.routing_code", false, null, false));
        c.add(new Col("routingVersion", "工艺路线版本", G_DOC, "text", "o.routing_version", false, null, false));
        c.add(new Col("workCenterName", "工作中心", G_DOC, "text", "o.work_center_name", false, null, false));
        c.add(new Col("workstationName", "工位", G_DOC, "text", "o.workstation_name", false, null, false));
        c.add(new Col("workshopManagerName", "车间主管", G_DOC, "text", "o.workshop_manager_name", false, null, false));
        c.add(new Col("priority", "优先级", G_DOC, "int", "o.priority", false, null, false));
        c.add(new Col("isUrgent", "是否加急", G_DOC, "text", "o.is_urgent", false, null, false));
        c.add(new Col("quantity", "计划数量", G_DOC, "number", "o.quantity", false, null, true));
        // 【2026-10-14 第三批·用户拍板】「已生产数量」列下线：o.produced_quantity 全系统零写入（死列），
        // 语义与「完工数量」（生产入库生效/重置双向回写，真实数据）重复 —— 留着一个永远显示假值的重复列只会误导。
        c.add(new Col("completedQuantity", "完工数量", G_DOC, "number", "o.completed_quantity", false, null, true));
        c.add(new Col("reportedQuantity", "已报工数量", G_DOC, "number", "o.reported_quantity", false, null, true));
        // 【2026-10-14 第二批】已领料数量改生产领料单(PICK)实时合计（o.picked_quantity 全系统零写入，死列）。
        c.add(new Col("pickedQuantity", "已领料数量", G_DOC, "number",
                RowWritebackSql.workOrderPickQtyDoc("o.order_no"), false, null, true));
        // 【2026-10-14 第四批·用户拍板】合格数量 = 生效工序汇报合格数实时合计；报废数量 = 工序行报废数实时合计
        // （原读 o.qualified_quantity / o.scrap_quantity，均为零写入死列，演示假值会假报合格/报废）。
        c.add(new Col("docQualifiedQuantity", "合格数量（整单）", G_DOC, "number",
                RowWritebackSql.opReportQualifiedQty("o.order_id"), false, null, true));
        c.add(new Col("docScrapQuantity", "报废数量（整单）", G_DOC, "number",
                RowWritebackSql.operationScrapQty("o.order_id"), false, null, true));
        c.add(new Col("docRejectedQuantity", "不合格数量（整单）", G_DOC, "number", "o.rejected_quantity", false, null, true));
        c.add(new Col("planStartDate", "计划开工日期", G_DOC, "date", "o.plan_start_date", false, null, true));
        c.add(new Col("planEndDate", "计划完工日期", G_DOC, "date", "o.plan_end_date", false, null, true));
        c.add(new Col("actualStartDate", "实际开工日期", G_DOC, "date", "o.actual_start_date", false, null, true));
        c.add(new Col("actualEndDate", "实际完工日期", G_DOC, "date", "o.actual_end_date", false, null, true));
        c.add(new Col("leadTimeDays", "提前期(天)", G_DOC, "int", "o.lead_time_days", false, null, false));
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", false, null, false))
                .select("warehouse", "long", "o.warehouse_id", false);
        c.add(new Col("sourceType", "来源类型", G_DOC, "text", "o.source_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("sourceNo", "来源单号(简)", G_DOC, "text", "o.source_no", false, null, false));
        c.add(new Col("salesOrderNo", "销售订单号", G_DOC, "text", "o.sales_order_no", false, null, false));
        c.add(new Col("isClosed", "是否已结案", G_DOC, "int", "o.is_closed", false, null, false));
        // 工单表**没有** currency_id / exchange_rate / bus_status → 不补这两个（补了会整条 SQL 报错）
        addCommonDocTail(c);
    }

    // ==================================================================
    // 1. 生产工单 · 原料明细报表（production_order / production_order_item）
    // ==================================================================
    public static BillDetailReportConfig workOrderMaterialConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "production_order";
        c.itemTable = "production_order_item";
        c.itemFkColumn = "order_id";
        c.docIdColumn = "order_id";
        c.itemIdColumn = "order_item_id";
        c.cfEntityDoc = "production_order";
        c.cfEntityItem = "production_order_item";
        c.docNoExpr = "o.order_no";
        c.dataScopeOwnerColumn = null;          // 实查 WorkOrderServiceImpl: applyIfLimited(wrapper, null, "created_by")
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "orderDate";
        c.defaultOrderBy = " ORDER BY o.order_date DESC NULLS LAST, o.order_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_MANAGER);
        c.extraJoins.add(JOIN_DEPT);
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_ITEM_BIN);

        addWorkOrderDocColumns(c);

        addItemMaterialTriple(c, "u.unit_name");
        c.add(new Col("bomQuantity", "BOM用量", G_ITEM, "number", "i.bom_quantity", false, null, true));
        c.add(new Col("requiredQuantity", "需求数量", G_ITEM, "number", "i.required_quantity", false, null, true));
        // 【2026-10-14 第二批】已发/已退/已领/已补改「生产领料/退料/补料单」实时聚合（bill_type=PICK/RETURN/SUPPLEMENT，
        // 行级 source_line_no = 本行行号、表头 source_bill_type='生产工单'；原读落库列：演示假值会假报领退补）。
        // 已耗用数量【2026-10-14 第三批·用户拍板】改「已发数量−已退数量」实时计算（净耗用，负数封底 0；
        // consumed_quantity 原为全系统零写入的死列，报工耗用能力未实现）。
        c.add(new Col("issuedQuantity", "已发数量", G_ITEM, "number",
                RowWritebackSql.workOrderReqQty("'PICK','SUPPLEMENT'", "i.line_no", "o.order_no", "i.material_code"), false, null, true));
        c.add(new Col("returnedQuantity", "已退数量", G_ITEM, "number",
                RowWritebackSql.workOrderReqQty("'RETURN'", "i.line_no", "o.order_no", "i.material_code"), false, null, true));
        c.add(new Col("consumedQuantity", "已耗用数量", G_ITEM, "number",
                RowWritebackSql.workOrderConsumedQty("i.line_no", "o.order_no", "i.material_code"), false, null, true));
        c.add(new Col("itemScrapQuantity", "报废数量", G_ITEM, "number", "i.scrap_quantity", false, null, true));
        c.add(new Col("itemPickedQuantity", "已领数量", G_ITEM, "number",
                RowWritebackSql.workOrderReqQty("'PICK'", "i.line_no", "o.order_no", "i.material_code"), false, null, true));
        c.add(new Col("supplementaryQuantity", "已补数量", G_ITEM, "number",
                RowWritebackSql.workOrderReqQty("'SUPPLEMENT'", "i.line_no", "o.order_no", "i.material_code"), false, null, true));
        c.add(new Col("supplyType", "供应类型", G_ITEM, "text", "i.supply_type", false, null, false));
        c.add(new Col("processNo", "工序号", G_ITEM, "text", "i.process_no", false, null, false));
        // 2026-11-14：编辑页明细有含税价/不含税价，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("unitCost", "单位成本", G_ITEM, "number", "i.unit_cost", false, null, false));
        c.add(new Col("totalCost", "总成本", G_ITEM, "amount", "i.total_cost", false, null, false));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("binName", "储位", G_ITEM, "text", "b.bin_name", false, null, false));
        c.add(new Col("modelNo", "型号", G_ITEM, "text", "i.model_no", false, null, false));
        c.add(new Col("itemUnitCode", "单位编码(明细)", G_ITEM, "text", "i.unit_code", false, null, false));
        c.add(new Col("sortOrder", "排序号", G_ITEM, "int", "i.sort_order", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("bomQuantity", "requiredQuantity", "issuedQuantity",
                "returnedQuantity", "consumedQuantity", "itemPickedQuantity", "supplementaryQuantity"));
        c.docTotalKeys.addAll(java.util.List.of("quantity", "completedQuantity",
                "reportedQuantity", "pickedQuantity"));
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax"));
        return c;
    }

    // ==================================================================
    // 2. 生产工单 · 成品明细报表（production_order / work_order_product）
    // ==================================================================
    public static BillDetailReportConfig workOrderProductConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "production_order";
        c.itemTable = "work_order_product";
        c.itemFkColumn = "order_id";
        c.docIdColumn = "order_id";
        c.itemIdColumn = "id";                   // ⚠ 成品明细主键就叫 id（不是 order_product_id）
        c.cfEntityDoc = "production_order";
        c.cfEntityItem = "work_order_product";
        c.docNoExpr = "o.order_no";
        c.dataScopeOwnerColumn = null;
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "orderDate";
        c.defaultOrderBy = " ORDER BY o.order_date DESC NULLS LAST, o.order_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_MANAGER);
        c.extraJoins.add(JOIN_DEPT);
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_UNIT);
        // ⚠ 成品明细的仓库列叫 warehouse_id（原料明细叫 stock_id），别名仍用 iw 但关联列不同
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE_BY_WHID);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        addWorkOrderDocColumns(c);

        // 单位名成品明细自带冗余列 unit_name，缺时回落单位主档
        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c, "COALESCE(NULLIF(i.unit_name, ''), u.unit_name)");
        c.add(new Col("itemModel", "型号", G_ITEM, "text", "i.model", false, null, false));
        c.add(new Col("itemQuantity", "成品数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("itemCompletedQuantity", "已完工数量", G_ITEM, "number", "i.completed_quantity", false, null, true));
        // 【2026-10-14 第三批】成品行「已报工数量」下线：work_order_product.reported_quantity 零写入（死列，
        // 工序汇报只回写工单表头「已报工数量」与工序行），与用户对「已生产数量」的下线口径一致。
        c.add(new Col("itemRejectedQuantity", "不合格数量", G_ITEM, "number", "i.rejected_quantity", false, null, true));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.warehouse_id", false);
        c.add(new Col("itemSourceType", "明细来源类型", G_ITEM, "text", "i.source_type", false, null, false));
        c.add(new Col("itemSourceBillType", "明细来源单据类型", G_ITEM, "text", "i.source_bill_type", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("itemQuantity", "itemCompletedQuantity", "itemRejectedQuantity"));
        c.docTotalKeys.addAll(java.util.List.of("quantity", "completedQuantity"));
        return c;
    }

    // ==================================================================
    // 3. 生产入库单明细报表（production_receipt / production_receipt_item）
    // ==================================================================
    public static BillDetailReportConfig productionReceiptConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "production_receipt";
        c.itemTable = "production_receipt_item";
        c.itemFkColumn = "receipt_id";
        c.docIdColumn = "receipt_id";
        c.itemIdColumn = "receipt_item_id";
        c.cfEntityDoc = "production_receipt";
        c.cfEntityItem = "production_receipt_item";
        c.docNoExpr = "o.receipt_no";
        c.dataScopeOwnerColumn = null;          // 实查 ProductionReceiptServiceImpl: applyIfLimited(wrapper, null, "created_by")
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "receiptDate";
        c.defaultOrderBy = " ORDER BY o.receipt_date DESC NULLS LAST, o.receipt_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_HANDLER);
        c.extraJoins.add(JOIN_DEPT);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add(JOIN_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_ITEM_BIN);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");
        // 2026-11-14：来源工序名称（编辑页有「来源工序」列）
        c.extraJoins.add("LEFT JOIN work_order_operation wo ON wo.id = i.source_operation_id");

        c.add(new Col("receiptNo", "入库单号", G_DOC, "text", "o.receipt_no", true, "left", true)).filter("text", true);
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", true, null, true))
                .select("warehouse", "long", "o.warehouse_id", true);
        c.add(new Col("receiptDate", "入库日期", G_DOC, "date", "o.receipt_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("handlerName", "经手人", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.handler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        // 2026-11-14：补「计税方式」（默认隐藏，列设置可勾）
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("productionOrderNo", "生产工单号", G_DOC, "text", "o.production_order_no", false, null, true));
        c.add(new Col("workOrderNo", "工单号(简)", G_DOC, "text", "o.work_order_no", false, null, false));
        c.add(new Col("deptName", "生产部门", G_DOC, "text", "dp.dept_name", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("totalQuantity", "入库数量（整单）", G_DOC, "number", "o.total_quantity", false, null, true));
        c.add(new Col("docQualifiedQuantity", "合格数量（整单）", G_DOC, "number", "o.qualified_quantity", false, null, true));
        c.add(new Col("docRejectedQuantity", "不合格数量（整单）", G_DOC, "number", "o.rejected_quantity", false, null, true));
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        // 生产入库单**独有**于工单的两个维度（工单表没有这两列）—— 在调用处显式补，不在公共尾列里加开关
        c.add(new Col("busStatus", "业务状态", G_DOC, "int", "o.bus_status", false, null, false));
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true));
        addCommonDocTail(c);

        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        addItemMaterialTriple(c, "u.unit_name");
        c.add(new Col("quantity", "入库数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("qualifiedQuantity", "合格数量", G_ITEM, "number", "i.qualified_quantity", false, null, true));
        c.add(new Col("rejectedQuantity", "不合格数量", G_ITEM, "number", "i.rejected_quantity", false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        // 2026-11-14：编辑页明细有含税价/不含税价/来源工序，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("sourceOperationName", "来源工序", G_ITEM, "text", "wo.operation_name", false, null, false));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("avgCost", "成本", G_ITEM, "number", "i.avg_cost", false, null, false));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("binName", "储位", G_ITEM, "text", "b.bin_name", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("productionDate", "生产日期", G_ITEM, "date", "i.production_date", false, null, false));
        c.add(new Col("expiryDate", "有效期至", G_ITEM, "date", "i.expiry_date", false, null, false));
        c.add(new Col("workOrderLineNo", "工单行号", G_ITEM, "int", "i.work_order_line_no", false, null, false));
        c.add(new Col("operationReportNo", "报工单号", G_ITEM, "text", "i.operation_report_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "qualifiedQuantity", "rejectedQuantity", "amount"));
        c.docTotalKeys.addAll(java.util.List.of("totalQuantity", "totalAmount"));
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax",
                "taxCalcMethod", "sourceOperationName"));
        return c;
    }

    // ==================================================================
    // 4~7. 组装单 / 拆卸单（旧结构表；成品明细 + 用料明细各一份报表）
    // ==================================================================

    /** 旧结构单据头列（组装/拆卸表结构完全一致，故共用） */
    private static void addAssembleDocColumns(BillDetailReportConfig c) {
        c.add(new Col("serialNo", "单据号", G_DOC, "text", "o.serial_no", true, "left", true)).filter("text", true);
        c.add(new Col("whName", "仓库", G_DOC, "text", "COALESCE(NULLIF(o.wh_name, ''), w.stock_name)", true, null, true))
                .select("warehouse", "long", "o.wh_id", true);
        c.add(new Col("dateline", "单据日期", G_DOC, "date", "(o.dateline)::date", true, null, true)).filter("dateRange", true);
        // 旧结构表**没有 created_by**，创建人列是 creator；且 业务员列(salesman/salesman_str) 实测为空，
        // 故第 4 位放「制单人」占位（与采购发票明细报表同口径，保持 7 项结构一致）
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.creator", true, null, true)).filter("text", true);
        // 状态是数字 → 筛选用「CASE 转中文后的表达式」做等值（2026-10-14 筛选条件自定义：text 模糊升级为下拉，
        // 选项 = 引擎 status 静态枚举中文；filterExpr 不能写裸 integer 列 o.status——中文比 integer 会报 SQL 错）
        c.add(new Col("statusText", "单据状态", G_DOC, "status", ASM_STATUS_TEXT, false, null, true))
                .select("status", "string", ASM_STATUS_TEXT, false);
        c.add(new Col("statusCode", "状态码", G_DOC, "int", "o.status", false, null, false));
        c.add(new Col("whNameBom", "用料仓库", G_DOC, "text", "o.wh_name_bom", false, null, false));
        c.add(new Col("whIdBom", "用料仓库ID", G_DOC, "long", "o.wh_id_bom", false, null, false));
        c.add(new Col("dept", "部门", G_DOC, "text", "o.dept", false, null, false));
        c.add(new Col("isTax", "是否含税", G_DOC, "text", "o.is_tax", false, null, false));
        c.add(new Col("taxType", "税种", G_DOC, "int", "o.tax_type", false, null, false));
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("currencyRate", "汇率", G_DOC, "number", "o.currency_rate", false, null, true));
        c.add(new Col("docAmount", "金额（整单）", G_DOC, "amount", "o.amount", false, null, true));
        c.add(new Col("docBaseAmount", "本位币金额（整单）", G_DOC, "amount", "o.base_amount", false, null, true));
        c.add(new Col("docTaxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("docTaxRate", "税率%（整单）", G_DOC, "number", "o.tax_rate", false, null, true));
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
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("updatedBy", "修改人", G_DOC, "text", "o.updated_by", false, null, false));
        c.add(new Col("updatedAt", "修改时间", G_DOC, "datetime", "o.updated_at", false, null, false));
        // 注意：旧结构主表**没有 org_code / bus_status / approved_by / approved_at**，故不上这些列
    }

    /**
     * 旧结构明细列（成品明细与用料明细结构一致）。
     *
     * <p>⚠ 两个坑：① 商品字段是 {@code product_code}/{@code product_name}；
     * ② **没有 {@code line_no}**，行号列用 {@code row_id}，且 key 必须**不叫 lineNo**
     * （引擎 orderBySql 见到 key=lineNo 会拼 {@code i.line_no} → 列不存在直接报错）。
     */
    private static void addAssembleItemColumns(BillDetailReportConfig c, boolean withParentRow) {
        c.add(new Col("rowNo", "行号", G_ITEM, "int", "i.row_id", false, null, true));
        c.add(new Col("materialName", "商品名称", G_ITEM, "text", "i.product_name", true, null, true))
                .filter("text", true);
        c.add(new Col("materialCode", "商品编号", G_ITEM, "text", "i.product_code", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", "COALESCE(NULLIF(i.unit, ''), u.unit_name)", false, null, false));
        if (withParentRow) {
            c.add(new Col("parentRowId", "父行号", G_ITEM, "int", "i.parent_row_id", false, null, false));
        }
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("basicQuantity", "基本数量", G_ITEM, "number", "i.basic_quantity", false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.price", false, null, true));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("itemTaxAmount", "税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("itemWhName", "明细仓库", G_ITEM, "text", "COALESCE(NULLIF(i.wh_name, ''), iw.stock_name)", false, null, false));
        c.add(new Col("binName", "储位", G_ITEM, "text", "COALESCE(NULLIF(i.storage_name, ''), b.bin_name)", false, null, false));
        c.add(new Col("basicUnit", "基本单位", G_ITEM, "text", "i.basic_unit", false, null, false));
        c.add(new Col("itemSourceType", "明细来源类型", G_ITEM, "text", "i.source_type", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_serial_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.notes", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "basicQuantity", "amount", "itemTaxAmount"));
        c.docTotalKeys.addAll(java.util.List.of("docAmount", "docBaseAmount", "docTaxAmount"));
    }

    /** 旧结构单据的公共 JOIN（组装/拆卸各 4 个报表共用） */
    private static void addAssembleJoins(BillDetailReportConfig c) {
        c.extraJoins.add(JOIN_ASM_DOC_WAREHOUSE);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add(JOIN_UNIT);                 // 明细 unit_id → 单位主档
        c.extraJoins.add(JOIN_ASM_ITEM_WAREHOUSE);
        c.extraJoins.add(JOIN_ASM_ITEM_STORAGE);
    }

    /** 4. 生产组装单 · 成品明细报表 */
    public static BillDetailReportConfig assembleProductConfig() {
        return assembleFamily("produce_assemble", "assemble_id", "produce_assemble_detail",
                "produce_assemble", "produce_assemble_detail", false);
    }

    /** 5. 生产组装单 · 用料明细报表 */
    public static BillDetailReportConfig assembleBomConfig() {
        return assembleFamily("produce_assemble", "assemble_id", "produce_assemble_bom",
                "produce_assemble", "produce_assemble_bom", true);
    }

    /** 6. 生产拆卸单 · 成品明细报表 */
    public static BillDetailReportConfig disassembleProductConfig() {
        return assembleFamily("produce_disassemble", "disassemble_id", "produce_disassemble_detail",
                "produce_disassemble", "produce_disassemble_detail", false);
    }

    /** 7. 生产拆卸单 · 用料明细报表 */
    public static BillDetailReportConfig disassembleBomConfig() {
        return assembleFamily("produce_disassemble", "disassemble_id", "produce_disassemble_bom",
                "produce_disassemble", "produce_disassemble_bom", true);
    }

    /**
     * 组装 / 拆卸 四张报表的公共骨架（主表 + 明细表只有表名/外键不同，其余完全一致）。
     * 抽成一份是刻意的：四张报表只要有一处跑偏，用户就会说"复刻缺东西"。
     */
    private static BillDetailReportConfig assembleFamily(String mainTable, String itemFk,
                                                         String itemTable, String cfDoc, String cfItem,
                                                         boolean withParentRow) {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = mainTable;
        c.itemTable = itemTable;
        c.itemFkColumn = itemFk;
        c.docIdColumn = "id";                    // ⚠ 旧结构主表主键就叫 id
        c.itemIdColumn = "id";                   // ⚠ 明细主键也叫 id
        c.cfEntityDoc = cfDoc;
        c.cfEntityItem = cfItem;
        c.docNoExpr = "o.serial_no";
        c.dataScopeOwnerColumn = null;           // 实查 ProduceAssembleServiceImpl/ProduceDisassembleServiceImpl: applyIfLimited(wrapper, null, "creator")
        c.dataScopeCreatedByColumn = "creator";  // ⚠ 旧结构表创建人列叫 creator，不是 created_by
        c.docDateFilterKey = "dateline";
        // ⚠ 不能写成 i.line_no（该表没有 line_no 列），用 row_id
        c.defaultOrderBy = " ORDER BY o.dateline DESC NULLS LAST, o.serial_no DESC NULLS LAST, i.row_id ASC NULLS LAST ";
        addAssembleJoins(c);

        addAssembleDocColumns(c);
        addAssembleItemColumns(c, withParentRow);
        return c;
    }

    // ==================================================================
    // 8~9. 生产退料单 / 生产补料单明细报表
    //    （与「生产领料单」共用 material_requisition 一张表，列清单也完全一致，
    //      故直接复用库存配置里的变体方法，只有 bill_type 与中文叫法不同）
    // ==================================================================
    public static BillDetailReportConfig materialReturnConfig() {
        return InventoryBillDetailReportConfigs.materialRequisitionVariant("RETURN", "退料");
    }

    public static BillDetailReportConfig materialSupplementConfig() {
        return InventoryBillDetailReportConfigs.materialRequisitionVariant("SUPPLEMENT", "补料");
    }
}
