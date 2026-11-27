package com.erp.dashboard.service;

import com.erp.config.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据统一摘要服务（2026-11-19 弹窗明细）：按单据类型返回 头信息 + 明细行，
 * 供全局弹窗展示「什么单据、什么内容」。全部表名/列名来自下方白名单配置，
 * 不接受外部传入表列名；查询参数一律走占位符。
 *
 * 头：单号 / 往来单位 / 金额 / 提交人（created_by）/ 提交时间（created_at）
 * 明细行：name（货品/摘要）、spec（规格）、qty（数量）、price（单价）、amount（金额）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillSummaryService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * type → [头表, 主键列, 单号列, 金额列(可空), 往来列表达式(可空；c.前缀=需 JOIN customer)]
     */
    private static final Map<String, String[]> HEAD = new LinkedHashMap<>();

    static {
        HEAD.put("SALE_ORDER",         new String[]{"sale_order",         "order_id",    "order_no",    "total_amount", "customer_name"});
        HEAD.put("SALE_DELIVERY",      new String[]{"sale_delivery",      "delivery_id", "delivery_no", "total_amount", "c.customer_name"});
        HEAD.put("SALE_RETURN",        new String[]{"sale_return",        "return_id",   "return_no",   "total_amount", "c.customer_name"});
        HEAD.put("SALE_INVOICE",       new String[]{"sale_invoice",       "invoice_id",  "invoice_no",  "total_amount", "c.customer_name"});
        HEAD.put("PURCHASE_ORDER",     new String[]{"purchase_order",     "order_id",    "order_no",    "total_amount", "supplier_name"});
        HEAD.put("PURCHASE_INBOUND",   new String[]{"purchase_inbound",   "inbound_id",  "inbound_no",  "total_amount", "supplier_name"});
        HEAD.put("PURCHASE_RETURN",    new String[]{"purchase_return",    "return_id",   "return_no",   "total_amount", "supplier_name"});
        HEAD.put("PURCHASE_INVOICE",   new String[]{"purchase_invoice",   "invoice_id",  "invoice_no",  "total_amount", "supplier_name"});
        HEAD.put("STOCK_TRANSFER",     new String[]{"stock_transfer",     "transfer_id", "transfer_no", "total_amount", null});
        HEAD.put("STOCK_CHECK",        new String[]{"stock_check",        "check_id",    "check_no",    "check_amount", null});
        HEAD.put("OPERATION_REPORT",   new String[]{"operation_report",   "report_id",   "report_no",   null,           null});
        HEAD.put("PRODUCTION_RECEIPT", new String[]{"production_receipt", "receipt_id",  "receipt_no",  "total_amount", null});
        // 财务管理（审批待办弹窗展示单据内容；列名均为下方白名单常量）
        HEAD.put("PURCHASE_EXPENSE",      new String[]{"purchase_expense",      "id",            "expense_no",    "total_amount", "supplier_name"});
        HEAD.put("PURCHASE_PRE_PAYMENT",  new String[]{"purchase_pre_payment",  "id",            "payment_no",    "amount",       "supplier_name"});
        HEAD.put("SALE_PRE_RECEIPT",      new String[]{"sale_pre_receipt",      "id",            "receipt_no",    "amount",       "customer_name"});
        HEAD.put("PAYMENT_RECEIPT",       new String[]{"cash_receipt",          "receipt_id",    "receipt_no",    "total_amount", "customer_name"});
        HEAD.put("PAYMENT_VOUCHER",       new String[]{"cash_payment",          "payment_id",    "payment_no",    "total_amount", "supplier_name"});
        HEAD.put("OPERATION_EXPENSE",     new String[]{"operation_expense",     "expense_id",    "expense_no",    "amount",       "applicant_name"});
        HEAD.put("WAGE_SETTLEMENT",       new String[]{"wage_settlement",       "settlement_id", "settlement_no", "total_amount", "employee_name"});
    }

    /**
     * type → [明细表, 外键列, 数量列, 金额列, 名称列, 规格列, 单价列, 排序列]（任一列可 null → 输出空值）；
     * OPERATION_REPORT / OPERATION_EXPENSE 无明细表（getItems 特构一行）
     */
    private static final Map<String, String[]> ITEM = new LinkedHashMap<>();

    static {
        ITEM.put("SALE_ORDER",         new String[]{"sale_order_item",         "order_id",    "quantity",       "amount",       "material_name", "specification", "unit_price", "line_no"});
        ITEM.put("SALE_DELIVERY",      new String[]{"sale_delivery_item",      "delivery_id", "quantity",       "amount",       "material_name", "specification", "unit_price", "line_no"});
        ITEM.put("SALE_RETURN",        new String[]{"sale_return_item",        "return_id",   "quantity",       "amount",       "material_name", "specification", "unit_price", "line_no"});
        ITEM.put("SALE_INVOICE",       new String[]{"sale_invoice_item",       "invoice_id",  "quantity",       "amount",       "material_name", "specification", "unit_price", "line_no"});
        ITEM.put("PURCHASE_ORDER",     new String[]{"purchase_order_item",     "order_id",    "quantity",       "amount",       "material_name", "specification", "unit_price", "line_no"});
        ITEM.put("PURCHASE_INBOUND",   new String[]{"purchase_inbound_item",   "inbound_id",  "quantity",       "amount",       "material_name", "specification", "unit_price", "line_no"});
        ITEM.put("PURCHASE_RETURN",    new String[]{"purchase_return_item",    "return_id",   "quantity",       "amount",       "material_name", "specification", "unit_price", "line_no"});
        ITEM.put("PURCHASE_INVOICE",   new String[]{"purchase_invoice_item",   "invoice_id",  "quantity",       "amount",       "material_name", "specification", "unit_price", "line_no"});
        ITEM.put("STOCK_TRANSFER",     new String[]{"stock_transfer_item",     "transfer_id", "quantity",       "amount",       "material_name", "specification", "unit_price", "line_no"});
        ITEM.put("STOCK_CHECK",        new String[]{"stock_check_item",        "check_id",    "check_quantity", "check_amount", "material_name", "specification", "unit_price", "line_no"});
        ITEM.put("PRODUCTION_RECEIPT", new String[]{"production_receipt_item", "receipt_id",  "quantity",       "amount",       "material_name", "specification", "unit_price", "line_no"});
        // 财务管理
        ITEM.put("PAYMENT_RECEIPT",      new String[]{"cash_receipt_item",         "receipt_id",    null,                 "amount", "account_name", "account_no", null,        "sort_no"});
        ITEM.put("PAYMENT_VOUCHER",      new String[]{"cash_payment_item",         "payment_id",    null,                 "amount", "account_name", "account_no", null,        "sort_no"});
        ITEM.put("SALE_PRE_RECEIPT",     new String[]{"sale_pre_receipt_item",     "pre_receipt_id", null,                "amount", "source_no",    "account_name", null,      "id"});
        ITEM.put("PURCHASE_PRE_PAYMENT", new String[]{"purchase_pre_payment_item", "pre_payment_id", null,                "amount", "source_no",    "account_name", null,      "id"});
        ITEM.put("WAGE_SETTLEMENT",      new String[]{"wage_settlement_item",      "settlement_id", "qualified_quantity", "amount", "operation_name", "employee_name", "piece_rate", "line_no"});
        ITEM.put("PURCHASE_EXPENSE",     new String[]{"purchase_expense_item",     "expense_id",    "quantity",           "amount", "material_name", "source_no",    null,        "id"});
        // OPERATION_REPORT：无明细表 → getItems 特构一行汇报摘要
        // OPERATION_EXPENSE：无明细表 → getItems 特构一行费用摘要
    }

    /**
     * 销售三单头表只有 customer_id 无客户名列 → JOIN customer 补名称；其余类型往来列可空。
     */
    private static final Map<String, String> PARTY_JOIN = new LinkedHashMap<>();

    static {
        PARTY_JOIN.put("SALE_DELIVERY", " LEFT JOIN customer c ON c.customer_id = h.customer_id ");
        PARTY_JOIN.put("SALE_RETURN",   " LEFT JOIN customer c ON c.customer_id = h.customer_id ");
        PARTY_JOIN.put("SALE_INVOICE",  " LEFT JOIN customer c ON c.customer_id = h.customer_id ");
    }

    /** 按类型+单据ID返回摘要：头信息 + 明细行列表 */
    public Map<String, Object> getBillSummary(String type, Long id) {
        String[] head = HEAD.get(type);
        if (head == null) throw new BusinessException("不支持的单据类型: " + type);
        if (id == null) throw new BusinessException("单据ID不能为空");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("billType", type);

        // ---- 头信息 ----
        String[] h = HEAD.get(type);
        String partyExpr = h.length > 4 ? h[4] : null;
        StringBuilder sql = new StringBuilder("SELECT h.")
                .append(h[2]).append(" AS bill_no, h.created_by AS creator, h.created_at AS created_at");
        if (h[3] != null) sql.append(", h.").append(h[3]).append(" AS amount");
        if (partyExpr != null) sql.append(", ").append(partyExpr).append(" AS party_name");
        String join = partyExpr != null && partyExpr.startsWith("c.") ? PARTY_JOIN.get(type) : null;
        sql.append(" FROM ").append(h[0]).append(" h").append(join == null ? "" : join)
           .append(" WHERE h.").append(h[1]).append(" = ?");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), id);
        if (rows.isEmpty()) throw new BusinessException("单据不存在或已被删除");
        Map<String, Object> r = rows.get(0);
        out.put("billNo", r.get("bill_no"));
        out.put("creator", r.get("creator"));
        out.put("createTime", r.get("created_at"));
        out.put("amount", r.get("amount"));
        out.put("partyName", r.get("party_name"));

        // ---- 明细行 ----
        out.put("items", getItems(type, id, r));
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getItems(String type, Long id, Map<String, Object> headRow) {
        List<Map<String, Object>> items = new ArrayList<>();
        if ("OPERATION_REPORT".equals(type)) {
            // 工序汇报无明细表：一行摘要 = 工序名（汇报人）+ 汇报数量
            Map<String, Object> it = new LinkedHashMap<>();
            String op = headRow.get("operation_name") == null ? "" : String.valueOf(headRow.get("operation_name"));
            String emp = headRow.get("employee_name") == null ? "" : String.valueOf(headRow.get("employee_name"));
            it.put("name", op + (emp.isEmpty() ? "" : "（汇报人 " + emp + "）"));
            it.put("spec", null);
            it.put("qty", headRow.get("total_quantity"));
            it.put("price", null);
            it.put("amount", headRow.get("qualified_quantity") == null ? null : null);
            items.add(it);
            return items;
        }
        String[] cfg = ITEM.get(type);
        if (cfg == null) {
            // 费用分摊无明细表：一行摘要 = 费用类型 + 金额
            if ("OPERATION_EXPENSE".equals(type)) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT expense_type, remark, amount FROM operation_expense WHERE expense_id = ?", id);
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.get(0);
                    Map<String, Object> it = new LinkedHashMap<>();
                    it.put("name", row.get("expense_type"));
                    it.put("spec", row.get("remark"));
                    it.put("qty", null);
                    it.put("price", null);
                    it.put("amount", row.get("amount"));
                    items.add(it);
                }
            }
            return items;
        }
        String sql = "SELECT " + col(cfg, 4) + " AS name, " + col(cfg, 5) + " AS spec, "
                + col(cfg, 2) + " AS qty, " + col(cfg, 6) + " AS price, " + col(cfg, 3) + " AS amount "
                + " FROM " + cfg[0] + " WHERE " + cfg[1] + " = ?" + orderBy(cfg);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        for (Map<String, Object> row : rows) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("name", row.get("name"));
            it.put("spec", row.get("spec"));
            it.put("qty", row.get("qty"));
            it.put("price", row.get("price"));
            it.put("amount", row.get("amount"));
            items.add(it);
        }
        return items;
    }

    /** 明细列取白名单常量；未配置（null）输出 SQL NULL，保证结果列数量一致 */
    private static String col(String[] cfg, int idx) {
        return cfg.length > idx && cfg[idx] != null ? cfg[idx] : "NULL";
    }

    /** 明细排序：未配置则不排序 */
    private static String orderBy(String[] cfg) {
        return cfg.length > 7 && cfg[7] != null ? " ORDER BY " + cfg[7] : "";
    }
}
