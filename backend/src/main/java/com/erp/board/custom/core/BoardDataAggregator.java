package com.erp.board.custom.core;

import com.erp.common.bill.link.BillLinkRegistry;
import com.erp.common.scope.DataScopeResolver;
import com.erp.report.custom.core.EntityMetadataRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自定义看板 · 卡片取数（服务端唯一计算入口）。
 *
 * <p>能力：</p>
 * <ul>
 *   <li>单表取数 + 数值列四则公式求值（分母为 0 → 空值）</li>
 *   <li>多级关联：表头、基础资料、<b>来源单据（上游）</b>三类关联，按 LEFT JOIN 穿插查询；
 *       字段写法 {@code joinKey.field}，不带前缀时先找主表再找已选关联单</li>
 *   <li><b>统一业务口径</b>：单据只统计有效状态（已生效/已确认等），并叠加数据权限，
 *       与内置看板一致（2026-11-06）</li>
 *   <li><b>业务日期</b>：默认时间字段按单据类型取业务日期，明细表取所属表头的日期</li>
 *   <li>明细下钻：点卡片数字 → 单据级明细清单（含单号/单据类型，可继续打开单据）</li>
 * </ul>
 *
 * <p>安全：表名/列名全部来自注册表白名单；用户输入只进参数占位符。</p>
 */
@Slf4j
@Service
public class BoardDataAggregator {

    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbc;

    private final EntityMetadataRegistry registry;
    private final BoardPolicy policy;
    private final BillLinkRegistry billLinkRegistry;

    public BoardDataAggregator(EntityMetadataRegistry registry, BoardPolicy policy,
                               BillLinkRegistry billLinkRegistry) {
        this.registry = registry;
        this.policy = policy;
        this.billLinkRegistry = billLinkRegistry;
    }

    private static final Set<String> AGGS = Set.of("sum", "avg", "count", "max", "min", "distinct");
    private static final Set<String> BUCKETS = Set.of("day", "week", "month", "year");
    private static final Set<String> NUMERIC_TYPES = Set.of("amount", "number", "int");
    /** 公式求值固定用合计 */
    private static final Agg SUM_AGG = new Agg("sum", "求和", false);

    // ==================== 数据源 / 字段 / 关联 ====================

    public List<Map<String, Object>> listSources() {
        return registry.listDataSources();
    }

    public List<Map<String, Object>> listFields(String source) {
        EntityMetadataRegistry.DataSourceMeta ds = requireSource(source);
        String preferred = policy.businessDateField(ds);
        List<Map<String, Object>> out = new ArrayList<>();
        for (EntityMetadataRegistry.FieldMeta f : ds.fields.values()) out.add(fieldInfo(f, null, preferred));
        return out;
    }

    /** 可关联单据清单（表头 / 基础资料 / 来源单据，含各关联单可用字段） */
    public List<Map<String, Object>> listJoins(String source) {
        EntityMetadataRegistry.DataSourceMeta ds = requireSource(source);
        String preferred = policy.businessDateField(ds);
        List<Map<String, Object>> out = new ArrayList<>();
        for (EntityMetadataRegistry.JoinMeta j : ds.joins.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", j.key);
            m.put("name", j.name);
            m.put("group", j.group);
            m.put("source", "来源单据".equals(j.group));
            List<Map<String, Object>> fs = new ArrayList<>();
            for (EntityMetadataRegistry.FieldMeta f : j.fields.values()) fs.add(fieldInfo(f, j.key, preferred));
            m.put("fields", fs);
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> fieldInfo(EntityMetadataRegistry.FieldMeta f, String joinKey, String preferredPath) {
        Map<String, Object> m = new LinkedHashMap<>();
        String full = joinKey == null ? f.field : joinKey + "." + f.field;
        m.put("field", full);
        m.put("label", f.label);
        m.put("type", f.type);
        m.put("joinKey", joinKey);
        m.put("time", "date".equals(f.type) || "datetime".equals(f.type));
        m.put("numeric", NUMERIC_TYPES.contains(f.type));
        m.put("preferred", preferredPath != null && preferredPath.equals(full));
        return m;
    }

    // ==================== 单卡取数 ====================

    /** 返回该卡的展示数据（metric/wave/compare/ana/table 各自结构） */
    public Map<String, Object> cardData(Map<String, Object> card, String periodOverride,
                                        DataScopeResolver.DataScopeContext scope) {
        // 多来源公式卡（2026-11-06）：多个取数项各自取数，再按公式做加减乘除
        if ("multi".equals(str(card.get("mode"))) && "metric".equals(str(card.get("type")))) {
            return multiCardData(card, periodOverride, scope);
        }
        String source = str(card.get("source"));
        EntityMetadataRegistry.DataSourceMeta ds = requireSource(source);
        Ctx ctx = buildCtx(ds, card);
        String type = str(card.get("type"));
        String period = periodOverride != null && !periodOverride.isBlank()
                ? periodOverride : strOr(card.get("period"), "month");
        LocalDate[] range = resolveRange(period);

        Res timeRef = resolveTimeRef(ds, ctx, str(card.get("timeField")));
        List<String> baseConds = new ArrayList<>();
        List<Object> baseArgs = new ArrayList<>();
        addStatusAndScope(ds, ctx, scope, baseConds, baseArgs);

        List<String> conds = new ArrayList<>(baseConds);
        List<Object> args = new ArrayList<>(baseArgs);
        if (timeRef != null) {
            conds.add("\"" + timeRef.alias + "\".\"" + timeRef.column + "\" >= ? AND \""
                    + timeRef.alias + "\".\"" + timeRef.column + "\" < ?");
            args.add(range[0].atStartOfDay());
            args.add(range[1].atStartOfDay());
        }
        String where = conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);

        Map<String, Object> out = new LinkedHashMap<>();
        switch (type == null ? "" : type) {
            case "metric" -> out.putAll(metricData(card, ctx, where, args));
            case "wave", "compare", "ana" -> out.putAll(chartData(card, ctx, baseConds, baseArgs, range, timeRef));
            case "table" -> out.putAll(tableData(card, ctx, where, args, timeRef));
            default -> throw new IllegalArgumentException("不支持的卡片类型: " + type);
        }
        out.put("source", source);
        out.put("period", period);
        out.put("timeField", timeRef == null ? null : timeRef.field);
        String effective = policy.effectiveLabel(ds);
        if (effective != null) out.put("criteria", effective);
        // 空数据区分：本期没数据 vs 整表没数据，前端据此给不同提示，避免误判"统计不出来"
        if (isBlank(out)) {
            long total = countAll(ctx, baseConds, baseArgs);
            out.put("emptyHint", total > 0 ? "本期无数据，可切换时间维度再看" : "该数据源暂无数据");
        }
        return out;
    }

    private boolean isBlank(Map<String, Object> out) {
        if (out.containsKey("points")) {
            List<?> p = (List<?>) out.get("points");
            return p == null || p.isEmpty();
        }
        if (out.containsKey("rows")) {
            List<?> r = (List<?>) out.get("rows");
            return r == null || r.isEmpty();
        }
        return out.get("value") == null;
    }

    private long countAll(Ctx ctx, List<String> conds, List<Object> args) {
        String where = conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM " + ctx.from + where, Long.class, args.toArray());
        return c == null ? 0 : c;
    }

    /** 有效状态 + 数据权限（统一套在“业务主体”表上：明细取表头，表头/主表取自己） */
    private void addStatusAndScope(EntityMetadataRegistry.DataSourceMeta ds, Ctx ctx,
                                   DataScopeResolver.DataScopeContext scope,
                                   List<String> conds, List<Object> args) {
        EntityMetadataRegistry.JoinMeta h = policy.headerJoin(ds);
        String alias = h == null ? "t1" : h.alias;
        Map<String, EntityMetadataRegistry.FieldMeta> fields = h == null ? ds.fields : h.fields;
        String status = policy.statusWhere(ds, alias, fields);
        if (status != null) conds.add(status);
        // 数据权限只对业务单据生效（主档/台账无归属列，沿用改造前的可见范围）
        if (policy.isBillTable(policy.subjectTable(ds))) {
            String scopeSql = policy.scopeWhere(ds, alias, fields, scope, args);
            if (scopeSql != null) conds.add(scopeSql);
        }
    }

    // ---------- 指标卡 ----------
    private Map<String, Object> metricData(Map<String, Object> card, Ctx ctx,
                                           String where, List<Object> args) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> tokens = (List<String>) card.get("tokens");
        if (tokens != null && !tokens.isEmpty()) {
            Double v = evalTokens(tokens, ctx, where, args);
            out.put("value", v);
            out.put("formula", tokens.stream()
                    .map(t -> {
                        Res r = ctx.resolveQuiet(t);
                        if (r != null) return r.meta.label;
                        return "*".equals(t) ? "×" : "/".equals(t) ? "÷" : t;
                    })
                    .collect(java.util.stream.Collectors.joining(" ")));
            return out;
        }
        Agg a = aggOf(card);
        Res res = ctx.resolveNumeric(str(card.get("field")));
        String expr = a.count ? "COUNT(*)" : a.sql(res.qualify());
        Double v = a.count ? scalarCount(ctx, where, args) : scalar(ctx, expr, where, args);
        out.put("value", v);
        out.put("formula", a.label + " " + (a.count ? "记录数" : res.meta.label));
        return out;
    }

    // ---------- 多来源公式卡 ----------

    /**
     * 多取数项 + 公式：每个取数项可来自不同单据，各自按时间/筛选/数据权限取数，
     * 公式对“取数项名称”做 + − × ÷ 与括号运算（2026-11-06，PRD FR-01~FR-03）。
     */
    private Map<String, Object> multiCardData(Map<String, Object> card, String periodOverride,
                                              DataScopeResolver.DataScopeContext scope) {
        String period = periodOverride != null && !periodOverride.isBlank()
                ? periodOverride : strOr(card.get("period"), "month");
        LocalDate[] range = resolveRange(period);
        List<Map<String, Object>> items = itemsOf(card);
        if (items.isEmpty()) throw new IllegalArgumentException("请先添加取数项");

        Map<String, Double> vars = new LinkedHashMap<>();
        List<Map<String, Object>> diag = new ArrayList<>();
        java.util.LinkedHashSet<String> criteria = new java.util.LinkedHashSet<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();
        String firstError = null;
        long totalRows = 0;

        for (Map<String, Object> item : items) {
            String name = strOr(item.get("name"), "").trim();
            if (name.isEmpty()) throw new IllegalArgumentException("取数项名称不能为空");
            if (!seenNames.add(name)) throw new IllegalArgumentException("取数项名称不能重复：" + name);
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("name", name);
            one.put("source", str(item.get("source")));
            try {
                ItemEval ev = evalItem(item, range, scope);
                one.put("value", ev.value);
                one.put("rows", ev.rows);
                vars.put(name, ev.value);
                totalRows += ev.rows;
                if (ev.criteria != null) criteria.add(ev.criteria);
                if (ev.value == null) one.put("hint", "取数为空，请检查筛选或时间范围");
            } catch (Exception e) {
                String msg = friendlyMessage(e);
                one.put("error", msg);
                vars.put(name, null);
                if (firstError == null) firstError = name + "：" + msg;
            }
            diag.add(one);
        }

        List<String> tokens = tokensOf(card.get("formula"));
        Double value = evalFormula(tokens, vars);
        String formulaError = null;
        if (value == null && firstError == null && !tokens.isEmpty()) {
            formulaError = "公式无法计算，请检查变量是否都有值、分母是否为 0";
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "multi");
        out.put("value", value);
        out.put("formula", formulaText(tokens));
        out.put("items", diag);
        out.put("period", period);
        if (!criteria.isEmpty()) out.put("criteria", String.join(" / ", criteria));
        if (firstError != null) out.put("error", firstError);
        else if (formulaError != null) out.put("formulaError", formulaError);
        if (value == null && totalRows == 0 && firstError == null) {
            out.put("emptyHint", "本期无数据，可切换时间维度再看");
        }
        return out;
    }

    /** 单个取数项的取数结果 */
    private static final class ItemEval {
        Double value;
        long rows;
        String criteria;
    }

    private ItemEval evalItem(Map<String, Object> item, LocalDate[] range,
                              DataScopeResolver.DataScopeContext scope) {
        EntityMetadataRegistry.DataSourceMeta ds = requireSource(str(item.get("source")));
        Ctx ctx = buildCtx(ds, item);
        Res timeRef = resolveTimeRef(ds, ctx, str(item.get("timeField")));

        List<String> conds = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addStatusAndScope(ds, ctx, scope, conds, args);
        if (timeRef != null) {
            conds.add("\"" + timeRef.alias + "\".\"" + timeRef.column + "\" >= ? AND \""
                    + timeRef.alias + "\".\"" + timeRef.column + "\" < ?");
            args.add(range[0].atStartOfDay());
            args.add(range[1].atStartOfDay());
        }
        applyFilters(item.get("filters"), ctx, conds, args);
        String where = conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);

        Agg a = aggOf(item);
        Double value;
        if (a.count) {
            value = scalarCount(ctx, where, args);
        } else {
            Res res = ctx.resolveNumeric(str(item.get("field")));
            value = scalar(ctx, a.sql(res.qualify()), where, args);
        }
        ItemEval ev = new ItemEval();
        ev.value = value;
        ev.rows = countAll(ctx, conds, args);
        ev.criteria = policy.effectiveLabel(ds);
        return ev;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itemsOf(Map<String, Object> card) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object raw = card.get("items");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) item.put(String.valueOf(e.getKey()), e.getValue());
                    out.add(item);
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<String> tokensOf(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) if (o != null) out.add(String.valueOf(o));
        }
        return out;
    }

    // ---------- 取数项筛选 ----------

    private void applyFilters(Object raw, Ctx ctx, List<String> conds, List<Object> args) {
        if (!(raw instanceof List<?> list)) return;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String field = str(m.get("field"));
            String op = strOr(m.get("op"), "eq").toLowerCase();
            Object val = m.get("value");
            if (field == null || field.isBlank() || val == null || String.valueOf(val).isBlank()) continue;
            Res r = ctx.resolveQuiet(field);
            if (r == null) continue;
            String col = r.qualify();
            boolean numeric = NUMERIC_TYPES.contains(r.meta.type);
            switch (op) {
                case "eq" -> { conds.add(col + " = ?"); args.add(numeric ? toDecimal(val) : String.valueOf(val)); }
                case "ne" -> { conds.add(col + " <> ?"); args.add(numeric ? toDecimal(val) : String.valueOf(val)); }
                case "gt" -> { conds.add(col + " > ?"); args.add(toDecimal(val)); }
                case "ge" -> { conds.add(col + " >= ?"); args.add(toDecimal(val)); }
                case "lt" -> { conds.add(col + " < ?"); args.add(toDecimal(val)); }
                case "le" -> { conds.add(col + " <= ?"); args.add(toDecimal(val)); }
                case "contains" -> { conds.add(col + "::text LIKE ?"); args.add("%" + val + "%"); }
                case "in" -> {
                    List<Object> vals = new ArrayList<>();
                    for (String s : String.valueOf(val).split(",")) {
                        if (s.isBlank()) continue;
                        vals.add(numeric ? toDecimal(s.trim()) : s.trim());
                    }
                    if (!vals.isEmpty()) {
                        conds.add(col + " IN (" + String.join(",", vals.stream().map(x -> "?").toList()) + ")");
                        args.addAll(vals);
                    }
                }
                default -> { /* 不支持的运算符忽略，避免拼错 SQL */ }
            }
        }
    }

    private java.math.BigDecimal toDecimal(Object v) {
        try {
            return new java.math.BigDecimal(String.valueOf(v).trim());
        } catch (Exception e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    /** 公式求值：变量名 + 常量 + 四则 + 括号 */
    private Double evalFormula(List<String> tokens, Map<String, Double> vars) {
        List<Object> rpn = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();
        Map<String, Integer> prec = Map.of("+", 1, "-", 1, "*", 2, "/", 2);
        boolean expectOperand = true;
        for (String t : tokens) {
            String tok = normalizeOp(t);
            if (tok.isEmpty()) continue;
            if (vars.containsKey(tok)) {
                Double v = vars.get(tok);
                if (v == null) return null;
                rpn.add(v);
                expectOperand = false;
            } else if (isNumber(tok)) {
                rpn.add(Double.parseDouble(tok));
                expectOperand = false;
            } else if ("(".equals(tok)) {
                ops.push(tok);
                expectOperand = true;
            } else if (")".equals(tok)) {
                while (!ops.isEmpty() && !"(".equals(ops.peek())) rpn.add(ops.pop());
                if (ops.isEmpty()) return null;
                ops.pop();
                expectOperand = false;
            } else if (prec.containsKey(tok)) {
                if (expectOperand && ("-".equals(tok) || "+".equals(tok))) return null;
                while (!ops.isEmpty() && !"(".equals(ops.peek()) && prec.get(ops.peek()) >= prec.get(tok)) rpn.add(ops.pop());
                ops.push(tok);
                expectOperand = true;
            } else {
                return null; // 未知变量
            }
        }
        while (!ops.isEmpty()) {
            String op = ops.pop();
            if ("(".equals(op)) return null;
            rpn.add(op);
        }
        Deque<Double> st = new ArrayDeque<>();
        for (Object t : rpn) {
            if (t instanceof Double d) { st.push(d); continue; }
            if (st.size() < 2) return null;
            double b = st.pop(), a = st.pop();
            switch (String.valueOf(t)) {
                case "+" -> st.push(a + b);
                case "-" -> st.push(a - b);
                case "*" -> st.push(a * b);
                case "/" -> { if (b == 0) return null; st.push(a / b); }
                default -> { return null; }
            }
        }
        if (st.size() != 1) return null;
        double v = st.pop();
        return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
    }

    private String normalizeOp(String t) {
        if (t == null) return "";
        String s = t.trim();
        return switch (s) {
            case "×" -> "*";
            case "÷" -> "/";
            case "−", "—" -> "-";
            default -> s;
        };
    }

    private String formulaText(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            String s = t == null ? "" : t.trim();
            if (s.isEmpty()) continue;
            if ("+".equals(s) || "-".equals(s) || "*".equals(s) || "/".equals(s)
                    || "(".equals(s) || ")".equals(s)) {
                sb.append(" ").append(s).append(" ");
            } else {
                sb.append(s);
            }
        }
        return sb.toString().trim();
    }

    private String friendlyMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return "取数失败";
        if (msg.contains("数据源未注册")) return "该数据源已下线";
        if (msg.contains("字段不存在")) return "所选字段已不存在";
        if (msg.contains("没有可统计的数值字段")) return "该数据源没有可统计的数值字段";
        return msg;
    }

    // ---------- 图表卡（波浪/对比/分析共用：维度聚合序列） ----------
    private Map<String, Object> chartData(Map<String, Object> card, Ctx ctx,
                                          List<String> baseConds, List<Object> baseArgs,
                                          LocalDate[] range, Res timeRef) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> dim = (Map<String, Object>) card.get("dim");
        Agg a = aggOf(card);
        boolean byTime = dim != null && "time".equals(str(dim.get("type")));
        String bucket = strOr(dim == null ? null : dim.get("bucket"), "month");
        if (!BUCKETS.contains(bucket)) bucket = "month";

        String groupExpr, labelExpr;
        if (byTime) {
            if (timeRef == null) {
                // 数据源没有时间字段：退化为按记录数一行，不报错
                groupExpr = "1";
                labelExpr = "'合计'";
            } else {
                groupExpr = "date_trunc('" + bucket + "', \"" + timeRef.alias + "\".\"" + timeRef.column + "\")";
                labelExpr = "to_char(" + groupExpr + ", '" + bucketFormat(bucket) + "')";
            }
            out.put("bucket", bucket);
        } else {
            // 维度字段失效（换数据源后常见）→ 回退到该源第一个可用维度
            Res dimRes = ctx.resolveDimension(str(dim == null ? null : dim.get("field")));
            groupExpr = dimRes.qualify();
            labelExpr = groupExpr + "::text";
        }
        String valueExpr;
        if (a.count) {
            valueExpr = "COUNT(*)";
        } else {
            Res res = ctx.resolveNumeric(str(card.get("field")));
            out.put("valueField", res.field);
            valueExpr = a.sql(res.qualify());
        }

        List<String> conds = new ArrayList<>(baseConds);
        List<Object> args = new ArrayList<>(baseArgs);
        if (timeRef != null) {
            conds.add("\"" + timeRef.alias + "\".\"" + timeRef.column + "\" >= ? AND \""
                    + timeRef.alias + "\".\"" + timeRef.column + "\" < ?");
            args.add(range[0].atStartOfDay());
            args.add(range[1].atStartOfDay());
        }
        String where = conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
        String sql = "SELECT " + labelExpr + " AS label, " + valueExpr + " AS value "
                + "FROM " + ctx.from + where + " GROUP BY " + groupExpr + " ORDER BY " + groupExpr;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        List<Map<String, Object>> points = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> p = new LinkedHashMap<>();
            Object lb = r.get("label");
            p.put("label", lb == null || String.valueOf(lb).isBlank() ? "未填写" : String.valueOf(lb));
            p.put("value", r.get("value") == null ? null : ((Number) r.get("value")).doubleValue());
            points.add(p);
        }
        out.put("points", points);

        // 本期 vs 上期
        if (Boolean.TRUE.equals(card.get("compare")) && timeRef != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(range[0], range[1]);
            LocalDate pFrom = range[0].minusDays(days), pTo = range[0];
            List<String> pconds = new ArrayList<>(baseConds);
            List<Object> pargs = new ArrayList<>(baseArgs);
            pconds.add("\"" + timeRef.alias + "\".\"" + timeRef.column + "\" >= ? AND \""
                    + timeRef.alias + "\".\"" + timeRef.column + "\" < ?");
            pargs.add(pFrom.atStartOfDay());
            pargs.add(pTo.atStartOfDay());
            String psql = "SELECT " + labelExpr + " AS label, " + valueExpr + " AS value "
                    + "FROM " + ctx.from
                    + " WHERE " + String.join(" AND ", pconds)
                    + " GROUP BY " + groupExpr + " ORDER BY " + groupExpr;
            List<Map<String, Object>> prows = jdbc.queryForList(psql, pargs.toArray());
            List<Map<String, Object>> prev = new ArrayList<>();
            for (Map<String, Object> r : prows) {
                Map<String, Object> p = new LinkedHashMap<>();
                Object lb = r.get("label");
                p.put("label", lb == null || String.valueOf(lb).isBlank() ? "未填写" : String.valueOf(lb));
                p.put("value", r.get("value") == null ? null : ((Number) r.get("value")).doubleValue());
                prev.add(p);
            }
            out.put("previous", prev);
        }
        return out;
    }

    // ---------- 明细表格卡 ----------
    private Map<String, Object> tableData(Map<String, Object> card, Ctx ctx,
                                          String where, List<Object> args, Res timeRef) {
        List<String> fields = (List<String>) card.get("fields");
        if (fields == null || fields.isEmpty()) {
            fields = ctx.ds.fields.values().stream().limit(4).map(f -> f.field).toList();
        }
        List<String> cols = new ArrayList<>();
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String f : fields) {
            Res r = ctx.resolveQuiet(f);
            if (r == null) continue;
            cols.add(r.qualify() + " AS \"" + r.field + "\"");
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("field", r.field);
            c.put("label", r.meta.label);
            c.put("type", r.meta.type);
            columns.add(c);
        }
        if (cols.isEmpty()) throw new IllegalArgumentException("明细卡没有可用展示列");
        int rows = card.get("rows") instanceof Number n ? Math.min(Math.max(n.intValue(), 1), 20) : 5;
        String pk = ctx.ds.pkColumn;
        if (pk == null) {
            for (EntityMetadataRegistry.FieldMeta f : ctx.ds.fields.values()) { pk = f.column; break; }
        }
        String order = timeRef != null ? "\"" + timeRef.alias + "\".\"" + timeRef.column + "\" DESC"
                : (pk != null ? "\"t1\".\"" + pk + "\" DESC" : "1");
        String sql = "SELECT " + String.join(", ", cols) + " FROM " + ctx.from + where + " ORDER BY " + order + " LIMIT " + rows;
        List<Map<String, Object>> data = jdbc.queryForList(sql, args.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("columns", columns);
        out.put("rows", data);
        return out;
    }

    // ==================== 下钻明细 ====================

    /**
     * 点卡片数字/图表点后的单据级明细清单。
     *
     * @param dimLabel 图表点的标签（时间桶标签或维度字段值）；指标卡传 null
     */
    public Map<String, Object> drill(Map<String, Object> card, String periodOverride,
                                     String dimLabel, Integer limitOverride, Integer itemIndex,
                                     DataScopeResolver.DataScopeContext scope) {
        // 多取数项卡片：按用户选中的取数项下钻（每项可能来自不同单据）
        if ("multi".equals(str(card.get("mode")))) {
            List<Map<String, Object>> items = itemsOf(card);
            if (items.isEmpty()) throw new IllegalArgumentException("请先添加取数项");
            int idx = itemIndex == null ? 0 : Math.max(0, Math.min(itemIndex, items.size() - 1));
            Map<String, Object> syn = new LinkedHashMap<>(items.get(idx));
            syn.put("type", "metric");
            if (card.get("dim") != null) syn.put("dim", card.get("dim"));
            card = syn;
        }
        String source = str(card.get("source"));
        EntityMetadataRegistry.DataSourceMeta ds = requireSource(source);
        Ctx ctx = buildCtx(ds, card);
        String period = periodOverride != null && !periodOverride.isBlank()
                ? periodOverride : strOr(card.get("period"), "month");
        LocalDate[] range = resolveRange(period);
        Res timeRef = resolveTimeRef(ds, ctx, str(card.get("timeField")));

        List<String> conds = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addStatusAndScope(ds, ctx, scope, conds, args);
        if (timeRef != null) {
            conds.add("\"" + timeRef.alias + "\".\"" + timeRef.column + "\" >= ? AND \""
                    + timeRef.alias + "\".\"" + timeRef.column + "\" < ?");
            args.add(range[0].atStartOfDay());
            args.add(range[1].atStartOfDay());
        }
        applyFilters(card.get("filters"), ctx, conds, args);
        // 维度过滤：图表点为时间桶 → 按同一格式比对；为字段 → 按文本比对
        Map<String, Object> dim = (Map<String, Object>) card.get("dim");
        int dimIdx = -1;
        if (dimLabel != null && !dimLabel.isBlank() && dim != null) {
            if ("time".equals(str(dim.get("type"))) && timeRef != null) {
                String bucket = strOr(dim.get("bucket"), "month");
                if (!BUCKETS.contains(bucket)) bucket = "month";
                String expr = "to_char(date_trunc('" + bucket + "', \"" + timeRef.alias + "\".\""
                        + timeRef.column + "\"), '" + bucketFormat(bucket) + "')";
                if ("未填写".equals(dimLabel)) {
                    conds.add(expr + " IS NULL");
                } else {
                    conds.add(expr + " = ?");
                    args.add(dimLabel);
                }
                dimIdx = 0;
            } else if ("field".equals(str(dim.get("type")))) {
                Res dr = ctx.resolveQuiet(str(dim.get("field")));
                if (dr != null) {
                    String expr = dr.qualify() + "::text";
                    if ("未填写".equals(dimLabel)) {
                        conds.add("(" + expr + " IS NULL OR trim(" + expr + ") = '')");
                    } else {
                        conds.add(expr + " = ?");
                        args.add(dimLabel);
                    }
                    dimIdx = 1;
                }
            }
        }
        String where = conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);

        String subjectTable = policy.subjectTable(ds);
        String subjectAlias = "t1";
        EntityMetadataRegistry.JoinMeta h = policy.headerJoin(ds);
        Map<String, EntityMetadataRegistry.FieldMeta> subjectFields = ds.fields;
        if (h != null) {
            subjectAlias = h.alias;
            subjectFields = h.fields;
        }
        String pkCol = h == null ? ds.pkColumn : h.rightColumn;
        String noCol = policy.noColumn(subjectTable);
        String statusCol = null;
        for (EntityMetadataRegistry.FieldMeta f : subjectFields.values()) {
            if (f.column.equals("status")) { statusCol = f.column; break; }
        }

        List<String> cols = new ArrayList<>();
        List<Map<String, Object>> columns = new ArrayList<>();
        String pkExpr = pkCol == null ? "NULL" : "\"" + subjectAlias + "\".\"" + pkCol + "\"";
        cols.add(pkExpr + " AS \"billId\"");
        addCol(columns, "billId", "单据ID", "string");
        String noExpr = noCol == null ? "NULL" : "\"" + subjectAlias + "\".\"" + noCol + "\"";
        cols.add(noExpr + " AS \"billNo\"");
        addCol(columns, "billNo", "单据号", "string");
        String dateExpr = timeRef == null ? "NULL" : "\"" + timeRef.alias + "\".\"" + timeRef.column + "\"";
        cols.add(dateExpr + " AS \"billDate\"");
        addCol(columns, "billDate", "业务日期", timeRef == null ? "string" : timeRef.meta.type);
        String stExpr = statusCol == null ? "NULL" : "\"" + subjectAlias + "\".\"" + statusCol + "\"";
        cols.add(stExpr + " AS \"status\"");
        addCol(columns, "status", "状态", "string");
        if (dimIdx == 1) {
            Res dr = ctx.resolveQuiet(str(dim.get("field")));
            cols.add(dr.qualify() + "::text AS \"dimLabel\"");
            addCol(columns, "dimLabel", dr.meta.label, "string");
        }
        Agg a = aggOf(card);
        List<String> tokens = (List<String>) card.get("tokens");
        if (tokens == null || tokens.isEmpty()) {
            Res res = ctx.resolveNumeric(str(card.get("field")));
            if (a.count) {
                cols.add("1 AS \"value\"");
                addCol(columns, "value", "记录数", "number");
            } else {
                cols.add(res.qualify() + " AS \"value\"");
                addCol(columns, "value", res.meta.label, "amount".equals(res.meta.type) ? "amount" : "number");
            }
        }

        String orderBy = timeRef != null ? "\"" + timeRef.alias + "\".\"" + timeRef.column + "\" DESC"
                : (pkCol != null ? "\"" + subjectAlias + "\".\"" + pkCol + "\" DESC" : "1");
        int limit = limitOverride == null ? 200 : Math.max(1, Math.min(limitOverride, 500));
        String sql = "SELECT " + String.join(", ", cols) + " FROM " + ctx.from + where
                + " ORDER BY " + orderBy + " LIMIT " + limit;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        long total = countAll(ctx, conds, args);

        BillLinkRegistry.DocDef doc = billLinkRegistry.docByTable(subjectTable);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("docType", doc == null ? null : doc.docType);
        out.put("docName", doc == null ? registry.getDataSourceName(ds.key) : doc.name);
        out.put("columns", columns);
        out.put("rows", rows);
        out.put("total", total);
        out.put("truncated", total > rows.size());
        out.put("period", period);
        out.put("criteria", policy.effectiveLabel(ds));
        if (timeRef != null) out.put("timeField", timeRef.field);
        return out;
    }

    private void addCol(List<Map<String, Object>> columns, String field, String label, String type) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("field", field);
        c.put("label", label);
        c.put("type", type);
        columns.add(c);
    }

    // ==================== 多级关联上下文 ====================

    /** 卡片取数上下文：主表 t1 + 勾选的关联单据（t2/t3… 表头/基础资料，u1/u2… 来源单据） */
    static final class Ctx {
        final EntityMetadataRegistry.DataSourceMeta ds;
        final List<EntityMetadataRegistry.JoinMeta> joins = new ArrayList<>();
        final String from;

        Ctx(EntityMetadataRegistry.DataSourceMeta ds, List<String> joinKeys) {
            this.ds = ds;
            // 关联单可能挂在另一个关联单上（明细→订单→客户）：依赖的上层必须先 JOIN，
            // 否则 SQL 引用了不存在的别名，取数会报错或静默为空（用户看到的就是「统计不出数据」）
            List<EntityMetadataRegistry.JoinMeta> ordered = new ArrayList<>();
            if (joinKeys != null) {
                for (String key : joinKeys) addJoinWithDeps(ds, key, ordered, 0);
            }
            StringBuilder sql = new StringBuilder("\"" + ds.table + "\" t1");
            for (EntityMetadataRegistry.JoinMeta j : ordered) {
                joins.add(j);
                sql.append(" LEFT JOIN \"").append(j.table).append("\" ").append(j.alias)
                   .append(" ON ").append(j.leftAlias).append(".\"").append(j.leftColumn)
                   .append("\" = ").append(j.rightAlias).append(".\"").append(j.rightColumn).append("\"");
                if (j.extraOn != null) sql.append(j.extraOn);
            }
            this.from = sql.toString();
        }

        /** 递归补齐依赖：ON 左侧别名不是 t1 时，先把它所依赖的那层 join 加进来 */
        private static void addJoinWithDeps(EntityMetadataRegistry.DataSourceMeta ds, String key,
                                            List<EntityMetadataRegistry.JoinMeta> out, int depth) {
            if (key == null || depth > 8) return;
            EntityMetadataRegistry.JoinMeta j = ds.joins.get(key);
            if (j == null || out.contains(j)) return;
            if (!"t1".equals(j.leftAlias)) {
                for (EntityMetadataRegistry.JoinMeta dep : ds.joins.values()) {
                    if (dep.alias.equals(j.leftAlias)) {
                        addJoinWithDeps(ds, dep.key, out, depth + 1);
                        break;
                    }
                }
            }
            if (!out.contains(j)) out.add(j);
        }

        /** 解析字段：支持 {@code joinKey.field}；不带前缀时先主表后关联单 */
        Res resolve(String field) {
            Res r = resolveQuiet(field);
            if (r == null) throw new IllegalArgumentException("字段不存在: " + field);
            return r;
        }

        Res resolveQuiet(String field) {
            if (field == null || field.isBlank()) return null;
            String f = field.trim();
            int dot = f.indexOf('.');
            if (dot > 0) {
                String jk = f.substring(0, dot), fn = f.substring(dot + 1);
                EntityMetadataRegistry.JoinMeta j = ds.joins.get(jk);
                if (j == null) return null;
                EntityMetadataRegistry.FieldMeta m = j.fields.get(fn);
                return m == null ? null : new Res(jk + "." + fn, j.alias, m.column, m);
            }
            EntityMetadataRegistry.FieldMeta main = ds.fields.get(f);
            if (main != null) return new Res(f, "t1", main.column, main);
            for (EntityMetadataRegistry.JoinMeta j : joins) {
                EntityMetadataRegistry.FieldMeta m = j.fields.get(f);
                if (m != null) return new Res(j.key + "." + f, j.alias, m.column, m);
            }
            return null;
        }

        /** 数值字段：失效时回退主表第一个数值字段（换数据源后不报错） */
        Res resolveNumeric(String field) {
            Res r = resolveQuiet(field);
            if (r != null && NUMERIC_TYPES.contains(r.meta.type)) return r;
            for (EntityMetadataRegistry.FieldMeta f : ds.fields.values()) {
                if (NUMERIC_TYPES.contains(f.type)) return new Res(f.field, "t1", f.column, f);
            }
            throw new IllegalArgumentException("该数据源没有可统计的数值字段");
        }

        /** 维度字段：失效时回退第一个非数值字段（换数据源后不报错） */
        Res resolveDimension(String field) {
            Res r = resolveQuiet(field);
            if (r != null) return r;
            for (EntityMetadataRegistry.FieldMeta f : ds.fields.values()) {
                if (!NUMERIC_TYPES.contains(f.type)) return new Res(f.field, "t1", f.column, f);
            }
            for (EntityMetadataRegistry.FieldMeta f : ds.fields.values()) return new Res(f.field, "t1", f.column, f);
            throw new IllegalArgumentException("该数据源没有可用维度字段");
        }
    }

    /** 解析后的字段引用 */
    static final class Res {
        final String field;
        final String alias;
        final String column;
        final EntityMetadataRegistry.FieldMeta meta;

        Res(String field, String alias, String column, EntityMetadataRegistry.FieldMeta meta) {
            this.field = field; this.alias = alias; this.column = column; this.meta = meta;
        }

        String qualify() { return alias + ".\"" + column + "\""; }
    }

    private Ctx buildCtx(EntityMetadataRegistry.DataSourceMeta ds, Map<String, Object> card) {
        List<String> keys = new ArrayList<>();
        Object raw = card.get("joins");
        if (raw instanceof List<?> list) {
            for (Object o : list) if (o != null) keys.add(String.valueOf(o));
        }
        // 明细数据源必须带表头（状态/日期/权限按表头判定，且业务日期通常在表头）
        EntityMetadataRegistry.JoinMeta h = policy.headerJoin(ds);
        if (h != null) keys.add(h.key);
        // 卡片里引用的关联字段（时间/数值/维度/公式/展示列）即使没写进 joins 也自动补齐
        addRefJoin(keys, ds, str(card.get("timeField")));
        addRefJoin(keys, ds, str(card.get("field")));
        Map<String, Object> dim = (Map<String, Object>) card.get("dim");
        if (dim != null) addRefJoin(keys, ds, str(dim.get("field")));
        if (card.get("tokens") instanceof List<?> tokens) {
            for (Object t : tokens) addRefJoin(keys, ds, t == null ? null : String.valueOf(t));
        }
        if (card.get("fields") instanceof List<?> fs) {
            for (Object f : fs) addRefJoin(keys, ds, f == null ? null : String.valueOf(f));
        }
        String pref = policy.businessDateField(ds);
        addRefJoin(keys, ds, pref);
        return new Ctx(ds, keys);
    }

    private void addRefJoin(List<String> keys, EntityMetadataRegistry.DataSourceMeta ds, String ref) {
        if (ref == null) return;
        int dot = ref.indexOf('.');
        if (dot <= 0) return;
        String jk = ref.substring(0, dot);
        if (ds.joins.containsKey(jk) && !keys.contains(jk)) keys.add(jk);
    }

    // ==================== 公式求值（四则 + 括号；字段 token 走合计） ====================

    private Double evalTokens(List<String> tokens, Ctx ctx, String where, List<Object> args) {
        List<Object> rpn = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();
        Map<String, Integer> prec = Map.of("+", 1, "-", 1, "*", 2, "/", 2);
        boolean expectOperand = true;
        for (String t : tokens) {
            String tok = t == null ? "" : t.trim();
            if (tok.isEmpty()) continue;
            if (isNumber(tok)) {
                rpn.add(Double.parseDouble(tok));
                expectOperand = false;
            } else if (ctx.resolveQuiet(tok) != null) {
                // 公式里的数值字段一律按「合计」取值，避免计数聚合把比率算成 100%
                Res r = ctx.resolveQuiet(tok);
                if (!NUMERIC_TYPES.contains(r.meta.type)) return null;
                Double v = scalar(ctx, SUM_AGG.sql(r.qualify()), where, args);
                rpn.add(v == null ? Double.NaN : v);
                expectOperand = false;
            } else if ("(".equals(tok)) {
                ops.push(tok);
                expectOperand = true;
            } else if (")".equals(tok)) {
                while (!ops.isEmpty() && !"(".equals(ops.peek())) rpn.add(ops.pop());
                if (ops.isEmpty()) return null;
                ops.pop();
                expectOperand = false;
            } else if (prec.containsKey(tok)) {
                if (expectOperand && ("-".equals(tok) || "+".equals(tok))) return null;
                while (!ops.isEmpty() && !"(".equals(ops.peek()) && prec.get(ops.peek()) >= prec.get(tok)) rpn.add(ops.pop());
                ops.push(tok);
                expectOperand = true;
            } else {
                return null;
            }
        }
        while (!ops.isEmpty()) {
            String op = ops.pop();
            if ("(".equals(op)) return null;
            rpn.add(op);
        }
        Deque<Double> st = new ArrayDeque<>();
        for (Object t : rpn) {
            if (t instanceof Double d) { st.push(d); continue; }
            if (st.size() < 2) return null;
            double b = st.pop(), a = st.pop();
            switch (String.valueOf(t)) {
                case "+" -> st.push(a + b);
                case "-" -> st.push(a - b);
                case "*" -> st.push(a * b);
                case "/" -> { if (b == 0) return null; st.push(a / b); }
                default -> { return null; }
            }
        }
        if (st.size() != 1) return null;
        double v = st.pop();
        return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
    }

    // ==================== SQL 小件 ====================

    private record Agg(String key, String label, boolean count) {
        String sql(String qualifiedColumn) {
            return switch (key) {
                case "sum" -> "COALESCE(SUM(" + qualifiedColumn + "), 0)";
                case "avg" -> "AVG(" + qualifiedColumn + ")";
                case "max" -> "MAX(" + qualifiedColumn + ")";
                case "min" -> "MIN(" + qualifiedColumn + ")";
                case "distinct" -> "COUNT(DISTINCT " + qualifiedColumn + ")";
                default -> "COUNT(*)";
            };
        }
    }

    private Agg aggOf(Map<String, Object> card) {
        String agg = strOr(card.get("agg"), "sum");
        if (!AGGS.contains(agg)) agg = "sum";
        return new Agg(agg, aggLabel(agg), "count".equals(agg));
    }

    private String aggLabel(String agg) {
        return switch (agg) {
            case "sum" -> "求和";
            case "avg" -> "平均";
            case "count" -> "计数";
            case "max" -> "最大";
            case "min" -> "最小";
            case "distinct" -> "去重计数";
            default -> agg;
        };
    }

    /**
     * 时间引用：优先用卡片选择的时间字段（支持关联单字段），失效时回退到该数据源的业务日期，
     * 最后才用创建时间/第一个日期字段。明细数据源的业务日期取表头日期。
     */
    private Res resolveTimeRef(EntityMetadataRegistry.DataSourceMeta ds, Ctx ctx, String timeField) {
        if (timeField != null && !timeField.isBlank()) {
            Res r = ctx.resolveQuiet(timeField);
            if (r != null && isDate(r.meta.type)) return r;
        }
        String pref = policy.businessDateField(ds);
        if (pref != null) {
            Res r = ctx.resolveQuiet(pref);
            if (r != null && isDate(r.meta.type)) return r;
        }
        EntityMetadataRegistry.FieldMeta c = ds.fields.get("createdAt");
        if (c != null && isDate(c.type)) return new Res("createdAt", "t1", c.column, c);
        for (EntityMetadataRegistry.FieldMeta f : ds.fields.values()) {
            if (isDate(f.type)) return new Res(f.field, "t1", f.column, f);
        }
        return null;
    }

    private boolean isDate(String type) {
        return "date".equals(type) || "datetime".equals(type);
    }

    private String bucketFormat(String bucket) {
        return switch (bucket) {
            case "day" -> "YYYY-MM-DD";
            case "week" -> "IYYY-\"W\"IW";
            case "year" -> "YYYY";
            default -> "YYYY-MM";
        };
    }

    private Double scalar(Ctx ctx, String aggExpr, String where, List<Object> args) {
        Object v = jdbc.queryForObject("SELECT " + aggExpr + " FROM " + ctx.from + where, Object.class, args.toArray());
        return v == null ? null : ((Number) v).doubleValue();
    }

    private Double scalarCount(Ctx ctx, String where, List<Object> args) {
        Long v = jdbc.queryForObject("SELECT COUNT(*) FROM " + ctx.from + where, Long.class, args.toArray());
        return v == null ? null : v.doubleValue();
    }

    private EntityMetadataRegistry.DataSourceMeta requireSource(String source) {
        if (source == null || !registry.isRegistered(source))
            throw new IllegalArgumentException("数据源未注册: " + source);
        return registry.getDataSource(source);
    }

    // ==================== 时间口径（与数据看板一致） ====================

    public static LocalDate[] resolveRange(String period) {
        LocalDate now = LocalDate.now();
        switch (period == null ? "month" : period) {
            case "day": return new LocalDate[]{now, now.plusDays(1)};
            case "week": {
                LocalDate start = now.with(DayOfWeek.MONDAY);
                return new LocalDate[]{start, start.plusWeeks(1)};
            }
            case "quarter": {
                LocalDate start = now.withDayOfMonth(1).withMonth(((now.getMonthValue() - 1) / 3) * 3 + 1);
                return new LocalDate[]{start, start.plusMonths(3)};
            }
            case "year": {
                LocalDate start = now.withDayOfYear(1);
                return new LocalDate[]{start, start.plusYears(1)};
            }
            default: {
                LocalDate start = now.withDayOfMonth(1);
                return new LocalDate[]{start, start.plusMonths(1)};
            }
        }
    }

    private static boolean isNumber(String s) {
        try { Double.parseDouble(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String strOr(Object o, String def) { String s = str(o); return s == null || s.isBlank() ? def : s; }
}
