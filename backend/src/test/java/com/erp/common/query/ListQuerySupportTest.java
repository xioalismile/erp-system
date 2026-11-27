package com.erp.common.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 列表查询统一件回归守门用例（2026-09-16 列表页查询口径通用化 P0）。
 *
 * <p>断言手法与 {@code PurchaseContractKeywordGroupTest} 一致：直接打印 MyBatis-Plus 生成的 SQL 段，
 * 不依赖数据库，也不「信任」实现约定。</p>
 */
@DisplayName("ListQuerySupport - 分页兼容 / 关键词分组 / 排序白名单 / 日期与ID维度")
class ListQuerySupportTest {

    private static final Set<String> SORTABLE = Set.of("order_no", "order_date", "total_amount");

    /** 把 wrapper 生成的 SQL 段转成可读断言文本（占位符统一替换为 ?） */
    private static String sql(QueryWrapper<?> w) {
        return w.getSqlSegment();
    }

    @Test
    @DisplayName("resolvePage：current 优先，旧参数 page 兜底，size 兜底与上限")
    void resolvePage_prefersCurrent_fallsBackToPage() {
        assertArrayEquals(new int[]{3, 50}, ListQuerySupport.resolvePage(3, 7, 50), "current 优先");
        assertArrayEquals(new int[]{7, 20}, ListQuerySupport.resolvePage(null, 7, null), "无 current 时用 page");
        assertArrayEquals(new int[]{1, 20}, ListQuerySupport.resolvePage(null, null, null), "都为空 → 第1页20条");
        assertArrayEquals(new int[]{1, 20}, ListQuerySupport.resolvePage(0, 0, 0), "非法值一律走默认");
        assertArrayEquals(new int[]{1, 500}, ListQuerySupport.resolvePage(1, null, 9999), "size 超上限截断为 500");
    }

    @Test
    @DisplayName("applyKeywordGroup：多列关键词强制成组，且后续条件在组外（修复采购合同那类漏括号）")
    void applyKeywordGroup_wrapsOrGroup_andKeepsLaterConditionsOutside() {
        QueryWrapper<Object> w = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroup(w, "SO-2026", "order_no", "customer_short_name");
        w.eq("status", "已生效");
        ListQuerySupport.applyEmployee(w, "5", "saler_id");

        String s = sql(w);
        // 两个关键词列必须被同一对括号包住，且后面以 AND 接其它条件
        assertTrue(s.matches("(?s).*\\(order_no LIKE [^\\s)]+ OR customer_short_name LIKE [^\\s)]+\\) AND status = .*"),
                "关键词 OR 组必须带括号且与后续条件 AND 串联，实际: " + s);
        // 反向断言：不允许出现「OR 分支后直接 AND」的漏括号形态
        assertFalse(s.matches("(?s).*OR customer_short_name LIKE [^\\s)]+ AND status = .*"),
                "出现漏括号形态，实际: " + s);
        assertTrue(s.contains("saler_id = "), "员工维度应生效，实际: " + s);
    }

    @Test
    @DisplayName("applyKeywordGroup：关键词为空 / 列全空 → 不产生任何条件（不阻断列表）")
    void applyKeywordGroup_blankInputs_addNothing() {
        QueryWrapper<Object> a = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroup(a, "   ", "order_no", "customer_short_name");
        assertEquals("", sql(a).trim(), "关键词为空不应生成条件");

        QueryWrapper<Object> b = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroup(b, "x", "", null);
        assertEquals("", sql(b).trim(), "列全空不应生成空的嵌套分组（否则会出现 AND () 非法 SQL）");
    }

    @Test
    @DisplayName("applyKeywordGroupWithIds：文本列与关联主键在同一 OR 组内，组外条件仍为 AND（客户名称搜索的前提）")
    void applyKeywordGroupWithIds_putsIdInSameOrGroup() {
        QueryWrapper<Object> w = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroupWithIds(w, "华东", List.of("quote_no"), "customer_id", List.of(11L, 22L));
        w.eq("status", "草稿");

        String s = sql(w);
        assertTrue(s.matches("(?s).*\\(quote_no LIKE [^\\s)]+ OR customer_id IN \\([^)]*\\)\\) AND status = .*"),
                "文本列与关联主键必须同处一对括号内，并整体 AND 到后续条件，实际: " + s);
        // 反向断言：不允许 IN 分支漏在组外（那样命中客户名的行会绕过状态/数据权限）
        assertFalse(s.matches("(?s).*customer_id IN \\([^)]*\\) AND status = .*"),
                "出现漏括号形态（客户主键分支跑到组外），实际: " + s);
    }

    @Test
    @DisplayName("applyKeywordGroupWithIds：主键集合空→纯文本匹配；文本列空→只按主键；两者皆空/关键词空→无任何条件")
    void applyKeywordGroupWithIds_boundaries() {
        QueryWrapper<Object> noIds = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroupWithIds(noIds, "SO-2026", List.of("order_no", "customer_name"),
                "customer_id", List.of());
        String s1 = sql(noIds);
        assertTrue(s1.contains("order_no LIKE ") && s1.contains("customer_name LIKE "), "实际: " + s1);
        assertFalse(s1.contains("customer_id"), "主键集合为空不得生成 IN 分支，实际: " + s1);

        QueryWrapper<Object> onlyIds = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroupWithIds(onlyIds, "华东", List.of(), "customer_id", List.of(7L));
        String s2 = sql(onlyIds);
        assertTrue(s2.contains("customer_id IN ("), "文本列为空时应只按关联主键匹配，实际: " + s2);
        assertFalse(s2.contains("LIKE"), "实际: " + s2);

        QueryWrapper<Object> both = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroupWithIds(both, "x", List.of(), "customer_id", List.of());
        assertEquals("", sql(both).trim(), "两者皆空不得生成 AND () 这类非法 SQL");

        QueryWrapper<Object> blank = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroupWithIds(blank, "   ", List.of("order_no"), "customer_id", List.of(1L));
        assertEquals("", sql(blank).trim(), "关键词为空不得产生任何条件");
    }

    @Test
    @DisplayName("applySort：命中白名单才生效，非法列名一律忽略（防注入）")
    void applySort_onlyWhitelistedColumns() {
        QueryWrapper<Object> ok = new QueryWrapper<>();
        assertTrue(ListQuerySupport.applySort(ok, "orderNo", "desc", SORTABLE), "驼峰转下划线后可命中白名单");
        assertTrue(sql(ok).contains("ORDER BY order_no DESC"), "实际: " + sql(ok));

        QueryWrapper<Object> bad = new QueryWrapper<>();
        assertFalse(ListQuerySupport.applySort(bad, "order_no; DROP TABLE x", "asc", SORTABLE), "非法列名必须被拒绝");
        assertFalse(ListQuerySupport.applySort(bad, "not_a_column", "asc", SORTABLE), "不在白名单必须被拒绝");
        assertFalse(sql(bad).contains("ORDER BY"), "非法排序不应产生 ORDER BY，实际: " + sql(bad));
    }

    @Test
    @DisplayName("applyDateRange：区间含端点，非法/空日期忽略")
    void applyDateRange_bindsBothEnds_ignoresInvalid() {
        QueryWrapper<Object> w = new QueryWrapper<>();
        ListQuerySupport.applyDateRange(w, "order_date", "2026-09-01", "2026-09-30");
        String s = sql(w);
        assertTrue(s.contains("order_date >= "), "实际: " + s);
        assertTrue(s.contains("order_date <= "), "实际: " + s);

        QueryWrapper<Object> onlyStart = new QueryWrapper<>();
        ListQuerySupport.applyDateRange(onlyStart, "order_date", "2026-09-01", "not-a-date");
        assertTrue(sql(onlyStart).contains("order_date >= "), "实际: " + sql(onlyStart));
        assertFalse(sql(onlyStart).contains("order_date <= "), "非法结束日期必须忽略，实际: " + sql(onlyStart));

        QueryWrapper<Object> none = new QueryWrapper<>();
        ListQuerySupport.applyDateRange(none, "order_date", "", "  ");
        assertEquals("", sql(none).trim(), "空区间不应产生条件");
    }

    @Test
    @DisplayName("applyId / applyEmployee：ID 维度生效，非数字忽略；员工入参映射到本页列")
    void applyIdAndEmployee_mapToPageColumn() {
        QueryWrapper<Object> w = new QueryWrapper<>();
        ListQuerySupport.applyId(w, "customer_id", "12");
        ListQuerySupport.applyEmployee(w, "34", "saler_id");
        String s = sql(w);
        assertTrue(s.contains("customer_id = "), "实际: " + s);
        assertTrue(s.contains("saler_id = "), "员工维度应映射到本页列，实际: " + s);
        assertTrue(s.contains(" AND "), "两个 ID 维度应为 AND 关系，实际: " + s);

        QueryWrapper<Object> bad = new QueryWrapper<>();
        ListQuerySupport.applyId(bad, "customer_id", "abc");
        ListQuerySupport.applyEmployee(bad, null, "saler_id");
        assertEquals("", sql(bad).trim(), "非法/空 ID 应被忽略，实际: " + sql(bad));
    }

    @Test
    @DisplayName("组合场景：关键词组 + 状态 + 日期 + 客户 + 员工 → OR 组之外的每个条件都生效")
    void combinedScenario_allConditionsApplyOutsideOrGroup() {
        QueryWrapper<Object> w = new QueryWrapper<>();
        ListQuerySupport.applyKeywordGroup(w, "SO", "order_no", "customer_short_name");
        w.eq("status", "草稿");
        ListQuerySupport.applyDateRange(w, "order_date", "2026-09-01", "2026-09-30");
        ListQuerySupport.applyId(w, "customer_id", "9");
        ListQuerySupport.applyEmployee(w, "5", "saler_id");

        String s = sql(w);
        assertTrue(s.matches("(?s).*\\(order_no LIKE [^\\s)]+ OR customer_short_name LIKE [^\\s)]+\\) AND status = .*"),
                "实际: " + s);
        assertTrue(s.contains("status = "), "实际: " + s);
        assertTrue(s.contains("order_date >= ") && s.contains("order_date <= "), "实际: " + s);
        assertTrue(s.contains("customer_id = "), "实际: " + s);
        assertTrue(s.contains("saler_id = "), "实际: " + s);
    }

    @Test
    @DisplayName("parseDate / parseLong：宽松解析，非法返回 null")
    void parseHelpers_areLenient() {
        assertEquals(LocalDate.of(2026, 9, 16), ListQuerySupport.parseDate(" 2026-09-16 "));
        assertNull(ListQuerySupport.parseDate("2026/09/16"));
        assertNull(ListQuerySupport.parseDate(""));
        assertEquals(88L, ListQuerySupport.parseLong(" 88 "));
        assertNull(ListQuerySupport.parseLong("8.8"));
        assertNull(ListQuerySupport.parseLong(null));
    }
}
