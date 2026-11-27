package com.erp.common.bill;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.config.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 删除入口守卫回归测试（纯单元测试，不连数据库）。
 *
 * <p>缺陷来源（2026-11-16）：删除已生效单据新增「先走 onReverseApproved 冲回」后，
 * 单据在「重置草稿 / 作废」里的前置守卫（已生成发票、已付款应付单等）没有在删除入口生效，
 * 删除会绕过守卫并把仍在被引用的衍生单据一并删掉。修复口径：基类在删除前调用
 * {@link AbstractBillService#validateBeforeDelete(Object)}，各单据复用既有守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("单据删除 - 前置守卫必须先于库存冲回执行")
class AbstractBillDeleteGuardTest {

    @Mock
    private BaseMapper<FakeBill> baseMapper;

    @Mock
    private BillCascadeDeleteService billCascadeDeleteService;

    private FakeBillService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        service = org.mockito.Mockito.spy(new FakeBillService());
        // MyBatis-Plus ServiceImpl.baseMapper / AbstractBillService.billCascadeDeleteService 均为 protected 字段
        setField(service, "baseMapper", baseMapper);
        setField(service, "billCascadeDeleteService", billCascadeDeleteService);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            }
        }
        throw new IllegalStateException("字段不存在: " + name);
    }

    @Test
    @DisplayName("已生效单据删除：守卫先执行，守卫通过后才冲回库存")
    void deleteBill_runsGuardBeforeReversal() {
        FakeBill bill = new FakeBill(1L, "\u5DF2\u751F\u6548");
        when(baseMapper.selectById(anyLong())).thenReturn(bill);
        when(billCascadeDeleteService.deleteChildrenByEntity(any(), any())).thenReturn(0);
        org.mockito.Mockito.doReturn(true).when(service).removeById((java.io.Serializable) any());

        service.deleteBill(1L);

        assertTrue(service.guardCalled, "删除前必须先执行 validateBeforeDelete");
        assertTrue(service.reversalCalled, "守卫通过后应继续库存冲回");
        assertEquals("guard,reverse", service.callOrder.toString(), "守卫必须早于库存冲回");
    }

    @Test
    @DisplayName("守卫拦截时删除立即中止，不得发生库存冲回")
    void deleteBill_guardRejects_noReversal() {
        FakeBill bill = new FakeBill(2L, "\u5DF2\u751F\u6548");
        when(baseMapper.selectById(anyLong())).thenReturn(bill);
        service.guardShouldReject = true;

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteBill(2L));

        assertEquals("\u5DF2\u751F\u6210\u4E0B\u6E38\u5355\u636E\uFF0C\u4E0D\u53EF\u5220\u9664", ex.getMessage());
        assertTrue(service.guardCalled);
        assertFalse(service.reversalCalled, "守卫拦截后不得再冲回库存");
    }

    // ================= 测试替身 =================

    static class FakeBill {
        private Long id;
        private String status;

        FakeBill(Long id, String status) {
            this.id = id;
            this.status = status;
        }

        Long getId() { return id; }
        String getStatus() { return status; }
    }

    static class FakeBillService extends AbstractBillService<BaseMapper<FakeBill>, FakeBill> {

        boolean guardCalled = false;
        boolean reversalCalled = false;
        boolean guardShouldReject = false;
        final StringBuilder callOrder = new StringBuilder();

        @Override
        protected void validateBeforeDelete(FakeBill bill) {
            guardCalled = true;
            callOrder.append("guard");
            if (guardShouldReject) {
                throw new BusinessException("\u5DF2\u751F\u6210\u4E0B\u6E38\u5355\u636E\uFF0C\u4E0D\u53EF\u5220\u9664");
            }
        }

        @Override
        public void onReverseApproved(FakeBill bill) {
            reversalCalled = true;
            callOrder.append(callOrder.length() == 0 ? "reverse" : ",reverse");
        }

        @Override
        protected String getBillNo(FakeBill bill) { return "FAKE-" + bill.getId(); }

        @Override
        protected Object getId(FakeBill bill) { return bill.getId(); }

        @Override
        protected String getStatus(FakeBill bill) { return bill.getStatus(); }

        @Override
        protected void setStatus(FakeBill bill, String statusLabel) { bill.status = statusLabel; }

        @Override
        protected Integer getCloseStatus(FakeBill bill) { return 0; }

        @Override
        protected void setCloseStatus(FakeBill bill, Integer closeStatus) { }

        @Override
        protected void setApprovedBy(FakeBill bill, String approver) { }

        @Override
        protected void setApprovedAt(FakeBill bill, LocalDateTime approvedAt) { }

        @Override
        protected void setApprovalStatus(FakeBill bill, String approvalStatusLabel) { }

        @Override
        protected void setClosedBy(FakeBill bill, String user) { }

        @Override
        protected void setClosedAt(FakeBill bill, LocalDateTime t) { }

        @Override
        protected void setClosedReason(FakeBill bill, String reason) { }

        @Override
        protected void setBillBeforeStatus(FakeBill bill, Integer numericStatus) { }

        @Override
        protected Integer getBillBeforeStatus(FakeBill bill) { return null; }

        @Override
        protected void setVoidedBy(FakeBill bill, String user) { }

        @Override
        protected void setVoidedAt(FakeBill bill, LocalDateTime t) { }

        @Override
        protected void setVoidReason(FakeBill bill, String reason) { }

        @Override
        protected void validateStrictSubmit(FakeBill bill) { }

        @Override
        protected String getBillTypeLabel() { return "\u6D4B\u8BD5\u5355\u636E"; }
    }
}
