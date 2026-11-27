package com.erp.production.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.erp.common.bill.ApprovalGateHelper;
import com.erp.config.BusinessException;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.MaterialCostService;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderProduct;
import com.erp.production.mapper.ProductionReceiptItemMapper;
import com.erp.production.mapper.ProductionReceiptMapper;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderMaterialMapper;
import com.erp.production.mapper.WorkOrderProductMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：生产入库单重置草稿（reverseApprove）必须回冲库存与工单完工数量。
 *
 * Bug 来源：ProductionReceiptServiceImpl.reverseApprove 原实现只把状态改回草稿，
 * 既不扣回审核时增加的库存，也不回退工单 completed_quantity / 成品明细完成数量：
 *   1) 草稿单据也能被"重置草稿"（从未入库却被置回草稿）；
 *   2) 已生效单据重置草稿后再审核 → 库存与完工数量重复累加（数据虚增、账实不符）。
 * 同模块其它单据（库存盘点单/其他入库单/销售出库单）重置草稿均会回冲，本单据属遗漏。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("生产入库单 - 重置草稿回冲缺口回归测试")
class ProductionReceiptReverseApproveTest {

    @Mock private ProductionReceiptMapper receiptMapper;
    @Mock private ProductionReceiptItemMapper itemMapper;
    @Mock private InventoryService inventoryService;
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private WorkOrderProductMapper workOrderProductMapper;
    @Mock private WorkOrderMaterialMapper workOrderMaterialMapper;
    @Mock private MaterialCostService materialCostService;
    @Mock private com.erp.inventory.service.BatchNoRuleService batchNoRuleService;
    @Mock private com.erp.qc.service.InboundQcService inboundQcService;
    @Mock private SysSerialNoConfigService sysSerialNoConfigService;
    @Mock private ApprovalGateHelper approvalGateHelper;
    @Mock private com.erp.common.bill.BillApprovalAdapter billApprovalAdapter;
    @Mock private com.erp.approval.service.ApprovalRuntimeService approvalRuntimeService;
    @Mock private com.erp.common.bill.BillRelationService billRelationService;
    /** 【2026-11-06 化妆品批记录】批次管理物料入库批号必填校验用 */
    @Mock private com.erp.masterdata.mapper.MaterialMapper materialMapper;

    private ProductionReceiptServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = spy(new ProductionReceiptServiceImpl(itemMapper, inventoryService, workOrderMapper,
                workOrderProductMapper, workOrderMaterialMapper, materialCostService, batchNoRuleService, inboundQcService,
                sysSerialNoConfigService, materialMapper));
        // 继承自 ServiceImpl 的写库方法在单测中不落库（部分用例走异常分支不会用到，故 lenient）
        lenient().doReturn(true).when(service).update(any(Wrapper.class));
        // 审批引擎防护字段为 @Lazy @Autowired（构造器注入不到）→ 单测显式注入；
        // 返回 null = 无在途审批任务，走原有重置草稿分支
        ReflectionTestUtils.setField(service, "approvalRuntimeService", approvalRuntimeService);
        lenient().when(approvalRuntimeService.getRunningTask(any(), any())).thenReturn(null);
        // 下游引用校验字段为 @Autowired → 单测显式注入；空列表 = 无已生效下游
        ReflectionTestUtils.setField(service, "billRelationService", billRelationService);
        lenient().when(billRelationService.listApprovedDownstream(any(), any())).thenReturn(java.util.List.of());
    }

    private ProductionReceipt receipt(Long id, String status) {
        ProductionReceipt r = new ProductionReceipt();
        r.setReceiptId(id);
        r.setReceiptNo("MOIN-TEST-0001");
        r.setStatus(status);
        r.setWarehouseId(3L);
        return r;
    }

    private ProductionReceiptItem item(int lineNo, BigDecimal qty, BigDecimal qualified) {
        ProductionReceiptItem it = new ProductionReceiptItem();
        it.setReceiptId(1L);
        it.setLineNo(lineNo);
        it.setMaterialId(5L);
        it.setStockId(3L);
        it.setQuantity(qty);
        it.setQualifiedQuantity(qualified);
        it.setWorkOrderLineNo(lineNo);
        return it;
    }

    @Test
    @DisplayName("重置草稿-非已生效状态：必须拒绝（原实现无任何状态守卫）")
    void reverse_draftStatus_shouldThrow() {
        doReturn(receipt(1L, "草稿")).when(service).getById(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reverseApprove(1L, "tester"));
        assertTrue(ex.getMessage().contains("只有已生效状态可重置草稿"),
                "错误信息应要求已生效态，实际: " + ex.getMessage());
        verifyNoInteractions(inventoryService);
        verifyNoInteractions(workOrderMapper);
    }

    @Test
    @DisplayName("重置草稿-单据不存在：抛业务异常")
    void reverse_notFound_shouldThrow() {
        doReturn(null).when(service).getById(404L);
        assertThrows(BusinessException.class, () -> service.reverseApprove(404L, "tester"));
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("重置草稿-已生效：必须扣回库存 + 回退工单完工数量与成品明细完成数量")
    void reverse_approved_shouldRollbackStockAndWorkOrder() {
        ProductionReceipt r = receipt(1L, "已生效");
        r.setQualifiedQuantity(new BigDecimal("10"));
        r.setProductionOrderId(77L);
        doReturn(r).when(service).getById(1L);

        ProductionReceiptItem it = item(1, new BigDecimal("10"), new BigDecimal("10"));
        when(itemMapper.selectList(any())).thenReturn(List.of(it));

        WorkOrder wo = new WorkOrder();
        wo.setOrderId(77L);
        wo.setOrderNo("MO-TEST-0001");
        wo.setQuantity(new BigDecimal("10"));
        wo.setCompletedQuantity(new BigDecimal("10"));
        when(workOrderMapper.selectById(77L)).thenReturn(wo);

        WorkOrderProduct p = new WorkOrderProduct();
        p.setLineNo(1);
        p.setCompletedQuantity(new BigDecimal("10"));
        when(workOrderProductMapper.selectByOrderId(77L)).thenReturn(List.of(p));

        service.reverseApprove(1L, "tester");

        // 1) 库存回冲：按审核时的 item.quantity 扣回，且来源类型为 *_REVERSE
        verify(inventoryService, times(1)).deductStock(
                eq(3L), isNull(), eq(5L), isNull(),
                eq(new BigDecimal("10")),
                eq("MOIN-TEST-0001"), eq("PRODUCTION_RECEIPT_REVERSE"),
                eq("ProductionReceipt"), eq("MOIN-TEST-0001"), eq("tester"));

        // 2) 工单完工数量回到重置草稿前的值
        assertEquals(0, wo.getCompletedQuantity().compareTo(BigDecimal.ZERO),
                "重置草稿后工单完工数量应为 0，实际: " + wo.getCompletedQuantity());
        verify(workOrderMapper, times(1)).updateById(wo);

        // 3) 成品明细完成数量同样回退
        assertEquals(0, p.getCompletedQuantity().compareTo(BigDecimal.ZERO),
                "重置草稿后成品明细完成数量应为 0，实际: " + p.getCompletedQuantity());
        verify(workOrderProductMapper, times(1)).updateById(p);
    }

    @Test
    @DisplayName("重置草稿-无工单关联：仍回冲库存，不报错")
    void reverse_withoutWorkOrder_shouldOnlyRollbackStock() {
        ProductionReceipt r = receipt(1L, "已生效");
        r.setQualifiedQuantity(new BigDecimal("2"));
        doReturn(r).when(service).getById(1L);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1, new BigDecimal("2"), new BigDecimal("2"))));

        service.reverseApprove(1L, "tester");

        verify(inventoryService, times(1)).deductStock(
                eq(3L), isNull(), eq(5L), isNull(), eq(new BigDecimal("2")),
                eq("MOIN-TEST-0001"), eq("PRODUCTION_RECEIPT_REVERSE"),
                eq("ProductionReceipt"), eq("MOIN-TEST-0001"), eq("tester"));
        verifyNoInteractions(workOrderMapper);
    }

    @Test
    @DisplayName("重置草稿-明细未带工单行号：整笔从成品明细第一行回退（与审核同口径）")
    void reverse_itemsWithoutWorkOrderLineNo_shouldRollbackFirstProduct() {
        ProductionReceipt r = receipt(1L, "已生效");
        r.setQualifiedQuantity(BigDecimal.ZERO);   // 合格数量只落在明细上
        r.setProductionOrderId(77L);
        doReturn(r).when(service).getById(1L);

        ProductionReceiptItem it = item(1, new BigDecimal("4"), new BigDecimal("4"));
        it.setWorkOrderLineNo(null);               // 触发"整笔计入第一行"分支
        when(itemMapper.selectList(any())).thenReturn(List.of(it));

        WorkOrder wo = new WorkOrder();
        wo.setOrderId(77L);
        wo.setOrderNo("MO-TEST-0001");
        wo.setCompletedQuantity(new BigDecimal("4"));
        when(workOrderMapper.selectById(77L)).thenReturn(wo);

        WorkOrderProduct p = new WorkOrderProduct();
        p.setLineNo(1);
        p.setCompletedQuantity(new BigDecimal("4"));
        when(workOrderProductMapper.selectByOrderId(77L)).thenReturn(List.of(p));

        service.reverseApprove(1L, "tester");

        assertEquals(0, wo.getCompletedQuantity().compareTo(BigDecimal.ZERO),
                "重置草稿后工单完工数量应为 0，实际: " + wo.getCompletedQuantity());
        assertEquals(0, p.getCompletedQuantity().compareTo(BigDecimal.ZERO),
                "无工单行号时整笔应从成品明细第一行回退，实际: " + p.getCompletedQuantity());
        verify(workOrderProductMapper, times(1)).updateById(p);
    }
}
