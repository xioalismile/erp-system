package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.erp.config.BusinessException;
import com.erp.config.service.SysConfigService;
import com.erp.finance.service.CostAccountingConfigService;
import com.erp.inventory.entity.Inventory;
import com.erp.masterdata.service.MaterialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 缺口覆盖：库存扣减的行锁与不足拦截。
 *
 * Bug 来源：InventoryServiceImpl.deductStock 原实现是"读 → 校验 → updateById 整行覆盖"，
 * 全程无 SELECT ... FOR UPDATE、无乐观锁版本号 → 并发审核两张出库/领料单时两边读到同一结存，
 * 各自校验通过后各自覆盖写 → 库存被超发（可致负库存）。
 * 修复：定位查询加 FOR UPDATE 行锁（addStock 同理，避免并发入库互相覆盖丢数量）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("即时库存 - 扣减行锁与不足拦截")
class InventoryDeductRowLockTest {

    @Mock private MaterialService materialService;
    @Mock private InventoryTransactionService transactionService;
    @Mock private CostAccountingConfigService costAccountingConfigService;
    @Mock private InventoryBatchService inventoryBatchService;
    @Mock private SysConfigService sysConfigService;

    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new InventoryServiceImpl(materialService, transactionService,
                costAccountingConfigService, inventoryBatchService));
        // sysConfigService 在主类中是字段注入（@Autowired），单测里手工塞入
        ReflectionTestUtils.setField(service, "sysConfigService", sysConfigService);
        lenient().doReturn(true).when(service).updateById(any());
        lenient().doReturn(true).when(service).save(any());
    }

    private Inventory stock(String quantity, String unitCost) {
        Inventory inv = new Inventory();
        inv.setInventoryId(1L);
        inv.setStockId(3L);
        inv.setMaterialId(5L);
        inv.setQuantity(new BigDecimal(quantity));
        inv.setAvailableQuantity(new BigDecimal(quantity));
        inv.setUnitCost(new BigDecimal(unitCost));
        return inv;
    }

    @Test
    @DisplayName("库存充足：按数量扣减，且定位查询必须带行锁（否则并发审核会超发）")
    void deduct_enoughStock_shouldLockRowAndDecreaseQuantity() {
        Inventory inv = stock("10", "2");
        doReturn(inv).when(service).getOne(any(Wrapper.class));
        when(sysConfigService.getBooleanValue("inventory.negative.allowed", false)).thenReturn(false);
        // 计价方法配置为空 → 默认移动加权平均，出库单价直接取库存单价（不触发 FIFO/LIFO 批次查询）
        when(costAccountingConfigService.getActiveConfig()).thenReturn(null);

        service.deductStock(3L, null, 5L, null, new BigDecimal("4"),
                "SOUT-TEST-0001", "SALE_OUT", "SaleDelivery", "SOUT-TEST-0001", "tester");

        assertEquals(0, inv.getQuantity().compareTo(new BigDecimal("6")), "结存应为 6，实际: " + inv.getQuantity());
        assertEquals(0, inv.getAvailableQuantity().compareTo(new BigDecimal("6")),
                "可用量应为 6，实际: " + inv.getAvailableQuantity());
        verify(service, times(1)).updateById(inv);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service, times(1)).getOne(captor.capture());
        String sqlSegment = String.valueOf(captor.getValue().getSqlSegment()).toUpperCase();
        assertTrue(sqlSegment.contains("FOR UPDATE"),
                "库存定位查询必须带 FOR UPDATE 行锁，实际SQL段: " + sqlSegment);
    }

    @Test
    @DisplayName("库存不足且未允许负库存：抛错且不写库")
    void deduct_notEnoughStock_shouldThrowAndNotWrite() {
        doReturn(stock("3", "2")).when(service).getOne(any(Wrapper.class));
        when(sysConfigService.getBooleanValue("inventory.negative.allowed", false)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deductStock(
                3L, null, 5L, null, new BigDecimal("5"),
                "SOUT-TEST-0002", "SALE_OUT", "SaleDelivery", "SOUT-TEST-0002", "tester"));

        assertTrue(ex.getMessage().contains("库存不足"), "错误信息应提示库存不足，实际: " + ex.getMessage());
        verify(service, never()).updateById(any());
    }
}
