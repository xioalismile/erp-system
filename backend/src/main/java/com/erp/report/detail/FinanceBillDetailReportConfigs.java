package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;

import static com.erp.report.detail.BillDetailReportConfig.G_DOC;
import static com.erp.report.detail.BillDetailReportConfig.G_ITEM;

/**
 * 财务模块 6 个单据明细报表的列配置（收款单 / 付款单 / 销售预收款单 / 采购预付款单 / 费用分摊单 / 工序工资核算单）。
 *
 * <p><b>列名来源</b>：全部取自各实体类的 {@code @TableName}/{@code @TableField} 注解（逐字实查，非推测）。
 * 财务模块几个**容易抄错**的列名，已在下方逐条标注：
 * <ul>
 *   <li>收款单/付款单：主表是 {@code cash_receipt}/{@code cash_payment}（**不是** payment_receipt/payment_voucher），
 *       明细是 {@code cash_receipt_item}/{@code cash_payment_item}，行序号列是 {@code sort_no}（**没有** line_no）</li>
 *   <li>销售预收款/采购预付款：主表 {@code sale_pre_receipt}/{@code purchase_pre_payment}，主键和外键都叫 {@code id}，
 *       明细外键是 {@code pre_receipt_id}/{@code pre_payment_id}；创建人列是 {@code create_by}（**不是** created_by），
 *       制单时间列是 {@code create_time}；状态列是**数字**（0草稿/1待审核/2已生效/3已核销/7已作废，实查两个列表页）</li>
 *   <li>费用分摊单：主表 {@code operation_expense} **没有 created_by 列**（实体无此字段），
 *       数据权限与列表页逐字同口径（OperationExpenseServiceImpl: applyTo(wrapper, null, "applicant_name")）；
 *       明细是 {@code operation_expense_allocation}（分摊明细，外键 expense_id，主键 allocation_id，无行号列）</li>
 *   <li>工序工资核算单：主表 {@code wage_settlement}，部门列是 {@code dept_id}（**不是** department_id）；
 *       列表页（WageSettlementServiceImpl.pageQuery）**没有任何数据权限过滤** → 两个数据权限维度都传 null；
 *       明细 {@code wage_settlement_item} 有行号列 {@code line_no}（每条已生效工序汇报单一行）</li>
 *   <li>收款/付款单明细的账户主档是 {@code bank_account}（account_id/account_name/account_no，实查 BankAccount 实体）</li>
 * </ul>
 *
 * <p><b>数据权限口径</b>（与各列表页 applyTo 逐字同口径）：
 * 收款单/付款单 = applyTo(wrapper, null) → 仅 created_by；
 * 销售预收款/采购预付款 = applyTo(wrapper, null, "create_by")；
 * 费用分摊单 = applyTo(wrapper, null, "applicant_name")；工序工资核算单 = 列表无数据权限 → 双 null。
 */
public final class FinanceBillDetailReportConfigs {

    private FinanceBillDetailReportConfigs() {
    }

    private static final String JOIN_CUSTOMER = "LEFT JOIN customer c ON c.customer_id = o.customer_id";
    private static final String JOIN_SUPPLIER = "LEFT JOIN supplier s ON s.supplier_id = o.supplier_id";
    private static final String JOIN_CURRENCY = "LEFT JOIN currency cu ON cu.currency_id = o.currency_id";
    private static final String JOIN_SETTLE = "LEFT JOIN settlement_mode sm ON sm.settle_mode_id = o.settle_mode_id";
    private static final String JOIN_DOC_DEPT = "LEFT JOIN department dp ON dp.dept_id = o.department_id";
    private static final String JOIN_WAGE_DEPT = "LEFT JOIN department dp ON dp.dept_id = o.dept_id";
    private static final String JOIN_DOC_ACCOUNT = "LEFT JOIN bank_account da ON da.account_id = o.account_id";
    private static final String JOIN_ITEM_ACCOUNT = "LEFT JOIN bank_account ba ON ba.account_id = i.account_id";

    /** 预收款/预付款的状态列是数字（0草稿/1待审核/2已生效/3已核销/7已作废），静态状态选项是中文标签
     *  → 用 CASE 把数字翻成中文，显示与下拉筛选共用同一表达式（避免 int = varchar 直接报 SQL 错误） */
    private static final String PREPAID_STATUS_EXPR =
            "CASE o.status WHEN 0 THEN '草稿' WHEN 1 THEN '待审核' WHEN 2 THEN '已生效' "
          + "WHEN 3 THEN '已核销' WHEN 7 THEN '已作废' ELSE '' END";

    // ==================================================================
    // 1. 收款单明细报表（cash_receipt / cash_receipt_item）
    // ==================================================================
    public static BillDetailReportConfig receiptConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "cash_receipt";
        // 【2026-10-19 用户口径】收款单的"明细"= 核销明细（选了单据/商品等信息），不是收款账户行：
        // 行表换成 bill_writeoff_detail（一行 = 一条核销：核销单据类型/单号/货品/金额/销售员/提成），
        // 与付款单共用同一张从表，用 fixedWhere 的 bill_type 区分（引擎注释里写明的用法）。
        c.itemTable = "bill_writeoff_detail";
        c.itemFkColumn = "bill_id";
        c.docIdColumn = "receipt_id";
        c.itemIdColumn = "writeoff_id";
        c.cfEntityDoc = "cash_receipt";
        c.cfEntityItem = null;
        c.docNoExpr = "o.receipt_no";
        c.itemFixedWhere.add("i.bill_type = '收款单'");
        // 实查 FinanceServiceImpl.pageReceipt：applyTo(wrapper, null) → 无业务归属维度，仅按制单人过滤
        c.dataScopeOwnerColumn = null;
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "receiptDate";
        // 明细行序号列是 sort_no（没有 line_no，故行号 key 不能叫 lineNo，否则引擎排序兜底会拼出不存在的 i.line_no）
        c.defaultOrderBy = " ORDER BY o.receipt_date DESC NULLS LAST, o.receipt_no DESC NULLS LAST, i.writeoff_id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_CUSTOMER);
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.cashier_id");
        c.extraJoins.add(JOIN_DOC_DEPT);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add(JOIN_SETTLE);

        c.add(new Col("receiptNo", "收款单号", G_DOC, "text", "o.receipt_no", true, "left", true)).filter("text", true);
        c.add(new Col("customerName", "客户名称", G_DOC, "text",
                "COALESCE(NULLIF(o.customer_name, ''), c.customer_name)", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("receiptDate", "收款日期", G_DOC, "date", "o.receipt_date", true, null, true)).filter("dateRange", true);
        // 主表没有 cashier_name 冗余列 → 名称从员工主档取（编辑页标签：收款人）
        c.add(new Col("cashierName", "收款人", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.cashier_id", false);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("documentNo", "单据流水", G_DOC, "text", "o.document_no", false, null, false));
        c.add(new Col("settleModeName", "结算方式", G_DOC, "text", "sm.settle_mode_name", false, null, false))
                .select("settleMode", "long", "o.settle_mode_id", false);
        c.add(new Col("bankAccount", "收款账户", G_DOC, "text", "o.bank_account", false, null, false));
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, false));
        c.add(new Col("totalAmount", "总金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("docAmount", "收款金额（整单）", G_DOC, "amount", "o.amount", false, null, true));
        c.add(new Col("discountAmount", "折扣金额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true));
        c.add(new Col("receivedAmount", "实收金额（整单）", G_DOC, "amount", "o.received_amount", false, null, true));
        // 销售提成（2026-10-16）：表头四列（行级提成在核销明细，编辑页查看）
        c.add(new Col("commissionRate", "提成比例%", G_DOC, "number", "o.commission_rate", false, null, false));
        c.add(new Col("specialServiceFee", "特殊服务费", G_DOC, "amount", "o.special_service_fee", false, null, true));
        c.add(new Col("taxExcludedReceivedAmount", "不含税收款金额", G_DOC, "amount", "o.tax_excluded_received_amount", false, null, true));
        c.add(new Col("salesCommission", "销售提成", G_DOC, "amount", "o.sales_commission", false, null, true));
        // 来源单据（2026-10-28）：明细行（核销明细）为准，表头只兜底历史数据
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text",
                "COALESCE(NULLIF(o.source_bill_type, ''), (SELECT string_agg(DISTINCT w.source_type, '、')"
                        + " FROM bill_writeoff_detail w WHERE w.bill_type = '收款单' AND w.bill_id = o.receipt_id))",
                false, null, false));
        c.add(new Col("docSourceBillNo", "来源单据号", G_DOC, "text",
                "COALESCE(NULLIF(o.source_bill_no, ''), (SELECT string_agg(DISTINCT w.source_no, '、')"
                        + " FROM bill_writeoff_detail w WHERE w.bill_type = '收款单' AND w.bill_id = o.receipt_id))",
                false, null, false));
        c.add(new Col("deptName", "部门", G_DOC, "text", "dp.dept_name", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));

        // 【2026-10-19 用户口径】收款单明细行 = 核销明细行：单据类型/单号/货品/金额/冲销状态/销售员/提成
        c.add(new Col("writeoffSource", "核销单据类型", G_ITEM, "text", "i.source_type", false, null, false));
        c.add(new Col("writeoffSourceNo", "核销单号", G_ITEM, "text", "i.source_no", true, null, false));
        c.add(new Col("sourceItemName", "货品（明细行）", G_ITEM, "text", "i.source_item_name", false, null, false));
        c.add(new Col("writeoffAmount", "核销金额", G_ITEM, "amount", "i.writeoff_amount", false, null, true));
        c.add(new Col("settledQty", "冲销状态", G_ITEM, "text", "i.settled_qty", false, null, false));
        c.add(new Col("writeoffDate", "核销日期", G_ITEM, "date", "i.writeoff_date", false, null, false));
        c.add(new Col("salerName", "销售员", G_ITEM, "text", "i.saler_name", false, null, false));
        c.add(new Col("itemTaxExcluded", "行不含税收款", G_ITEM, "amount", "i.tax_excluded_amount", false, null, false));
        c.add(new Col("itemCommissionRate", "行提成比例%", G_ITEM, "number", "i.commission_rate", false, null, false));
        c.add(new Col("itemSpecialFee", "行特殊服务费", G_ITEM, "amount", "i.special_service_fee", false, null, false));
        c.add(new Col("itemCommission", "行销售提成", G_ITEM, "amount", "i.commission", false, null, false));
        c.add(new Col("itemRemark", "行备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("writeoffAmount", "itemTaxExcluded", "itemCommission"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "docAmount", "discountAmount", "receivedAmount"));
        return c;
    }

    // ==================================================================
    // 2. 付款单明细报表（cash_payment / cash_payment_item）
    // ==================================================================
    public static BillDetailReportConfig voucherConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "cash_payment";
        // 【2026-10-19 用户口径】付款单的"明细"= 核销明细（与收款单同口径）：行表 bill_writeoff_detail，
        // fixedWhere 用 bill_type='付款单' 区分（与收款单共用从表）。
        c.itemTable = "bill_writeoff_detail";
        c.itemFkColumn = "bill_id";
        c.docIdColumn = "payment_id";
        c.itemIdColumn = "writeoff_id";
        c.cfEntityDoc = "cash_payment";
        c.cfEntityItem = null;
        c.docNoExpr = "o.payment_no";
        c.itemFixedWhere.add("i.bill_type = '付款单'");
        // 实查 FinanceServiceImpl.pageVoucher：applyTo(wrapper, null) → 无业务归属维度，仅按制单人过滤
        c.dataScopeOwnerColumn = null;
        c.dataScopeCreatedByColumn = "created_by";
        c.docDateFilterKey = "paymentDate";
        c.defaultOrderBy = " ORDER BY o.payment_date DESC NULLS LAST, o.payment_no DESC NULLS LAST, i.writeoff_id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.cashier_id");
        c.extraJoins.add(JOIN_DOC_DEPT);
        c.extraJoins.add(JOIN_CURRENCY);
        c.extraJoins.add(JOIN_SETTLE);

        c.add(new Col("paymentNo", "付款单号", G_DOC, "text", "o.payment_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商名称", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("paymentDate", "付款日期", G_DOC, "date", "o.payment_date", true, null, true)).filter("dateRange", true);
        // 编辑页标签：付款人（实体注释叫出纳ID，以编辑页为准）
        c.add(new Col("cashierName", "付款人", G_DOC, "text", "e.employee_name", true, null, true))
                .select("employee", "long", "o.cashier_id", false);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("documentNo", "凭证号", G_DOC, "text", "o.document_no", false, null, false));
        c.add(new Col("settleModeName", "结算方式", G_DOC, "text", "sm.settle_mode_name", false, null, false))
                .select("settleMode", "long", "o.settle_mode_id", false);
        c.add(new Col("bankAccount", "付款账户", G_DOC, "text", "o.bank_account", false, null, false));
        c.add(new Col("currencyCode", "币种", G_DOC, "text", "cu.currency_code", false, null, false))
                .select("currency", "long", "o.currency_id", false);
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, false));
        c.add(new Col("totalAmount", "总金额（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("docAmount", "付款金额（整单）", G_DOC, "amount", "o.amount", false, null, true));
        c.add(new Col("discountAmount", "折扣金额（整单）", G_DOC, "amount", "o.discount_amount", false, null, true));
        c.add(new Col("paidAmount", "已付金额（整单）", G_DOC, "amount", "o.paid_amount", false, null, true));
        // 来源单据（2026-10-28）：明细行（核销明细）为准，表头只兜底历史数据
        c.add(new Col("sourceBillType", "来源单据类型", G_DOC, "text",
                "COALESCE(NULLIF(o.source_bill_type, ''), (SELECT string_agg(DISTINCT w.source_type, '、')"
                        + " FROM bill_writeoff_detail w WHERE w.bill_type = '付款单' AND w.bill_id = o.payment_id))",
                false, null, false));
        c.add(new Col("docSourceBillNo", "来源单据号", G_DOC, "text",
                "COALESCE(NULLIF(o.source_bill_no, ''), (SELECT string_agg(DISTINCT w.source_no, '、')"
                        + " FROM bill_writeoff_detail w WHERE w.bill_type = '付款单' AND w.bill_id = o.payment_id))",
                false, null, false));
        c.add(new Col("deptName", "部门", G_DOC, "text", "dp.dept_name", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));
        c.add(new Col("orgCode", "组织编码", G_DOC, "text", "o.org_code", false, null, false));

        // 【2026-10-19 用户口径】付款单明细行 = 核销明细行（与收款单同口径）
        c.add(new Col("writeoffSource", "核销单据类型", G_ITEM, "text", "i.source_type", false, null, false));
        c.add(new Col("writeoffSourceNo", "核销单号", G_ITEM, "text", "i.source_no", true, null, false));
        c.add(new Col("sourceItemName", "品名（明细行）", G_ITEM, "text", "i.source_item_name", false, null, false));
        c.add(new Col("writeoffAmount", "核销金额", G_ITEM, "amount", "i.writeoff_amount", false, null, true));
        c.add(new Col("settledQty", "冲销状态", G_ITEM, "text", "i.settled_qty", false, null, false));
        c.add(new Col("writeoffDate", "核销日期", G_ITEM, "date", "i.writeoff_date", false, null, false));
        c.add(new Col("itemRemark", "行备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("writeoffAmount"));
        c.docTotalKeys.addAll(java.util.List.of("totalAmount", "docAmount", "discountAmount", "paidAmount"));
        return c;
    }

    // ==================================================================
    // 3. 销售预收款单明细报表（sale_pre_receipt / sale_pre_receipt_item）
    // ==================================================================
    public static BillDetailReportConfig salePreReceiptConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "sale_pre_receipt";
        c.itemTable = "sale_pre_receipt_item";
        c.itemFkColumn = "pre_receipt_id";
        c.docIdColumn = "id";
        c.itemIdColumn = "id";
        c.cfEntityDoc = "sale_pre_receipt";
        c.cfEntityItem = "sale_pre_receipt_item";
        c.docNoExpr = "o.receipt_no";
        // 实查 SalePreReceiptServiceImpl.pageQuery：applyTo(wrapper, null, "create_by") —— 创建人列是 create_by（历史坑，实锤）
        c.dataScopeOwnerColumn = null;
        c.dataScopeCreatedByColumn = "create_by";
        c.docDateFilterKey = "receiptDate";
        // 明细表没有行号/序号列 → 按明细主键排序（key 不能叫 lineNo，否则引擎排序兜底拼 i.line_no 报错）
        c.defaultOrderBy = " ORDER BY o.receipt_date DESC NULLS LAST, o.receipt_no DESC NULLS LAST, i.id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_CUSTOMER);
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.cashier_id");
        c.extraJoins.add(JOIN_DOC_ACCOUNT);
        c.extraJoins.add(JOIN_ITEM_ACCOUNT);

        c.add(new Col("receiptNo", "预收款单号", G_DOC, "text", "o.receipt_no", true, "left", true)).filter("text", true);
        c.add(new Col("customerName", "客户名称", G_DOC, "text",
                "COALESCE(NULLIF(o.customer_name, ''), c.customer_name)", true, null, true))
                .select("customer", "long", "o.customer_id", true);
        c.add(new Col("receiptDate", "收款日期", G_DOC, "date", "o.receipt_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("cashierName", "收款人", G_DOC, "text",
                "COALESCE(NULLIF(o.cashier_name, ''), e.employee_name)", true, null, true))
                .select("employee", "long", "o.cashier_id", false);
        // 状态是数字列 → CASE 翻中文，显示与筛选共用同一表达式（见类注释）
        c.add(new Col("status", "单据状态", G_DOC, "status", PREPAID_STATUS_EXPR, false, null, false))
                .select("status", "string", PREPAID_STATUS_EXPR, false);
        c.add(new Col("orderNo", "关联订单号", G_DOC, "text", "o.order_no", false, null, false));
        c.add(new Col("currencyCode", "币别", G_DOC, "text", "o.currency_code", false, null, false));
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, false));
        c.add(new Col("paymentMethod", "收款方式", G_DOC, "text", "o.payment_method", false, null, false));
        c.add(new Col("accountName", "账户", G_DOC, "text",
                "COALESCE(NULLIF(o.account_name, ''), da.account_name)", false, null, false));
        c.add(new Col("docAmount", "收款金额（整单）", G_DOC, "amount", "o.amount", false, null, true));
        c.add(new Col("docBaseAmount", "本位币金额（整单）", G_DOC, "amount", "o.base_amount", false, null, true));
        c.add(new Col("docUsedAmount", "已用金额（整单）", G_DOC, "amount", "o.used_amount", false, null, true));
        c.add(new Col("docRemainAmount", "剩余金额（整单）", G_DOC, "amount", "o.remain_amount", false, null, true));
        c.add(new Col("sourceType", "来源标记", G_DOC, "text", "o.source_type", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.create_by", false, null, false));
        c.add(new Col("createTime", "制单时间", G_DOC, "datetime", "o.create_time", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));

        c.add(new Col("itemSourceType", "来源类型（明细）", G_ITEM, "text", "i.source_type", false, null, false));
        c.add(new Col("itemSourceNo", "来源单号（明细）", G_ITEM, "text", "i.source_no", false, null, false));
        c.add(new Col("itemAccountName", "账户（明细）", G_ITEM, "text",
                "COALESCE(NULLIF(i.account_name, ''), ba.account_name)", true, null, false));
        c.add(new Col("itemAccountNo", "银行账号（明细）", G_ITEM, "text", "i.account_no", false, null, false));
        // 明细无 net_amount 列 → 统计卡口径列 itemNetAmount 用实际金额列 i.amount
        c.add(new Col("itemNetAmount", "收款金额（明细）", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("returnAmount", "退回金额", G_ITEM, "amount", "i.return_amount", false, null, true));
        c.add(new Col("baseAmount", "收款本位币", G_ITEM, "amount", "i.base_amount", false, null, true));
        c.add(new Col("usedAmount", "已核金额", G_ITEM, "amount", "i.used_amount", false, null, true));
        c.add(new Col("usedBaseAmount", "已核本位币", G_ITEM, "amount", "i.used_base_amount", false, null, true));
        c.add(new Col("returnBaseAmount", "退回本位币", G_ITEM, "amount", "i.return_base_amount", false, null, true));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("itemNetAmount", "returnAmount", "baseAmount", "usedAmount"));
        c.docTotalKeys.addAll(java.util.List.of("docAmount", "docBaseAmount", "docUsedAmount", "docRemainAmount"));
        return c;
    }

    // ==================================================================
    // 4. 采购预付款单明细报表（purchase_pre_payment / purchase_pre_payment_item）
    // ==================================================================
    public static BillDetailReportConfig purchasePrePaymentConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "purchase_pre_payment";
        c.itemTable = "purchase_pre_payment_item";
        c.itemFkColumn = "pre_payment_id";
        c.docIdColumn = "id";
        c.itemIdColumn = "id";
        c.cfEntityDoc = "purchase_pre_payment";
        c.cfEntityItem = "purchase_pre_payment_item";
        c.docNoExpr = "o.payment_no";
        // 实查 PurchasePrePaymentServiceImpl.pageQuery：applyTo(wrapper, null, "create_by") —— 创建人列是 create_by
        c.dataScopeOwnerColumn = null;
        c.dataScopeCreatedByColumn = "create_by";
        c.docDateFilterKey = "paymentDate";
        c.defaultOrderBy = " ORDER BY o.payment_date DESC NULLS LAST, o.payment_no DESC NULLS LAST, i.id ASC NULLS LAST ";
        c.extraJoins.add(JOIN_SUPPLIER);
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.cashier_id");
        c.extraJoins.add(JOIN_DOC_ACCOUNT);
        c.extraJoins.add(JOIN_ITEM_ACCOUNT);

        c.add(new Col("paymentNo", "预付款单号", G_DOC, "text", "o.payment_no", true, "left", true)).filter("text", true);
        c.add(new Col("supplierName", "供应商名称", G_DOC, "text",
                "COALESCE(NULLIF(o.supplier_name, ''), s.supplier_name)", true, null, true))
                .select("supplier", "long", "o.supplier_id", true);
        c.add(new Col("paymentDate", "付款日期", G_DOC, "date", "o.payment_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("cashierName", "付款人", G_DOC, "text",
                "COALESCE(NULLIF(o.cashier_name, ''), e.employee_name)", true, null, true))
                .select("employee", "long", "o.cashier_id", false);
        // 状态是数字列 → CASE 翻中文（同销售预收款）
        c.add(new Col("status", "单据状态", G_DOC, "status", PREPAID_STATUS_EXPR, false, null, false))
                .select("status", "string", PREPAID_STATUS_EXPR, false);
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("orderNo", "关联订单号", G_DOC, "text", "o.order_no", false, null, false));
        c.add(new Col("currencyCode", "币别", G_DOC, "text", "o.currency_code", false, null, false));
        c.add(new Col("exchangeRate", "汇率", G_DOC, "number", "o.exchange_rate", false, null, false));
        c.add(new Col("paymentMethod", "付款方式", G_DOC, "text", "o.payment_method", false, null, false));
        c.add(new Col("accountName", "账户", G_DOC, "text",
                "COALESCE(NULLIF(o.account_name, ''), da.account_name)", false, null, false));
        c.add(new Col("docAmount", "付款金额（整单）", G_DOC, "amount", "o.amount", false, null, true));
        c.add(new Col("docBaseAmount", "本位币金额（整单）", G_DOC, "amount", "o.base_amount", false, null, true));
        c.add(new Col("docUsedAmount", "已用金额（整单）", G_DOC, "amount", "o.used_amount", false, null, true));
        c.add(new Col("docRemainAmount", "剩余金额（整单）", G_DOC, "amount", "o.remain_amount", false, null, true));
        c.add(new Col("sourceType", "来源标记", G_DOC, "text", "o.source_type", false, null, false));
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.create_by", false, null, false));
        c.add(new Col("createTime", "制单时间", G_DOC, "datetime", "o.create_time", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));

        c.add(new Col("itemSourceType", "来源类型（明细）", G_ITEM, "text", "i.source_type", false, null, false));
        c.add(new Col("itemSourceNo", "来源单号（明细）", G_ITEM, "text", "i.source_no", false, null, false));
        c.add(new Col("itemAccountName", "账户（明细）", G_ITEM, "text",
                "COALESCE(NULLIF(i.account_name, ''), ba.account_name)", true, null, false));
        c.add(new Col("itemAccountNo", "银行账号（明细）", G_ITEM, "text", "i.account_no", false, null, false));
        c.add(new Col("itemNetAmount", "付款金额（明细）", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("returnAmount", "退回金额", G_ITEM, "amount", "i.return_amount", false, null, true));
        c.add(new Col("baseAmount", "付款本位币", G_ITEM, "amount", "i.base_amount", false, null, true));
        c.add(new Col("usedAmount", "已核金额", G_ITEM, "amount", "i.used_amount", false, null, true));
        c.add(new Col("usedBaseAmount", "已核本位币", G_ITEM, "amount", "i.used_base_amount", false, null, true));
        c.add(new Col("returnBaseAmount", "退回本位币", G_ITEM, "amount", "i.return_base_amount", false, null, true));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("itemNetAmount", "returnAmount", "baseAmount", "usedAmount"));
        c.docTotalKeys.addAll(java.util.List.of("docAmount", "docBaseAmount", "docUsedAmount", "docRemainAmount"));
        return c;
    }

    // ==================================================================
    // 5. 费用分摊单明细报表（operation_expense / operation_expense_allocation 分摊明细）
    // ==================================================================
    public static BillDetailReportConfig operationExpenseConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "operation_expense";
        c.itemTable = "operation_expense_allocation";
        c.itemFkColumn = "expense_id";
        c.docIdColumn = "expense_id";
        c.itemIdColumn = "allocation_id";
        c.cfEntityDoc = "operation_expense";
        c.cfEntityItem = "operation_expense_allocation";
        c.docNoExpr = "o.expense_no";
        // 实查 OperationExpenseServiceImpl.pageQuery：applyTo(wrapper, null, "applicant_name")
        // 主表没有 created_by 列（实体无此字段），列表页用申请人姓名作用户维度 → 逐字同口径
        c.dataScopeOwnerColumn = null;
        c.dataScopeCreatedByColumn = "applicant_name";
        c.docDateFilterKey = "expenseDate";
        // 分摊明细没有行号列 → 按明细主键排序
        c.defaultOrderBy = " ORDER BY o.expense_date DESC NULLS LAST, o.expense_no DESC NULLS LAST, i.allocation_id ASC NULLS LAST ";
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.applicant_id");
        c.extraJoins.add(JOIN_DOC_DEPT);

        c.add(new Col("expenseNo", "费用单号", G_DOC, "text", "o.expense_no", true, "left", true)).filter("text", true);
        c.add(new Col("expenseDate", "费用日期", G_DOC, "date", "o.expense_date", true, null, true)).filter("dateRange", true);
        c.add(new Col("expenseType", "费用类型", G_DOC, "text", "o.expense_type", false, null, false));
        c.add(new Col("applicantName", "申请人", G_DOC, "text",
                "COALESCE(NULLIF(o.applicant_name, ''), e.employee_name)", true, null, true))
                .select("employee", "long", "o.applicant_id", false);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("allocationMethod", "分摊方式", G_DOC, "text", "o.allocation_method", false, null, false));
        c.add(new Col("docAmount", "费用金额（整单）", G_DOC, "amount", "o.amount", false, null, true));
        c.add(new Col("relatedOrderType", "关联单据类型", G_DOC, "text", "o.related_order_type", false, null, false));
        c.add(new Col("relatedOrderNo", "关联单据编号", G_DOC, "text", "o.related_order_no", false, null, false));
        c.add(new Col("deptName", "部门", G_DOC, "text",
                "COALESCE(NULLIF(o.department_name, ''), dp.dept_name)", false, null, false))
                .select("department", "long", "o.department_id", false);
        c.add(new Col("closeStatus", "结案状态", G_DOC, "int", "o.close_status", false, null, false))
                .select("closeStatus", "int", "o.close_status", false);
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        // 主表没有 created_by 列 → 不设"制单人"列，制单时间用 created_at
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", false, null, true));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));
        c.add(new Col("attachmentUrls", "附件", G_DOC, "text", "o.attachment_urls", false, null, false));

        c.add(new Col("orderType", "分摊对象类型", G_ITEM, "text", "i.order_type", false, null, false));
        c.add(new Col("orderNo", "分摊对象", G_ITEM, "text", "i.order_no", false, null, false));
        c.add(new Col("itemExpenseType", "费用类型（明细）", G_ITEM, "text", "i.expense_type", false, null, false));
        c.add(new Col("allocationBase", "分摊基数", G_ITEM, "number", "i.allocation_base", false, null, true));
        c.add(new Col("allocationRatio", "比例%", G_ITEM, "number", "i.allocation_ratio", false, null, true));
        c.add(new Col("itemNetAmount", "分摊金额", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.add("itemNetAmount");
        c.docTotalKeys.add("docAmount");
        return c;
    }

    // ==================================================================
    // 6. 工序工资核算单明细报表（wage_settlement / wage_settlement_item）
    // ==================================================================
    public static BillDetailReportConfig wageSettlementConfig() {
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "wage_settlement";
        c.itemTable = "wage_settlement_item";
        c.itemFkColumn = "settlement_id";
        c.docIdColumn = "settlement_id";
        c.itemIdColumn = "item_id";
        c.cfEntityDoc = "wage_settlement";
        c.cfEntityItem = "wage_settlement_item";
        c.docNoExpr = "o.settlement_no";
        // 实查 WageSettlementServiceImpl.pageQuery：列表页没有任何数据权限过滤 → 双 null（逐字同口径）
        c.dataScopeOwnerColumn = null;
        c.dataScopeCreatedByColumn = null;
        c.docDateFilterKey = "createdAt";
        // 主表没有业务日期列 → 默认按制单时间倒序；明细有行号列 line_no（key 用 lineNo 与引擎排序兜底对齐）
        c.defaultOrderBy = " ORDER BY o.created_at DESC NULLS LAST, o.settlement_no DESC NULLS LAST, i.line_no ASC NULLS LAST ";
        c.extraJoins.add(JOIN_WAGE_DEPT);
        c.extraJoins.add("LEFT JOIN employee e ON e.employee_id = o.employee_id");
        c.extraJoins.add("LEFT JOIN employee ie ON ie.employee_id = i.employee_id");

        c.add(new Col("settlementNo", "核算单号", G_DOC, "text", "o.settlement_no", true, "left", true)).filter("text", true);
        c.add(new Col("period", "核算期间", G_DOC, "text", "o.period", true, null, true)).filter("text", true);
        c.add(new Col("createdAt", "制单时间", G_DOC, "datetime", "o.created_at", true, null, true)).filter("dateRange", true);
        c.add(new Col("deptName", "部门", G_DOC, "text",
                "COALESCE(NULLIF(o.dept_name, ''), dp.dept_name)", false, null, false))
                .select("department", "long", "o.dept_id", false);
        // 主表 employee_id = 生成时的员工筛选（为空 = 全部员工）
        c.add(new Col("employeeName", "员工（生成范围）", G_DOC, "text",
                "COALESCE(NULLIF(o.employee_name, ''), e.employee_name)", false, null, false))
                .select("employee", "long", "o.employee_id", false);
        c.add(new Col("status", "单据状态", G_DOC, "status", "o.status", false, null, true))
                .select("status", "string", "o.status", false);
        c.add(new Col("approvalStatus", "审批状态", G_DOC, "text", "o.approval_status", false, null, false));
        c.add(new Col("docQualifiedQuantity", "合格数量（整单）", G_DOC, "number", "o.qualified_quantity", false, null, true));
        c.add(new Col("docPieceAmount", "计件工资（整单）", G_DOC, "amount", "o.piece_amount", false, null, true));
        c.add(new Col("docTimeAmount", "计时工资（整单）", G_DOC, "amount", "o.time_amount", false, null, true));
        c.add(new Col("totalAmount", "工资合计（整单）", G_DOC, "amount", "o.total_amount", false, null, true));
        c.add(new Col("approvedBy", "审核人", G_DOC, "text", "o.approved_by", false, null, false));
        c.add(new Col("approvedAt", "审核时间", G_DOC, "datetime", "o.approved_at", false, null, false));
        c.add(new Col("voidedBy", "作废人", G_DOC, "text", "o.voided_by", false, null, false));
        c.add(new Col("voidedAt", "作废时间", G_DOC, "datetime", "o.voided_at", false, null, false));
        c.add(new Col("voidReason", "作废原因", G_DOC, "text", "o.void_reason", false, null, false));
        c.add(new Col("createdBy", "制单人", G_DOC, "text", "o.created_by", false, null, false));
        c.add(new Col("remark", "单据备注", G_DOC, "text", "o.remark", false, null, false));

        c.add(new Col("lineNo", "行号", G_ITEM, "int", "i.line_no", false, null, true));
        c.add(new Col("orderNo", "生产工单号", G_ITEM, "text", "i.order_no", false, null, false));
        c.add(new Col("operationCode", "工序编码", G_ITEM, "text", "i.operation_code", false, null, false));
        c.add(new Col("operationName", "工序名称", G_ITEM, "text", "i.operation_name", false, null, true));
        c.add(new Col("itemEmployeeName", "员工（明细）", G_ITEM, "text",
                "COALESCE(NULLIF(i.employee_name, ''), ie.employee_name)", true, null, true))
                .select("employee", "long", "i.employee_id", false);
        c.add(new Col("itemQualifiedQuantity", "合格数量（明细）", G_ITEM, "number", "i.qualified_quantity", false, null, true));
        c.add(new Col("pieceRate", "计件单价", G_ITEM, "number", "i.piece_rate", false, null, true));
        c.add(new Col("itemPieceAmount", "计件工资（明细）", G_ITEM, "amount", "i.piece_amount", false, null, true));
        c.add(new Col("workHours", "工时", G_ITEM, "number", "i.work_hours", false, null, true));
        c.add(new Col("hourlyRate", "工时费率", G_ITEM, "number", "i.hourly_rate", false, null, true));
        c.add(new Col("itemTimeAmount", "计时工资（明细）", G_ITEM, "amount", "i.time_amount", false, null, true));
        // 明细无 net_amount 列 → 统计卡口径列 itemNetAmount 用小计列 i.amount（计件+计时）
        c.add(new Col("itemNetAmount", "工资小计", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("itemRemark", "明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of(
                "itemQualifiedQuantity", "itemPieceAmount", "itemTimeAmount", "itemNetAmount"));
        c.docTotalKeys.addAll(java.util.List.of("docPieceAmount", "docTimeAmount", "totalAmount"));
        return c;
    }

    // ==================================================================
    // 收款单 / 付款单 · 第二张明细（收款明细 / 付款明细）
    //   2026-11-27 列表页明细模式适配：编辑页两张明细卡片，明细报表此前只出核销明细，
    //   收款/付款明细的字段在列设置里看不到。这里补一张与核销版共用单据列的明细配置，
    //   由 BillDetailReportUnion 合并成一张"全明细"（多一个「明细类型」列）。
    // ==================================================================

    /** 收款单 · 收款明细（cash_receipt_item） */
    public static BillDetailReportConfig receiptItemConfig() {
        BillDetailReportConfig docSource = receiptConfig();
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "cash_receipt";
        c.itemTable = "cash_receipt_item";
        c.itemFkColumn = "receipt_id";
        c.docIdColumn = "receipt_id";
        c.itemIdColumn = "item_id";
        c.cfEntityDoc = "cash_receipt";
        c.cfEntityItem = "cash_receipt_item";
        c.docNoExpr = docSource.docNoExpr;
        c.dataScopeOwnerColumn = docSource.dataScopeOwnerColumn;
        c.dataScopeCreatedByColumn = docSource.dataScopeCreatedByColumn;
        c.docDateFilterKey = docSource.docDateFilterKey;
        c.defaultOrderBy = " ORDER BY o.receipt_date DESC NULLS LAST, o.receipt_no DESC NULLS LAST, i.sort_no ASC NULLS LAST ";
        c.extraJoins.addAll(docSource.extraJoins);
        c.extraJoins.add(JOIN_ITEM_ACCOUNT);
        copyDocColumns(docSource, c);

        c.add(new Col("receiptItemAccount", "收款账户（明细）", G_ITEM, "text",
                "COALESCE(NULLIF(i.account_name, ''), ba.account_name)", false, null, false));
        c.add(new Col("receiptItemAccountNo", "银行账号（明细）", G_ITEM, "text",
                "COALESCE(NULLIF(i.bank_account, ''), i.account_no)", false, null, false));
        c.add(new Col("receiptItemAmount", "收款金额（明细）", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("receiptItemBaseAmount", "收款本位币（明细）", G_ITEM, "amount", "i.base_amount", false, null, true));
        c.add(new Col("receiptItemPriceWithTax", "含税金额（明细）", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("receiptItemPriceWithoutTax", "不含税金额（明细）", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("receiptItemRemark", "收款明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("receiptItemAmount", "receiptItemBaseAmount"));
        c.docTotalKeys.addAll(docSource.docTotalKeys);
        return c;
    }

    /** 付款单 · 付款明细（cash_payment_item） */
    public static BillDetailReportConfig paymentItemConfig() {
        BillDetailReportConfig docSource = voucherConfig();
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "cash_payment";
        c.itemTable = "cash_payment_item";
        c.itemFkColumn = "payment_id";
        c.docIdColumn = "payment_id";
        c.itemIdColumn = "item_id";
        c.cfEntityDoc = "cash_payment";
        c.cfEntityItem = "cash_payment_item";
        c.docNoExpr = docSource.docNoExpr;
        c.dataScopeOwnerColumn = docSource.dataScopeOwnerColumn;
        c.dataScopeCreatedByColumn = docSource.dataScopeCreatedByColumn;
        c.docDateFilterKey = docSource.docDateFilterKey;
        c.defaultOrderBy = " ORDER BY o.payment_date DESC NULLS LAST, o.payment_no DESC NULLS LAST, i.sort_no ASC NULLS LAST ";
        c.extraJoins.addAll(docSource.extraJoins);
        c.extraJoins.add(JOIN_ITEM_ACCOUNT);
        copyDocColumns(docSource, c);

        c.add(new Col("paymentItemAccount", "付款账户（明细）", G_ITEM, "text",
                "COALESCE(NULLIF(i.account_name, ''), ba.account_name)", false, null, false));
        c.add(new Col("paymentItemAccountNo", "银行账号（明细）", G_ITEM, "text",
                "COALESCE(NULLIF(i.bank_account, ''), i.account_no)", false, null, false));
        c.add(new Col("paymentItemAmount", "付款金额（明细）", G_ITEM, "amount", "i.amount", false, null, true));
        c.add(new Col("paymentItemBaseAmount", "付款本位币（明细）", G_ITEM, "amount", "i.base_amount", false, null, true));
        c.add(new Col("paymentItemPriceWithTax", "含税金额（明细）", G_ITEM, "number", "i.price_with_tax", false, null, false));
        c.add(new Col("paymentItemPriceWithoutTax", "不含税金额（明细）", G_ITEM, "number", "i.price_without_tax", false, null, false));
        c.add(new Col("paymentItemRemark", "付款明细备注", G_ITEM, "text", "i.remark", false, null, false));

        c.itemTotalKeys.addAll(java.util.List.of("paymentItemAmount", "paymentItemBaseAmount"));
        c.docTotalKeys.addAll(docSource.docTotalKeys);
        return c;
    }

    /** 费用分摊单 · 费用明细（operation_expense.expense_lines_json 展开成行） */
    public static BillDetailReportConfig expenseLinesConfig() {
        BillDetailReportConfig docSource = operationExpenseConfig();
        BillDetailReportConfig c = new BillDetailReportConfig();
        c.mainTable = "operation_expense";
        c.itemJoinSql = "LEFT JOIN LATERAL jsonb_array_elements("
                + "CASE WHEN o.expense_lines_json IS NULL OR btrim(o.expense_lines_json) = '' "
                + "THEN '[]'::jsonb ELSE o.expense_lines_json::jsonb END)"
                + " WITH ORDINALITY AS i(line, idx) ON TRUE";
        c.docIdColumn = "expense_id";
        c.itemIdColumn = "idx";
        c.cfEntityDoc = "operation_expense";
        c.cfEntityItem = null;
        c.docNoExpr = docSource.docNoExpr;
        c.dataScopeOwnerColumn = docSource.dataScopeOwnerColumn;
        c.dataScopeCreatedByColumn = docSource.dataScopeCreatedByColumn;
        c.docDateFilterKey = docSource.docDateFilterKey;
        c.defaultOrderBy = " ORDER BY o.expense_date DESC NULLS LAST, o.expense_no DESC NULLS LAST, i.idx ASC NULLS LAST ";
        c.extraJoins.addAll(docSource.extraJoins);
        copyDocColumns(docSource, c);

        c.add(new Col("expenseItemType", "费用项目（明细）", G_ITEM, "text", "i.line->>'expenseType'", false, null, false));
        c.add(new Col("expenseItemAmount", "费用金额（明细）", G_ITEM, "amount", "(i.line->>'amount')::numeric", false, null, true));
        c.add(new Col("expenseItemRemark", "费用说明（明细）", G_ITEM, "text", "i.line->>'remark'", false, null, false));
        c.add(new Col("expenseItemRowNo", "明细行号", G_ITEM, "int", "i.idx", false, null, false));

        c.itemTotalKeys.add("expenseItemAmount");
        c.docTotalKeys.addAll(docSource.docTotalKeys);
        return c;
    }

    /** 复制单据列（两张明细共用同一套表头列；重新 new 一份，避免两套配置共享可变筛选状态） */
    private static void copyDocColumns(BillDetailReportConfig src, BillDetailReportConfig dst) {
        for (Col s : src.columns) {
            if (!G_DOC.equals(s.group)) continue;
            Col c = new Col(s.key, s.label, s.group, s.type, s.expr, s.visible, s.fixed, s.sortable);
            c.filterType = s.filterType;
            c.filterExpr = s.filterExpr;
            c.optionSource = s.optionSource;
            c.valueKind = s.valueKind;
            c.filterDefault = s.filterDefault;
            c.cfEntityType = s.cfEntityType;
            c.cfFieldKey = s.cfFieldKey;
            dst.add(c);
        }
    }
}
