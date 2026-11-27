package com.erp.purchase.service.impl;

import com.erp.common.bill.ApprovalGateHelper;
import com.erp.config.BusinessException;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.purchase.entity.PurchaseContract;
import com.erp.purchase.mapper.PurchaseContractItemMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：采购合同删除——状态守卫 + 明细级联删除。
 *
 * Bug 来源：
 *   ①Controller DELETE 直接 removeById（绕过状态守卫，已生效合同也能被删）；
 *   ②deleteBill 只删主表，purchase_contract_item 残留孤儿明细。
 * 守卫口径与前端 canDelete 一致（草稿族：草稿/已驳回/已取消），其余状态走作废/重置草稿。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("采购合同 - 删除守卫与明细级联删除")
class PurchaseContractDeleteGuardTest {

    @Mock private PurchaseContractItemMapper itemMapper;
    @Mock private SupplierService supplierService;
    @Mock private EmployeeService employeeService;
    @Mock private CurrencyService currencyService;
    @Mock private WarehouseService warehouseService;
    @Mock private MaterialService materialService;
    @Mock private UnitOfMeasureService unitOfMeasureService;
    @Mock private ApprovalGateHelper approvalGateHelper;
    @Mock private SysSerialNoConfigService sysSerialNoConfigService;
    @Mock private com.erp.common.bill.BillApprovalAdapter billApprovalAdapter;
    /** 2026-09-15 补：PurchaseContractServiceImpl 新增了 SysParamValidator 构造参数，原测试未同步 → 测试编译全挂 */
    @Mock private com.erp.config.SysParamValidator sysParamValidator;
    /** 2026-10-09 补：基类 2026-09-19 加了"整单删除"的级联清理依赖，原测试未提供桩 → 草稿族删除用例 NPE */
    @Mock private com.erp.common.bill.BillCascadeDeleteService billCascadeDeleteService;

    private PurchaseContractServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        PurchaseContractServiceImpl real = new PurchaseContractServiceImpl(itemMapper, supplierService, employeeService, currencyService,
                warehouseService, materialService, unitOfMeasureService, sysSerialNoConfigService, sysParamValidator);
        // 级联清理依赖已上移到统一状态机基类（@Autowired 非构造参数）：按父类反射注入，再 spy
        org.springframework.test.util.ReflectionTestUtils.setField(real, "billCascadeDeleteService", billCascadeDeleteService);
        service = spy(real);
        // 继承自 ServiceImpl 的删除在单测中不落库（异常分支用不到，故 lenient）
        // 注意 removeById 在 IService(T) 与 ServiceImpl(Serializable) 之间有重载，需显式转型
        lenient().doReturn(true).when(service).removeById((java.io.Serializable) any());
    }

    private PurchaseContract contract(Long id, String status) {
        PurchaseContract c = new PurchaseContract();
        c.setContractId(id);
        c.setContractNo("PCON-TEST-0001");
        c.setStatus(status);
        return c;
    }

    @Nested
    @DisplayName("状态守卫")
    class StatusGuardTests {

        @Test
        @DisplayName("已生效：必须拒绝删除（原实现可直接删除，数据永久丢失）")
        void delete_approved_shouldThrow() {
            doReturn(contract(1L, "已生效")).when(service).getById(1L);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteBill(1L));

            assertTrue(ex.getMessage().contains("非草稿不能删除"), "错误信息应要求草稿态，实际: " + ex.getMessage());
            verify(service, never()).removeById((java.io.Serializable) any());
            verifyNoInteractions(itemMapper);
        }

        @Test
        @DisplayName("已作废：必须拒绝删除（终态走反作废）")
        void delete_voided_shouldThrow() {
            doReturn(contract(2L, "已作废")).when(service).getById(2L);

            assertThrows(BusinessException.class, () -> service.deleteBill(2L));
            verify(service, never()).removeById((java.io.Serializable) any());
            verifyNoInteractions(itemMapper);
        }

        @Test
        @DisplayName("待审核：必须拒绝删除")
        void delete_pending_shouldThrow() {
            doReturn(contract(3L, "待审核")).when(service).getById(3L);

            assertThrows(BusinessException.class, () -> service.deleteBill(3L));
            verify(service, never()).removeById((java.io.Serializable) any());
        }
    }

    @Nested
    @DisplayName("允许删除（草稿族）")
    class AllowedTests {

        @Test
        @DisplayName("草稿：明细与主表一起删除（原实现留下孤儿明细）")
        void delete_draft_shouldRemoveItemsAndHeader() {
            doReturn(contract(10L, "草稿")).when(service).getById(10L);

            assertDoesNotThrow(() -> service.deleteBill(10L));

            verify(itemMapper, times(1)).delete(any());
            verify(service, times(1)).removeById(10L);
        }

        @Test
        @DisplayName("已取消：允许删除（与前端 canDelete 口径一致）")
        void delete_canceled_shouldSucceed() {
            doReturn(contract(11L, "已取消")).when(service).getById(11L);

            assertDoesNotThrow(() -> service.deleteBill(11L));

            verify(itemMapper, times(1)).delete(any());
            verify(service, times(1)).removeById(11L);
        }

        @Test
        @DisplayName("已驳回：允许删除")
        void delete_rejected_shouldSucceed() {
            doReturn(contract(12L, "已驳回")).when(service).getById(12L);

            assertDoesNotThrow(() -> service.deleteBill(12L));

            verify(itemMapper, times(1)).delete(any());
            verify(service, times(1)).removeById(12L);
        }

        @Test
        @DisplayName("不存在的ID：幂等返回，不抛异常、不删任何数据")
        void delete_notFound_shouldBeIdempotent() {
            doReturn(null).when(service).getById(9999L);

            assertDoesNotThrow(() -> service.deleteBill(9999L));

            verify(service, never()).removeById(anyLong());
            verifyNoInteractions(itemMapper);
        }
    }
}
