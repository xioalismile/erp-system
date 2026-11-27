package com.erp.kai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 小楷独立版 · 查询结果集（2026-10-20）。
 *
 * 解决"工具查出来的结果没存起来、无法二次分析"的根因：
 * 每次查询第三方系统的结果整份落库（永久保留），返回结果集编号；
 * 后续的分组汇总、两份结果集对比都直接基于库里的结果集计算，不必重复查询对方系统。
 *
 * 口径：
 * ① 结果集归属用户，跨会话可用（同一登录人后续任意会话都能按编号取回）；
 * ② 单结果集行数上限 {@link #MAX_ROWS}、序列化上限 {@link #MAX_JSON_CHARS}，
 *    超限截断并显式标记 truncated（不静默丢数据）；
 * ③ 聚合/对比只在本地内存中对已落库数据计算，字段名必须真实存在于结果集列中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiResultSetService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 单结果集最多保留行数（防止单次查询把库撑爆） */
    public static final int MAX_ROWS = 20000;
    /** 单结果集序列化字符上限（约 8MB） */
    private static final int MAX_JSON_CHARS = 8 * 1024 * 1024;
    /** 返回给模型的样例行数 */
    private static final int SAMPLE_ROWS = 30;

    /** 一次查询的结果集头部信息（不含全量数据）。 */
    public record Header(Long id, String label, String objectType, int rowCount,
                         List<String> columns, String createdAt, boolean truncated) {}

    // ---------------- 写入 ----------------

    /**
     * 保存一次查询结果。
     * @param label 人话标签（如「销售订单 2026-10-01 至 2026-10-31」），供后续引用
     * @return {resultSetId, rowCount, columns, sample, truncated, note}
     */
    public Map<String, Object> save(Long kaiUserId, Long sessionId, Long connectionId,
                                    String label, String objectType,
                                    Map<String, Object> filters, List<Map<String, Object>> rows) {
        List<Map<String, Object>> safeRows = rows == null ? new ArrayList<>() : rows;
        boolean truncated = false;
        if (safeRows.size() > MAX_ROWS) {
            safeRows = new ArrayList<>(safeRows.subList(0, MAX_ROWS));
            truncated = true;
        }
        int rowCount = safeRows.size();
        LinkedHashSet<String> cols = new LinkedHashSet<>();
        for (Map<String, Object> r : safeRows) {
            if (r != null) cols.addAll(r.keySet());
        }
        List<String> columns = new ArrayList<>(cols);
        String dataJson = jsonOf(safeRows);
        if (dataJson.length() > MAX_JSON_CHARS) {
            // 超长：按比例收缩行数后重试一次，仍超则只保存元数据（保底不丢结果集编号）
            int keep = Math.max(1, (int) ((long) rowCount * MAX_JSON_CHARS / dataJson.length()));
            safeRows = new ArrayList<>(safeRows.subList(0, Math.min(keep, safeRows.size())));
            rowCount = safeRows.size();
            truncated = true;
            dataJson = jsonOf(safeRows);
            if (dataJson.length() > MAX_JSON_CHARS) {
                dataJson = "[]";
            }
        }
        jdbcTemplate.update("""
                INSERT INTO kai_query_result
                    (kai_user_id, session_id, connection_id, label, object_type, filters_json, columns_json, row_count, data_json)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, kaiUserId, sessionId, connectionId, truncate(label, 300), truncate(objectType, 64),
                jsonOf(filters == null ? new LinkedHashMap<>() : filters), jsonOf(columns), rowCount, dataJson);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM kai_query_result WHERE kai_user_id=? ORDER BY id DESC LIMIT 1", Long.class, kaiUserId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultSetId", id);
        out.put("label", label);
        out.put("objectType", objectType);
        out.put("rowCount", rowCount);
        out.put("columns", columns);
        out.put("sample", safeRows.subList(0, Math.min(SAMPLE_ROWS, rowCount)));
        out.put("truncated", truncated);
        out.put("note", "结果已保存为结果集 " + id + "（共 " + rowCount + " 行）。"
                + "需要统计请用 ext_result_aggregate，需要与另一份结果对比请用 ext_result_compare，"
                + "需要重新看明细请用 ext_result_query；不必再查一次对方系统。");
        return out;
    }

    // ---------------- 读取 ----------------

    /** 我的结果集清单（最近优先）。 */
    public List<Map<String, Object>> list(Long kaiUserId, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        int cap = limit <= 0 ? 30 : Math.min(limit, 200);
        jdbcTemplate.query("""
                SELECT id, label, object_type, row_count, columns_json, created_at
                FROM kai_query_result WHERE kai_user_id = ? ORDER BY id DESC LIMIT ?
                """, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resultSetId", rs.getLong("id"));
            m.put("label", rs.getString("label"));
            m.put("objectType", rs.getString("object_type"));
            m.put("rowCount", rs.getInt("row_count"));
            m.put("columns", parseList(rs.getString("columns_json")));
            m.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
            out.add(m);
        }, kaiUserId, cap);
        return out;
    }

    /** 取回结果集明细（分页 + 关键词过滤）。 */
    public Map<String, Object> query(Long kaiUserId, Long resultSetId, Integer offset, Integer limit, String keyword) {
        Raw rs = load(kaiUserId, resultSetId);
        List<Map<String, Object>> rows = rs.rows();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                for (Object v : r.values()) {
                    if (v != null && String.valueOf(v).toLowerCase().contains(kw)) {
                        filtered.add(r);
                        break;
                    }
                }
            }
            rows = filtered;
        }
        int total = rows.size();
        int from = offset == null || offset < 0 ? 0 : offset;
        int size = limit == null || limit <= 0 ? 200 : Math.min(limit, 1000);
        int to = Math.min(from + size, total);
        List<Map<String, Object>> page = from >= total ? new ArrayList<>() : new ArrayList<>(rows.subList(from, to));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultSetId", resultSetId);
        out.put("label", rs.header().label());
        out.put("total", total);
        out.put("offset", from);
        out.put("limit", size);
        out.put("columns", rs.header().columns());
        out.put("rows", page);
        return out;
    }

    // ---------------- 聚合 ----------------

    /**
     * 分组汇总。
     * @param groupBy        分组字段（可空 = 全表汇总一行）；支持 granularity=day/month/quarter/year 截断日期字符串
     * @param metrics        指标 [{field, func, label}]，func=SUM/COUNT/AVG/MAX/MIN
     * @param orderByMetric  排序指标下标（可空）
     * @param orderDesc      是否降序
     */
    public Map<String, Object> aggregate(Long kaiUserId, Long resultSetId,
                                         List<String> groupBy, String granularity,
                                         List<Map<String, Object>> metrics,
                                         Integer orderByMetric, Boolean orderDesc, Integer limit) {
        Raw rs = load(kaiUserId, resultSetId);
        List<Map<String, Object>> rows = rs.rows();
        List<String> cols = rs.header().columns();
        List<String> groups = groupBy == null ? new ArrayList<>() : groupBy;
        for (String g : groups) {
            requireColumn(cols, g, resultSetId);
        }
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("请至少给一个统计指标：field（字段名）与 func（SUM/COUNT/AVG/MAX/MIN）");
        }
        for (Map<String, Object> m : metrics) {
            String f = str(m.get("field"));
            String fn = str(m.get("func")).toUpperCase(Locale.ROOT);
            if (!List.of("SUM", "COUNT", "AVG", "MAX", "MIN").contains(fn)) {
                throw new IllegalArgumentException("不支持的统计方式：" + fn + "（只能是 SUM/COUNT/AVG/MAX/MIN）");
            }
            boolean countWithoutField = "COUNT".equals(fn) && f.isBlank();
            if (!countWithoutField) {
                if (f.isBlank()) throw new IllegalArgumentException(fn + " 需要指定要统计的字段 field");
                requireColumn(cols, f, resultSetId);
            }
        }

        // 分组累加
        Map<String, Agg> bucket = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String key = groups.isEmpty() ? "合计" : keyOf(r, groups, granularity);
            Agg agg = bucket.computeIfAbsent(key, k -> new Agg(groups, metrics));
            agg.add(r);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Agg> e : bucket.entrySet()) {
            result.add(e.getValue().toRow(e.getKey()));
        }
        // 排序
        if (orderByMetric != null && orderByMetric >= 0 && orderByMetric < metrics.size()) {
            String metricKey = metricOutputName(metrics.get(orderByMetric));
            Comparator<Map<String, Object>> cmp = Comparator.comparing(
                    m -> toDecimal(m.get(metricKey)));
            if (orderDesc == null || orderDesc) cmp = cmp.reversed();
            result.sort(cmp);
        }
        int cap = limit == null || limit <= 0 ? 200 : Math.min(limit, 1000);
        boolean more = result.size() > cap;
        if (more) result = new ArrayList<>(result.subList(0, cap));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultSetId", resultSetId);
        out.put("label", rs.header().label());
        out.put("groupBy", groups);
        out.put("inputRows", rows.size());
        out.put("groupCount", bucket.size());
        out.put("metrics", metrics.stream().map(this::metricOutputName).toList());
        out.put("hasMore", more);
        out.put("rows", result);
        return out;
    }

    // ---------------- 对比 ----------------

    /**
     * 两份结果集对比：按同一分组字段对齐，逐组给出 A 值、B 值、差值、变化率。
     * 用于"本月 vs 上月"这类场景。
     */
    public Map<String, Object> compare(Long kaiUserId, Long resultSetIdA, Long resultSetIdB,
                                       List<String> groupBy, String granularity,
                                       String field, String func, String labelA, String labelB) {
        Raw a = load(kaiUserId, resultSetIdA);
        Raw b = load(kaiUserId, resultSetIdB);
        List<String> groups = groupBy == null ? new ArrayList<>() : groupBy;
        for (String g : groups) {
            requireColumn(a.header().columns(), g, resultSetIdA);
            requireColumn(b.header().columns(), g, resultSetIdB);
        }
        String fn = func == null || func.isBlank() ? "SUM" : func.toUpperCase(Locale.ROOT);
        if (!List.of("SUM", "COUNT", "AVG", "MAX", "MIN").contains(fn)) {
            throw new IllegalArgumentException("不支持的对比方式：" + fn + "（只能是 SUM/COUNT/AVG/MAX/MIN 或 COUNT）");
        }
        boolean countMode = "COUNT".equals(fn);
        if (!countMode) {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("请给出要比较的数值字段 field（如金额/数量字段）；只想比条数时 func 传 COUNT");
            }
            requireColumn(a.header().columns(), field, resultSetIdA);
            requireColumn(b.header().columns(), field, resultSetIdB);
        }

        Map<String, BigDecimal> va = sumBy(a.rows(), groups, granularity, field, fn, countMode);
        Map<String, BigDecimal> vb = sumBy(b.rows(), groups, granularity, field, fn, countMode);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.addAll(va.keySet());
        keys.addAll(vb.keySet());

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal ta = BigDecimal.ZERO;
        BigDecimal tb = BigDecimal.ZERO;
        for (String k : keys) {
            BigDecimal x = va.getOrDefault(k, BigDecimal.ZERO);
            BigDecimal y = vb.getOrDefault(k, BigDecimal.ZERO);
            ta = ta.add(x);
            tb = tb.add(y);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put(groups.size() == 1 ? groups.get(0) : "分组", k);
            r.put(labelA == null || labelA.isBlank() ? "基准期" : labelA, x);
            r.put(labelB == null || labelB.isBlank() ? "对比期" : labelB, y);
            r.put("差值", y.subtract(x));
            r.put("变化率%", changePct(x, y));
            rows.add(r);
        }
        rows.sort((m1, m2) -> toDecimal(m2.get("差值")).compareTo(toDecimal(m1.get("差值"))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultSetA", resultSetIdA);
        out.put("labelA", a.header().label());
        out.put("resultSetB", resultSetIdB);
        out.put("labelB", b.header().label());
        out.put("groupBy", groups);
        out.put("metric", countMode ? "COUNT" : fn + "(" + field + ")");
        out.put("rowsA", a.header().rowCount());
        out.put("rowsB", b.header().rowCount());
        out.put("totalA", ta);
        out.put("totalB", tb);
        out.put("totalDiff", tb.subtract(ta));
        out.put("totalChangePct", changePct(ta, tb));
        out.put("groups", rows);
        return out;
    }

    // ---------------- 内部 ----------------

    private record Raw(Header header, List<Map<String, Object>> rows) {}

    private Raw load(Long kaiUserId, Long resultSetId) {
        if (resultSetId == null) throw new IllegalArgumentException("请给出结果集编号 resultSetId（查询结果里会返回）");
        List<Raw> found = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT id, label, object_type, row_count, columns_json, data_json, created_at
                FROM kai_query_result WHERE id = ? AND kai_user_id = ?
                """, rs -> {
            List<String> cols = parseList(rs.getString("columns_json"));
            Header h = new Header(rs.getLong("id"), rs.getString("label"), rs.getString("object_type"),
                    rs.getInt("row_count"), cols, String.valueOf(rs.getTimestamp("created_at")), false);
            found.add(new Raw(h, parseRows(rs.getString("data_json"))));
        }, resultSetId, kaiUserId);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("结果集 " + resultSetId + " 不存在或不属于当前账号；"
                    + "可先用最近查询结果里的编号，或重新查询一次");
        }
        return found.get(0);
    }

    private void requireColumn(List<String> cols, String field, Long resultSetId) {
        if (!cols.contains(field)) {
            throw new IllegalArgumentException("结果集 " + resultSetId + " 里没有字段「" + field + "」。可用字段："
                    + String.join("、", cols));
        }
    }

    /** 按分组键累加。 */
    private Map<String, BigDecimal> sumBy(List<Map<String, Object>> rows, List<String> groups, String granularity,
                                          String field, String func, boolean countMode) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String key = groups.isEmpty() ? "合计" : keyOf(r, groups, granularity);
            BigDecimal v;
            if (countMode) {
                v = BigDecimal.ONE;
            } else {
                v = toDecimal(r.get(field));
                if ("MAX".equals(func) || "MIN".equals(func)) {
                    BigDecimal cur = out.get(key);
                    if (cur == null) {
                        out.put(key, v);
                    } else {
                        out.put(key, "MAX".equals(func) ? cur.max(v) : cur.min(v));
                    }
                    continue;
                }
            }
            out.merge(key, v, BigDecimal::add);
        }
        if ("AVG".equals(func)) {
            Map<String, Integer> cnt = new LinkedHashMap<>();
            for (Map<String, Object> r : rows) {
                String key = groups.isEmpty() ? "合计" : keyOf(r, groups, granularity);
                cnt.merge(key, 1, Integer::sum);
            }
            for (Map.Entry<String, BigDecimal> e : out.entrySet()) {
                int c = cnt.getOrDefault(e.getKey(), 0);
                if (c > 0) {
                    e.setValue(e.getValue().divide(BigDecimal.valueOf(c), 4, RoundingMode.HALF_UP));
                }
            }
        }
        return out;
    }

    private String keyOf(Map<String, Object> row, List<String> groups, String granularity) {
        if (groups.size() == 1) {
            return dateTruncate(str(row.get(groups.get(0))), granularity);
        }
        StringBuilder sb = new StringBuilder();
        for (String g : groups) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(dateTruncate(str(row.get(g)), granularity));
        }
        return sb.toString();
    }

    /** 日期字符串按粒度截断（其余值原样返回）。 */
    private String dateTruncate(String v, String granularity) {
        if (v == null || v.isBlank() || granularity == null || granularity.isBlank()) return v;
        String s = v.trim();
        // 只处理形如 2026-10-20 / 2026-10-20 10:00:00 / 2026/10/20 的值
        if (!s.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")) return v;
        String[] parts = s.replace('/', '-').split("-");
        if (parts.length < 3) return v;
        String year = parts[0];
        int mon;
        int day;
        try {
            mon = Integer.parseInt(parts[1]);
            day = Integer.parseInt(parts[2].substring(0, Math.min(2, parts[2].length())));
        } catch (Exception e) {
            return v;
        }
        String mm = mon < 10 ? "0" + mon : String.valueOf(mon);
        return switch (granularity.toLowerCase(Locale.ROOT)) {
            case "year" -> year;
            case "month" -> year + "-" + mm;
            case "quarter" -> year + "-Q" + ((mon - 1) / 3 + 1);
            default -> year + "-" + mm + "-" + (day < 10 ? "0" + day : String.valueOf(day));
        };
    }

    /** 指标输出列名：SUM(字段) → sum_字段；COUNT → count。 */
    private String metricOutputName(Map<String, Object> m) {
        if (m.get("label") != null && !str(m.get("label")).isBlank()) return str(m.get("label"));
        String fn = str(m.get("func")).toUpperCase(Locale.ROOT);
        String f = str(m.get("field"));
        return "COUNT".equals(fn) || f.isBlank() ? "count" : fn.toLowerCase(Locale.ROOT) + "_" + f;
    }

    private BigDecimal changePct(BigDecimal from, BigDecimal to) {
        if (from == null || from.compareTo(BigDecimal.ZERO) == 0) {
            return to == null || to.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return to.subtract(from).multiply(BigDecimal.valueOf(100))
                .divide(from.abs(), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String s = String.valueOf(o).replace(",", "").replace("¥", "").replace("￥", "").trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** 单组累加器：按指标逐项累计（SUM/AVG 累加值，MAX/MIN 取极值，COUNT 计行数）。 */
    private class Agg {
        final List<String> groups;
        final List<Map<String, Object>> metrics;
        final List<BigDecimal> values;
        int count;

        Agg(List<String> groups, List<Map<String, Object>> metrics) {
            this.groups = groups;
            this.metrics = metrics;
            this.values = new ArrayList<>(Collections.nCopies(metrics.size(), null));
        }

        void add(Map<String, Object> row) {
            count++;
            for (int i = 0; i < metrics.size(); i++) {
                Map<String, Object> m = metrics.get(i);
                String fn = str(m.get("func")).toUpperCase(Locale.ROOT);
                String f = str(m.get("field"));
                if ("COUNT".equals(fn)) continue;
                BigDecimal v = toDecimal(row.get(f));
                BigDecimal cur = values.get(i);
                switch (fn) {
                    case "MAX" -> values.set(i, cur == null ? v : cur.max(v));
                    case "MIN" -> values.set(i, cur == null ? v : cur.min(v));
                    default -> values.set(i, cur == null ? v : cur.add(v));
                }
            }
        }

        Map<String, Object> toRow(String key) {
            Map<String, Object> out = new LinkedHashMap<>();
            if (!groups.isEmpty()) out.put(groups.size() == 1 ? groups.get(0) : "分组", key);
            for (int i = 0; i < metrics.size(); i++) {
                Map<String, Object> m = metrics.get(i);
                String fn = str(m.get("func")).toUpperCase(Locale.ROOT);
                String name = metricOutputName(m);
                if ("COUNT".equals(fn)) {
                    out.put(name, count);
                } else if ("AVG".equals(fn)) {
                    BigDecimal v = values.get(i);
                    out.put(name, v == null || count == 0 ? BigDecimal.ZERO
                            : v.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP));
                } else {
                    out.put(name, values.get(i) == null ? BigDecimal.ZERO : values.get(i));
                }
            }
            if (groups.isEmpty()) out.put("行数", count);
            return out;
        }
    }

    private String jsonOf(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRows(String json) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            List<?> l = mapper.readValue(json, List.class);
            for (Object x : l) {
                if (x instanceof Map) out.add((Map<String, Object>) x);
            }
        } catch (Exception e) {
            log.warn("[小楷独立版] 结果集解析失败: {}", e.getMessage());
        }
        return out;
    }

    private List<String> parseList(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            List<?> l = mapper.readValue(json, List.class);
            for (Object x : l) out.add(String.valueOf(x));
        } catch (Exception e) {
            // 列清单损坏不影响取数
        }
        return out;
    }

    private String truncate(String s, int len) {
        if (s == null) return null;
        return s.length() > len ? s.substring(0, len) : s;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
