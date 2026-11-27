package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_CF_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_CF_ITEM;
import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 另外三个销售单据明细报表的列配置（销售合同 / 销售退货单 / 销售报价单）。
 *
 * <p>与 {@link SaleBillDetailReportConfigs}（销售订单 / 销售出库 / 销售发票）同一套引擎
 * {@link BillDetailReportEngine}、同一套写法；三个配置放在同一个文件里便于横向比对，不漏东西。
 *
 * <p>逐表实证口径（写死前均实查过实体 @TableField 与 db 补丁脚本）：
 * <ul>
 *   <li>sale_contract / sale_contract_item：主键 contract_id / item_id，明细排序列是 sort_no（无 line_no），
 *       表头<b>无 created_by 列</b>（db/26 兜底建表 DDL 无此列、后续补丁也未加）→
 *       数据权限只能走业务归属 saler_id，created_by 维度必须置 null</li>
 *   <li>sale_return / sale_return_item：主键 return_id / return_item_id；明细仓库列是 stock_id（不是 warehouse_id），
 *       有 line_no / batch_no / bin_id / source_line_no / return_reason；表头有 created_by / created_at</li>
 *   <li>sale_quote / sale_quote_item：主键 quote_id / quote_item_id；有 line_no / is_free / turned_order_quantity /
 *       delivery_days；<b>无仓库维度</b>（表头明细均无 warehouse 列）→ 不加仓库 JOIN；
 *       明细无来源单号列（报价单本身是源头单）</li>
 *   <li>三张明细表均有 material_id → 都挂物料主档图片列；price_with_tax / price_without_tax 由
 *       db/139 补丁统一补列，可查</li>
 * </ul>
 */
public final class SaleExtraBillDetailReportConfigs {

    private SaleExtraBillDetailReportConfigs() {
    }

    /** 三个报表共用的关联片段：客户 / 销售员 / 币种 / 单位（其余按单据追加） */
    private static final String JOIN_CUSTOMER = "LEFT JOIN customer c ON c.customer_id = o.customer_id";
    private static final String JOIN_EMPLOYEE = "LEFT JOIN employee e ON e.employee_id = o.saler_id";
    private static final String JOIN_CURRENCY = "LEFT JOIN currency cu ON cu.currency_id = o.currency_id";
    private static final String JOIN_UNIT = "LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id";
    /** 物料主档（图片列数据源）：与销售订单等既有明细报表同字段 */
    private static final String JOIN_MATERIAL = "LEFT JOIN material m ON m.material_id = i.material_id";

    // ==================================================================
    // 销售合同明细报表
    // ==================================================================
    public static BillDetailReportConfig contractConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "sale_contract";
        c.itemTable = "sale_contract_item";
        c.itemFkColumn = "contract_id";
        c.docIdColumn = "contract_id";
        c.itemIdColumn = "item_id";
        c.cfEntityDoc = "sale_contract";
        c.cfEntityItem = "sale_contract_item";
        c.docNoExpr = "o.contract_no";
        c.dataScopeOwnerColumn = "saler_id";     // 实查 SaleContract：有 saler_id
        // 实查：sale_contract 无 created_by 列（db/26 兜底建表 DDL 无、后续补丁未加），必须置 null，
        // 否则引擎会把 o.created_by 拼进 WHERE 直接报表列不存在报错；数据权限只走 saler_id 维度
        c.dataScopeCreatedByColumn = null;
        c.docDateFilterKey = "contractDate";
        // 明细排序列是 sort_no（合同明细无 line_no）
        c.defaultOrderBy = " ORDER BY o.contract_date DESC NULLS LAST, o.contract_no DESC NULLS LAST, i.sort_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_CUSTOMER);
        c.extraJoins.add(JOIN_EMPLOYEE);
        c.extraJoins.add("LEFT JOIN department dp ON dp.dept_id = o.department_id");
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add("LEFT JOIN payment_term pt ON pt.payment_term_id = o.payment_term_id");
        c.extraJoins.add("LEFT JOIN warehouse w ON w.stock_id = o.warehouse_id");
        c.extraJoins.add("LEFT JOIN price_level pl ON pl.price_level_id = o.price_level_id");
        c.extraJoins.add("LEFT JOIN employee pk ON pk.employee_id = o.picker_id");
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add("LEFT JOIN warehouse iw ON iw.stock_id = i.warehouse_id");
        c.extraJoins.add(JOIN_MATERIAL);

        c.add(new Col("contractNo", "合同号", G_DOC, "text", "o.contract_no", true, "left", true))
                .filter("text", true);
        c.add(new Col("customerName", "客户", G_DOC, "text", "c.customer_name", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("contractDate", "合同日期", G_DOC, "date", "o.contract_date", true, null, true))
                .filter("dateRange", true);
        c.add(new Col("salerName", "销售员", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.saler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("contractType", "合同类型", G_DOC, "text", "o.contract_type", false, null, false));
        c.add(new Col("validityStart", "有效期起", G_DOC, "date", "o.validity_start", false, null, true));
        c.add(new Col("validityEnd", "有效期止", G_DOC, "date", "o.validity_end", false, null, true));
        c.add(new Col("customerNo", "客户编号", G_DOC, "text", "o.customer_no", false, null, true));
        c.add(new Col("customerShortName", "客户简称", G_DOC, "text", "o.customer_short_name", false, null, true));
        c.add(new Col("contactPerson", "联系人", G_DOC, "text", "o.contact_person", false, null, false));
        c.add(new Col("contactPhone", "联系电话", G_DOC, "text", "o.contact_phone", false, null, false));
        c.add(new Col("receiveAddress", "收货地址", G_DOC, "text", "o.receive_address", false, null, false));
        c.add(new Col("deptName", "所属部门", G_DOC, "text", "dp.dept_name", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true));
        c.add(new Col("paymentTermName", "付款条件", G_DOC, "text", "pt.payment_term_name", false, null, false));
        c.add(new Col("paymentMethod", "付款方式", G_DOC, "text", "o.payment_method", false, null, false))
                .select("paymentMethod", "string", "o.payment_method", false);
        c.add(new Col("depositAmount", "定金金额（整单）", G_DOC, "amount", "o.deposit_amount", false, null, true));
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", false, null, false))
                .select("warehouse", "long", "COALESCE(i.warehouse_id, o.warehouse_id)", false);
        c.add(new Col("priceLevelName", "价格等级", G_DOC, "text", "pl.price_level_name", false, null, false));
        c.add(new Col("totalAmount", "合同金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("discountRate", "整单折扣率%", G_DOC, "number", "o.discount_rate", false, null, true));
        c.add(new Col("discountAmount", "折扣额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true));
        c.add(new Col("taxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("netAmount", "价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true));
        c.add(new Col("deliveryDate", "交货日期", G_DOC, "date", "o.delivery_date", false, null, true));
        c.add(new Col("isTax", "是否含税", G_DOC, "text", "o.is_tax", false, null, false));
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("isInvoiced", "是否开票", G_DOC, "text", "o.is_invoiced", false, null, false));
        c.add(new Col("isConsignment", "是否代发", G_DOC, "text", "o.is_consignment", false, null, false));
        c.add(new Col("pickerName", "拣货人", G_DOC, "text", "pk.employee_name", false, null, false));
        c.add(new Col("logisticsCompany", "物流公司", G_DOC, "text", "o.logistics_company", false, null, false));
        c.add(new Col("logisticsNo", "物流单号", G_DOC, "text", "o.logistics_no", false, null, false));
        c.add(new Col("invoiceType", "开票类型", G_DOC, "text", "o.invoice_type", false, null, false));
        c.add(new Col("projectNo", "项目号", G_DOC, "text", "o.project_no", false, null, false));
        c.add(new Col("customerOrderNo", "客户订单号", G_DOC, "text", "o.customer_order_no", false, null, false));
        // 2026-11-27 明细列设置补单据字段（汇总列表有「审核」、明细缺）
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("opportunityNo", "商机编号", G_DOC, "text", "o.opportunity_no", false, null, false));
        c.add(new Col("sourceBillType", "源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));

        c.add(new Col("sortNo", "行号", G_ITEM, "int", "i.sort_no", false, null, true));
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
        c.add(new Col("avgCost", "成本", G_ITEM, "number", "i.avg_cost", false, null, false));
        // 合同明细的交货日期落库为 VARCHAR(50)（db/26 DDL），按文本取数，不当日期解析
        c.add(new Col("itemDeliveryDate", "明细交货日期", G_ITEM, "text", "i.delivery_date", false, null, false));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.warehouse_id", false);
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "discountAmount", "taxAmount", "netAmount", "depositAmount"));
        return c;
    }

    // ==================================================================
    // 销售退货单明细报表
    // ==================================================================
    public static BillDetailReportConfig returnConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "sale_return";
        c.itemTable = "sale_return_item";
        c.itemFkColumn = "return_id";
        c.docIdColumn = "return_id";
        c.itemIdColumn = "return_item_id";
        c.cfEntityDoc = "sale_return";
        c.cfEntityItem = "sale_return_item";
        c.docNoExpr = "o.return_no";
        c.dataScopeOwnerColumn = "saler_id";     // 实查 SaleReturn：有 saler_id
        // 实查 SaleReturn：created_by / created_at 都有（FieldFill.INSERT），沿用默认 created_by
        c.docDateFilterKey = "returnDate";
        c.defaultOrderBy = " ORDER BY o.return_date DESC NULLS LAST, o.return_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_CUSTOMER);
        c.extraJoins.add(JOIN_EMPLOYEE);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add("LEFT JOIN warehouse w ON w.stock_id = o.warehouse_id");
        c.extraJoins.add("LEFT JOIN payment_term pt ON pt.payment_term_id = o.payment_term_id");
        c.extraJoins.add("LEFT JOIN price_level pl ON pl.price_level_id = o.price_level_id");
        c.extraJoins.add("LEFT JOIN settlement_mode sm ON sm.settle_mode_id = o.settle_mode_id");
        c.extraJoins.add(JOIN_UNIT);
        // 退货明细的仓库列名是 stock_id（不是 warehouse_id），与销售出库明细同款
        c.extraJoins.add("LEFT JOIN warehouse iw ON iw.stock_id = i.stock_id");
        c.extraJoins.add("LEFT JOIN location b ON b.bin_id = i.bin_id");
        c.extraJoins.add(JOIN_MATERIAL);

        c.add(new Col("returnNo", "退货单号", G_DOC, "text", "o.return_no", true, "left", true))
                .filter("text", true);
        c.add(new Col("customerName", "客户", G_DOC, "text", "c.customer_name", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("returnDate", "退货日期", G_DOC, "date", "o.return_date", true, null, true))
                .filter("dateRange", true);
        c.add(new Col("salerName", "销售员", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.saler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", false, null, false))
                .select("warehouse", "long", "COALESCE(i.stock_id, o.warehouse_id)", false);
        c.add(new Col("contactPerson", "联系人", G_DOC, "text", "o.contact_person", false, null, false));
        c.add(new Col("contactPhone", "联系电话", G_DOC, "text", "o.contact_phone", false, null, false));
        c.add(new Col("deliveryAddress", "收货地址", G_DOC, "text", "o.delivery_address", false, null, false));
        c.add(new Col("docReturnReason", "退货原因（整单）", G_DOC, "text", "o.return_reason", false, null, false));
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true));
        c.add(new Col("priceLevelName", "价格等级", G_DOC, "text", "pl.price_level_name", false, null, false));
        c.add(new Col("paymentTermName", "付款条件", G_DOC, "text", "pt.payment_term_name", false, null, false));
        c.add(new Col("settleModeName", "结算方式", G_DOC, "text", "sm.settle_mode_name", false, null, false))
                .select("settleMode", "long", "o.settle_mode_id", false);
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("discountRate", "整单折扣率%", G_DOC, "number", "o.discount_rate", false, null, true));
        c.add(new Col("discountAmount", "折扣额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true));
        c.add(new Col("taxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("netAmount", "价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true));
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("prepaidRate", "预付比例%", G_DOC, "number", "o.prepaid_rate", false, null, true));
        c.add(new Col("prepaidAmount", "预付款金额（整单）", G_DOC, "amount", "o.prepaid_amount", false, null, true));
        c.add(new Col("deliveryNo", "出库单号", G_DOC, "text", "o.delivery_no", false, null, false));
        c.add(new Col("sourceDeliveryNo", "源出库单号", G_DOC, "text", "o.source_delivery_no", false, null, false));
        c.add(new Col("invoiceNo", "发票号", G_DOC, "text", "o.invoice_no", false, null, false));
        c.add(new Col("originalInvoiceNo", "原蓝字发票号", G_DOC, "text", "o.original_invoice_no", false, null, false));
        // 2026-11-27 明细列设置补单据字段（汇总列表有「审核」、明细缺）
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
        c.add(new Col("sourceBillType", "源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));

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
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("binName", "货位", G_ITEM, "text", "b.bin_name", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("deliveryLineNo", "出库单行号", G_ITEM, "int", "i.delivery_line_no", false, null, false));
        // 【2026-10-14 第二批】已开票数量改下游发票明细实时聚合（原读落库列：演示假值会假报已开票）
        c.add(new Col("turnedInvoiceQuantity", "已开票数量", G_ITEM, "number",
                RowWritebackSql.saleInvoiceQty("i.return_item_id", "o.return_no", "i.material_code"), false, null, false));
        // 行级回写展示列（2026-10-14）：退货单明细已落库的回写字段只有"已开票数量"
        // 【2026-10-14 修复】取数改为下游发票明细实时聚合（原读落库列，演示假值会假报已开票）。
        c.add(new Col("rowInvoiceStatus", "开票情况", G_ITEM, "text",
                "'已开 ' || " + RowWritebackSql.saleInvoiceQty("i.return_item_id", "o.return_no", "i.material_code")
                        + " || ' / ' || COALESCE(i.quantity,0)", false, null, false));
        c.reportHiddenKeys.add("rowInvoiceStatus");
        c.add(new Col("returnReason", "退货原因", G_ITEM, "text", "i.return_reason", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceLineNo", "来源行号", G_ITEM, "int", "i.source_line_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "discountAmount", "taxAmount", "netAmount", "prepaidAmount"));
        return c;
    }

    // ==================================================================
    // 销售报价单明细报表
    // ==================================================================
    public static BillDetailReportConfig quoteConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "sale_quote";
        c.itemTable = "sale_quote_item";
        c.itemFkColumn = "quote_id";
        c.docIdColumn = "quote_id";
        c.itemIdColumn = "quote_item_id";
        c.cfEntityDoc = "sale_quote";
        c.cfEntityItem = "sale_quote_item";
        c.docNoExpr = "o.quote_no";
        c.dataScopeOwnerColumn = "saler_id";     // 实查 SaleQuote：有 saler_id
        // 实查 SaleQuote：created_by / created_at 都有（FieldFill.INSERT），沿用默认 created_by
        c.docDateFilterKey = "quoteDate";
        c.defaultOrderBy = " ORDER BY o.quote_date DESC NULLS LAST, o.quote_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_CUSTOMER);
        c.extraJoins.add(JOIN_EMPLOYEE);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add("LEFT JOIN payment_term pt ON pt.payment_term_id = o.payment_term_id");
        c.extraJoins.add("LEFT JOIN price_level pl ON pl.price_level_id = o.price_level_id");
        c.extraJoins.add("LEFT JOIN settlement_mode sm ON sm.settle_mode_id = o.settle_mode_id");
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add(JOIN_MATERIAL);

        c.add(new Col("quoteNo", "报价单号", G_DOC, "text", "o.quote_no", true, "left", true))
                .filter("text", true);
        c.add(new Col("customerName", "客户", G_DOC, "text", "c.customer_name", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("quoteDate", "报价日期", G_DOC, "date", "o.quote_date", true, null, true))
                .filter("dateRange", true);
        c.add(new Col("salerName", "销售员", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.saler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("customerShortName", "客户简称", G_DOC, "text", "o.customer_short_name", false, null, true));
        c.add(new Col("contactPerson", "联系人", G_DOC, "text", "o.contact_person", false, null, false));
        c.add(new Col("contactPhone", "联系电话", G_DOC, "text", "o.contact_phone", false, null, false));
        c.add(new Col("deliveryAddress", "收货地址", G_DOC, "text", "o.delivery_address", false, null, false));
        c.add(new Col("opportunityNo", "商机编号", G_DOC, "text", "o.opportunity_no", false, null, false));
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true));
        c.add(new Col("paymentTermName", "付款条件", G_DOC, "text", "pt.payment_term_name", false, null, false));
        c.add(new Col("settleModeName", "结算方式", G_DOC, "text", "sm.settle_mode_name", false, null, false))
                .select("settleMode", "long", "o.settle_mode_id", false);
        c.add(new Col("priceLevelName", "价格等级", G_DOC, "text", "pl.price_level_name", false, null, false));
        c.add(new Col("totalAmount", "报价金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("discountRate", "整单折扣率%", G_DOC, "number", "o.discount_rate", false, null, true));
        c.add(new Col("discountAmount", "折扣额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true));
        c.add(new Col("taxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("netAmount", "价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true));
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("validFrom", "有效期起", G_DOC, "date", "o.valid_from", false, null, true));
        c.add(new Col("validTo", "有效期止", G_DOC, "date", "o.valid_to", false, null, true));
        c.add(new Col("deliveryDate", "交货日期", G_DOC, "date", "o.delivery_date", false, null, true));
        c.add(new Col("prepaidRate", "预付比例%", G_DOC, "number", "o.prepaid_rate", false, null, true));
        c.add(new Col("prepaidAmount", "预付款金额（整单）", G_DOC, "amount", "o.prepaid_amount", false, null, true));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
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
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));

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
        // 是否赠品：静态选项里没有 isFree 来源，且基类 staticOptions 不允许改（禁止改既有文件），
        // 故不做下拉筛选，仅作普通整型列展示（0=否 1=是）
        c.add(new Col("isFree", "是否赠品", G_ITEM, "int", "i.is_free", false, null, false));
        // 【2026-10-14 第二批】已转订单数量改下游订单明细实时聚合（原读落库列：演示假值会假报已下单）
        c.add(new Col("turnedOrderQuantity", "已转订单数量", G_ITEM, "number",
                RowWritebackSql.saleOrderQty("i.quote_item_id", "o.quote_no", "i.material_code"), false, null, false));
        // 行级回写展示列（2026-10-14）：报价单明细已落库的回写字段 = 已转订单数量（报价→销售订单）
        // 【2026-10-14 修复】取数改为下游订单明细实时聚合（原读落库列，演示假值会假报已下单）。
        c.add(new Col("rowOrderStatus", "下单情况", G_ITEM, "text",
                "'已下单 ' || " + RowWritebackSql.saleOrderQty("i.quote_item_id", "o.quote_no", "i.material_code")
                        + " || ' / ' || COALESCE(i.quantity,0)", false, null, false));
        c.reportHiddenKeys.add("rowOrderStatus");
        c.add(new Col("itemDeliveryDate", "明细交货日期", G_ITEM, "date", "i.delivery_date", false, null, false));
        c.add(new Col("deliveryDays", "备货天数", G_ITEM, "int", "i.delivery_days", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "discountAmount", "taxAmount", "netAmount", "prepaidAmount"));
        return c;
    }
}
