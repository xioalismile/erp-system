package com.erp.common.impact;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * MRP需求占用/释放服务（Phase 2 占位实现，仅打印日志）。
 *
 * <p>典型影响：
 * <ul>
 *   <li>销售订单 审核 → 锁定毛需求（按"物料 + 需求日期 + 数量"占用MRP毛需求池）</li>
 *   <li>销售订单 作废/结案 → 释放未执行部分毛需求（已下推生产/委外/采购的部分不释放，由下游释放）</li>
 *   <li>预测单 审核 → 录入独立需求</li>
 *   <li>生产/委外/采购 完工入库 → 释放对应占用</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class MrpImpactService {

    public static class MrpDemandEntry {
        public Long materialId;
        public BigDecimal qty;
        public LocalDate demandDate;
    }

    /** 占用毛需求 */
    public void occupyGrossDemand(String billNo, List<MrpDemandEntry> entries) {
        for (MrpDemandEntry e : entries) {
            log.info("[MRP占位] 单据[{}] 占用毛需求 物料[{}] 日期[{}] 数量{}",
                    billNo, e.materialId, e.demandDate, e.qty);
        }
        // TODO PhaseN：调用 MrpService.occupyDemand(billNo, entries)
    }

    /** 释放毛需求 */
    public void releaseGrossDemand(String billNo, List<MrpDemandEntry> entries) {
        for (MrpDemandEntry e : entries) {
            log.info("[MRP占位] 单据[{}] 释放毛需求 物料[{}] 日期[{}] 数量{}",
                    billNo, e.materialId, e.demandDate, e.qty);
        }
        // TODO PhaseN：调用 MrpService.releaseDemand(billNo, entries)
    }
}
