package com.erp.report.detail;

import com.erp.report.detail.BillDetailReportConfig.Col;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多明细合并配置（2026-11-27 列表页明细模式适配）。
 *
 * <p><b>背景</b>：编辑页有两张明细表的单据（收款单＝收款明细＋核销明细、付款单＝付款明细＋核销明细、
 * 生产工单＝成品明细＋原料明细、组装/拆卸＝成品明细＋用料明细），列表页明细模式此前只绑定其中一张，
 * 另一张的字段在「列设置」里根本看不到。用户口径：<b>不改列设置布局，把两张明细的字段全部自动列出来，
 * 由用户自己勾选</b>。</p>
 *
 * <p><b>做法</b>：把各来源明细表 UNION ALL 成一张"明细子表"（多一个「明细类型」列），
 * 再合成一份普通 {@link BillDetailReportConfig} 交给原引擎——引擎仍按"主表 + 一张明细表"工作，
 * 无需改动查询/合计/导出逻辑：</p>
 * <ul>
 *   <li>列 = 各来源列并集（同 key 合并，标签取第一个来源）；两张明细的自定义字段按来源加后缀，避免同名字段串值</li>
 *   <li>行 = 各来源明细行；没有明细的单据仍按来源各出一行表头（与单明细口径一致）</li>
 *   <li>行主键 = 来源序号 * 1e12 + 明细主键（导出勾选行时仍可按主键精确取数）</li>
 *   <li>筛选：单据级条件两边共用；明细级条件按来源各自投影，用不到的一边为 NULL</li>
 * </ul>
 */
public final class BillDetailReportUnion {

    /** 明细来源 */
    public static final class Source {
        /** 行类型显示值（成品 / 原料 / 核销 / 收款…） */
        final String tag;
        /** 英文短标识（两张明细表的自定义字段 key 前缀，避免同名字段冲突） */
        final String slug;
        final BillDetailReportConfig cfg;

        public Source(String tag, String slug, BillDetailReportConfig cfg) {
            this.tag = tag;
            this.slug = slug;
            this.cfg = cfg;
        }
    }

    /** 行主键分区间隔：第 N 个来源的明细主键偏移 N * ID_OFFSET（明细主键均为自增 bigint，远小于 1e12） */
    private static final long ID_OFFSET = 1_000_000_000_000L;

    private BillDetailReportUnion() {}

    /** 合并列（外层的 Col 表达式统一指向 UNION 子表 i） */
    private static final class Merged {
        final String key;
        final String label;
        final String type;
        final String group;
        final boolean visible;
        final String fixed;
        final boolean sortable;
        String filterType;
        String optionSource;
        String valueKind;
        boolean filterDefault;
        String cfEntityType;
        String cfFieldKey;
        /** 该 key 在各来源里的原始列（用于分支投影） */
        final Map<Source, Col> sourceCols = new LinkedHashMap<>();

        Merged(String key, String label, String type, String group,
               boolean visible, String fixed, boolean sortable) {
            this.key = key; this.label = label; this.type = type; this.group = group;
            this.visible = visible; this.fixed = fixed; this.sortable = sortable;
        }
    }

    public static BillDetailReportConfig build(JdbcTemplate jdbcTemplate, List<Source> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("多明细配置至少需要一个明细来源");
        }
        BillDetailReportConfig first = sources.get(0).cfg;
        BillDetailReportConfig out = new BillDetailReportConfig();
        out.mainTable = first.mainTable;
        out.docIdColumn = first.docIdColumn;
        out.docNoExpr = first.docNoExpr;
        out.docDateFilterKey = first.docDateFilterKey;
        out.dataScopeOwnerColumn = first.dataScopeOwnerColumn;
        out.dataScopeCreatedByColumn = first.dataScopeCreatedByColumn;
        // UNION 子表只暴露一个主键列；行主键见 _item_key
        out.itemFkColumn = "_doc_id";
        out.itemIdColumn = "_item_key";
        // 自定义字段由各来源自带，合并时统一处理；这里不再让引擎重复加载
        out.cfEntityDoc = null;
        out.cfEntityItem = null;

        List<Merged> merged = mergeColumns(jdbcTemplate, sources);

        // 「明细类型」列放在明细字段最前面，用户可据此区分行来源（也可当普通筛选条件）
        Merged typeCol = new Merged("detailType", "明细类型", "text",
                BillDetailReportConfig.G_ITEM, true, null, false);
        typeCol.filterType = "select";
        typeCol.filterDefault = false;
        int insertAt = merged.size();
        for (int i = 0; i < merged.size(); i++) {
            if (BillDetailReportConfig.G_ITEM.equals(merged.get(i).group)) { insertAt = i; break; }
        }
        // 明细类型的下拉选项直接由来源标签组成（静态枚举，前端不需要再拉主档）
        List<String> typeOptions = new ArrayList<>();
        for (Source s : sources) typeOptions.add(s.tag);
        typeCol.optionSource = "detailType:" + String.join("|", typeOptions);
        merged.add(insertAt, typeCol);

        for (Merged m : merged) out.add(toOuterCol(m));

        // 明细来源 SQL（UNION ALL 子表）
        StringBuilder unionSql = new StringBuilder("(");
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) unionSql.append(" UNION ALL ");
            unionSql.append(branchSql(sources.get(i), merged, (i + 1) * ID_OFFSET));
        }
        unionSql.append(") ");
        out.itemTable = unionSql.toString();

        // 默认排序：单据日期倒序、单号倒序（与单明细报表同口径；日期表达式只在引用主表 o 时采用）
        String dateExpr = null;
        for (Col c : first.columns) {
            if (c.key.equals(first.docDateFilterKey)) { dateExpr = c.expr; break; }
        }
        StringBuilder order = new StringBuilder(" ORDER BY ");
        if (dateExpr != null && dateExpr.matches("^\\s*\\(?o\\.[A-Za-z0-9_]+\\)?(::date)?\\s*$")) {
            order.append(dateExpr).append(" DESC NULLS LAST, ");
        }
        order.append(out.docNoExpr).append(" DESC NULLS LAST");
        out.defaultOrderBy = order.toString();

        // 合计/隐藏列口径：按合并后的 key 去重
        Set<String> keys = new LinkedHashSet<>();
        for (Merged m : merged) keys.add(m.key);
        for (Source s : sources) {
            for (String k : s.cfg.itemTotalKeys) if (keys.contains(k)) out.itemTotalKeys.add(k);
            for (String k : s.cfg.docTotalKeys) if (keys.contains(k)) out.docTotalKeys.add(k);
            for (String k : s.cfg.reportHiddenKeys) if (keys.contains(k)) out.reportHiddenKeys.add(k);
        }
        return out;
    }

    // ==================== 列合并 ====================

    private static List<Merged> mergeColumns(JdbcTemplate jdbcTemplate, List<Source> sources) {
        List<Merged> merged = new ArrayList<>();
        Map<String, Merged> byKey = new LinkedHashMap<>();
        for (Source s : sources) {
            List<Col> cols = new ArrayList<>(s.cfg.columns);
            cols.addAll(BillDetailReportEngine.loadCustomFieldColumns(jdbcTemplate, s.cfg));
            for (Col c : cols) {
                BillDetailReportEngine.deriveFilter(c);
                boolean itemCf = BillDetailReportConfig.G_CF_ITEM.equals(c.group);
                String key = itemCf ? c.key + "__" + s.slug : c.key;
                Merged m = byKey.get(key);
                if (m == null) {
                    String label = itemCf ? c.label + "（" + s.tag + "）" : c.label;
                    m = new Merged(key, label, c.type, c.group, c.visible, c.fixed, c.sortable);
                    m.filterType = c.filterType;
                    m.optionSource = c.optionSource;
                    m.valueKind = c.valueKind;
                    m.filterDefault = c.filterDefault;
                    m.cfEntityType = c.cfEntityType;
                    m.cfFieldKey = c.cfFieldKey;
                    // 同一 key 两边筛选口径不同时以第一个来源为准（例如单号在两边都是文本模糊）
                    byKey.put(key, m);
                    merged.add(m);
                }
                m.sourceCols.put(s, c);
            }
        }
        return merged;
    }

    /** 外层列：表达式/筛选表达式都指向 UNION 子表 i */
    private static Col toOuterCol(Merged m) {
        Col c = new Col(m.key, m.label, m.group, m.type,
                "i." + quote(m.key), m.visible, m.fixed, m.sortable);
        c.filterType = m.filterType;
        c.optionSource = m.optionSource;
        c.valueKind = m.valueKind;
        c.filterDefault = m.filterDefault;
        c.cfEntityType = m.cfEntityType;
        c.cfFieldKey = m.cfFieldKey;
        // 「明细类型」直接比较行类型文本；其余列比较各来源投影出来的筛选表达式
        c.filterExpr = (m.filterType == null) ? null
                : "detailType".equals(m.key) ? "i." + quote(m.key)
                : "i." + quote("__f_" + m.key);
        return c;
    }

    // ==================== 分支 SQL ====================

    private static String branchSql(Source s, List<Merged> merged, long offset) {
        BillDetailReportConfig c = s.cfg;
        StringBuilder sb = new StringBuilder("SELECT ");
        sb.append("o.").append(c.docIdColumn).append(" AS _doc_id, ");
        sb.append("(CAST(").append(itemIdExpr(c)).append(" AS BIGINT) + ").append(offset).append(") AS _item_key");
        for (Merged m : merged) {
            String expr;
            if ("detailType".equals(m.key)) {
                expr = "'" + escape(s.tag) + "'";
            } else {
                Col sc = m.sourceCols.get(s);
                expr = sc == null ? "NULL" : sc.expr;
            }
            sb.append(", ").append(expr).append(" AS ").append(quote(m.key));
        }
        for (Merged m : merged) {
            if (m.filterType == null || "detailType".equals(m.key)) continue;
            Col sc = m.sourceCols.get(s);
            String expr = sc == null ? "NULL"
                    : (sc.filterExpr != null ? sc.filterExpr : sc.expr);
            sb.append(", ").append(expr).append(" AS ").append(quote("__f_" + m.key));
        }
        // 引擎自定义排序会追加 i.line_no：各分支都投影一列（没有行号列的来源给 NULL）
        String lineNoExpr = "NULL";
        for (Col col : c.columns) {
            if ("lineNo".equals(col.key)) { lineNoExpr = col.expr; break; }
        }
        sb.append(", ").append(lineNoExpr).append(" AS line_no");
        sb.append(branchFrom(c));
        return sb.toString();
    }

    private static String itemIdExpr(BillDetailReportConfig c) {
        return c.hasItems()
                ? "i." + c.itemIdColumn
                : "o." + c.docIdColumn;
    }

    /** 与引擎 fromSql() 同口径（主表 + 明细 LEFT JOIN + 额外主档 JOIN），供 UNION 分支复用 */
    private static String branchFrom(BillDetailReportConfig c) {
        StringBuilder sb = new StringBuilder(" FROM ").append(c.mainTable).append(" o");
        if (c.itemJoinSql != null && !c.itemJoinSql.isBlank()) {
            sb.append(" ").append(c.itemJoinSql);
        } else if (c.hasItems()) {
            sb.append(" LEFT JOIN ").append(c.itemTable)
              .append(" i ON i.").append(c.itemFkColumn)
              .append(" = o.").append(c.docIdColumn);
            for (String f : c.itemFixedWhere) {
                if (f != null && !f.isBlank()) sb.append(" AND (").append(f).append(")");
            }
        }
        for (String j : c.extraJoins) sb.append(" ").append(j);
        if (!c.fixedWhere.isEmpty()) {
            sb.append(" WHERE 1 = 1 ");
            for (String f : c.fixedWhere) {
                if (f != null && !f.isBlank()) sb.append(" AND (").append(f).append(") ");
            }
        }
        return sb.toString();
    }

    private static String quote(String key) {
        return "\"" + key.replace("\"", "\"\"") + "\"";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
