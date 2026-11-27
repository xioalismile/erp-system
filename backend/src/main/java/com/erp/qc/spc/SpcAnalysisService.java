package com.erp.qc.spc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SPC 质量看板取数与组装（数据看板 → SPC质量看板）。
 *
 * <p>数据来源：工序质检流程写入的检验记录 + 检验明细实测值；规格限优先取检验明细行上的
 * 上/下限（与质检模板同源），历史上限缺失时回退该检验项最近一次非空限值。</p>
 *
 * <p>权限口径：与「工序质检报表」一致——接口登录即可访问，页面入口由菜单授权控制
 * （超级管理员/系统管理员/生产经理/生产，质检动作由生产角色承载）；本看板是管理者视角，
 * 不做逐行数据权限裁剪（与经营看板质量块同口径）。</p>
 */
@Slf4j
@Service
public class SpcAnalysisService {

    /** 主业务库（与 SchemaUpgradeEnsurer 同款注入：两个 JdbcTemplate 存在时必须显式限定主库） */
    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbc;

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 检验项候选清单（仅数值型实测值可入 SPC；返回 numeric 标记与最近限值） */
    public List<Map<String, Object>> items(LocalDate from, LocalDate to, String orderNo, String operationName) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendCommonWhere(where, args, from, to, orderNo, operationName);
        String sql =
            "SELECT i.item_name AS itemName, COUNT(*) AS cnt, " +
            " MAX(i.upper_limit) AS upperLimit, MIN(i.lower_limit) AS lowerLimit, " +
            " MAX(COALESCE(r.inspect_time, r.created_at)) AS lastTime " +
            "FROM qc_record_item i JOIN qc_record r ON r.record_id = i.record_id" + where +
            " AND i.measured_value ~ '^\\s*-?[0-9]+(\\.[0-9]+)?\\s*$' " +
            " GROUP BY i.item_name ORDER BY cnt DESC, itemName";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item", r.get("itemName"));
            m.put("count", ((Number) r.get("cnt")).intValue());
            Object u = r.get("upperLimit"), l = r.get("lowerLimit");
            m.put("usl", u == null ? null : ((Number) u).doubleValue());
            m.put("lsl", l == null ? null : ((Number) l).doubleValue());
            m.put("numeric", true);
            out.add(m);
        }
        return out;
    }

    /** 全量分析：KPI + 三种控制图 + 过程能力 + 判异明细（服务端唯一计算入口） */
    public Map<String, Object> analyze(String item, LocalDate from, LocalDate to,
                                       String orderNo, String operationName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("item", item);
        result.put("from", from == null ? null : from.toString());
        result.put("to", to == null ? null : to.toString());

        // 1) KPI（批次口径，与工序质检报表同源同口径）
        StringBuilder kw = new StringBuilder(" WHERE 1=1");
        List<Object> kargs = new ArrayList<>();
        appendCommonWhere(kw, kargs, from, to, orderNo, operationName);
        Map<String, Object> kpiRow = jdbc.queryForMap(
            "SELECT COUNT(*) AS batches, " +
            " COUNT(*) FILTER (WHERE result = '合格') AS passBatches, " +
            " COUNT(*) FILTER (WHERE result = '不合格') AS defectBatches, " +
            " COALESCE(SUM(inspect_qty), 0) AS inspectQty, " +
            " COALESCE(SUM(qualified_qty), 0) AS qualifiedQty, " +
            " COALESCE(SUM(defect_qty), 0) AS defectQty " +
            "FROM qc_record r" + kw, kargs.toArray());
        long batches = ((Number) kpiRow.get("batches")).longValue();
        long pass = ((Number) kpiRow.get("passBatches")).longValue();
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("batches", batches);
        kpi.put("passBatches", pass);
        kpi.put("defectBatches", ((Number) kpiRow.get("defectBatches")).longValue());
        kpi.put("passRate", batches > 0 ? round(pass * 100.0 / batches) : null);
        // 已判定批次 = 合格 + 不合格；历史记录 result 为空（未判定）时不计入，页面据此明示口径，避免看数时对不上
        kpi.put("judgedBatches", pass + ((Number) kpiRow.get("defectBatches")).longValue());
        kpi.put("inspectQty", ((Number) kpiRow.get("inspectQty")).doubleValue());
        kpi.put("qualifiedQty", ((Number) kpiRow.get("qualifiedQty")).doubleValue());
        kpi.put("defectQty", ((Number) kpiRow.get("defectQty")).doubleValue());
        result.put("kpi", kpi);

        // 2) 实测值序列（时间升序；脏数据剔除并计数）
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendCommonWhere(w, args, from, to, orderNo, operationName);
        args.add(item);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT COALESCE(r.inspect_time, r.created_at) AS t, r.record_no AS recordNo, " +
            " r.order_no AS orderNo, r.operation_name AS opName, r.result AS result, " +
            " i.measured_value AS mv, i.upper_limit AS ul, i.lower_limit AS ll " +
            "FROM qc_record_item i JOIN qc_record r ON r.record_id = i.record_id" + w +
            " AND i.item_name = ? ORDER BY t", args.toArray());

        List<Double> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Map<String, Object>> points = new ArrayList<>();
        Double usl = null, lsl = null;
        Timestamp uslT = null, lslT = null;
        int skipped = 0;
        for (Map<String, Object> r : rows) {
            Double v = parse(r.get("mv"));
            if (v == null) { skipped++; continue; }
            Object tObj = r.get("t");
            String lb = tObj == null ? "" : (tObj instanceof Timestamp ts
                    ? ts.toLocalDateTime().format(HM) : String.valueOf(tObj));
            values.add(v);
            labels.add(lb);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", lb);
            p.put("recordNo", r.get("recordNo"));
            p.put("orderNo", r.get("orderNo"));
            p.put("operation", r.get("opName"));
            p.put("value", v);
            points.add(p);
            // 规格限：取时间最近的非空限值
            if (r.get("ul") != null && (uslT == null || tObj != null && ((Timestamp) tObj).after(uslT))) {
                usl = ((Number) r.get("ul")).doubleValue();
                uslT = (Timestamp) tObj;
            }
            if (r.get("ll") != null && (lslT == null || tObj != null && ((Timestamp) tObj).after(lslT))) {
                lsl = ((Number) r.get("ll")).doubleValue();
                lslT = (Timestamp) tObj;
            }
        }
        result.put("points", points);
        result.put("skippedDirty", skipped);
        result.put("usl", usl);
        result.put("lsl", lsl);

        // 3) 控制图：子组数据足够 → X̄-R；否则降级 I-MR
        if (values.size() >= SpcMath.DEFAULT_SUBGROUP * 2) {
            Map<String, Object> x = SpcMath.xbarR(values, labels, usl, lsl);
            // 判异明细补时间标签
            for (Map<String, Object> f : (List<Map<String, Object>>) x.get("findings")) {
                int idx = (Integer) f.get("idx");
                if (labels != null && idx < labels.size()) f.put("label", labels.get(idx));
            }
            result.put("xbarR", x);
        }
        if (values.size() >= 2) {
            Map<String, Object> im = SpcMath.imr(values, labels, usl, lsl);
            for (Map<String, Object> f : (List<Map<String, Object>>) im.get("findings")) {
                int idx = (Integer) f.get("idx");
                if (labels != null && idx < labels.size()) f.put("label", labels.get(idx));
            }
            result.put("imr", im);
        }
        result.put("chartMode", values.size() >= SpcMath.DEFAULT_SUBGROUP * 2 ? "xbarR" : "imr");

        // 4) P 图：按检验记录整批判定（n=检验数量，d=判定不合格数）
        StringBuilder pw = new StringBuilder(" WHERE 1=1");
        List<Object> pargs = new ArrayList<>();
        appendCommonWhere(pw, pargs, from, to, orderNo, operationName);
        List<Map<String, Object>> prs = jdbc.queryForList(
            "SELECT COALESCE(r.inspect_time, r.created_at) AS t, r.inspect_qty AS n, r.defect_qty AS d " +
            "FROM qc_record r" + pw + " AND r.inspect_qty > 0 ORDER BY t", pargs.toArray());
        List<Double> ns = new ArrayList<>(), ds = new ArrayList<>();
        List<String> pl = new ArrayList<>();
        for (Map<String, Object> r : prs) {
            ns.add(((Number) r.get("n")).doubleValue());
            ds.add(r.get("d") == null ? 0d : ((Number) r.get("d")).doubleValue());
            Object tObj = r.get("t");
            pl.add(tObj == null ? "" : (tObj instanceof Timestamp ts
                    ? ts.toLocalDateTime().format(HM) : String.valueOf(tObj)));
        }
        result.put("pchart", prs.isEmpty() ? null : SpcMath.pChart(ns, ds, pl));

        // 5) 过程能力等级
        Map<String, Object> mainChart = "xbarR".equals(result.get("chartMode"))
                ? (Map<String, Object>) result.get("xbarR") : (Map<String, Object>) result.get("imr");
        if (mainChart != null) {
            Double cpk = (Double) mainChart.get("cpk");
            result.put("cpk", cpk);
            result.put("cp", mainChart.get("cp"));
            result.put("cpkLevel", SpcMath.capabilityLevel(cpk));
        }
        return result;
    }

    private void appendCommonWhere(StringBuilder w, List<Object> args,
                                   LocalDate from, LocalDate to, String orderNo, String operationName) {
        if (from != null) { w.append(" AND COALESCE(r.inspect_time, r.created_at) >= ?"); args.add(from.atStartOfDay()); }
        if (to != null)   { w.append(" AND COALESCE(r.inspect_time, r.created_at) < ?");  args.add(to.atStartOfDay()); }
        if (orderNo != null && !orderNo.isBlank()) { w.append(" AND r.order_no = ?"); args.add(orderNo.trim()); }
        if (operationName != null && !operationName.isBlank()) { w.append(" AND r.operation_name = ?"); args.add(operationName.trim()); }
    }

    /** 时间维度 → 半开区间 [from, to)，与数据看板既有口径一致 */
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

    private static Double parse(Object v) {
        if (v == null) return null;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round(double v) { return Math.round(v * 10000d) / 10000d; }
}
