package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 打样模块两个单据明细报表的列配置（打样单 / 免费样品订单）。
 *
 * <p>两个配置**放在同一个文件里**是刻意的（与 {@link SaleBillDetailReportConfigs} 同一口径）：
 * 同模块单据的列清单可以一眼横向比对，不会改一个漏一个。业务逻辑全在
 * {@link BillDetailReportEngine} 唯一一份实现里。
 *
 * <p>共同约定：
 * <ul>
 *   <li>SQL 主表别名 o、明细表别名 i（与全部明细报表一致）</li>
 *   <li>默认筛选：单号（文本）/ 单据时间（日期区间，前端预置本月）/ 客户（下拉）/ 状态（下拉）；
 *       免费样品订单另有业务员下拉（实体有 sales_id）；打样单无业务员字段，责任工程师仅作普通下拉</li>
 *   <li>明细行带 material_id / child_material_id 的，LEFT JOIN material 取图片列（type=image）</li>
 *   <li>打样单表头无"单据日期"字段，默认时间锚点 = 制单时间（created_at，列表页默认排序同源）</li>
 *   <li>数据权限：两个单据的列表页实查均为 created_by 单维度（ownerField 传 null），
 *       故 dataScopeOwnerColumn = null，与列表页逐字对齐（守则 2.7.2-6）</li>
 * </ul>
 */
public final class SampleBillDetailReportConfigs {

    private SampleBillDetailReportConfigs() {
    }

    // ==================================================================
    // 打样单明细报表
    // ==================================================================
    public static BillDetailReportConfig sampleOrderConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "sample_order";
        c.itemTable = "sample_order_item";
        c.itemFkColumn = "sample_id";
        c.docIdColumn = "sample_id";
        c.itemIdColumn = "item_id";
        c.cfEntityDoc = "sample_order";
        c.cfEntityItem = "sample_order_item";
        c.docNoExpr = "o.sample_no";
        // 实查 SampleOrderServiceImpl.pageQuery：scopeCtx.applyTo(wrapper, null) —— 只有创建人维度，无业务归属列
        c.dataScopeOwnerColumn = null;
        c.docDateFilterKey = "createdAt";
        c.defaultOrderBy = " ORDER BY o.created_at DESC NULLS LAST, o.sample_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add("LEFT JOIN customer c ON c.customer_id = o.customer_id");
        c.extraJoins.add("LEFT JOIN department dp ON dp.dept_id = o.department_id");
        c.extraJoins.add("LEFT JOIN employee eg ON eg.employee_id = o.engineer_id");
        c.extraJoins.add("LEFT JOIN unit_of_measure du ON du.unit_id = o.unit_id");
        c.extraJoins.add("LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id");
        // 明细行的物料外键是 child_material_id（不是 material_id），图片列从这里取
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.child_material_id");

        c.add(new Col("sampleNo", "打样单号", G_DOC, "text", "o.sample_no", true, "left", true))
                .filter("text", true);
        c.add(new Col("customerName", "客户", G_DOC, "text", "c.customer_name", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", true, null, true))
                .filter("dateRange", true);
        c.add(new Col("engineerName", "责任工程师", G_DOC, "text", "eg.employee_name", true, null, true))
                .select("employee", "long", "o.engineer_id", false);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("sampleName", "样品名称", G_DOC, "text", "o.sample_name", true, null, true));
        c.add(new Col("sampleType", "样品类型", G_DOC, "text", "o.sample_type", false, null, true));
        c.add(new Col("sampleStage", "打样阶段", G_DOC, "text", "o.sample_stage", false, null, true));
        c.add(new Col("contactPerson", "联系人", G_DOC, "text", "o.contact_person", false, null, false));
        c.add(new Col("contactPhone", "联系电话", G_DOC, "text", "o.contact_phone", false, null, false));
        c.add(new Col("materialCode", "物料编码", G_DOC, "text", "o.material_code", false, null, true));
        c.add(new Col("specification", "规格型号", G_DOC, "text", "o.specification", false, null, true));
        c.add(new Col("unitName", "单位", G_DOC, "text", "du.unit_name", false, null, false))
                .select("unit", "long", "o.unit_id", false);
        c.add(new Col("sampleQty", "打样数量", G_DOC, "number", "o.sample_qty", false, null, true));
        c.add(new Col("priority", "优先级", G_DOC, "text", "o.priority", false, null, true));
        // 打样进度的数据库列是 sample_progress（实查 SampleOrder.progress 的 @TableField）
        c.add(new Col("progress", "打样进度", G_DOC, "text", "o.sample_progress", false, null, true));
        c.add(new Col("demandDate", "需求交期", G_DOC, "date", "o.demand_date", false, null, true));
        c.add(new Col("planFinishDate", "计划完成", G_DOC, "date", "o.plan_finish_date", false, null, true));
        c.add(new Col("actualFinishDate", "实际完成", G_DOC, "date", "o.actual_finish_date", false, null, true));
        c.add(new Col("deptName", "责任部门", G_DOC, "text", "dp.dept_name", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("costAmount", "成本金额", G_DOC, "amount", "o.cost_amount", false, null, true));
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("isBomGenerated", "是否已转BOM", G_DOC, "int", "o.is_bom_generated", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));

        c.add(new Col("lineNo", "行号", G_ITEM, "int", "i.line_no", false, null, true));
        // 图片列：取物料库图片（明细行物料外键 = child_material_id）；type=image 前端缩略图渲染、不做筛选
        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        c.add(new Col("childMaterialName", "子件名称", G_ITEM, "text", "i.child_material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("childMaterialCode", "子件编码", G_ITEM, "text", "i.child_material_code", true, null, true))
                .filter("text", true);
        // 明细行持久化了 specification（编辑页列名"规格型号"）；与表头同名列以 item 前缀区分 key
        c.add(new Col("itemSpecification", "规格型号", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("itemUnitName", "单位", G_ITEM, "text", "u.unit_name", false, null, false));
        // 明细行数量列是 base_qty（基本用量），报表口径列 key 固定为 quantity（统计卡"商品数量合计"）
        c.add(new Col("quantity", "基本用量", G_ITEM, "number", "i.base_qty", false, null, true));
        c.add(new Col("qtyType", "用量类型", G_ITEM, "text", "i.qty_type", false, null, false));
        c.add(new Col("scrapRate", "损耗率%", G_ITEM, "number", "i.scrap_rate", false, null, true));
        c.add(new Col("supplyType", "供应类型", G_ITEM, "text", "i.supply_type", false, null, false));
        c.add(new Col("isKeyPart", "关键件", G_ITEM, "int", "i.is_key_part", false, null, false));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, true));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, true));
        // 明细只有"金额"一列金额（无 net_amount 列），报表口径列 key 固定为 itemNetAmount（统计卡/合计行）
        c.add(new Col("itemNetAmount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("costAmount"));
        return c;
    }

    // ==================================================================
    // 免费样品订单明细报表
    // ==================================================================
    public static BillDetailReportConfig freeSampleConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "free_sample_order";
        c.itemTable = "free_sample_order_item";
        c.itemFkColumn = "order_id";
        c.docIdColumn = "order_id";
        c.itemIdColumn = "item_id";
        c.cfEntityDoc = "free_sample_order";
        c.cfEntityItem = "free_sample_order_item";
        c.docNoExpr = "o.order_no";
        // 实查 FreeSampleOrderServiceImpl 列表查询：applyIfLimited(wrapper, null, "created_by") —— 只有创建人维度
        c.dataScopeOwnerColumn = null;
        c.docDateFilterKey = "orderDate";
        c.defaultOrderBy = " ORDER BY o.order_date DESC NULLS LAST, o.order_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add("LEFT JOIN customer c ON c.customer_id = o.customer_id");
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.sales_id");
        c.extraJoins.add("LEFT JOIN warehouse w ON w.stock_id = o.warehouse_id");
        // 明细行带 material_id，取物料库的图片串与规格（编辑页"规格"即取物料档案的型号）
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("orderNo", "单号", G_DOC, "text", "o.order_no", true, "left", true))
                .filter("text", true);
        c.add(new Col("customerName", "客户", G_DOC, "text", "c.customer_name", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("orderDate", "日期", G_DOC, "date", "o.order_date", true, null, true))
                .filter("dateRange", true);
        c.add(new Col("salerName", "业务员", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.sales_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", false, null, false))
                .select("warehouse", "long", "o.warehouse_id", false);
        c.add(new Col("totalQuantity", "总数量", G_DOC, "number", "o.total_quantity", false, null, true));
        // 免费样品订单金额强制 0.00，列保留口径完整
        c.add(new Col("totalAmount", "金额", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("deliveryDate", "发货日期", G_DOC, "date", "o.delivery_date", false, null, true));
        c.add(new Col("expressCompany", "快递公司", G_DOC, "text", "o.express_company", false, null, false));
        c.add(new Col("expressNo", "快递单号", G_DOC, "text", "o.express_no", false, null, false));
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));

        c.add(new Col("lineNo", "行号", G_ITEM, "int", "i.line_no", false, null, true));
        // 图片列：取物料库图片；type=image 前端缩略图渲染、不做筛选
        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        c.add(new Col("materialName", "商品名称", G_ITEM, "text", "i.material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("materialCode", "商品编号", G_ITEM, "text", "i.material_code", true, null, true))
                .filter("text", true);
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("color", "颜色", G_ITEM, "text", "i.color", false, null, true));
        // 编辑页"规格"列 = 物料档案的型号（onMaterialChange 里 row.specification = m.model），
        // 行表没有 specification 列（遗留 model 列前端不写入），故与编辑页同源取 m.model
        c.add(new Col("specification", "规格", G_ITEM, "text", "m.model", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", "i.unit_name", false, null, false));
        c.add(new Col("binName", "储位", G_ITEM, "text", "i.bin_name", false, null, false));
        c.add(new Col("itemRemark", "备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount"));
        return c;
    }
}
