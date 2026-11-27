package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 售后维修单明细报表配置（2026-11-27 补齐）。
 *
 * <p>背景：售后维修单编辑页有两张明细（维修明细 sale_repair_item、配件领用 sale_repair_part），
 * 列表页此前没有明细模式——按用户口径「两张明细的字段全部自动加入列设置，由用户自己勾选」，
 * 走 {@link BillDetailReportUnion} 合并成一张全明细（多一列「明细类型」）。</p>
 *
 * <p>口径：表头字段两边必须<b>完全一致</b>（合并按 key 取并集，缺的一边会变 NULL）；
 * 明细字段按来源各自登记，同名概念用不同 key（如整单收件数量 receivedQuantity / 明细行 itemReceivedQuantity）。
 * 逐列对照实体 {@code SaleRepair / SaleRepairItem / SaleRepairPart}，不硬凑不存在的列。</p>
 */
public final class SaleRepairBillDetailReportConfigs {

    private SaleRepairBillDetailReportConfigs() {
    }

    /** 维修明细（客户产品行） */
    public static BillDetailReportConfig repairItemConfig() {
        BillDetailReportConfig c = base("sale_repair_item", "repair_item_id", "sale_repair_item");
        c.extraJoins.add("LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id");
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");
        addDocColumns(c);

        c.add(new Col("lineNo", "行号", G_ITEM, "int", "i.line_no", false, null, true));
        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        c.add(new Col("materialCode", "商品编号", G_ITEM, "text", "i.material_code", true, null, true))
                .filter("text", true);
        c.add(new Col("materialName", "商品名称", G_ITEM, "text", "i.material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", "u.unit_name", false, null, false));
        c.add(new Col("serialNo", "序列号", G_ITEM, "text", "i.serial_no", false, null, false))
                .filter("text", false);
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("quantity", "维修数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("itemFaultDescription", "故障描述（明细）", G_ITEM, "text", "i.fault_description", false, null, false));
        c.add(new Col("repairPlan", "维修方案", G_ITEM, "text", "i.repair_plan", false, null, false))
                .filter("text", false);
        c.add(new Col("itemWarrantyStatus", "保修状态（明细）", G_ITEM, "text", "i.warranty_status", false, null, false));
        c.add(new Col("laborHours", "工时", G_ITEM, "number", "i.labor_hours", false, null, true));
        c.add(new Col("itemReceivedQuantity", "已收数量（明细）", G_ITEM, "number", "i.received_quantity", false, null, true));
        c.add(new Col("itemReturnedQuantity", "已返数量（明细）", G_ITEM, "number", "i.returned_quantity", false, null, true));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of(
                "quantity", "laborHours", "itemReceivedQuantity", "itemReturnedQuantity"));
        return c;
    }

    /** 配件领用明细 */
    public static BillDetailReportConfig repairPartConfig() {
        BillDetailReportConfig c = base("sale_repair_part", "part_id", "sale_repair_part");
        c.extraJoins.add("LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id");
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");
        c.extraJoins.add("LEFT JOIN warehouse iw ON iw.stock_id = i.warehouse_id");
        addDocColumns(c);

        c.add(new Col("lineNo", "行号", G_ITEM, "int", "i.line_no", false, null, true));
        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        c.add(new Col("materialCode", "商品编号", G_ITEM, "text", "i.material_code", true, null, true))
                .filter("text", true);
        c.add(new Col("materialName", "商品名称", G_ITEM, "text", "i.material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", "u.unit_name", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("quantity", "领用数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("partWarehouseName", "领用仓库", G_ITEM, "text", "iw.stock_name", false, null, false));
        c.add(new Col("avgCost", "成本单价", G_ITEM, "number", "i.avg_cost", false, null, true));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("issuedQuantity", "已出库数量", G_ITEM, "number", "i.issued_quantity", false, null, true));
        c.add(new Col("issueNo", "出库单号", G_ITEM, "text", "i.issue_no", false, null, false))
                .filter("text", false);
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "amount", "issuedQuantity"));
        return c;
    }

    /** 两张明细共用的表头骨架（key/表达式/筛选口径必须一致，合并时才能对上） */
    private static BillDetailReportConfig base(String itemTable, String itemIdColumn, String cfItem) {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "sale_repair";
        c.itemTable = itemTable;
        c.itemFkColumn = "repair_id";
        c.docIdColumn = "repair_id";
        c.itemIdColumn = itemIdColumn;
        c.cfEntityDoc = "sale_repair";
        c.cfEntityItem = cfItem;
        c.docNoExpr = "o.repair_no";
        // 售后维修单没有销售员/部门列：数据权限按制单人（与列表页服务层同口径）
        c.dataScopeOwnerColumn = null;
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "repairDate";
        c.defaultOrderBy = " ORDER BY o.repair_date DESC NULLS LAST, o.repair_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add("LEFT JOIN warehouse w ON w.stock_id = o.warehouse_id");
        c.extraJoins.add("LEFT JOIN warehouse pw ON pw.stock_id = o.parts_warehouse_id");
        return c;
    }

    /** 表头列（两张明细的来源配置共用同一份；不要在其中一处增删，否则合并后另一边会变空） */
    private static void addDocColumns(BillDetailReportConfig c) {
        c.add(new Col("repairNo", "维修单号", G_DOC, "text", "o.repair_no", true, "left", true))
                .filter("text", true);
        c.add(new Col("customerName", "客户", G_DOC, "text", "o.customer_name", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("repairDate", "维修日期", G_DOC, "date", "o.repair_date", true, null, true))
                .filter("dateRange", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("serviceType", "服务方式", G_DOC, "text", "o.service_type", false, null, false))
                .filter("text", false);
        c.add(new Col("warrantyStatus", "保修状态（整单）", G_DOC, "text", "o.warranty_status", false, null, false))
                .filter("text", false);
        c.add(new Col("repairResult", "维修结果", G_DOC, "text", "o.repair_result", false, null, false))
                .filter("text", false);
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("sourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false))
                .filter("text", false);
        c.add(new Col("contactPerson", "联系人", G_DOC, "text", "o.contact_person", false, null, false))
                .filter("text", false);
        c.add(new Col("contactPhone", "联系电话", G_DOC, "text", "o.contact_phone", false, null, false))
                .filter("text", false);
        c.add(new Col("faultDescription", "故障描述（整单）", G_DOC, "text", "o.fault_description", false, null, false));
        c.add(new Col("receiveDate", "收件日期", G_DOC, "date", "o.receive_date", false, null, false))
                .filter("dateRange", false);
        c.add(new Col("returnDate", "返还日期", G_DOC, "date", "o.return_date", false, null, false))
                .filter("dateRange", false);
        c.add(new Col("receivedQuantity", "收件数量（整单）", G_DOC, "number", "o.received_quantity", false, null, true));
        c.add(new Col("returnedQuantity", "返还数量（整单）", G_DOC, "number", "o.returned_quantity", false, null, true));
        c.add(new Col("repairFee", "维修费", G_DOC, "amount", "o.repair_fee", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("partsFee", "配件费", G_DOC, "amount", "o.parts_fee", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("totalFee", "合计费用", G_DOC, "amount", "o.total_fee", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("warehouseName", "维修仓", G_DOC, "text", "w.stock_name", false, null, false));
        c.add(new Col("partsWarehouseName", "备件仓", G_DOC, "text", "pw.stock_name", false, null, false));
        c.add(new Col("transferredBillType", "转出单据类型", G_DOC, "text", "o.transferred_bill_type", false, null, false));
        c.add(new Col("transferredBillNo", "转出单据号", G_DOC, "text", "o.transferred_bill_no", false, null, false))
                .filter("text", false);
        c.add(new Col("receivedInboundNo", "收件入库单号", G_DOC, "text", "o.received_inbound_no", false, null, false));
        c.add(new Col("returnedIssueNo", "返还出库单号", G_DOC, "text", "o.returned_issue_no", false, null, false));
        c.add(new Col("receiptNo", "收款单号", G_DOC, "text", "o.receipt_no", false, null, false));
        c.add(new Col("totalQuantity", "合计数量（整单）", G_DOC, "number", "o.total_quantity", false, null, true));
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false))
                .filter("text", false);

        c.docTotalKeys.addAll(java.util.List.of(
                "totalQuantity", "receivedQuantity", "returnedQuantity",
                "repairFee", "partsFee", "totalFee"));
    }
}
