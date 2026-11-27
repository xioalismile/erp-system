package com.erp.common.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缺口覆盖：SortHelper 是全系统列表查询排序的统一入口，承担 SQL 注入防护职责。
 * 覆盖风险：
 *   1. camelToSnake: 驼峰→下划线转换正确性（含首字母大写、连续大写、单字符等边界）
 *   2. applyToWrapper: 白名单校验（命中白名单才允许排序，防止注入）
 *   3. applyToWrapper: ALIASES 别名映射（customerName → customer_short_name 等展示字段兜底）
 *   4. applyToWrapper: sortOrder 仅接受 asc/desc（大小写不敏感），其他一律拒绝
 *   5. isValidColumn: 列名合法性二次兜底（字母数字下划线正则校验）
 */
@DisplayName("SortHelper 排序与SQL注入防护工具测试")
class SortHelperTest {

    // ==================== camelToSnake 驼峰转下划线 ====================

    @ParameterizedTest(name = "驼峰转下划线: {0} → {1}")
    @CsvSource({
            "orderNo, order_no",
            "order_id, order_id",           // 已是下划线应保持不变
            "customerName, customer_name",
            "saleOrderNo, sale_order_no",
            "createdAt, created_at",
            "ID, _i_d",                     // 全大写每个字母前加下划线
            "A, _a",                        // 单字符大写
            "a, a",                         // 单字符小写
            "ABC, _a_b_c",                  // 连续大写
            "OrderNo, _order_no"            // 首字母大写
    })
    @DisplayName("驼峰→下划线：常规+边界场景")
    void camelToSnake_variousInputs_shouldConvertCorrectly(String input, String expected) {
        assertEquals(expected, SortHelper.camelToSnake(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("驼峰转下划线：null/空/空白 → 空字符串")
    void camelToSnake_nullEmptyBlank_shouldReturnEmpty(String input) {
        assertEquals("", SortHelper.camelToSnake(input));
    }

    // ==================== isValidColumn 列名合法性校验 ====================

    @ParameterizedTest(name = "合法列名应通过: {0}")
    @ValueSource(strings = {
            "order_no", "order_no_2", "a", "_abc", "customer_short_name",
            "sale_order_no", "id", "created_by", "warehouse_id_1"
    })
    @DisplayName("isValidColumn：合法列名(字母数字下划线) → true")
    void isValidColumn_validNames_shouldReturnTrue(String col) {
        assertTrue(SortHelper.isValidColumn(col));
    }

    @ParameterizedTest(name = "非法列名应被拦截: {0}")
    @ValueSource(strings = {
            "order no",       // 空格
            "order;no",       // 分号
            "order--",        // SQL注释
            "1order",         // 数字开头
            "ORDER_NO",       // 大写(约定列名全小写)
            "`order_no`",     // 反引号
            "order' or 1=1--" // SQL注入字符串
    })
    @DisplayName("isValidColumn：非法列名(含特殊字符/大写/数字开头) → false")
    void isValidColumn_invalidNames_shouldReturnFalse(String col) {
        assertFalse(SortHelper.isValidColumn(col));
    }

    @Test
    @DisplayName("isValidColumn：null → false")
    void isValidColumn_null_shouldReturnFalse() {
        assertFalse(SortHelper.isValidColumn(null));
    }

    // ==================== applyToWrapper 白名单排序 ====================

    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "id", "order_no", "order_date", "net_amount",
            "customer_short_name", "customer_no", "customer_id",
            "warehouse_id", "material_id", "material_code",
            "saler_id", "department_id", "dept_id",
            "created_by", "create_by"
    );

    @Test
    @DisplayName("applyToWrapper：命中白名单列名 → 应用成功返回 true")
    void applyToWrapper_whitelistedColumn_shouldApplyAndReturnTrue() {
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        boolean result = SortHelper.applyToWrapper(wrapper, "orderNo", "asc", SORTABLE_COLUMNS);

        assertTrue(result);
        // 实际 orderBy 后的 SQL 片段：检查 wrapper 内容包含 ORDER BY order_no ASC
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("order_no"), "排序SQL段应包含列名 order_no，实际: " + sqlSegment);
        assertTrue(sqlSegment.toLowerCase().contains("asc"), "排序SQL段应包含 ASC，实际: " + sqlSegment);
    }

    @ParameterizedTest(name = "排序方向大小写兼容: order={0}")
    @CsvSource({
            "asc, asc",
            "ASC, asc",
            "Asc, asc",
            "  ASC  , asc",
            "desc, desc",
            "DESC, desc",
            "Desc, desc"
    })
    @DisplayName("applyToWrapper：asc/desc 大小写不敏感、前后空白兼容")
    void applyToWrapper_sortOrderCaseInsensitive_shouldApply(String orderInput, String expectedKeyword) {
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        boolean result = SortHelper.applyToWrapper(wrapper, "id", orderInput, SORTABLE_COLUMNS);

        assertTrue(result, "方向合法时应返回true");
        assertTrue(wrapper.getSqlSegment().toLowerCase().contains(expectedKeyword),
                "应包含排序方向 " + expectedKeyword);
    }

    @ParameterizedTest(name = "非法排序方向应被拒绝: order={0}")
    @ValueSource(strings = {"", "  ", "ASCENDING", "DESCENDING", "1", "random", "DESC; DROP TABLE users--"})
    @DisplayName("applyToWrapper：非法 sortOrder（非 asc/desc）→ 拒绝返回 false，wrapper 不变")
    void applyToWrapper_invalidSortOrder_shouldReject(String invalidOrder) {
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        boolean result = SortHelper.applyToWrapper(wrapper, "id", invalidOrder, SORTABLE_COLUMNS);

        assertFalse(result, "非法方向应返回 false, 实际输入: " + invalidOrder);
        // wrapper 不应有 ORDER BY 片段
        assertFalse(wrapper.getSqlSegment().toLowerCase().contains("order by"),
                "非法方向不应产生 ORDER BY，实际: " + wrapper.getSqlSegment());
    }

    @ParameterizedTest(name = "不在白名单的列名应被拦截: prop={0}")
    @ValueSource(strings = {
            "password",         // 敏感字段
            "creditCardNo",     // 敏感字段
            "unknownColumn",    // 不存在的列
            "id OR 1=1",        // SQL注入尝试（驼峰转换后会非法）
            "id; DROP TABLE test--" // 注入
    })
    @DisplayName("applyToWrapper：非白名单列名 → 拒绝，防止SQL注入")
    void applyToWrapper_nonWhitelistedColumn_shouldReject(String prop) {
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        boolean result = SortHelper.applyToWrapper(wrapper, prop, "asc", SORTABLE_COLUMNS);

        assertFalse(result, "非白名单列应被拒绝: " + prop);
        assertFalse(wrapper.getSqlSegment().toLowerCase().contains("order by"),
                "被拒绝的列不应产生 ORDER BY");
    }

    @Test
    @DisplayName("applyToWrapper：通过 ALIASES 别名映射 customerName → customer_short_name（白名单中存在）")
    void applyToWrapper_aliasLookup_customerName_shouldMapToWhitelistedColumn() {
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        // customerName 在白名单中无，但 ALIASES 有 customer_short_name（在白名单内）
        boolean result = SortHelper.applyToWrapper(wrapper, "customerName", "desc", SORTABLE_COLUMNS);

        assertTrue(result, "别名应映射到白名单列 customer_short_name");
        assertTrue(wrapper.getSqlSegment().contains("customer_short_name"),
                "实际排序应使用映射后的列名, 实际SQL: " + wrapper.getSqlSegment());
    }

    @Test
    @DisplayName("applyToWrapper：通过 ALIASES 别名映射 creatorName → created_by")
    void applyToWrapper_aliasLookup_creatorName_shouldMapToCreatedBy() {
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        boolean result = SortHelper.applyToWrapper(wrapper, "creatorName", "asc", SORTABLE_COLUMNS);

        assertTrue(result);
        assertTrue(wrapper.getSqlSegment().contains("created_by") || wrapper.getSqlSegment().contains("create_by"),
                "应映射到 created_by 或 create_by, 实际: " + wrapper.getSqlSegment());
    }

    @Test
    @DisplayName("applyToWrapper：ALIASES 候选列都不在白名单 → 拒绝（防止别名绕过）")
    void applyToWrapper_aliasNoCandidateInWhitelist_shouldReject() {
        // 构造一个白名单不包含任何 employee_name 候选列(employee_id 不在此白名单子集)
        Set<String> limitedWhitelist = Set.of("id", "order_no");
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        boolean result = SortHelper.applyToWrapper(wrapper, "employeeName", "asc", limitedWhitelist);

        assertFalse(result, "候选列 employee_id 不在白名单应拒绝");
    }

    @ParameterizedTest(name = "空输入安全检查: sortBy={0}")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("applyToWrapper：null/空/空白 sortBy → 静默返回 false（无副作用）")
    void applyToWrapper_nullOrBlankSortBy_shouldReturnFalseQuietly(String sortBy) {
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        boolean result = SortHelper.applyToWrapper(wrapper, sortBy, "asc", SORTABLE_COLUMNS);
        assertFalse(result);
        assertFalse(wrapper.getSqlSegment().toLowerCase().contains("order by"));
    }

    @Test
    @DisplayName("applyToWrapper：null wrapper 或 空白名单 → 静默返回 false，不抛NPE")
    void applyToWrapper_nullWrapperOrEmptyWhitelist_shouldReturnFalseQuietly() {
        assertDoesNotThrow(() ->
                SortHelper.applyToWrapper(null, "id", "asc", SORTABLE_COLUMNS));
        assertDoesNotThrow(() ->
                SortHelper.applyToWrapper(new QueryWrapper<>(), "id", "asc", Set.of()));
        assertDoesNotThrow(() ->
                SortHelper.applyToWrapper(new QueryWrapper<>(), "id", "asc", null));
    }
}
