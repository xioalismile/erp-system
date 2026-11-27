package com.erp.common.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缺口覆盖：BillStatusUtils 是全系统所有单据状态转换的基础工具。
 * 覆盖风险：
 *   1. 字符串别名（草稿/待审核/已生效/已作废及各类同义别名）→ 数字 code 的映射正确性
 *   2. null / 空字符串 / 空白字符 → 默认值（草稿类=0）
 *   3. 扩展状态码 3/4/5/6（已下达/执行中/已完工/已结案）→ 统一降级为 APPROVED(2)
 *   4. 未知字符串 / 未知数字 → 不应抛错，默认降级为已生效类（避免误判为草稿可编辑）
 *   5. isDraft / isPending / isApprovedClass / isVoided / isFinalized 便捷判断
 */
@DisplayName("BillStatusUtils 状态转换工具测试")
class BillStatusUtilsTest {

    // ==================== toNumber: 字符串 → 数字 ====================

    @ParameterizedTest(name = "草稿类别名映射: alias={0} → 0")
    @ValueSource(strings = {"草稿", "未审核", "驳回", "已取消"})
    @DisplayName("草稿类字符串 → code=0")
    void toNumber_draftAliases_shouldReturn0(String alias) {
        assertEquals(0, BillStatusUtils.toNumber(alias));
    }

    @ParameterizedTest(name = "待审核类别名映射: alias={0} → 1")
    @ValueSource(strings = {"待审核", "待审批", "审批中"})
    @DisplayName("待审核类字符串 → code=1")
    void toNumber_pendingAliases_shouldReturn1(String alias) {
        assertEquals(1, BillStatusUtils.toNumber(alias));
    }

    @ParameterizedTest(name = "已生效类别名映射: alias={0} → 2")
    @ValueSource(strings = {"已生效", "审批通过", "已确认", "已下达", "执行中", "已完工", "已关闭"})
    @DisplayName("已生效类字符串 → code=2")
    void toNumber_approvedAliases_shouldReturn2(String alias) {
        assertEquals(2, BillStatusUtils.toNumber(alias));
    }

    @ParameterizedTest(name = "作废类别名映射: alias={0} → 7")
    @ValueSource(strings = {"已作废", "作废"})
    @DisplayName("作废类字符串 → code=7")
    void toNumber_voidedAliases_shouldReturn7(String alias) {
        assertEquals(7, BillStatusUtils.toNumber(alias));
    }

    @ParameterizedTest(name = "null/空/空白 → 默认草稿(0)")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("null / 空字符串 / 空白 → code=0")
    void toNumber_nullEmptyBlank_shouldReturn0(String input) {
        assertEquals(0, BillStatusUtils.toNumber(input));
    }

    @Test
    @DisplayName("未知字符串 → 降级为已生效类(2)，避免误判为草稿可编辑")
    void toNumber_unknownString_shouldFallbackToApproved() {
        assertEquals(2, BillStatusUtils.toNumber("其他状态"));
        assertEquals(2, BillStatusUtils.toNumber("UNKNOWN"));
        assertEquals(2, BillStatusUtils.toNumber("部分发货"));
    }

    // ==================== toNumber: Number 类型输入 ====================

    @ParameterizedTest(name = "数字输入直接映射: num={0} → {1}")
    @CsvSource({
            "0, 0",   // 草稿
            "1, 1",   // 待审核
            "2, 2",   // 已生效
            "7, 7",   // 作废
            "3, 2",   // 已下达(生产内部扩展) → 已生效类
            "4, 2",   // 执行中 → 已生效类
            "5, 2",   // 已完工 → 已生效类
            "6, 2"    // 已结案 → 已生效类(close_status另算)
    })
    @DisplayName("数字输入 → 标准code（3/4/5/6统一降级为2）")
    void toNumber_numberInput_shouldMapCorrectly(int input, int expected) {
        assertEquals(expected, BillStatusUtils.toNumber(input));
        assertEquals(expected, BillStatusUtils.toNumber((long) input));
        assertEquals(expected, BillStatusUtils.toNumber(Integer.valueOf(input)));
    }

    @Test
    @DisplayName("未知数字码(>7) → 降级为已生效类(2)，不抛异常")
    void toNumber_unknownNumber_shouldFallbackToApproved() {
        assertEquals(2, BillStatusUtils.toNumber(8));
        assertEquals(2, BillStatusUtils.toNumber(99));
        assertEquals(2, BillStatusUtils.toNumber(-1));
    }

    // ==================== toString: 数字 → 中文文案 ====================

    @ParameterizedTest(name = "数字 → 文案: code={0} → {1}")
    @CsvSource({
            "0, 草稿",
            "1, 待审核",
            "2, 已生效",
            "7, 已作废",
            "3, 已生效",  // 扩展码统一显示已生效
            "4, 已生效",
            "5, 已生效",
            "6, 已生效"
    })
    @DisplayName("数字 code → 标准中文文案")
    void toString_codeToLabel_shouldMatch(Integer code, String expectedLabel) {
        assertEquals(expectedLabel, BillStatusUtils.toString(code));
    }

    @Test
    @DisplayName("null code → 草稿")
    void toString_nullCode_shouldReturnDraft() {
        assertEquals("草稿", BillStatusUtils.toString(null));
    }

    @Test
    @DisplayName("未知数字码 → 降级为已生效文案，不抛异常")
    void toString_unknownCode_shouldFallbackToApproved() {
        assertEquals("已生效", BillStatusUtils.toString(100));
        assertEquals("已生效", BillStatusUtils.toString(-5));
    }

    // ==================== 便捷判断方法 ====================

    @Test
    @DisplayName("isDraft 草稿判断：仅 0 和 草稿别名返回 true")
    void isDraft_shouldOnlyMatchDraftClass() {
        assertTrue(BillStatusUtils.isDraft(0));
        assertTrue(BillStatusUtils.isDraft("草稿"));
        assertTrue(BillStatusUtils.isDraft("未审核"));
        assertTrue(BillStatusUtils.isDraft("驳回"));
        assertTrue(BillStatusUtils.isDraft(null));
        assertTrue(BillStatusUtils.isDraft(""));

        assertFalse(BillStatusUtils.isDraft(1));
        assertFalse(BillStatusUtils.isDraft(2));
        assertFalse(BillStatusUtils.isDraft(7));
        assertFalse(BillStatusUtils.isDraft("已生效"));
    }

    @Test
    @DisplayName("isPending 待审核判断：仅 1 和待审核别名返回 true")
    void isPending_shouldOnlyMatchPendingClass() {
        assertTrue(BillStatusUtils.isPending(1));
        assertTrue(BillStatusUtils.isPending("待审核"));
        assertTrue(BillStatusUtils.isPending("待审批"));
        assertTrue(BillStatusUtils.isPending("审批中"));

        assertFalse(BillStatusUtils.isPending(0));
        assertFalse(BillStatusUtils.isPending(2));
        assertFalse(BillStatusUtils.isPending(7));
    }

    @Test
    @DisplayName("isApprovedClass 已生效类判断：code=2/3/4/5/6 及各种已生效别名")
    void isApprovedClass_shouldMatchAllApprovedSubclasses() {
        assertTrue(BillStatusUtils.isApprovedClass(2));
        assertTrue(BillStatusUtils.isApprovedClass(3));
        assertTrue(BillStatusUtils.isApprovedClass(4));
        assertTrue(BillStatusUtils.isApprovedClass(5));
        assertTrue(BillStatusUtils.isApprovedClass(6));
        assertTrue(BillStatusUtils.isApprovedClass("已生效"));
        assertTrue(BillStatusUtils.isApprovedClass("已下达"));
        assertTrue(BillStatusUtils.isApprovedClass("执行中"));
        assertTrue(BillStatusUtils.isApprovedClass("已完工"));
        assertTrue(BillStatusUtils.isApprovedClass("已关闭"));
        assertTrue(BillStatusUtils.isApprovedClass("未知状态")); // 未知态默认已生效类

        assertFalse(BillStatusUtils.isApprovedClass(0));
        assertFalse(BillStatusUtils.isApprovedClass(1));
        assertFalse(BillStatusUtils.isApprovedClass(7));
    }

    @Test
    @DisplayName("isVoided 作废判断：仅 7 和作废别名返回 true")
    void isVoided_shouldOnlyMatchVoided() {
        assertTrue(BillStatusUtils.isVoided(7));
        assertTrue(BillStatusUtils.isVoided("已作废"));
        assertTrue(BillStatusUtils.isVoided("作废"));

        assertFalse(BillStatusUtils.isVoided(0));
        assertFalse(BillStatusUtils.isVoided(1));
        assertFalse(BillStatusUtils.isVoided(2));
        assertFalse(BillStatusUtils.isVoided("已生效"));
    }

    // ==================== isFinalized 终态判断 ====================

    @Test
    @DisplayName("isFinalized 双终态：已作废 或 已结案(close_status=1) → true")
    void isFinalized_voidedOrClosed_shouldReturnTrue() {
        // 已作废 → 终态（无论 close_status）
        assertTrue(BillStatusUtils.isFinalized("已作废", 0));
        assertTrue(BillStatusUtils.isFinalized(7, 0));
        assertTrue(BillStatusUtils.isFinalized(7, null));

        // 已生效 + 已结案 → 终态
        assertTrue(BillStatusUtils.isFinalized("已生效", "已结案"));
        assertTrue(BillStatusUtils.isFinalized(2, 1));
        assertTrue(BillStatusUtils.isFinalized(6, 1)); // 已结案状态码+close

        // 字符串 closeStatus
        assertTrue(BillStatusUtils.isFinalized(2, "1"));
    }

    @Test
    @DisplayName("isFinalized 非终态：草稿/待审/已生效(未结案) → false")
    void isFinalized_nonFinalState_shouldReturnFalse() {
        assertFalse(BillStatusUtils.isFinalized("草稿", 0));
        assertFalse(BillStatusUtils.isFinalized("待审核", 0));
        assertFalse(BillStatusUtils.isFinalized("已生效", 0));
        assertFalse(BillStatusUtils.isFinalized(2, 0));
        assertFalse(BillStatusUtils.isFinalized(2, null));
        assertFalse(BillStatusUtils.isFinalized(null, null));
    }

    @Test
    @DisplayName("isFinalized closeStatus 兼容多种类型：数字/字符串/null")
    void isFinalized_closeStatusMultiType_shouldHandleGracefully() {
        // Integer / Long / String(数字) 都能识别
        assertTrue(BillStatusUtils.isFinalized(2, Integer.valueOf(1)));
        assertTrue(BillStatusUtils.isFinalized(2, Long.valueOf(1)));
        assertTrue(BillStatusUtils.isFinalized(2, "1"));

        // 非法字符串 → 当作 0(未结案)
        assertFalse(BillStatusUtils.isFinalized(2, "不是数字"));
        assertFalse(BillStatusUtils.isFinalized(2, ""));
    }
}
