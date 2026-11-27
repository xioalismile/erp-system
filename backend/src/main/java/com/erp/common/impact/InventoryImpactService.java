package com.erp.common.impact;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 库存影响服务（Phase 2 占位实现，仅打印日志，后续接真实库存账本）。
 *
 * <p>典型影响：
 * <ul>
 *   <li>销售出库单 审核：扣减"仓库库存"（库存账本 → 出库流水）</li>
 *   <li>销售出库单 反审/作废：补回"仓库库存"</li>
 *   <li>采购入库单 审核：增加"仓库库存"</li>
 *   <li>采购入库单 反审/作废：扣减"仓库库存"</li>
 *   <li>其他出入库 审核/反审：按方向调整库存</li>
 *   <li>调拨 审核：转出仓减、转入仓增</li>
 *   <li>盘点 审核：盘盈入/盘亏出</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class InventoryImpactService {

    /** 审核后影响方向（true=增加库存 / false=扣减库存） */
    public void changeStock(Object billId, String billNo, String direction,
                            Long warehouseId, Long materialId, BigDecimal qty, String unit) {
        log.info("[库存占位] 单据[{}:{}] 物料[{}] 仓库[{}] {} 数量{} {}",
                direction, billNo, materialId, warehouseId,
                "in".equals(direction) ? "+" : "-", qty, unit);
        // TODO PhaseN：接库存账本Service（stockLedgerService.postStockChange(...)）
    }
}
