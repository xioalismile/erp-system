package com.erp.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.BusinessException;
import com.erp.purchase.entity.PurchaseRequest;
import com.erp.purchase.entity.PurchaseRequestItem;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import com.erp.purchase.mapper.PurchaseRequestItemMapper;
import com.erp.purchase.mapper.PurchaseRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：采购申请单状态机防护（reject/void/unvoid/delete）。
 *
 * Bug 来源（过去24小时提交引入）: PurchaseRequestServiceImpl 独立实现了 4 个终态操作，
 * 但未做任何状态守卫（AbstractBillService基类有统一实现，而本类绕过直接写库）。
 *
 * 高影响缺陷覆盖：
 *   1. deleteBill 无状态守卫 → 已生效单据被直接删除，数据永久丢失且下游来源悬空
 *   2. reject 无状态守卫 → 已生效单据直接驳回到草稿，approved_by 残留，状态不一致
 *   3. voidBill 无状态守卫 → 已结案/已作废/已被下游引用的单据仍能作废
 *   4. unvoidBill 无状态守卫 → 从未作废过的单据可被"反作废"，审计字段被清空
 *
 * 触发门槛：攻击者通过 curl/Postman 直接调用 Controller 暴露的 REST 接口，
 * 绕过前端 BillActions 按钮的 v-if 可见性限制。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("采购申请单 - 状态机守卫缺口回归测试")
class PurchaseRequestStatusGuardTest {

    @Mock private PurchaseRequestMapper requestMapper;
    @Mock private PurchaseRequestItemMapper itemMapper;
    @Mock private PurchaseOrderMapper purchaseOrderMapper;
    @Mock private com.erp.serialno.service.SysSerialNoConfigService sysSerialNoConfigService;
    @Mock private com.erp.basedata.service.EmployeeService employeeService;
    @Mock private com.erp.masterdata.service.MaterialService materialService;
    @Mock private com.erp.basedata.service.UnitOfMeasureService unitOfMeasureService;
    @Mock private com.erp.common.bill.ApprovalGateHelper approvalGateHelper;
    /** 删除单据时级联删子表用；构造注入缺它会 NPE（删除类用例覆盖到） */
    @Mock private com.erp.common.bill.BillCascadeDeleteService billCascadeDeleteService;

    @org.mockito.Spy
    @InjectMocks
    private PurchaseRequestServiceImpl service;

    /**
     * 补注入 Mockito 构造注入覆盖不到的字段。
     *
     * <p>被测算的服务用 @RequiredArgsConstructor（构造注入）。凡是**非 final 字段**
     * （如父类 MyBatis-Plus 的 baseMapper、以及本类里 @Autowired 非 final 的级联删除服务），
     * 都不在构造参数里 → Mockito 走构造注入后不会再做字段注入 → 运行时 NPE。
     * 此处按字段名沿继承链显式补注入。</p>
     */
    @BeforeEach
    void injectFieldsMockitoMissed() throws Exception {
        setFieldIfPresent("baseMapper", requestMapper);
        setFieldIfPresent("billCascadeDeleteService", billCascadeDeleteService);
        // 纯单元测试没有 MyBatis 运行环境，ServiceImpl.removeById 取不到表信息（NPE）。
        // 桩掉它但**转发**到真实 Mapper 调用，这样「删主表」这个动作仍然可被断言验证。
        lenient().doAnswer(inv -> {
            Object id = inv.getArgument(0);
            requestMapper.deleteById((java.io.Serializable) id);
            return true;
        }).when(service).removeById(org.mockito.ArgumentMatchers.<java.io.Serializable>any());
    }

    /** 沿继承链按字段名注入；字段不存在则跳过（说明它已被构造注入，无需处理） */
    private void setFieldIfPresent(String name, Object value) throws Exception {
        Class<?> c = service.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(service, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
    }

    private PurchaseRequest buildRequest(Long id, String status, String requestNo) {
        PurchaseRequest r = new PurchaseRequest();
        r.setRequestId(id);
        r.setRequestNo(requestNo);
        r.setStatus(status);
        r.setIsClosed(0);
        r.setRequestDate(LocalDate.now());
        r.setDemandDate(LocalDate.now().plusDays(7));
        r.setApplyType("普通采购");
        r.setIsUrgent(0);
        r.setTotalQty(BigDecimal.ZERO);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        r.setCreatedBy("tester");
        r.setDepartmentId(3L);
        r.setApplyDeptName("采购部");
        r.setApplicantId(1L);
        r.setApplierName("测试员");
        return r;
    }

    // ==================== 1. deleteBill 守卫 ====================

    @Nested
    @DisplayName("删除操作守卫 (deleteBill)")
    class DeleteGuardTests {

        @Test
        @DisplayName("删除-草稿态+无下游：允许删除")
        void delete_draft_noDownstream_shouldSucceed() {
            PurchaseRequest draft = buildRequest(1L, "草稿", "PR-TEST-001");
            when(requestMapper.selectById(1L)).thenReturn(draft);
            when(purchaseOrderMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

            assertDoesNotThrow(() -> service.deleteBill(1L));
            verify(requestMapper, times(1)).deleteById(eq(1L));
        }

        @Test
        @DisplayName("删除-已驳回：允许删除（DRAFT_CLASS_SET）")
        void delete_rejected_shouldSucceed() {
            PurchaseRequest r = buildRequest(2L, "已驳回", "PR-TEST-002");
            when(requestMapper.selectById(2L)).thenReturn(r);
            when(purchaseOrderMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

            assertDoesNotThrow(() -> service.deleteBill(2L));
            verify(requestMapper, times(1)).deleteById(eq(2L));
        }

        @Test
        @DisplayName("删除-已生效：必须拒绝，避免数据丢失")
        void delete_approved_shouldThrow() {
            PurchaseRequest approved = buildRequest(3L, "已生效", "PR-TEST-003");
            when(requestMapper.selectById(3L)).thenReturn(approved);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteBill(3L));
            assertTrue(ex.getMessage().contains("非草稿不能删除"),
                    "错误信息应明确要求草稿态，实际: " + ex.getMessage());
            verify(requestMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除-老状态别名'已审批'：同样必须拒绝")
        void delete_legacyApproved_shouldThrow() {
            PurchaseRequest r = buildRequest(4L, "已审批", "PR-TEST-004");
            when(requestMapper.selectById(4L)).thenReturn(r);

            assertThrows(BusinessException.class, () -> service.deleteBill(4L));
            verify(requestMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除-待审核：必须拒绝（非草稿类）")
        void delete_pending_shouldThrow() {
            PurchaseRequest pending = buildRequest(5L, "待审核", "PR-TEST-005");
            when(requestMapper.selectById(5L)).thenReturn(pending);

            assertThrows(BusinessException.class, () -> service.deleteBill(5L));
            verify(requestMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除-已作废：必须拒绝（终态）")
        void delete_voided_shouldThrow() {
            PurchaseRequest voided = buildRequest(6L, "已作废", "PR-TEST-006");
            when(requestMapper.selectById(6L)).thenReturn(voided);

            assertThrows(BusinessException.class, () -> service.deleteBill(6L));
            verify(requestMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除-草稿但有下游采购订单引用：必须拒绝，避免下游来源悬空")
        void delete_draft_withDownstream_shouldThrow() {
            PurchaseRequest draft = buildRequest(7L, "草稿", "PR-TEST-007");
            when(requestMapper.selectById(7L)).thenReturn(draft);
            when(purchaseOrderMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteBill(7L));
            assertTrue(ex.getMessage().contains("已被下游采购订单引用"),
                    "错误信息应提示下游引用，实际: " + ex.getMessage());
            verify(requestMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("删除-不存在的ID：幂等返回，不抛异常")
        void delete_notFound_shouldBeIdempotent() {
            when(requestMapper.selectById(9999L)).thenReturn(null);
            assertDoesNotThrow(() -> service.deleteBill(9999L));
            verify(requestMapper, never()).deleteById(anyLong());
        }
    }

    // ==================== 2. reject 守卫 ====================

    @Nested
    @DisplayName("驳回操作守卫 (reject)")
    class RejectGuardTests {

        @Test
        @DisplayName("驳回-待审核：允许驳回")
        void reject_pending_shouldSucceed() {
            PurchaseRequest pending = buildRequest(10L, "待审核", "PR-TEST-010");
            when(requestMapper.selectById(10L)).thenReturn(pending);

            assertDoesNotThrow(() -> service.reject(10L, "tester", "驳回原因"));
        }

        @Test
        @DisplayName("驳回-老别名'待审批'：兼容允许")
        void reject_legacyPending_shouldSucceed() {
            PurchaseRequest r = buildRequest(11L, "待审批", "PR-TEST-011");
            when(requestMapper.selectById(11L)).thenReturn(r);

            assertDoesNotThrow(() -> service.reject(11L, "tester", "原因"));
        }

        @Test
        @DisplayName("驳回-草稿：必须拒绝（应先提交审核）")
        void reject_draft_shouldThrow() {
            PurchaseRequest draft = buildRequest(12L, "草稿", "PR-TEST-012");
            when(requestMapper.selectById(12L)).thenReturn(draft);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.reject(12L, "tester", "原因"));
            assertTrue(ex.getMessage().contains("只有待审核状态可驳回"),
                    "实际信息: " + ex.getMessage());
        }

        @Test
        @DisplayName("驳回-已生效：必须拒绝（应走重置草稿流程）")
        void reject_approved_shouldThrow() {
            PurchaseRequest approved = buildRequest(13L, "已生效", "PR-TEST-013");
            when(requestMapper.selectById(13L)).thenReturn(approved);

            assertThrows(BusinessException.class, () -> service.reject(13L, "tester", "原因"));
        }

        @Test
        @DisplayName("驳回-已作废：必须拒绝（终态）")
        void reject_voided_shouldThrow() {
            PurchaseRequest v = buildRequest(14L, "已作废", "PR-TEST-014");
            when(requestMapper.selectById(14L)).thenReturn(v);

            assertThrows(BusinessException.class, () -> service.reject(14L, "tester", "原因"));
        }
    }

    // ==================== 3. voidBill 守卫 ====================

    @Nested
    @DisplayName("作废操作守卫 (voidBill)")
    class VoidGuardTests {

        @Test
        @DisplayName("作废-草稿+无下游：允许作废")
        void void_draft_noDownstream_shouldSucceed() {
            PurchaseRequest draft = buildRequest(20L, "草稿", "PR-TEST-020");
            when(requestMapper.selectById(20L)).thenReturn(draft);
            when(purchaseOrderMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

            assertDoesNotThrow(() -> service.voidBill(20L, "tester", "作废原因"));
        }

        @Test
        @DisplayName("作废-已作废：必须拒绝（不能重复作废）")
        void void_alreadyVoided_shouldThrow() {
            PurchaseRequest v = buildRequest(21L, "已作废", "PR-TEST-021");
            when(requestMapper.selectById(21L)).thenReturn(v);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.voidBill(21L, "tester", "原因"));
            assertTrue(ex.getMessage().contains("已作废，不能重复作废"),
                    "实际信息: " + ex.getMessage());
        }

        @Test
        @DisplayName("作废-已结案(is_closed=1)：必须拒绝（双终态互斥）")
        void void_alreadyClosed_shouldThrow() {
            PurchaseRequest closed = buildRequest(22L, "已生效", "PR-TEST-022");
            closed.setIsClosed(1);
            when(requestMapper.selectById(22L)).thenReturn(closed);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.voidBill(22L, "tester", "原因"));
            assertTrue(ex.getMessage().contains("已结案"),
                    "实际信息: " + ex.getMessage());
        }

        @Test
        @DisplayName("作废-已生效但有下游采购订单：必须拒绝")
        void void_approved_withDownstream_shouldThrow() {
            PurchaseRequest approved = buildRequest(23L, "已生效", "PR-TEST-023");
            when(requestMapper.selectById(23L)).thenReturn(approved);
            when(purchaseOrderMapper.selectCount(any(QueryWrapper.class))).thenReturn(2L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.voidBill(23L, "tester", "原因"));
            assertTrue(ex.getMessage().contains("已被下游采购订单引用"),
                    "实际信息: " + ex.getMessage());
        }
    }

    // ==================== 4. unvoidBill 守卫 ====================

    @Nested
    @DisplayName("反作废操作守卫 (unvoidBill)")
    class UnvoidGuardTests {

        @Test
        @DisplayName("反作废-已作废+记录了原状态=2(已生效)：恢复到已生效")
        void unvoid_voided_approved_shouldRestoreToApproved() {
            PurchaseRequest v = buildRequest(30L, "已作废", "PR-TEST-030");
            v.setBillBeforeStatus(2);
            when(requestMapper.selectById(30L)).thenReturn(v);

            assertDoesNotThrow(() -> service.unvoidBill(30L, "tester"));
        }

        @Test
        @DisplayName("反作废-从未作废过(草稿态)：必须拒绝")
        void unvoid_neverVoided_draft_shouldThrow() {
            PurchaseRequest draft = buildRequest(31L, "草稿", "PR-TEST-031");
            when(requestMapper.selectById(31L)).thenReturn(draft);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.unvoidBill(31L, "tester"));
            assertTrue(ex.getMessage().contains("只有已作废状态可反作废"),
                    "实际信息: " + ex.getMessage());
        }

        @Test
        @DisplayName("反作废-待审核态：必须拒绝（从未作废过）")
        void unvoid_pending_shouldThrow() {
            PurchaseRequest p = buildRequest(32L, "待审核", "PR-TEST-032");
            when(requestMapper.selectById(32L)).thenReturn(p);

            assertThrows(BusinessException.class, () -> service.unvoidBill(32L, "tester"));
        }

        @Test
        @DisplayName("反作废-已生效态：必须拒绝（从未作废过）")
        void unvoid_approved_shouldThrow() {
            PurchaseRequest a = buildRequest(33L, "已生效", "PR-TEST-033");
            when(requestMapper.selectById(33L)).thenReturn(a);

            assertThrows(BusinessException.class, () -> service.unvoidBill(33L, "tester"));
        }
    }
}
