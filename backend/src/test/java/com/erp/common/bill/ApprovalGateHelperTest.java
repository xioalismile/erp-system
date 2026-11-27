package com.erp.common.bill;

import com.erp.config.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：ApprovalGateHelper 是单据提交时判断是否走人工审批的统一网关。
 * 覆盖风险：
 *   1. billType 为 null/空/空白 → 一律免审批（false），不抛错（未接入开关的单据安全兜底）
 *   2. 全局开关 approval.enabled = false → 所有单据一律免审批（忽略单据级开关）
 *   3. 全局开关 on + 单据级开关 on → 需要审批（true）
 *   4. 全局开关 on + 单据级开关 off/不存在(默认false) → 免审批
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalGateHelper 审批开关网关测试")
class ApprovalGateHelperTest {

    @Mock
    private SysConfigService sysConfigService;

    private ApprovalGateHelper gateHelper;

    @BeforeEach
    void setUp() {
        gateHelper = new ApprovalGateHelper(sysConfigService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("billType 为空/空白 → 一律免审批（未接入开关的单据兜底）")
    void needApproval_blankBillType_alwaysFalse(String billType) {
        assertFalse(gateHelper.needApproval(billType),
                "billType=[" + billType + "] 应为免审批");
        // billType 空 → 不应调用 sysConfigService（避免空 key 查询）
        verify(sysConfigService, never()).getBooleanValue(contains("approval.bill"), anyBoolean());
    }

    @Test
    @DisplayName("全局开关 approval.enabled=false → 所有单据免审批（单据级开关被短路忽略）")
    void needApproval_globalDisabled_alwaysFalse() {
        when(sysConfigService.getBooleanValue("approval.enabled", true)).thenReturn(false);

        // 即使单据级开关开启，也应返回 false
        lenient().when(sysConfigService.getBooleanValue("approval.bill.SALE_ORDER.enabled", false))
                .thenReturn(true);

        assertFalse(gateHelper.needApproval("SALE_ORDER"));
        assertFalse(gateHelper.needApproval("PURCHASE_REQUEST"));
    }

    @Test
    @DisplayName("全局开 + 单据级开 → 需要审批（true）")
    void needApproval_bothEnabled_shouldReturnTrue() {
        when(sysConfigService.getBooleanValue("approval.enabled", true)).thenReturn(true);

        // SALE_ORDER 开审批
        when(sysConfigService.getBooleanValue("approval.bill.SALE_ORDER.enabled", false))
                .thenReturn(true);
        assertTrue(gateHelper.needApproval("SALE_ORDER"));

        // PURCHASE_REQUEST 也开审批
        when(sysConfigService.getBooleanValue("approval.bill.PURCHASE_REQUEST.enabled", false))
                .thenReturn(true);
        assertTrue(gateHelper.needApproval("PURCHASE_REQUEST"));
    }

    @Test
    @DisplayName("全局开 + 单据级关（默认false）→ 免审批（提交即生效）")
    void needApproval_globalOnButBillLevelOff_shouldReturnFalse() {
        when(sysConfigService.getBooleanValue("approval.enabled", true)).thenReturn(true);
        // 未单独配置 → 默认为 false
        when(sysConfigService.getBooleanValue("approval.bill.SALE_QUOTE.enabled", false))
                .thenReturn(false);

        assertFalse(gateHelper.needApproval("SALE_QUOTE"));
    }

    @Test
    @DisplayName("全局开 + 不同单据独立配置：A开B关 → 互不干扰")
    void needApproval_perBillIndependence_shouldRespectEach() {
        when(sysConfigService.getBooleanValue("approval.enabled", true)).thenReturn(true);
        when(sysConfigService.getBooleanValue("approval.bill.SALE_ORDER.enabled", false))
                .thenReturn(true);
        when(sysConfigService.getBooleanValue("approval.bill.SALE_DELIVERY.enabled", false))
                .thenReturn(false);

        assertTrue(gateHelper.needApproval("SALE_ORDER"));
        assertFalse(gateHelper.needApproval("SALE_DELIVERY"));
    }
}
