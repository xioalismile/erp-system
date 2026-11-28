package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_CF_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_CF_ITEM;
import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 三个销售单据明细报表的列配置（销售订单 / 销售出库 / 销售发票）。
 *
 * <p>三个配置**放在同一个文件里**是刻意的：这样"三个报表功能是否一致"可以一眼横向比对，
 * 不会出现改了一个漏了另外两个。业务逻辑全在 {@link BillDetailReportEngine} 唯一一份实现里。
 *
 * <p>共同约定（三个报表完全一致，作为"复刻不得缺东西"的硬口径）：
 * <ul>
 *   <li>默认可见列 7 列且顺序一致：单号 / 客户名称 / 日期 / 销售员 / 商品名称 / 商品编号 / 规格</li>
 *   <li>默认勾选的搜索条件 7 项（同上，商品名称/编号/规格拆成 3 个独立条件）</li>
 *   <li>单据日期标记 defaultCurrentMonth → 前端预置「本月 1 日 ~ 今天」</li>
 *   <li>单据信息组在商品明细组之前；自定义字段组在最后</li>
 *   <li>列 key 必须避开前端 FORCE_VISIBLE_PROPS（sourceBillNo / sourceNo / sourceLineNo），
 *       否则用户永远关不掉那一列</li>
 *   <li>明细级金额列 = 数量 / 明细折扣额 / 金额 / 明细税额 / 明细价税合计（直接求和）
 *       单据级金额列 = 合计金额 / 折扣额 / 税额 / 价税合计 / 预付款金额（按单据去重求和）</li>
 *   <li>额外的关联主档：客户 / 销售员 / 部门 / 币种 / 仓库 / 结算方式 / 单位 / 储位</li>
 * </ul>
 */
public final class SaleBillDetailReportConfigs {

    private SaleBillDetailReportConfigs() {
    }

    /** 三个报表共用的关联片段：客户 / 销售员 / 币种 / 单位（其余按单据追加） */
    private static final String JOIN_CUSTOMER = "LEFT JOIN customer c ON c.customer_id = o.customer_id";
    private static final String JOIN_EMPLOYEE = "LEFT JOIN employee e ON e.employee_id = o.saler_id";
    private static final String JOIN_CURRENCY = "LEFT JOIN currency cu ON cu.currency_id = o.currency_id";
    private static final String JOIN_UNIT = "LEFT JOIN unit_of_measure u ON u.unit_id = i.unit_id";

    // ==================================================================
    // 销售订单明细报表
    // ==================================================================
    public static BillDetailReportConfig orderConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "sale_order";
        c.itemTable = "sale_order_item";
        c.itemFkColumn = "order_id";
        c.docIdColumn = "order_id";
        c.itemIdColumn = "order_item_id";
        c.cfEntityDoc = "sale_order";
        c.cfEntityItem = "sale_order_item";
        c.docNoExpr = "o.order_no";
        c.dataScopeOwnerColumn = "saler_id";     // 销售单据的业务归属维度（实查 SaleDeliveryServiceImpl/销售列表口径）
        c.docDateFilterKey = "orderDate";
        c.defaultOrderBy = " ORDER BY o.order_date DESC NULLS LAST, o.order_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_CUSTOMER);
        c.extraJoins.add(JOIN_EMPLOYEE);
        c.extraJoins.add("LEFT JOIN department dp ON dp.dept_id = o.department_id");
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add("LEFT JOIN payment_term pt ON pt.payment_term_id = o.payment_term_id");
        c.extraJoins.add("LEFT JOIN warehouse w ON w.stock_id = o.warehouse_id");
        c.extraJoins.add("LEFT JOIN price_level pl ON pl.price_level_id = o.price_level_id");
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add("LEFT JOIN warehouse iw ON iw.stock_id = i.warehouse_id");
        c.extraJoins.add("LEFT JOIN location b ON b.bin_id = i.bin_id");
        // 物料主档（图片列数据源）：明细行带 material_id，取物料库的图片串（与物料库弹窗同字段）
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("orderNo", "订单编号", G_DOC, "text", "o.order_no", true, "left", true))
                .filter("text", true);
        c.add(new Col("customerName", "客户名称", G_DOC, "text", "c.customer_name", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("orderDate", "单据日期", G_DOC, "date", "o.order_date", true, null, true))
                .filter("dateRange", true);
        c.add(new Col("salerName", "销售员", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.saler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        // 2026-11-14：明细模式补「计税方式」（编辑页已有；默认隐藏，列设置可勾）
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("customerNo", "客户编号", G_DOC, "text", "o.customer_no", false, null, true))
                .filter("text", false);
        c.add(new Col("customerShortName", "客户简称", G_DOC, "text", "o.customer_short_name", false, null, true))
                .filter("text", false);
        c.add(new Col("contactPerson", "联系人", G_DOC, "text", "o.contact_person", false, null, false))
                .filter("text", false);
        c.add(new Col("contactPhone", "联系电话", G_DOC, "text", "o.contact_phone", false, null, false))
                .filter("text", false);
        c.add(new Col("receiveAddress", "收货地址", G_DOC, "text", "o.receive_address", false, null, false))
                .filter("text", false);
        c.add(new Col("deptName", "所属部门", G_DOC, "text", "dp.dept_name", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("opportunityNo", "商机编号", G_DOC, "text", "o.opportunity_no", false, null, false))
                .filter("text", false);
        c.add(new Col("quoteNo", "来源报价单号", G_DOC, "text", "o.quote_no", false, null, false))
                .filter("text", false);
        c.add(new Col("customerOrderNo", "客户订单号", G_DOC, "text", "o.customer_order_no", false, null, false))
                .filter("text", false);
        c.add(new Col("projectNo", "项目号", G_DOC, "text", "o.project_no", false, null, false))
                .filter("text", false);
        c.add(new Col("businessType", "业务类型", G_DOC, "text", "o.business_type", false, null, false))
                .filter("text", false);
        c.add(new Col("orderSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false))
                .filter("text", false);
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("paymentTermName", "付款条件", G_DOC, "text", "pt.payment_term_name", false, null, false));
        c.add(new Col("paymentMethod", "付款方式", G_DOC, "text", "o.payment_method", false, null, false))
                .select("paymentMethod", "string", "o.payment_method", false);
        c.add(new Col("accountName", "收款账户", G_DOC, "text", "o.account_name", false, null, false))
                .filter("text", false);
        c.add(new Col("transportMethod", "运输方式", G_DOC, "text", "o.transport_method", false, null, false))
                .select("transportMethod", "string", "o.transport_method", false);
        c.add(new Col("logisticsCompany", "物流公司", G_DOC, "text", "o.logistics_company", false, null, false))
                .filter("text", false);
        c.add(new Col("logisticsNo", "物流单号", G_DOC, "text", "o.logistics_no", false, null, false))
                .filter("text", false);
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", false, null, false))
                .select("warehouse", "long", "COALESCE(i.warehouse_id, o.warehouse_id)", false);
        c.add(new Col("priceLevelName", "价格等级", G_DOC, "text", "pl.price_level_name", false, null, false))
                .select("priceLevel", "long", "o.price_level_id", false);
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("discountRate", "整单折扣率%", G_DOC, "number", "o.discount_rate", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("discountAmount", "折扣额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("taxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("netAmount", "价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true))
                .filter("numberRange", false);
        c.add(new Col("receivedAmount", "已收金额（整单）", G_DOC, "amount", "o.received_amount", false, null, true));
        // 定金（2026-10-14 筛选条件自定义补登记）：列登记进 reportHiddenKeys —— 明细报表默认列不变（守则 2.7.2-6），
        // 仅让明细模式能按定金区间过滤；列表页需要显示时可自行在列设置勾出
        c.add(new Col("depositAmount", "定金", G_DOC, "amount", "o.deposit_amount", false, null, false))
                .filter("numberRange", false);
        c.add(new Col("deliveryDate", "交货日期", G_DOC, "date", "o.delivery_date", false, null, true))
                .filter("dateRange", false);
        c.add(new Col("isTax", "是否含税", G_DOC, "text", "o.is_tax", false, null, false));
        // 2026-11-27 明细列设置补单据字段（汇总列表有、明细缺）：审核状态 / 超量发货比例
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("overshipRatio", "超量发货比例", G_DOC, "number", "o.overship_ratio", false, null, false));
        // 与采购单据同一套标准单据字段：附件 / 组织编码 / 本位币金额（此前明细列设置里没有）
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
        c.add(new Col("baseTotalAmount", "本位币合计金额", G_DOC, "amount", "o.base_total_amount", false, null, true));
        c.add(new Col("baseTaxAmount", "本位币税额", G_DOC, "amount", "o.base_tax_amount", false, null, true));
        c.add(new Col("baseNetAmount", "本位币价税合计", G_DOC, "amount", "o.base_net_amount", false, null, true));
        c.add(new Col("invoiceType", "发票类型", G_DOC, "text", "o.invoice_type", false, null, false))
                .filter("text", false);
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false))
                .filter("text", false);

        c.add(new Col("lineNo", "行号", G_ITEM, "int", "i.line_no", false, null, true));
        // 图片列（2026-10-12 用户口径）：固定在商品编号前面，取物料库图片；type=image 前端缩略图渲染、不做筛选
        c.add(new Col("image", "图片", G_ITEM, "image", "m.image_urls", false, null, false));
        c.add(new Col("materialName", "商品名称", G_ITEM, "text", "i.material_name", true, null, true))
                .filter("text", true);
        c.add(new Col("materialCode", "商品编号", G_ITEM, "text", "i.material_code", true, null, true))
                .filter("text", true);
        c.add(new Col("specification", "规格", G_ITEM, "text", "i.specification", true, null, true))
                .filter("text", true);
        c.add(new Col("unitName", "单位", G_ITEM, "text", "u.unit_name", false, null, false));
        c.add(new Col("quantity", "数量", G_ITEM, "number", "i.quantity", false, null, true));
        // 【2026-10-14 修复】已发/未发与下面全部行级回写列改为「下游单据明细实时聚合」：
        // 原 delivered_quantity 全系统没有任何回写点（死列，仅演示数据塞过假值），
        // 真实下推生效后回写列不动 + 无下游的单据被演示假值假报"有下推"（用户实锤反馈）。
        // 取数口径 = 汇总页"下推状态"服务同款：已生效类下游计入数量、已作废剔除、草稿/待审核不计。
        c.add(new Col("deliveredQuantity", "已发货数量", G_ITEM, "number",
                RowWritebackSql.saleDeliveryQty("i.order_item_id", "o.order_no", "i.material_code"), false, null, true));
        c.add(new Col("undeliveredQuantity", "未发货数量", G_ITEM, "number",
                "COALESCE(i.quantity,0) - " + RowWritebackSql.saleDeliveryQty("i.order_item_id", "o.order_no", "i.material_code"), false, null, false));
        // 行级回写展示列（2026-10-12 列表页明细模式默认列；2026-10-14 起取数改为下游实时聚合）。
        // 登记进 reportHiddenKeys：既有"销售订单明细报表"默认视图保持不变（守则 2.7.2-6），
        // 报表用户想要可在列设置手动勾出；列表页明细模式按自己的默认列清单取这几列。
        c.add(new Col("rowDeliveryStatus", "发货情况", G_ITEM, "text",
                "'已发 ' || " + RowWritebackSql.saleDeliveryQty("i.order_item_id", "o.order_no", "i.material_code")
                        + " || ' / ' || COALESCE(i.quantity,0)", false, null, false));
        // 收款情况：received_amount 有真实回写点（收款单确认后累计），维持原取数不动
        c.add(new Col("rowReceiptStatus", "收款情况", G_ITEM, "text",
                "'已收 ' || TO_CHAR(COALESCE(i.received_amount,0), 'FM999999999990.00')", false, null, false));
        c.add(new Col("rowInvoiceStatus", "开票情况", G_ITEM, "text",
                "'已开 ' || " + RowWritebackSql.saleOrderInvoiceQty("i.order_item_id", "o.order_no", "i.material_code")
                        + " || ' / ' || COALESCE(i.quantity,0)", false, null, false));
        c.add(new Col("rowProduceStatus", "生产情况", G_ITEM, "text",
                "'已产 ' || " + RowWritebackSql.saleProduceQty("i.order_item_id", "o.order_no", "i.material_code")
                        + " || ' / ' || COALESCE(i.quantity,0)", false, null, false));
        // 退货情况（2026-10-14 补）：退货量在订单明细上累加（退货单回到订单冲减），出库单无该列
        c.add(new Col("rowReturnStatus", "退货情况", G_ITEM, "text",
                "'已退 ' || " + RowWritebackSql.saleOrderReturnQty("i.order_item_id", "o.order_no", "i.material_code")
                        + " || ' / ' || COALESCE(i.quantity,0)", false, null, false));
        c.reportHiddenKeys.addAll(java.util.List.of(
                "rowDeliveryStatus", "rowReceiptStatus", "rowInvoiceStatus", "rowProduceStatus", "rowReturnStatus",
                "depositAmount"));
        c.add(new Col("unitPrice", "单价", G_ITEM, "number", "i.unit_price", false, null, true));
        // 2026-11-14：编辑页明细有含税价/不含税价，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("itemDiscountRate", "明细折扣率%", G_ITEM, "number", "i.discount_rate", false, null, true));
        c.add(new Col("itemDiscountAmount", "明细折扣额", G_ITEM, "amount", "i.discount_amount", false, null, true));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("itemTaxAmount", "明细税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", "i.net_amount", false, null, true));
        c.add(new Col("avgCost", "成本", G_ITEM, "number", "i.avg_cost", false, null, false));
        c.add(new Col("itemDeliveryDate", "明细交货日期", G_ITEM, "date", "i.delivery_date", false, null, false));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.warehouse_id", false);
        c.add(new Col("binName", "储位", G_ITEM, "text", "b.bin_name", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceLineNo", "来源行号", G_ITEM, "int", "i.source_line_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "discountAmount", "taxAmount", "netAmount", "receivedAmount"));
        // 报表中心默认视图不变：新增列只在列设置里供用户勾选
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax", "taxCalcMethod"));
        return c;
    }

    // ==================================================================
    // 销售出库明细报表
    // ==================================================================
    public static BillDetailReportConfig deliveryConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "sale_delivery";
        c.itemTable = "sale_delivery_item";
        c.itemFkColumn = "delivery_id";
        c.docIdColumn = "delivery_id";
        c.itemIdColumn = "delivery_item_id";
        c.cfEntityDoc = "sale_delivery";
        c.cfEntityItem = "sale_delivery_item";
        c.docNoExpr = "o.delivery_no";
        c.dataScopeOwnerColumn = "saler_id";
        c.docDateFilterKey = "deliveryDate";
        c.defaultOrderBy = " ORDER BY o.delivery_date DESC NULLS LAST, o.delivery_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_CUSTOMER);
        c.extraJoins.add(JOIN_EMPLOYEE);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add("LEFT JOIN warehouse w ON w.stock_id = o.warehouse_id");
        c.extraJoins.add("LEFT JOIN settlement_mode sm ON sm.settle_mode_id = o.settle_mode_id");
        c.extraJoins.add(JOIN_UNIT);
        // 明细行的仓库列名是 stock_id（不是 warehouse_id），别抄订单的写法
        c.extraJoins.add("LEFT JOIN warehouse iw ON iw.stock_id = i.stock_id");
        c.extraJoins.add("LEFT JOIN location b ON b.bin_id = i.bin_id");
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("deliveryNo", "出库单号", G_DOC, "text", "o.delivery_no", true, "left", true))
                .filter("text", true);
        c.add(new Col("customerName", "客户名称", G_DOC, "text", "c.customer_name", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("deliveryDate", "出库日期", G_DOC, "date", "o.delivery_date", true, null, true))
                .filter("dateRange", true);
        c.add(new Col("salerName", "销售员", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.saler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        // 2026-11-14：补「计税方式」（默认隐藏，列设置可勾）
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("contactPerson", "联系人", G_DOC, "text", "o.contact_person", false, null, false));
        c.add(new Col("contactPhone", "联系电话", G_DOC, "text", "o.contact_phone", false, null, false));
        c.add(new Col("contactAddress", "联系地址", G_DOC, "text", "o.contact_address", false, null, false));
        c.add(new Col("warehouseName", "仓库", G_DOC, "text", "w.stock_name", false, null, false))
                .select("warehouse", "long", "COALESCE(i.stock_id, o.warehouse_id)", false);
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true));
        c.add(new Col("totalAmount", "合计金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("discountRate", "整单折扣率%", G_DOC, "number", "o.discount_rate", false, null, true));
        c.add(new Col("discountAmount", "折扣额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true));
        c.add(new Col("taxAmount", "税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("netAmount", "价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true));
        c.add(new Col("prepaidRate", "预付款比例%", G_DOC, "number", "o.prepaid_rate", false, null, true));
        c.add(new Col("prepaidAmount", "预付款金额（整单）", G_DOC, "amount", "o.prepaid_amount", false, null, true));
        c.add(new Col("settleModeName", "结算方式", G_DOC, "text", "sm.settle_mode_name", false, null, false))
                .select("settleMode", "long", "o.settle_mode_id", false);
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("opportunityNo", "商机编号", G_DOC, "text", "o.opportunity_no", false, null, false));
        c.add(new Col("orderNo", "来源订单号", G_DOC, "text", "o.order_no", false, null, false));
        c.add(new Col("invoiceNo", "已开发票号", G_DOC, "text", "o.invoice_no", false, null, false));
        c.add(new Col("expressCompany", "快递公司", G_DOC, "text", "o.express_company", false, null, false));
        c.add(new Col("expressNo", "快递单号", G_DOC, "text", "o.express_no", false, null, false));
        c.add(new Col("carrier", "承运人", G_DOC, "text", "o.carrier", false, null, false));
        c.add(new Col("trackingNo", "跟踪单号", G_DOC, "text", "o.tracking_no", false, null, false));
        c.add(new Col("deliveryCost", "运费（整单）", G_DOC, "amount", "o.freight", false, null, true));
        // 2026-11-27 明细列设置补单据字段（汇总列表有「审核」、明细缺）
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        // 与采购单据同一套标准单据字段：附件 / 组织编码 / 本位币金额
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));
        c.add(new Col("baseTotalAmount", "本位币合计金额", G_DOC, "amount", "o.base_total_amount", false, null, true));
        c.add(new Col("baseTaxAmount", "本位币税额", G_DOC, "amount", "o.base_tax_amount", false, null, true));
        c.add(new Col("baseNetAmount", "本位币价税合计", G_DOC, "amount", "o.base_net_amount", false, null, true));
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
        // 2026-11-14：编辑页明细有含税价/不含税价，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("itemDiscountRate", "明细折扣率%", G_ITEM, "number", "i.discount_rate", false, null, true));
        c.add(new Col("itemDiscountAmount", "明细折扣额", G_ITEM, "amount", "i.discount_amount", false, null, true));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("amount", "金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("itemTaxAmount", "明细税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("itemNetAmount", "明细价税合计", G_ITEM, "amount", "i.net_amount", false, null, true));
        c.add(new Col("avgCost", "成本", G_ITEM, "number", "i.avg_cost", false, null, false));
        c.add(new Col("itemWarehouseName", "明细仓库", G_ITEM, "text", "iw.stock_name", false, null, false))
                .select("warehouse", "long", "i.stock_id", false);
        c.add(new Col("binName", "储位", G_ITEM, "text", "b.bin_name", false, null, false));
        c.add(new Col("batchNo", "批次号", G_ITEM, "text", "i.batch_no", false, null, false));
        c.add(new Col("serialNo", "序列号", G_ITEM, "text", "i.serial_no", false, null, false));
        c.add(new Col("productionDate", "生产日期", G_ITEM, "date", "i.production_date", false, null, false));
        c.add(new Col("expiryDate", "有效期至", G_ITEM, "date", "i.expiry_date", false, null, false));
        // 【2026-10-14 第二批】已开票数量改下游发票明细实时聚合（原读落库列：演示假值会假报已开票）
        c.add(new Col("turnedInvoiceQuantity", "已开票数量", G_ITEM, "number",
                RowWritebackSql.saleInvoiceQty("i.delivery_item_id", "o.delivery_no", "i.material_code"), false, null, false));
        // 行级回写展示列（2026-10-14 补：一期登记表列了"开票情况"却漏做）。
        // 出库单明细表已落库的只有"已开票数量"，退货量不在出库单上（退货单回到订单冲减）→ 不出退货列，不硬凑。
        // 【2026-10-14 修复】取数改为下游发票明细实时聚合（原读落库列，演示假值会假报已开票）。
        c.add(new Col("rowInvoiceStatus", "开票情况", G_ITEM, "text",
                "'已开 ' || " + RowWritebackSql.saleInvoiceQty("i.delivery_item_id", "o.delivery_no", "i.material_code")
                        + " || ' / ' || COALESCE(i.quantity,0)", false, null, false));
        c.reportHiddenKeys.add("rowInvoiceStatus");
        c.add(new Col("orderLineNo", "订单行号", G_ITEM, "int", "i.order_line_no", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceLineNo", "来源行号", G_ITEM, "int", "i.source_line_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "discountAmount", "taxAmount", "netAmount", "prepaidAmount"));
        // 报表中心默认视图不变：新增列只在列设置里供用户勾选
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax", "taxCalcMethod"));
        return c;
    }

    // ==================================================================
    // 销售发票明细报表
    // ==================================================================
    public static BillDetailReportConfig invoiceConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "sale_invoice";
        c.itemTable = "sale_invoice_item";
        c.itemFkColumn = "invoice_id";
        c.docIdColumn = "invoice_id";
        c.itemIdColumn = "invoice_item_id";
        c.cfEntityDoc = "sale_invoice";
        c.cfEntityItem = "sale_invoice_item";
        c.docNoExpr = "o.invoice_no";
        c.dataScopeOwnerColumn = "saler_id";
        c.docDateFilterKey = "invoiceDate";
        c.defaultOrderBy = " ORDER BY o.invoice_date DESC NULLS LAST, o.invoice_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_CUSTOMER);
        c.extraJoins.add(JOIN_EMPLOYEE);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add("LEFT JOIN settlement_mode sm ON sm.settle_mode_id = o.settle_mode_id");
        c.extraJoins.add(JOIN_UNIT);
        c.extraJoins.add("LEFT JOIN material m ON m.material_id = i.material_id");

        c.add(new Col("invoiceNo", "发票号码", G_DOC, "text", "o.invoice_no", true, "left", true))
                .filter("text", true);
        c.add(new Col("customerName", "客户名称", G_DOC, "text", "c.customer_name", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("invoiceDate", "开票日期", G_DOC, "date", "o.invoice_date", true, null, true))
                .filter("dateRange", true);
        c.add(new Col("salerName", "销售员", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.saler_id", true);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        // 2026-11-14：补「计税方式」（默认隐藏，列设置可勾）
        c.add(new Col("taxCalcMethod", "计税方式", G_DOC, "text", "o.tax_calc_method", false, null, false));
        c.add(new Col("invoiceType", "发票类型", G_DOC, "text", "o.invoice_type", false, null, false));
        c.add(new Col("taxNo", "税号", G_DOC, "text", "o.tax_no", false, null, false));
        c.add(new Col("address", "地址", G_DOC, "text", "o.address", false, null, false));
        c.add(new Col("bankName", "开户银行", G_DOC, "text", "o.bank_name", false, null, false));
        c.add(new Col("bankAccount", "银行账号", G_DOC, "text", "o.bank_account", false, null, false));
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, true));
        c.add(new Col("totalAmount", "开票金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("discountRate", "整单折扣率%", G_DOC, "number", "o.discount_rate", false, null, true));
        c.add(new Col("discountAmount", "开票折扣额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true));
        c.add(new Col("taxAmount", "开票税额（整单）", G_DOC, "amount", "o.tax_amount", false, null, true));
        c.add(new Col("netAmount", "开票价税合计（整单）", G_DOC, "amount", "o.net_amount", false, null, true));
        c.add(new Col("taxRateAvg", "平均税率%", G_DOC, "number", "o.tax_rate_avg", false, null, false));
        c.add(new Col("prepaidRate", "预付款比例%", G_DOC, "number", "o.prepaid_rate", false, null, true));
        c.add(new Col("prepaidAmount", "预付款金额（整单）", G_DOC, "amount", "o.prepaid_amount", false, null, true));
        c.add(new Col("arNo", "应收单号", G_DOC, "text", "o.ar_no", false, null, false));
        c.add(new Col("settleModeName", "结算方式", G_DOC, "text", "sm.settle_mode_name", false, null, false))
                .select("settleMode", "long", "o.settle_mode_id", false);
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text", "o.source_bill_type", false, null, false));
        c.add(new Col("docSourceBillNo", "来源单号（整单）", G_DOC, "text", "o.source_bill_no", false, null, false));
        c.add(new Col("deliveryNo", "来源出库单号", G_DOC, "text", "o.delivery_no", false, null, false));
        c.add(new Col("opportunityNo", "商机编号", G_DOC, "text", "o.opportunity_no", false, null, false));
        c.add(new Col("isRedInvoice", "红蓝字", G_DOC, "int", "o.is_red_invoice", false, null, false))
                .select("isRedInvoice", "int", "o.is_red_invoice", false);
        c.add(new Col("originalInvoiceNo", "原发票号码", G_DOC, "text", "o.original_invoice_no", false, null, false));
        // 2026-11-27 明细列设置补单据字段（汇总列表有「税票号码」「审核」、明细缺）
        c.add(new Col("taxInvoiceNo", "税票号码", G_DOC, "text", "o.tax_invoice_no", false, null, false));
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        // 与采购单据同一套标准单据字段：附件 / 组织编码
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
        // 2026-11-14：编辑页明细有含税价/不含税价，明细模式补齐（默认隐藏，列设置可勾）
        c.add(new Col("priceWithTax", "含税价", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("priceWithoutTax", "不含税价", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("itemDiscountRate", "明细折扣率%", G_ITEM, "number", "i.discount_rate", false, null, true));
        c.add(new Col("itemDiscountAmount", "明细折扣额", G_ITEM, "amount", "i.discount_amount", false, null, true));
        c.add(new Col("taxRate", "税率%", G_ITEM, "number", "i.tax_rate", false, null, true));
        c.add(new Col("amount", "开票金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("itemTaxAmount", "明细开票税额", G_ITEM, "amount", "i.tax_amount", false, null, true));
        c.add(new Col("itemNetAmount", "明细开票价税合计", G_ITEM, "amount", "i.net_amount", false, null, true));
        c.add(new Col("deliveryLineNo", "出库单行号", G_ITEM, "int", "i.delivery_line_no", false, null, false));
        c.add(new Col("itemSourceBillNo", "明细来源单号", G_ITEM, "text", "i.source_bill_no", false, null, false));
        c.add(new Col("itemSourceLineNo", "来源行号", G_ITEM, "int", "i.source_line_no", false, null, false));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("quantity", "itemDiscountAmount", "amount", "itemTaxAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "discountAmount", "taxAmount", "netAmount", "prepaidAmount"));
        // 报表中心默认视图不变：新增列只在列设置里供用户勾选
        c.reportHiddenKeys.addAll(java.util.List.of("priceWithTax", "priceWithoutTax", "taxCalcMethod"));
        return c;
    }
}
