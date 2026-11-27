package com.erp.inventory.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * 虚拟库存服务（订单占用量 / 预计可用量）
 *
 * 业务口径：
 * - 占用量(reserved)：生效销售订单(status='已生效' 且未结案)中尚未发货的数量合计
 *   = SUM(订单明细.quantity - 已发货数量 delivered_quantity)
 * - 预计可用量(virtual available，历史名"虚拟可用") = 实际可用库存(available_quantity) - 占用量
 *   例：实际库存20，下单10未发货 → 实际库存仍为20，预计可用量=10
 *   界面口径名：即时库存页列名与单据明细列名统一显示为"预计可用量"
 */
public interface VirtualStockService {

    /**
     * 查询物料占用量（生效销售订单待发货数量合计）
     *
     * @param materialId     物料ID
     * @param excludeOrderNo 需排除的订单号（出库单来源于该订单时排除自身占用，避免自我占用误判），可为null
     */
    BigDecimal getReservedQuantity(Long materialId, String excludeOrderNo);

    /**
     * 批量查询物料占用量（库存列表页一次性填充）
     *
     * @param materialIds 物料ID集合
     * @return materialId -> 占用量（未涉及的物料不返回，调用方按0处理）
     */
    Map<Long, BigDecimal> batchGetReservedQuantity(Collection<Long> materialIds);

    /**
     * 查询物料预计可用量 = 全部仓库实际可用库存合计 - 占用量
     */
    BigDecimal getVirtualAvailable(Long materialId);
}
