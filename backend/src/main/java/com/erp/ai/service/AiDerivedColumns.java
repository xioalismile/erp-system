package com.erp.ai.service;

import cn.hutool.core.util.ReflectUtil;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * AI 查询结果「派生列（计算列）」登记处（2026-11-22 表达层机制重构 · 立项一期）。
 *
 * <p>背景（用户 2026-11-22 明确要求）：小楷查出的结果表不能只有原始字段，还要能"算给你看"——
 * 例如销售订单超期清单要带「超期天数」（当前日期−交货日期），应收口径要带「未收款金额」。
 * 原先通用查询只回显登记的展示列，任何计算口径都出不来。</p>
 *
 * <p>设计：</p>
 * <ul>
 *   <li>规则按 intent 登记，函数从<b>实体</b>取值计算（与 fillForeignDisplayNames 同一口径：
 *       结果 Map 里没有的字段从实体反射取，绝不依赖展示列里恰好有该列）；</li>
 *   <li>派生列在展示列顺序中紧跟手工展示列之后（见 {@link AiQuerySchemaEnhancer} 的列排序），
 *       保证前端结果表前几列就能看到「超期天数」这类关键口径；</li>
 *   <li>计算失败的规则只跳过该行该列，绝不影响查询本身（与补全器同一容错口径）；</li>
 *   <li>新增常用口径 = 在此登记一条规则即自动生效（结果列 + 中文列名 + 能力侧自动可见）。</li>
 * </ul>
 */
@Slf4j
public final class AiDerivedColumns {

    private AiDerivedColumns() {
    }

    /** 单条派生列规则：结果键 + 中文列名 + 取值函数（entity → value） */
    record Rule(String key, String label, BiFunction<Object, Map<String, Object>, Object> fn) {
    }

    /** intent → 规则列表（LinkedHashMap 保证列序稳定） */
    private static final Map<String, List<Rule>> RULES = new LinkedHashMap<>();

    private static void rule(String intent, String key, String label,
                             BiFunction<Object, Map<String, Object>, Object> fn) {
        RULES.computeIfAbsent(intent, k -> new ArrayList<>()).add(new Rule(key, label, fn));
    }

    static {
        // 销售订单：交货超期天数（当前日期−交货日期；未填交货日期或未过期 = 空串，不显示误导数字）
        rule("QUERY_SALE_ORDER", "overdueDays", "超期天数", (entity, row) -> {
            LocalDate d = (LocalDate) ReflectUtil.getFieldValue(entity, "deliveryDate");
            LocalDate today = LocalDate.now();
            if (d == null || !d.isBefore(today)) {
                return "";
            }
            return (int) ChronoUnit.DAYS.between(d, today);
        });
        // 销售订单：未收款金额（价税合计−已收款；无净额 = 空串，未收款按 0 处理）
        rule("QUERY_SALE_ORDER", "unreceivedAmount", "未收款金额", (entity, row) -> {
            BigDecimal net = (BigDecimal) ReflectUtil.getFieldValue(entity, "netAmount");
            if (net == null) {
                return "";
            }
            BigDecimal received = (BigDecimal) ReflectUtil.getFieldValue(entity, "receivedAmount");
            return net.subtract(received == null ? BigDecimal.ZERO : received);
        });
    }

    /** 该 intent 的派生列键（保持登记顺序；无规则 = 空列表） */
    public static List<String> derivedKeys(String intent) {
        List<Rule> rules = intent == null ? null : RULES.get(intent);
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>();
        for (Rule r : rules) {
            keys.add(r.key());
        }
        return keys;
    }

    /** 该 intent 的派生列中文列名（键 → 中文标签） */
    public static Map<String, String> derivedLabels(String intent) {
        List<Rule> rules = intent == null ? null : RULES.get(intent);
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> labels = new LinkedHashMap<>();
        for (Rule r : rules) {
            labels.put(r.key(), r.label());
        }
        return labels;
    }

    /**
     * 把派生列写入结果行（rows 与 entities 一一对应、同序）。
     * 单行单规则异常只跳过该列并 WARN，不影响查询结果。
     */
    public static void apply(String intent, List<?> entities, List<Map<String, Object>> rows) {
        List<Rule> rules = intent == null ? null : RULES.get(intent);
        if (rules == null || rules.isEmpty() || entities == null || rows == null
                || entities.size() != rows.size()) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Object entity = entities.get(i);
            Map<String, Object> row = rows.get(i);
            for (Rule r : rules) {
                try {
                    Object val = r.fn().apply(entity, row);
                    row.put(r.key(), val != null ? val : "");
                } catch (Exception e) {
                    log.warn("[AI派生列] 计算失败 intent={} key={}: {}", intent, r.key(), e.getMessage());
                }
            }
        }
    }
}
