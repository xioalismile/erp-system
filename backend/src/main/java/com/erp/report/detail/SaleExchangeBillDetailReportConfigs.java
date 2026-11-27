package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 销售换货单明细报表的列配置（2026-11-22 补齐）。
 *
 * <p>背景：列表页「销售换货单」已登记明细模式（billDetailView.js → /report/sale/exchange-detail），
 * 但后端一直没有对应接口 → 进入页面即弹「接口不存在」。本配置与销售退货/报价同一套引擎
 * {@link BillDetailReportEngine}，只描述换货单字段，无独立业务逻辑。
 *
 * <p>逐列实查口径（DDL 见 SchemaUpgradeEnsurer 的 sale_exchange / sale_exchange_item）：
 * <ul>
 *   <li>主键 exchange_id / exchange_item_id；明细排序列 line_no；</li>
 *   <li>表头有 saler_id / dept_id / created_by / created_at，数据权限沿用销售口径（saler_id + created_by）；</li>
 *   <li><b>表头没有 discount_rate 列</b>（只有 discount_amount）→ 不登记整单折扣率，避免查询报列不存在；</li>
 *   <li>明细无仓库/货位/批次/成本列 → 不登记这些列，不硬凑。</li>
 * </ul>
 */
public final class SaleExchangeBillDetailReportConfigs {

    private SaleExchangeBillDetailReportConfigs() {
    }

    public static BillDetailReportConfig exchangeConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "sale_exchange";
        c.itemTable = "sale_exchange_item";
        c.itemFkColumn = "exchange_id";
        c.docIdColumn = "exchange_id";
        c.itemIdColumn = "exchange_item_id";
        c.cfEntityDoc = "sale_exchange";
        c.cfEntityItem = "sale_exchange_item";
        c.docNoExpr = "o.exchange_no";
        c.dataScopeOwnerColumn = "saler_id";
        c.docDateFilterKey = "exchangeDate";
        c.defaultOrderBy = " ORDER BY o.exchange_date DESC NULLS LAST, o.exchange_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add("LEFT JOIN customer c ON c.customer_id = o.customer_id");
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.saler_id");
        c.extraJoins.add("LEFT JOIN department dp ON dp.dept_id = o.dept_id");
        c.extraJoins.add("LEFT JOIN currency cu ON cu.currency_id = o.currency_id");
        c.extraJoins.add("LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id");
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("exchangeNo", "换货单号", G_DOC, "text", "o.exchange_no", true, "left", true))
                .filter("text", true);
        c.add(new Col("customerName", "客户", G_DOC, "text", "c.customer_name", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("exchangeDate", "换货日期", G_DOC, "date", "o.exchange_date", true, null, true))
                .filter("dateRange", true);
        c.add(new Col("salerName", "销售员", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.saler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("contactPerson", "联系人", G_DOC, "text", "o.contact_person", false, null, false))
                .filter("text", false);
        c.add(new Col("contactPhone", "联系电话", G_DOC, "text", "o.contact_phone", false, null, false))
                .filter("text", false);
        c.add(new Col("deliveryAddress", "收货地址", G_DOC, "text", "o.delivery_address", false, null, false))
                .filter("text", false);
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false))
                .filter("text", false);
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("deptName", "所属部门", G_DOC, "text", "dp.dept_name", false, null, false))
                .select("department", "long", "o.dept_id", false);
        c.add(new Col("totalQuantity", "合计数量（整单）", G_DOC, "number", "o.total_quantity", false, null, true));
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("discountAmount", "折扣额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("taxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("netAmount", "价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("generatedDeliveryNo", "已生成出库单号", G_DOC, "text", "o.generated_delivery_no", false, null, false));
        // 2026-11-27 明细列设置补单据字段（汇总列表有「审核」、明细缺）
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false))
                .filter("text", false);

        c.add(new Col("lineNo", "行号", G_ITEM, "int", "i.line_no", false, null, true));
        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        c.add(new Col("materialName", "商品名称", G_ITEM, "text", "i.material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("materialCode", "商品编号", G_ITEM, "text", "i.material_code", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", "u.unit_name", false, null, false));
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("itemDiscountRate", "明细折扣率%", G_ITEM, "number", "i.discount_rate", false, null, true));
        c.add(new Col("itemDiscountAmount", "明细折扣额", G_ITEM, "amount", "i.discount_amount", false, null, true));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("itemTaxAmount", "明细税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", "i.net_amount", false, null, true));
        c.add(new Col("originMaterialCode", "原商品编号", G_ITEM, "text", "i.origin_material_code", false, null, false))
                .filter("text", false);
        c.add(new Col("originMaterialName", "原商品名称", G_ITEM, "text", "i.origin_material_name", false, null, false))
                .filter("text", false);
        c.add(new Col("routeType", "处理方式", G_ITEM, "text", "i.route_type", false, null, false));
        c.add(new Col("returnedQuantity", "已退数量", G_ITEM, "number", "i.returned_quantity", false, null, false));
        c.add(new Col("substituteReturnedQuantity", "替代品退回数量", G_ITEM, "number", "i.substitute_returned_quantity", false, null, false));
        c.add(new Col("pendingReturnQuantity", "待退数量", G_ITEM, "number", "i.pending_return_quantity", false, null, false));
        c.add(new Col("waivedQuantity", "免退数量", G_ITEM, "number", "i.waived_quantity", false, null, false));
        c.add(new Col("turnedDeliveryQuantity", "已转出库数量", G_ITEM, "number", "i.turned_delivery_quantity", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceLineNo", "来源行号", G_ITEM, "int", "i.source_line_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalQuantity", "totalAmount", "discountAmount", "taxAmount", "netAmount"));
        return c;
    }
}
