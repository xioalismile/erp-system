package com.erp.qc.spc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPC 标准公式断言（2026-11-05）。
 *
 * <p>口径：全部用可手工复算的固定数据，断言到 1e-4；公式出错必须在这里失败，
 * 不允许只靠"页面能显示"判断。</p>
 */
class SpcMathTest {

    /** 5 组子组（n=5）的原始实测值 */
    private static List<Double> values() {
        return new ArrayList<>(Arrays.asList(
                10.01, 9.99, 10.02, 10.00, 9.98,
                10.03, 10.01, 10.00, 10.02, 10.01,
                10.01, 10.03, 10.02, 10.01, 10.03,
                10.02, 10.04, 10.13, 10.02, 10.01,
                10.02, 10.12, 10.00, 9.99, 10.01
        ));
    }

    private static List<String> labels() {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < 25; i++) l.add("11-0" + (i / 5 + 1) + " 0" + (i % 9 + 1) + ":00");
        return l;
    }

    @Test
    void xbarRMatchesHandCalculation() {
        Map<String, Object> r = SpcMath.xbarR(values(), labels(), 10.1, 9.9);

        assertEquals("xbarR", r.get("mode"));
        assertEquals(5, r.get("subgroup"));
        // 子组均值 / 极差
        List<Double> means = cast(r.get("means"));
        List<Double> ranges = cast(r.get("ranges"));
        assertEquals(10.000, means.get(0), 1e-4);
        assertEquals(10.014, means.get(1), 1e-4);
        assertEquals(0.04, ranges.get(0), 1e-4);
        assertEquals(0.13, ranges.get(4), 1e-4);

        // X̄̄ = 10.0212，R̄ = 0.068
        assertEquals(10.0212, (Double) r.get("xbb"), 1e-4);
        assertEquals(0.068, (Double) r.get("rb"), 1e-4);
        // 控制限：X̄̄±A₂R̄（A₂=0.577）
        assertEquals(10.0604, (Double) r.get("uclX"), 1e-4);
        assertEquals(9.9820, (Double) r.get("lclX"), 1e-4);
        // 极差图：D₃=0，D₄=2.114
        assertEquals(0.1438, (Double) r.get("uclR"), 1e-4);
        assertEquals(0.0, (Double) r.get("lclR"), 1e-4);
        // σ̂ = R̄/d₂（d₂=2.326）
        assertEquals(0.029234, (Double) r.get("sigma"), 1e-5);
        // Cp / Cpk
        assertEquals(1.1405, (Double) r.get("cp"), 1e-3);
        assertEquals(0.8985, (Double) r.get("cpk"), 1e-3);
        assertEquals("能力不足", SpcMath.capabilityLevel((Double) r.get("cpk")));

        // 第 17 个点（子组 4 的均值 10.044）在控制限内，不应有"规则1"；
        // 但极差 0.12/0.13 也都在 UCL_R 内 → 本组数据不应触发规则1
        List<Map<String, Object>> findings = castFindings(r.get("findings"));
        assertTrue(findings.stream().noneMatch(f -> Integer.valueOf(1).equals(f.get("rule"))),
                "本组数据不应触发规则1");
    }

    @Test
    void imrMatchesHandCalculation() {
        List<Double> vals = new ArrayList<>(Arrays.asList(8.024, 8.022, 8.026, 8.023, 8.025));
        Map<String, Object> r = SpcMath.imr(vals, labels().subList(0, 5), 8.05, 8.00);
        assertEquals("imr", r.get("mode"));
        // X̄ = 8.024, MR = (0.002+0.004+0.003+0.002)/4 = 0.00275
        assertEquals(8.024, (Double) r.get("xb"), 1e-5);
        assertEquals(0.00275, (Double) r.get("mrb"), 1e-6);
        assertEquals(8.024 + 2.66 * 0.00275, (Double) r.get("uclI"), 1e-5);
        // 返回值统一保留 5 位小数（SpcMath.round），故断言容差取 1e-5
        assertEquals(3.267 * 0.00275, (Double) r.get("uclMr"), 1e-5);
        // σ̂ = MR̄/1.128（返回值保留 5 位小数）
        assertEquals(0.00275 / 1.128, (Double) r.get("sigma"), 1e-5);
    }

    @Test
    void pChartComputesPerBatchLimits() {
        List<Double> n = new ArrayList<>(Arrays.asList(100d, 96d, 104d, 98d));
        List<Double> d = new ArrayList<>(Arrays.asList(3d, 4d, 2d, 5d));
        Map<String, Object> r = SpcMath.pChart(n, d, labels().subList(0, 4));
        // p̄ = 14/398
        assertEquals(14d / 398d, (Double) r.get("pb"), 1e-5);
        List<Double> ucls = cast(r.get("ucls"));
        assertEquals(Math.min(14d / 398d + 3 * Math.sqrt((14d / 398d) * (1 - 14d / 398d) / 100d), 1), ucls.get(0), 1e-5);
        assertEquals(4, cast(r.get("rates")).size());
    }

    @Test
    void capabilityHandlesSingleSidedAndMissingLimits() {
        // 只有上限：Cp 不适用，Cpk 给单边
        assertNull(SpcMath.capability(10.1, null, 10.0, 0.02, "cp"));
        assertEquals((10.1 - 10.0) / (3 * 0.02), SpcMath.capability(10.1, null, 10.0, 0.02, "cpk"), 1e-5);
        // 无规格限：都为 null
        assertNull(SpcMath.capability(null, null, 10.0, 0.02, "cp"));
        assertNull(SpcMath.capability(null, null, 10.0, 0.02, "cpk"));
        // σ̂=0：不计算（避免除零）
        assertNull(SpcMath.capability(10.1, 9.9, 10.0, 0, "cpk"));
    }

    @Test
    void capabilityLevelThresholds() {
        assertEquals("优秀", SpcMath.capabilityLevel(1.67));
        assertEquals("充足", SpcMath.capabilityLevel(1.33));
        assertEquals("尚可", SpcMath.capabilityLevel(1.0));
        assertEquals("能力不足", SpcMath.capabilityLevel(0.67));
        assertEquals("严重不足", SpcMath.capabilityLevel(0.5));
        assertNull(SpcMath.capabilityLevel(null));
    }

    @Test
    void rulesDetectOutOfControlAndRuns() {
        // 规则1：显式给一个超限点
        List<Double> v1 = new ArrayList<>(Arrays.asList(10.0, 10.0, 10.6, 10.0, 10.0));
        List<Map<String, Object>> f1 = SpcMath.evaluateRules(v1, 10.0, 10.4, 9.6, 0.1);
        assertTrue(f1.stream().anyMatch(f -> Integer.valueOf(1).equals(f.get("rule")) && Integer.valueOf(2).equals(f.get("idx"))),
                "第 3 点超上限应命中规则1");

        // 规则2：连续 9 点同侧
        List<Double> v2 = new ArrayList<>();
        for (int i = 0; i < 9; i++) v2.add(10.01);
        List<Map<String, Object>> f2 = SpcMath.evaluateRules(v2, 10.0, 10.5, 9.5, 0.1);
        assertTrue(f2.stream().anyMatch(f -> Integer.valueOf(2).equals(f.get("rule"))),
                "连续9点同侧应命中规则2");

        // 规则3：连续 6 点递增
        List<Double> v3 = new ArrayList<>(Arrays.asList(10.001, 10.002, 10.003, 10.004, 10.005, 10.006));
        List<Map<String, Object>> f3 = SpcMath.evaluateRules(v3, 10.0, 10.5, 9.5, 0.1);
        assertTrue(f3.stream().anyMatch(f -> Integer.valueOf(3).equals(f.get("rule"))),
                "连续6点递增应命中规则3");

        // 受控数据：不应有任何判异
        List<Double> ok = new ArrayList<>(Arrays.asList(10.0, 10.05, 9.95, 10.02, 9.98));
        assertTrue(SpcMath.evaluateRules(ok, 10.0, 10.4, 9.6, 0.2).isEmpty(), "受控数据不应触发判异");
    }

    @Test
    void coefficientTableMatchesStandard() {
        // n=5：A2=0.577, D3=0, D4=2.114, d2=2.326
        double[] c5 = SpcMath.coef(5);
        assertEquals(0.577, c5[0], 1e-6);
        assertEquals(0.0, c5[1], 1e-6);
        assertEquals(2.114, c5[2], 1e-6);
        assertEquals(2.326, c5[3], 1e-6);
        // 越界 n 收敛到 n=5（不抛异常）
        assertNotNull(SpcMath.coef(20));
        assertEquals(0.577, SpcMath.coef(20)[0], 1e-6);
    }

    @SuppressWarnings("unchecked")
    private static List<Double> cast(Object o) {
        return (List<Double>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castFindings(Object o) {
        return (List<Map<String, Object>>) o;
    }
}
