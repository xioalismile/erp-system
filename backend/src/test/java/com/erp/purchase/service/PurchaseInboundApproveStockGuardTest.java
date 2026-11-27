package com.erp.purchase.service;

import com.erp.common.bill.ApprovalGateHelper;
import com.erp.config.BusinessException;
import com.erp.config.SysParamValidator;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.masterdata.service.MaterialService;
import com.erp.masterdata.service.SupplierService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInboundItem;
import com.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.erp.purchase.mapper.PurchaseInvoiceMapper;
import com.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：采购入库单审核——缺仓库/缺物料不得静默跳过。
 *
 * Bug 来源：PurchaseInboundServiceImpl.approve 中 `if (stockId != null && materialId != null)` 无 else，
 * 缺仓库/物料的明细被静默跳过（不入库、不报错），但下面照旧回写采购订单"已入库数量"与勾稽数量
 * → 单据变"已生效"、库存却没增加（账实不符）。销售出库单/生产入库单同位置已改为显式报错。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("采购入库单 - 缺仓库/物料不得静默跳过")
class PurchaseInboundApproveStockGuardTest {

    @Mock private PurchaseInboundItemMapper itemMapper;
    @Mock private PurchaseOrderMapper purchaseOrderMapper;
    @Mock private PurchaseOrderItemMapper purchaseOrderItemMapper;
    @Mock private PurchaseInvoiceMapper purchaseInvoiceMapper;
    @Mock private com.erp.purchase.mapper.PurchaseReturnMapper purchaseReturnMapper;
    @Mock private com.erp.purchase.mapper.PurchaseReturnItemMapper purchaseReturnItemMapper;
    @Mock private InventoryService inventoryService;
    @Mock private PurchaseOrderService purchaseOrderService;
    @Mock private MaterialService materialService;
    @Mock private com.erp.inventory.service.BatchNoRuleService batchNoRuleService;
    @Mock private com.erp.qc.service.InboundQcService inboundQcService;
    @Mock private PurchaseReturnService purchaseReturnService;
    @Mock private MaterialCostService materialCostService;
    @Mock private SysParamValidator sysParamValidator;
    @Mock private SysSerialNoConfigService sysSerialNoConfigService;
    @Mock private SupplierService supplierService;
    @Mock private EmployeeService employeeService;
    @Mock private WarehouseService warehouseService;
    @Mock private UnitOfMeasureService unitOfMeasureService;
    @Mock private ApprovalGateHelper approvalGateHelper;
    @Mock private PurchaseInvoiceService purchaseInvoiceService;
    @Mock private com.erp.common.bill.BillApprovalAdapter billApprovalAdapter;
    @Mock private com.erp.approval.service.ApprovalRuntimeService approvalRuntimeService;

    private PurchaseInboundServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new PurchaseInboundServiceImpl(itemMapper, purchaseOrderMapper, purchaseOrderItemMapper,
                purchaseInvoiceMapper, purchaseReturnMapper, purchaseReturnItemMapper, inventoryService, purchaseOrderService,
                materialService, batchNoRuleService, inboundQcService, purchaseReturnService, materialCostService,
                sysParamValidator, sysSerialNoConfigService, supplierService, employeeService, warehouseService,
                unitOfMeasureService, purchaseInvoiceService));
        // 继承自 ServiceImpl 的写库方法在单测中不落库（异常分支用不到，故 lenient）
        lenient().doReturn(true).when(service).updateById(any());
        // approve() 落库走的是 IService.update(entity, updateWrapper) 双参重载（不是 updateById）
        lenient().doReturn(true).when(service).update(any(), any());
        // approve() 的「有在途审批任务时禁止绕过引擎直审」防护读的是 @Lazy @Autowired 字段（构造器注入不到）
        // → 单测显式注入；返回 null = 无在途审批任务，走原有校验分支
        ReflectionTestUtils.setField(service, "approvalRuntimeService", approvalRuntimeService);
        lenient().when(approvalRuntimeService.getRunningTask(any(), any())).thenReturn(null);
    }

    private PurchaseInbound inbound(String receiptNo, Long stockId) {
        PurchaseInbound in = new PurchaseInbound();
        in.setReceiptId(1L);
        in.setReceiptNo(receiptNo);
        in.setStatus("待审核");
        in.setStockId(stockId);
        return in;
    }

    private PurchaseInboundItem item(Long materialId, String qty, String price) {
        PurchaseInboundItem it = new PurchaseInboundItem();
        it.setReceiptId(1L);
        it.setLineNo(1);
        it.setMaterialId(materialId);
        it.setQuantity(new BigDecimal(qty));
        it.setUnitPrice(new BigDecimal(price));
        return it;
    }

    @Nested
    @DisplayName("缺仓库 / 缺物料")
    class MissingRefTests {

        @Test
        @DisplayName("明细行未选择仓库：必须报错，且不加库存、不回写订单已入库量")
        void approve_noWarehouse_shouldThrowAndSkipWriteback() {
            doReturn(inbound("PI-TEST-0001", null)).when(service).getById(1L);
            when(itemMapper.selectList(any())).thenReturn(List.of(item(5L, "10", "2")));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L, "tester"));

            assertTrue(ex.getMessage().contains("未选择仓库"), "错误信息应提示未选择仓库，实际: " + ex.getMessage());
            verify(inventoryService, never()).addStock(any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any());
            verify(purchaseOrderService, never()).updateTurnedQuantity(any(), any(), any(), any());
        }

        @Test
        @DisplayName("明细缺物料：必须报错，且不加库存")
        void approve_noMaterial_shouldThrow() {
            doReturn(inbound("PI-TEST-0002", 3L)).when(service).getById(1L);
            when(itemMapper.selectList(any())).thenReturn(List.of(item(null, "10", "2")));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L, "tester"));

            assertTrue(ex.getMessage().contains("缺少物料"), "错误信息应提示缺少物料，实际: " + ex.getMessage());
            verify(inventoryService, never()).addStock(any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("正常路径（回归保护）")
    class HappyPathTests {

        @Test
        @DisplayName("仓库与物料齐全：按 PURCHASE_IN 增加库存")
        void approve_complete_shouldAddStock() {
            doReturn(inbound("PI-TEST-0003", 3L)).when(service).getById(1L);
            PurchaseInboundItem it = item(5L, "10", "2");
            it.setStockId(3L);
            when(itemMapper.selectList(any())).thenReturn(List.of(it));

            service.approve(1L, "tester");

            verify(inventoryService, times(1)).addStock(
                    eq(3L), isNull(), eq(5L), isNull(),
                    eq(new BigDecimal("10")), eq(new BigDecimal("2")),
                    eq("PI-TEST-0003"), eq("PURCHASE_IN"),
                    eq("PurchaseInbound"), eq("PI-TEST-0003"), eq("tester"));
        }

        @Test
        @DisplayName("已生效状态：不允许重复审核")
        void approve_approved_shouldThrow() {
            PurchaseInbound in = inbound("PI-TEST-0004", 3L);
            in.setStatus("已生效");
            doReturn(in).when(service).getById(1L);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L, "tester"));

            assertTrue(ex.getMessage().contains("只有待审核状态可审核"), "实际: " + ex.getMessage());
            verifyNoInteractions(inventoryService);
        }
    }
}
