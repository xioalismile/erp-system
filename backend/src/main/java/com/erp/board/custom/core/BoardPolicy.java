package com.erp.board.custom.core;

import com.erp.common.scope.DataScopeResolver;
import com.erp.report.custom.core.EntityMetadataRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自定义看板取数口径（2026-11-06 新增）。
 *
 * <p>解决用户实测的三类偏差：</p>
 * <ul>
 *   <li><b>业务口径</b>：与内置看板一致，单据只统计有效状态（已生效 / 已确认等），
 *       不再把草稿、已作废、已取消算进金额；明细类数据源按它所属的表头判定。</li>
 *   <li><b>业务日期</b>：按单据类型指定默认日期（订单日期 / 出库日期 / 检验时间…），
 *       不再取字段列表里第一个日期（曾把“生产日期”“交货日期”当默认）。</li>
 *   <li><b>数据权限</b>：与列表页同一套数据范围（创建人 + 业务归属人/部门），看板不放大可见范围。</li>
 * </ul>
 *
 * <p>表名/列名全部来自注册表白名单，用户输入只作为绑定参数。</p>
 */
@Component
public class BoardPolicy {

    /** 单号列（下钻清单展示与单据跳转用） */
    private static final Map<String, String> NO_COLUMN = new LinkedHashMap<>();
    /** 业务日期列（默认时间字段；明细表通过表头取） */
    private static final Map<String, String> BUSINESS_DATE = new LinkedHashMap<>();
    /** 业务归属列（数据权限的“本人/本部门”维度） */
    private static final Map<String, String> OWNER_COLUMN = new LinkedHashMap<>();

    /** 需要按有效状态统计的单据表（主档/台账不在此列，避免把“停用”当无效） */
    private static final Set<String> BILL_TABLES = Set.of(
            "sale_quote", "sale_contract", "sale_order", "sale_delivery", "sale_invoice", "sale_return",
            "purchase_request", "purchase_contract", "purchase_order", "purchase_receipt",
            "purchase_invoice", "purchase_return",
            "stock_transfer", "stock_check", "other_receipt", "other_issue", "material_requisition",
            "production_order", "production_receipt", "operation_report",
            "subcontract_order", "subcontract_pick", "subcontract_inbound",
            "subcontract_return", "subcontract_supplement",
            "cash_receipt", "cash_payment", "sale_pre_receipt", "purchase_pre_payment",
            "operation_expense", "sample_order", "free_sample_order"
    );

    /** 文本状态的有效取值（不同单据用词不同，统一白名单） */
    private static final String EFFECTIVE_TEXT_SQL = "IN ('已生效','已确认','已发货','已完成','已审核')";

    /** 数字状态单据的有效值（其余单据若为数字状态则不强行过滤，避免误杀） */
    private static final Map<String, String> NUMERIC_EFFECTIVE = Map.of(
            "subcontract_order", "1,2",
            "subcontract_pick", "1,2",
            "subcontract_inbound", "1,2",
            "subcontract_return", "1,2",
            "subcontract_supplement", "1,2",
            "sale_pre_receipt", "1,2",
            "purchase_pre_payment", "1,2"
    );

    static {
        NO_COLUMN.put("sale_quote", "quote_no");
        NO_COLUMN.put("sale_contract", "contract_no");
        NO_COLUMN.put("sale_order", "order_no");
        NO_COLUMN.put("sale_delivery", "delivery_no");
        NO_COLUMN.put("sale_invoice", "invoice_no");
        NO_COLUMN.put("sale_return", "return_no");
        NO_COLUMN.put("purchase_request", "request_no");
        NO_COLUMN.put("purchase_contract", "contract_no");
        NO_COLUMN.put("purchase_order", "order_no");
        NO_COLUMN.put("purchase_receipt", "receipt_no");
        NO_COLUMN.put("purchase_invoice", "invoice_no");
        NO_COLUMN.put("purchase_return", "return_no");
        NO_COLUMN.put("stock_transfer", "transfer_no");
        NO_COLUMN.put("stock_check", "check_no");
        NO_COLUMN.put("other_receipt", "receipt_no");
        NO_COLUMN.put("other_issue", "issue_no");
        NO_COLUMN.put("material_requisition", "requisition_no");
        NO_COLUMN.put("production_order", "order_no");
        NO_COLUMN.put("production_receipt", "receipt_no");
        NO_COLUMN.put("operation_report", "report_no");
        NO_COLUMN.put("subcontract_order", "order_no");
        NO_COLUMN.put("subcontract_pick", "pick_no");
        NO_COLUMN.put("subcontract_inbound", "inbound_no");
        NO_COLUMN.put("subcontract_return", "return_no");
        NO_COLUMN.put("subcontract_supplement", "supplement_no");
        NO_COLUMN.put("cash_receipt", "receipt_no");
        NO_COLUMN.put("cash_payment", "payment_no");
        NO_COLUMN.put("sale_pre_receipt", "receipt_no");
        NO_COLUMN.put("purchase_pre_payment", "payment_no");
        NO_COLUMN.put("operation_expense", "expense_no");
        NO_COLUMN.put("sample_order", "sample_no");
        NO_COLUMN.put("free_sample_order", "order_no");

        BUSINESS_DATE.put("sale_quote", "quote_date");
        BUSINESS_DATE.put("sale_contract", "contract_date");
        BUSINESS_DATE.put("sale_order", "order_date");
        BUSINESS_DATE.put("sale_delivery", "delivery_date");
        BUSINESS_DATE.put("sale_invoice", "invoice_date");
        BUSINESS_DATE.put("sale_return", "return_date");
        BUSINESS_DATE.put("purchase_request", "request_date");
        BUSINESS_DATE.put("purchase_contract", "contract_date");
        BUSINESS_DATE.put("purchase_order", "order_date");
        BUSINESS_DATE.put("purchase_receipt", "receipt_date");
        BUSINESS_DATE.put("purchase_invoice", "invoice_date");
        BUSINESS_DATE.put("purchase_return", "return_date");
        BUSINESS_DATE.put("stock_transfer", "transfer_date");
        BUSINESS_DATE.put("stock_check", "check_date");
        BUSINESS_DATE.put("other_receipt", "receipt_date");
        BUSINESS_DATE.put("other_issue", "issue_date");
        BUSINESS_DATE.put("material_requisition", "requisition_date");
        BUSINESS_DATE.put("production_order", "order_date");
        BUSINESS_DATE.put("production_receipt", "receipt_date");
        BUSINESS_DATE.put("operation_report", "report_date");
        BUSINESS_DATE.put("subcontract_order", "order_date");
        BUSINESS_DATE.put("subcontract_pick", "pick_date");
        BUSINESS_DATE.put("subcontract_inbound", "inbound_date");
        BUSINESS_DATE.put("subcontract_return", "return_date");
        BUSINESS_DATE.put("subcontract_supplement", "supplement_date");
        BUSINESS_DATE.put("cash_receipt", "receipt_date");
        BUSINESS_DATE.put("cash_payment", "payment_date");
        BUSINESS_DATE.put("sale_pre_receipt", "receipt_date");
        BUSINESS_DATE.put("purchase_pre_payment", "payment_date");
        BUSINESS_DATE.put("operation_expense", "expense_date");
        BUSINESS_DATE.put("sample_order", "demand_date");
        BUSINESS_DATE.put("free_sample_order", "order_date");
        BUSINESS_DATE.put("qc_record", "inspect_time");

        OWNER_COLUMN.put("sale_quote", "saler_id");
        OWNER_COLUMN.put("sale_contract", "saler_id");
        OWNER_COLUMN.put("sale_order", "saler_id");
        OWNER_COLUMN.put("sale_delivery", "saler_id");
        OWNER_COLUMN.put("sale_invoice", "saler_id");
        OWNER_COLUMN.put("sale_return", "saler_id");
        OWNER_COLUMN.put("purchase_request", "buyer_id");
        OWNER_COLUMN.put("purchase_contract", "buyer_id");
        OWNER_COLUMN.put("purchase_order", "buyer_id");
        OWNER_COLUMN.put("purchase_receipt", "buyer_id");
        OWNER_COLUMN.put("purchase_invoice", "buyer_id");
        OWNER_COLUMN.put("purchase_return", "buyer_id");
    }

    /** 明细数据源所属的表头关联（group=表头）；没有返回 null */
    public EntityMetadataRegistry.JoinMeta headerJoin(EntityMetadataRegistry.DataSourceMeta ds) {
        for (EntityMetadataRegistry.JoinMeta jm : ds.joins.values()) {
            if ("表头".equals(jm.group)) return jm;
        }
        return null;
    }

    /** 统计所依据的“业务主体”表：明细表取表头，表头/主表取自己 */
    public String subjectTable(EntityMetadataRegistry.DataSourceMeta ds) {
        EntityMetadataRegistry.JoinMeta h = headerJoin(ds);
        return h == null ? ds.table : h.table;
    }

    /** 业务日期字段路径（主表字段 或 表头字段的 "关联标识.字段名"）；找不到返回 null */
    public String businessDateField(EntityMetadataRegistry.DataSourceMeta ds) {
        EntityMetadataRegistry.JoinMeta h = headerJoin(ds);
        String mainCol = BUSINESS_DATE.get(ds.table);
        if (mainCol != null) {
            for (EntityMetadataRegistry.FieldMeta f : ds.fields.values()) {
                if (f.column.equals(mainCol) && isDateType(f.type)) return f.field;
            }
        }
        if (h != null) {
            String headerCol = BUSINESS_DATE.get(h.table);
            if (headerCol != null) {
                for (EntityMetadataRegistry.FieldMeta f : h.fields.values()) {
                    if (f.column.equals(headerCol) && isDateType(f.type)) return h.key + "." + f.field;
                }
            }
        }
        return null;
    }

    /** 是否“只统计有效状态”的单据（用于界面口径提示） */
    public String effectiveLabel(EntityMetadataRegistry.DataSourceMeta ds) {
        EntityMetadataRegistry.JoinMeta h = headerJoin(ds);
        String table = h == null ? ds.table : h.table;
        Map<String, EntityMetadataRegistry.FieldMeta> fields = h == null ? ds.fields : h.fields;
        EntityMetadataRegistry.FieldMeta st = statusField(fields);
        if (st == null || !BILL_TABLES.contains(table)) return null;
        if ("number".equals(st.type) || "amount".equals(st.type)) {
            return NUMERIC_EFFECTIVE.containsKey(table) ? "仅有效状态" : null;
        }
        return "仅已生效";
    }

    /**
     * 有效状态条件（带表别名）；不需要过滤时返回 null。
     * 明细数据源按表头状态过滤，避免统计到已作废单据的明细。
     */
    public String statusWhere(EntityMetadataRegistry.DataSourceMeta ds, String alias,
                              Map<String, EntityMetadataRegistry.FieldMeta> fields) {
        EntityMetadataRegistry.FieldMeta st = statusField(fields);
        if (st == null) return null;
        String tableForRule = tableOfAlias(ds, alias);
        if (tableForRule == null || !BILL_TABLES.contains(tableForRule)) return null;
        if ("number".equals(st.type) || "amount".equals(st.type)) {
            String vals = NUMERIC_EFFECTIVE.get(tableForRule);
            if (vals == null) return null;
            return "\"" + alias + "\".\"" + st.column + "\" IN (" + vals + ")";
        }
        return "\"" + alias + "\".\"" + st.column + "\" " + EFFECTIVE_TEXT_SQL;
    }

    /** 数据权限条件（带表别名）；全部可见时返回 null */
    public String scopeWhere(EntityMetadataRegistry.DataSourceMeta ds, String alias,
                             Map<String, EntityMetadataRegistry.FieldMeta> fields,
                             DataScopeResolver.DataScopeContext ctx, List<Object> args) {
        if (ctx == null || ctx.isAll()) return null;
        String table = tableOfAlias(ds, alias);
        boolean hasCreatedBy = fields.values().stream().anyMatch(f -> f.column.equals("created_by"));
        String ownerCol = table == null ? null : OWNER_COLUMN.get(table);
        String ownerField = null;
        if (ownerCol != null) {
            for (EntityMetadataRegistry.FieldMeta f : fields.values()) {
                if (f.column.equals(ownerCol)) { ownerField = f.column; break; }
            }
        }
        if (!hasCreatedBy && ownerField == null) {
            // 无归属列的表（台账/主档等）：沿用改造前的可见范围，不在此处强行拦截
            return null;
        }
        List<String> ors = new ArrayList<>();
        if ("DEPT".equals(ctx.getScope())) {
            if (hasCreatedBy && ctx.getDeptUsernames() != null && !ctx.getDeptUsernames().isEmpty()) {
                ors.add(createdByIn(alias, "created_by", ctx.getDeptUsernames(), args));
            } else if (hasCreatedBy && ctx.getUsername() != null) {
                ors.add(eq(alias, "created_by", ctx.getUsername(), args));
            }
            if (ownerField != null && ctx.getDeptEmployeeIds() != null && !ctx.getDeptEmployeeIds().isEmpty()) {
                ors.add(inList(alias, ownerField, ctx.getDeptEmployeeIds(), args));
            }
        } else {
            if (hasCreatedBy && ctx.getUsername() != null) {
                ors.add(eq(alias, "created_by", ctx.getUsername(), args));
            }
            if (ownerField != null && ctx.getEmployeeId() != null) {
                ors.add(eq(alias, ownerField, ctx.getEmployeeId(), args));
            }
        }
        if (ors.isEmpty()) return "1 = 0";
        return "(" + String.join(" OR ", ors) + ")";
    }

    /** 是否业务单据表（主档/台账不在内）；数据权限只对单据表生效 */
    public boolean isBillTable(String table) {
        return table != null && BILL_TABLES.contains(table);
    }

    /** 单号列（下钻清单用）；未登记返回 null */
    public String noColumn(String table) {
        return table == null ? null : NO_COLUMN.get(table);
    }

    private String tableOfAlias(EntityMetadataRegistry.DataSourceMeta ds, String alias) {
        if ("t1".equals(alias)) return ds.table;
        for (EntityMetadataRegistry.JoinMeta jm : ds.joins.values()) {
            if (jm.alias.equals(alias)) return jm.table;
        }
        return null;
    }

    private EntityMetadataRegistry.FieldMeta statusField(Map<String, EntityMetadataRegistry.FieldMeta> fields) {
        for (EntityMetadataRegistry.FieldMeta f : fields.values()) {
            if (f.column.equals("status")) return f;
        }
        return null;
    }

    private boolean isDateType(String type) {
        return "date".equals(type) || "datetime".equals(type);
    }

    private String eq(String alias, String col, Object v, List<Object> args) {
        args.add(v);
        return "\"" + alias + "\".\"" + col + "\" = ?";
    }

    private String createdByIn(String alias, String col, List<String> vals, List<Object> args) {
        List<String> marks = new ArrayList<>();
        for (String v : vals) { marks.add("?"); args.add(v); }
        if (marks.isEmpty()) return "1 = 0";
        return "\"" + alias + "\".\"" + col + "\" IN (" + String.join(",", marks) + ")";
    }

    private String inList(String alias, String col, List<Long> vals, List<Object> args) {
        List<String> marks = new ArrayList<>();
        for (Long v : vals) { marks.add("?"); args.add(v); }
        if (marks.isEmpty()) return "1 = 0";
        return "\"" + alias + "\".\"" + col + "\" IN (" + String.join(",", marks) + ")";
    }
}
