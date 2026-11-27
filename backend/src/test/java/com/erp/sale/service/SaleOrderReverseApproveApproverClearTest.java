package com.erp.sale.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.erp.basedata.mapper.MaterialCategoryMapper;
import com.erp.basedata.service.CurrencyService;
import com.erp.basedata.service.EmployeeService;
import com.erp.basedata.service.PaymentTermService;
import com.erp.basedata.service.UnitOfMeasureService;
import com.erp.basedata.service.WarehouseService;
import com.erp.common.bill.BillRelationService;
import com.erp.config.KgwBusinessRules;
import com.erp.config.SysParamValidator;
import com.erp.inventory.service.MaterialCostService;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.MaterialService;
import com.erp.purchase.service.PurchaseOrderService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleQuoteItemMapper;
import com.erp.sale.mapper.SaleQuoteMapper;
import com.erp.serialno.service.SysSerialNoConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：销售订单重置草稿必须显式清空审核人/审核时间。
 *
 * <p>Bug 来源（2026-10-28 免审批「审核人」留空口径）：MyBatis-Plus 默认 update 策略是 NOT_NULL，
 * 基类 {@code reverseApprove} 里 {@code setApprovedBy(null) + updateById} 不会把 null 写进库。
 * 结果是「人工审核通过（审核人=张三）→ 重置草稿 → 免审批再提交」后，单据仍显示上一次的真人审核人，
 * 而这一轮根本没有人工审核。采购订单等其余单据都已用 UpdateWrapper 显式置空，销售订单遗漏。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("销售订单 - 重置草稿清空审核人回归测试")
class SaleOrderReverseApproveApproverClearTest {

    @Mock private SaleOrderItemMapper itemMapper;
    @Mock private SaleQuoteItemMapper saleQuoteItemMapper;
    @Mock private SaleQuoteMapper saleQuoteMapper;
    @Mock private CustomerService customerService;
    @Mock private EmployeeService employeeService;
    @Mock private CurrencyService currencyService;
    @Mock private PaymentTermService paymentTermService;
    @Mock private MaterialService materialService;
    @Mock private MaterialCategoryMapper materialCategoryMapper;
    @Mock private UnitOfMeasureService unitOfMeasureService;
    @Mock private MaterialCostService materialCostService;
    @Mock private KgwBusinessRules kgwRules;
    @Mock private SysParamValidator sysParamValidator;
    @Mock private SaleOverQtyGuardService saleOverQtyGuardService;
    @Mock private SysSerialNoConfigService sysSerialNoConfigService;
    @Mock private BillRelationService billRelationService;
    @Mock private PurchaseOrderService purchaseOrderService;
    @Mock private WarehouseService warehouseService;

    private SaleOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new SaleOrderServiceImpl(itemMapper, saleQuoteItemMapper, saleQuoteMapper,
                customerService, employeeService, currencyService, paymentTermService, materialService,
                materialCategoryMapper, unitOfMeasureService, materialCostService, kgwRules, sysParamValidator,
                saleOverQtyGuardService, sysSerialNoConfigService, billRelationService, purchaseOrderService, warehouseService));
        // 继承自 ServiceImpl 的写库方法在单测中不落库
        lenient().doReturn(true).when(service).updateById(any(SaleOrder.class));
        lenient().doReturn(true).when(service).update(any(Wrapper.class));
        lenient().when(billRelationService.listApprovedDownstream(any(), any())).thenReturn(List.of());
    }

    private SaleOrder approvedOrder() {
        SaleOrder o = new SaleOrder();
        o.setOrderId(1L);
        o.setOrderNo("SO-TEST-0001");
        o.setStatus("已生效");
        o.setApprovalStatus("已生效");
        o.setCloseStatus(0);
        o.setApprovedBy("张三");
        return o;
    }

    @Test
    @DisplayName("重置草稿-已生效：必须显式置空 approved_by / approved_at（不能依赖 updateById 写 null）")
    @SuppressWarnings("unchecked")
    void reverse_approved_mustClearApproverColumnsExplicitly() {
        SaleOrder order = approvedOrder();
        doReturn(order).when(service).getById(1L);

        service.reverseApprove(1L, "操作员");

        assertEquals("草稿", order.getStatus(), "重置草稿后单据状态应为草稿");
        assertNull(order.getApprovedBy(), "内存对象上的审核人应被清空");

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service, times(1)).update(captor.capture());
        String setSql = ((UpdateWrapper<?>) captor.getValue()).getSqlSet();
        assertNotNull(setSql, "重置草稿必须发起一次显式 UPDATE");
        assertTrue(setSql.contains("approved_by"), "UPDATE 必须显式包含 approved_by，实际: " + setSql);
        assertTrue(setSql.contains("approved_at"), "UPDATE 必须显式包含 approved_at，实际: " + setSql);
    }
}
