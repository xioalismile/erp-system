package com.erp.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料库存汇总（按物料跨仓库合计，非数据库表，供单据明细行展示库存用）
 *
 * 口径说明：
 * - 库存量 quantity           = SUM(inventory_current.quantity)        （不区分仓库/批次）
 * - 可用量 availableQuantity  = SUM(inventory_current.available_quantity)
 * - 预计可用量 expectedAvailableQuantity = 可用量 - 订单占用（生效销售订单未发货数量合计）
 */
@Data
public class MaterialStockSummaryDTO {

    /** 物料ID */
    private Long materialId;

    /** 库存量：该物料全部可见仓库的库存数量合计 */
    private BigDecimal quantity = BigDecimal.ZERO;

    /** 可用量：该物料全部可见仓库的可用数量合计 */
    private BigDecimal availableQuantity = BigDecimal.ZERO;

    /** 预计可用量：可用量 - 订单占用（可为负，负数表示接单缺口） */
    private BigDecimal expectedAvailableQuantity = BigDecimal.ZERO;
}
