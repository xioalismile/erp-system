package com.erp.common.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缺口覆盖：BillStatusEnum / CloseStatusEnum 是状态机的基础枚举。
 * 覆盖风险：
 *   1. fromCode: code=3/4/5/6 生产扩展码降级为 APPROVED；未知码抛 IllegalArgumentException
 *   2. fromLabel: 各种中文别名兼容映射；null/空 → DRAFT；未知标签 → APPROVED（保守降级）
 *   3. isDraftClass / isPendingClass / isApprovedClass / isVoidedClass 分类方法边界
 *   4. CloseStatusEnum: fromCode(null/0 → OPEN；1 → CLOSED)；isClosed 语义
 */
@DisplayName("单据状态枚举测试")
class BillStatusEnumsTest {

    // ==================== BillStatusEnum.fromCode ====================

    @ParameterizedTest(name = "fromCode 标准映射: code={0} → {1}")
    @CsvSource({
            "0, DRAFT",
            "1, PENDING",
            "2, APPROVED",
            "7, VOIDED"
    })
    @DisplayName("BillStatusEnum.fromCode：标准状态码 → 正确枚举")
    void fromCode_standardCodes_shouldReturnCorrectEnum(int code, BillStatusEnum expected) {
        assertEquals(expected, BillStatusEnum.fromCode(code));
    }

    @ParameterizedTest(name = "fromCode 生产扩展码降级: code={0} → APPROVED")
    @ValueSource(ints = {3, 4, 5, 6})
    @DisplayName("BillStatusEnum.fromCode：3/4/5/6(生产内部扩展) → APPROVED 统一语义")
    void fromCode_productionExtendedCodes_shouldFallbackToApproved(int code) {
        assertEquals(BillStatusEnum.APPROVED, BillStatusEnum.fromCode(code));
    }

    @ParameterizedTest(name = "fromCode 未知码应抛异常: code={0}")
    @ValueSource(ints = {-1, 8, 99, 1000})
    @DisplayName("BillStatusEnum.fromCode：未知code(<0 或 >7 且非3-6) → IllegalArgumentException")
    void fromCode_unknownCodes_shouldThrow(int code) {
        assertThrows(IllegalArgumentException.class, () -> BillStatusEnum.fromCode(code));
    }

    // ==================== BillStatusEnum.fromLabel ====================

    @ParameterizedTest(name = "fromLabel 标准标签: label={0} → {1}")
    @CsvSource({
            "草稿, DRAFT",
            "待审核, PENDING",
            "已生效, APPROVED",
            "已作废, VOIDED",
            "已驳回, REJECTED",  // 同 code=0
            "已确认, CONFIRMED",  // 同 code=2
            "已下达, ISSUED",     // 同 code=2
            "执行中, IN_PROGRESS", // 同 code=2
            "已完工, COMPLETED",  // 同 code=2
            "已关闭, CLOSED"      // 同 code=2
    })
    @DisplayName("BillStatusEnum.fromLabel：标准中文标签 → 对应枚举")
    void fromLabel_standardLabels_shouldReturnCorrectEnum(String label, BillStatusEnum expected) {
        assertEquals(expected, BillStatusEnum.fromLabel(label));
    }

    @ParameterizedTest(name = "fromLabel 兼容别名: alias={0} → {1}")
    @CsvSource({
            "未审核, DRAFT",
            "驳回, DRAFT",
            "待审批, PENDING",
            "审批中, PENDING",
            "审批通过, APPROVED",
            "作废, VOIDED",
            "已废弃, VOIDED"
    })
    @DisplayName("BillStatusEnum.fromLabel：兼容历史别名 → 正确枚举")
    void fromLabel_compatAliases_shouldMapCorrectly(String alias, BillStatusEnum expected) {
        assertEquals(expected, BillStatusEnum.fromLabel(alias));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("BillStatusEnum.fromLabel：null/空 → DRAFT（安全默认）")
    void fromLabel_nullEmpty_shouldReturnDraft(String input) {
        assertEquals(BillStatusEnum.DRAFT, BillStatusEnum.fromLabel(input));
    }

    @Test
    @DisplayName("BillStatusEnum.fromLabel：未知标签 → APPROVED（保守降级，避免误判为草稿可编辑）")
    void fromLabel_unknownLabel_shouldFallbackToApproved() {
        assertEquals(BillStatusEnum.APPROVED, BillStatusEnum.fromLabel("部分执行"));
        assertEquals(BillStatusEnum.APPROVED, BillStatusEnum.fromLabel("UNKNOWN_STATE"));
        assertEquals(BillStatusEnum.APPROVED, BillStatusEnum.fromLabel("其他"));
    }

    // ==================== BillStatusEnum 分类静态方法 ====================

    @Test
    @DisplayName("BillStatusEnum.isDraftClass：仅 code=0 返回 true")
    void isDraftClass_onlyZero_shouldReturnTrue() {
        assertTrue(BillStatusEnum.isDraftClass(0));
        assertFalse(BillStatusEnum.isDraftClass(1));
        assertFalse(BillStatusEnum.isDraftClass(2));
        assertFalse(BillStatusEnum.isDraftClass(3));
        assertFalse(BillStatusEnum.isDraftClass(7));
    }

    @Test
    @DisplayName("BillStatusEnum.isPendingClass：仅 code=1 返回 true")
    void isPendingClass_onlyOne_shouldReturnTrue() {
        assertTrue(BillStatusEnum.isPendingClass(1));
        assertFalse(BillStatusEnum.isPendingClass(0));
        assertFalse(BillStatusEnum.isPendingClass(2));
    }

    @Test
    @DisplayName("BillStatusEnum.isApprovedClass：code=2/3/4/5/6 返回 true（含生产扩展态）")
    void isApprovedClass_approvedAndExtendedCodes_shouldReturnTrue() {
        assertTrue(BillStatusEnum.isApprovedClass(2));
        assertTrue(BillStatusEnum.isApprovedClass(3));
        assertTrue(BillStatusEnum.isApprovedClass(4));
        assertTrue(BillStatusEnum.isApprovedClass(5));
        assertTrue(BillStatusEnum.isApprovedClass(6));

        assertFalse(BillStatusEnum.isApprovedClass(0));
        assertFalse(BillStatusEnum.isApprovedClass(1));
        assertFalse(BillStatusEnum.isApprovedClass(7));
    }

    @Test
    @DisplayName("BillStatusEnum.isVoidedClass：仅 code=7 返回 true")
    void isVoidedClass_onlySeven_shouldReturnTrue() {
        assertTrue(BillStatusEnum.isVoidedClass(7));
        assertFalse(BillStatusEnum.isVoidedClass(0));
        assertFalse(BillStatusEnum.isVoidedClass(2));
    }

    // ==================== BillStatusEnum getCode/getLabel 基本属性 ====================

    @Test
    @DisplayName("BillStatusEnum：code 和 label 基本值正确")
    void enumProperties_codeAndLabel_shouldBeConsistent() {
        assertEquals(0, BillStatusEnum.DRAFT.getCode());
        assertEquals("草稿", BillStatusEnum.DRAFT.getLabel());

        assertEquals(1, BillStatusEnum.PENDING.getCode());
        assertEquals("待审核", BillStatusEnum.PENDING.getLabel());

        assertEquals(2, BillStatusEnum.APPROVED.getCode());
        assertEquals("已生效", BillStatusEnum.APPROVED.getLabel());

        assertEquals(7, BillStatusEnum.VOIDED.getCode());
        assertEquals("已作废", BillStatusEnum.VOIDED.getLabel());
    }

    // ==================== CloseStatusEnum ====================

    @ParameterizedTest(name = "CloseStatusEnum.fromCode: {0} → {1}")
    @CsvSource({
            ", OPEN",        // null → OPEN
            "0, OPEN",
            "1, CLOSED",
            "99, OPEN",      // 其他数字视为未结案
            "-1, OPEN"
    })
    @DisplayName("CloseStatusEnum.fromCode：null/0/其他→OPEN；1→CLOSED")
    void closeStatusEnum_fromCode_variousInputs(Integer code, CloseStatusEnum expected) {
        assertEquals(expected, CloseStatusEnum.fromCode(code));
    }

    @Test
    @DisplayName("CloseStatusEnum.isClosed：仅 1 返回 true")
    void closeStatusEnum_isClosed_onlyOne_shouldReturnTrue() {
        assertTrue(CloseStatusEnum.isClosed(1));
        assertFalse(CloseStatusEnum.isClosed(0));
        assertFalse(CloseStatusEnum.isClosed(null));
        assertFalse(CloseStatusEnum.isClosed(2));
    }

    @Test
    @DisplayName("CloseStatusEnum：code/label 属性正确")
    void closeStatusEnum_properties_shouldBeConsistent() {
        assertEquals(0, CloseStatusEnum.OPEN.getCode());
        assertEquals("未结案", CloseStatusEnum.OPEN.getLabel());

        assertEquals(1, CloseStatusEnum.CLOSED.getCode());
        assertEquals("已结案", CloseStatusEnum.CLOSED.getLabel());
    }
}
