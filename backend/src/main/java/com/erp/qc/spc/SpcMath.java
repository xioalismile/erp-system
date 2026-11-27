package com.erp.qc.spc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SPC 统计过程控制 · 标准公式库（纯静态、无副作用，服务端唯一计算入口）。
 *
 * <p>口径（与交互式 PRD「规则与口径」页签一致）：</p>
 * <ul>
 *   <li>均值-极差图 X̄-R：中心线 X̄̄ / R̄，控制限 X̄̄±A₂R̄、D₃R̄~D₄R̄（系数按子组大小查标准表）</li>
 *   <li>单值-移动极差图 I-MR：X̄±2.66·MR̄、UCL_MR=3.267·MR̄（n=2 特例）</li>
 *   <li>不合格品率图 P：p̄=Σd/Σn，逐批控制限 p̄±3√(p̄(1−p̄)/nᵢ)</li>
 *   <li>过程能力：σ̂=R̄/d₂（I-MR 降级用 MR̄/1.128），Cp=(USL−LSL)/6σ̂，Cpk=min(USL−X̄̄, X̄̄−LSL)/3σ̂；
 *       单边规格限只给 Cpk 单边值</li>
 *   <li>八条判异规则（GB/T 4091 通行口径），在均值/单值序列上判定</li>
 * </ul>
 *
 * <p>前端只做呈现，不允许任何判异/控制限的前端计算。</p>
 */
public final class SpcMath {

    private SpcMath() {}

    /** 默认子组大小 n=5（工业通行；系数表覆盖 2~9） */
    public static final int DEFAULT_SUBGROUP = 5;

    /** 控制限系数表：COEF[n-2] = {A2, D3, D4, d2}（n=2..9，GB/T 4091 标准值） */
    private static final double[][] COEF = {
            {1.880, 0.000, 3.267, 1.128}, // n=2
            {1.023, 0.000, 2.574, 1.693}, // n=3
            {0.729, 0.000, 2.282, 2.059}, // n=4
            {0.577, 0.000, 2.114, 2.326}, // n=5
            {0.483, 0.000, 2.004, 2.534}, // n=6
            {0.419, 0.076, 1.924, 2.704}, // n=7
            {0.373, 0.136, 1.864, 2.847}, // n=8
            {0.337, 0.184, 1.816, 2.970}, // n=9
    };

    /** 取子组大小 n 的系数（n 不在 2~9 时收敛到 n=5） */
    public static double[] coef(int n) {
        if (n < 2 || n > 9) n = DEFAULT_SUBGROUP;
        return COEF[n - 2];
    }

    public static double mean(List<Double> v) {
        if (v == null || v.isEmpty()) return Double.NaN;
        double s = 0;
        for (double x : v) s += x;
        return s / v.size();
    }

    // ==================== 均值-极差图 X̄-R ====================

    /**
     * @param values 按时间升序的实测值序列（≥2 组完整子组才出 X̄-R，否则调用方降级 I-MR）
     * @param labels 每个原始点的时间标签（与 values 同长，可空）
     * @param usl/lsl 规格上限/下限（可空：单边或无边限时 Cp/Cpk 相应置空）
     */
    public static Map<String, Object> xbarR(List<Double> values, List<String> labels,
                                            Double usl, Double lsl) {
        int n = DEFAULT_SUBGROUP;
        double[] cf = coef(n);
        int groups = values.size() / n;
        List<Double> means = new ArrayList<>();
        List<Double> ranges = new ArrayList<>();
        List<String> groupLabels = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            List<Double> seg = values.subList(g * n, g * n + n);
            double hi = -Double.MAX_VALUE, lo = Double.MAX_VALUE;
            for (double x : seg) { hi = Math.max(hi, x); lo = Math.min(lo, x); }
            means.add(mean(seg));
            ranges.add(hi - lo);
            groupLabels.add(labels == null ? ("子组" + (g + 1)) : labels.get(g * n));
        }
        double xbb = mean(means), rb = mean(ranges);
        double uclX = xbb + cf[0] * rb, lclX = xbb - cf[0] * rb;
        double uclR = cf[2] * rb, lclR = cf[1] * rb;
        double sigma = rb / cf[3];

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", "xbarR");
        m.put("subgroup", n);
        m.put("means", means);
        m.put("ranges", ranges);
        m.put("groupLabels", groupLabels);
        m.put("xbb", round(xbb));
        m.put("rb", round(rb));
        m.put("uclX", round(uclX));
        m.put("lclX", round(lclX));
        m.put("uclR", round(uclR));
        m.put("lclR", round(lclR));
        m.put("sigma", round(sigma));
        m.put("cp", capability(usl, lsl, xbb, sigma, "cp"));
        m.put("cpk", capability(usl, lsl, xbb, sigma, "cpk"));
        m.put("findings", evaluateRules(means, xbb, uclX, lclX, sigma));
        return m;
    }

    // ==================== 单值-移动极差图 I-MR ====================

    public static Map<String, Object> imr(List<Double> values, List<String> labels,
                                          Double usl, Double lsl) {
        List<Double> mrs = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) mrs.add(Math.abs(values.get(i) - values.get(i - 1)));
        double xb = mean(values), mrb = mean(mrs);
        double uclI = xb + 2.66 * mrb, lclI = xb - 2.66 * mrb;
        double uclMr = 3.267 * mrb;
        double sigma = mrb / 1.128;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", "imr");
        m.put("values", values);
        m.put("mrs", mrs);
        m.put("groupLabels", labels);
        m.put("xb", round(xb));
        m.put("mrb", round(mrb));
        m.put("uclI", round(uclI));
        m.put("lclI", round(lclI));
        m.put("uclMr", round(uclMr));
        m.put("sigma", round(sigma));
        m.put("cp", capability(usl, lsl, xb, sigma, "cp"));
        m.put("cpk", capability(usl, lsl, xb, sigma, "cpk"));
        m.put("findings", evaluateRules(values, xb, uclI, lclI, sigma));
        return m;
    }

    // ==================== 不合格品率图 P ====================

    /** @param inspected 各批检验数量 n；@param defect 各批不合格数 d */
    public static Map<String, Object> pChart(List<Double> inspected, List<Double> defect,
                                             List<String> labels) {
        double sumN = 0, sumD = 0;
        for (int i = 0; i < inspected.size(); i++) { sumN += inspected.get(i); sumD += defect.get(i); }
        double pb = sumN > 0 ? sumD / sumN : 0;
        List<Double> rates = new ArrayList<>(), ucls = new ArrayList<>(), lcls = new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < inspected.size(); i++) {
            double n = Math.max(inspected.get(i), 1e-9);
            double p = defect.get(i) / n;
            double half = 3 * Math.sqrt(Math.max(pb * (1 - pb) / n, 0));
            double ucl = Math.min(pb + half, 1), lcl = Math.max(pb - half, 0);
            rates.add(round(p)); ucls.add(round(ucl)); lcls.add(round(lcl));
            if (p > ucl + 1e-12 || p < lcl - 1e-12) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("rule", "规则1 · 1点落在A区以外");
                f.put("idx", i);
                f.put("label", labels == null ? null : labels.get(i));
                f.put("value", round(p));
                f.put("ucl", round(ucl));
                f.put("lcl", round(lcl));
                out.add(f);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", "pchart");
        m.put("pb", round(pb));
        m.put("rates", rates);
        m.put("ucls", ucls);
        m.put("lcls", lcls);
        m.put("labels", labels);
        m.put("findings", out);
        return m;
    }

    // ==================== 八条判异规则 ====================

    /**
     * 在均值/单值序列上判定八条规则；返回出界明细（规则号/规则名/点序/数值/时间标签由调用方补）。
     * 规则：
     * 1 = 1点落在A区以外（出控制限）
     * 2 = 连续9点落在中心线同一侧
     * 3 = 连续6点递增或递减
     * 4 = 连续14点相邻上下交替
     * 5 = 连续3点中2点落在中心线同侧2σ~3σ
     * 6 = 连续5点中4点落在中心线同侧1σ~3σ
     * 7 = 连续15点落在中心线±1σ内
     * 8 = 连续8点落在中心线±1σ之外（双侧）
     */
    public static List<Map<String, Object>> evaluateRules(List<Double> v, double cl,
                                                          double ucl, double lcl, double sigma) {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = v.size();
        for (int i = 0; i < n; i++) {
            if (v.get(i) > ucl || v.get(i) < lcl) out.add(finding(1, "1点落在A区以外", i, v.get(i)));
        }
        for (int s = 0; s + 9 <= n; s++) {
            boolean up = true, down = true;
            for (int k = s; k < s + 9; k++) {
                if (v.get(k) <= cl) up = false;
                if (v.get(k) >= cl) down = false;
            }
            if (up || down) out.add(finding(2, "连续9点落在中心线同一侧", s + 8, v.get(s + 8)));
        }
        for (int s = 0; s + 6 <= n; s++) {
            boolean inc = true, dec = true;
            for (int k = 1; k < 6; k++) {
                if (v.get(s + k) <= v.get(s + k - 1)) inc = false;
                if (v.get(s + k) >= v.get(s + k - 1)) dec = false;
            }
            if (inc || dec) out.add(finding(3, "连续6点递增或递减", s + 5, v.get(s + 5)));
        }
        for (int s = 0; s + 14 <= n; s++) {
            boolean alt = true;
            for (int k = 2; k < 14; k++) {
                double a = v.get(s + k - 2) - v.get(s + k - 1), b = v.get(s + k - 1) - v.get(s + k);
                if (!(a > 0 && b > 0) && !(a < 0 && b < 0)) { alt = false; break; }
            }
            if (alt) out.add(finding(4, "连续14点相邻上下交替", s + 13, v.get(s + 13)));
        }
        for (int s = 0; s + 3 <= n; s++) {
            int hit = 0;
            boolean side = v.get(s) > cl;
            for (int k = 0; k < 3; k++) {
                double x = v.get(s + k);
                if ((x > cl) == side && Math.abs(x - cl) > 2 * sigma && Math.abs(x - cl) <= 3 * sigma
                        && x <= ucl && x >= lcl) hit++;
            }
            if (hit >= 2) out.add(finding(5, "连续3点中2点落在中心线同侧2σ~3σ", s + 2, v.get(s + 2)));
        }
        for (int s = 0; s + 5 <= n; s++) {
            int hit = 0;
            boolean side = v.get(s) > cl;
            for (int k = 0; k < 5; k++) {
                double x = v.get(s + k);
                if ((x > cl) == side && Math.abs(x - cl) > sigma && x <= ucl && x >= lcl) hit++;
            }
            if (hit >= 4) out.add(finding(6, "连续5点中4点落在中心线同侧1σ~3σ", s + 4, v.get(s + 4)));
        }
        for (int s = 0; s + 15 <= n; s++) {
            boolean all = true;
            for (int k = 0; k < 15; k++) if (Math.abs(v.get(s + k) - cl) > sigma) { all = false; break; }
            if (all) out.add(finding(7, "连续15点落在中心线±1σ内", s + 14, v.get(s + 14)));
        }
        for (int s = 0; s + 8 <= n; s++) {
            boolean all = true;
            for (int k = 0; k < 8; k++) if (Math.abs(v.get(s + k) - cl) <= sigma) { all = false; break; }
            if (all) out.add(finding(8, "连续8点落在中心线±1σ之外", s + 7, v.get(s + 7)));
        }
        // 同点同规则去重（窗口重叠会造成重复报告）
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Map<String, Object>> dedup = new ArrayList<>();
        for (Map<String, Object> f : out) {
            String key = f.get("rule") + "#" + f.get("idx");
            if (seen.add(key)) dedup.add(f);
        }
        dedup.sort((a, b) -> {
            int ra = (Integer) a.get("rule"), rb = (Integer) b.get("rule");
            if (ra != rb) return Integer.compare(ra, rb);
            return Integer.compare((Integer) a.get("idx"), (Integer) b.get("idx"));
        });
        return dedup;
    }

    private static Map<String, Object> finding(int rule, String name, int idx, double value) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("rule", rule);
        f.put("ruleName", "规则" + rule + " · " + name);
        f.put("idx", idx);
        f.put("value", round(value));
        return f;
    }

    // ==================== 过程能力 ====================

    /** kind = "cp" | "cpk"；单边/无边限时按口径给单边 Cpk 或置空 */
    public static Double capability(Double usl, Double lsl, double center, double sigma, String kind) {
        if (sigma <= 0) return null;
        boolean hasU = usl != null, hasL = lsl != null;
        if ("cp".equals(kind)) {
            if (!hasU || !hasL) return null;            // 单边限 Cp 不适用
            return round((usl - lsl) / (6 * sigma));
        }
        double twoSide = hasU && hasL ? Math.min(usl - center, center - lsl) : Double.NaN;
        double val;
        if (hasU && hasL) val = twoSide / (3 * sigma);
        else if (hasU) val = (usl - center) / (3 * sigma);
        else if (hasL) val = (center - lsl) / (3 * sigma);
        else return null;                                // 无规格限 → 无过程能力
        return round(val);
    }

    /** 能力等级（通行分级）：Cpk≥1.67 优秀 / ≥1.33 充足 / ≥1.0 尚可 / ≥0.67 不足 / 其余严重不足 */
    public static String capabilityLevel(Double cpk) {
        if (cpk == null) return null;
        if (cpk >= 1.67) return "优秀";
        if (cpk >= 1.33) return "充足";
        if (cpk >= 1.0) return "尚可";
        if (cpk >= 0.67) return "能力不足";
        return "严重不足";
    }

    private static double round(double v) { return Math.round(v * 100000d) / 100000d; }
}
