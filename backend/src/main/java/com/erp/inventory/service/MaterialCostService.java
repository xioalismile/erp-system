package com.erp.inventory.service;

import com.erp.finance.cost.calculation.service.CostCalculationService;
import com.erp.inventory.entity.Inventory;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物料成本服务 - 统一提供物料成本查询
 *
 * 提供两种成本口径:
 * 1. 最新核算成本: 取自 cost_calculation 表最新"已确认"的 unit_cost (期间核算口径, 核算后才更新)
 * 2. 库存移动平均成本: 取自 inventory_current.unit_cost (实时移动加权平均)
 *
 * 用于各单据明细回填 avg_cost 字段, 以及仓库页面展示成本.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialCostService {

    private final CostCalculationService costCalculationService;
    private final InventoryMapper inventoryMapper;

    /** 本地缓存: 物料ID -> 最新核算成本 (单次请求内有效, 避免重复查询)
     *  使用 ConcurrentHashMap 保证线程安全 */
    private final Map<Long, BigDecimal> calcCostCache = new ConcurrentHashMap<>();
    private final Map<Long, BigDecimal> stockAvgCostCache = new ConcurrentHashMap<>();

    /**
     * 获取物料的最新核算成本 (期间核算口径)
     * 取自 cost_calculation 表最新"已确认"状态的 unit_cost
     *
     * @param materialId 物料ID
     * @return 最新核算单位成本, 无记录返回 BigDecimal.ZERO
     */
    public BigDecimal getLatestCalcCost(Long materialId) {
        if (materialId == null) return BigDecimal.ZERO;
        return calcCostCache.computeIfAbsent(materialId, k -> {
            BigDecimal cost = costCalculationService.getUnitCost(k);
            return cost != null ? cost : BigDecimal.ZERO;
        });
    }

    /**
     * 获取物料的库存移动平均成本 (实时口径)
     * 取自 inventory_current 表, 按物料ID汇总: 总成本 / 总数量
     * 若无库存记录返回最新核算成本作为兜底
     *
     * @param materialId 物料ID
     * @return 库存移动平均成本, 无记录返回 BigDecimal.ZERO
     */
    public BigDecimal getStockAvgCost(Long materialId) {
        if (materialId == null) return BigDecimal.ZERO;
        return stockAvgCostCache.computeIfAbsent(materialId, k -> {
            List<Inventory> list = inventoryMapper.selectList(
                    new QueryWrapper<Inventory>().eq("material_id", k));
            if (list == null || list.isEmpty()) {
                // 无库存记录, 兜底返回最新核算成本
                return getLatestCalcCost(k);
            }
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            for (Inventory inv : list) {
                BigDecimal qty = inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO;
                BigDecimal cost = inv.getTotalCost() != null ? inv.getTotalCost() : BigDecimal.ZERO;
                totalQty = totalQty.add(qty);
                totalCost = totalCost.add(cost);
            }
            if (totalQty.compareTo(BigDecimal.ZERO) == 0) {
                // 数量为0但曾有记录, 取最新一条的unit_cost
                for (Inventory inv : list) {
                    if (inv.getUnitCost() != null && inv.getUnitCost().compareTo(BigDecimal.ZERO) > 0) {
                        return inv.getUnitCost();
                    }
                }
                return getLatestCalcCost(k);
            }
            return totalCost.divide(totalQty, 4, RoundingMode.HALF_UP);
        });
    }

    /**
     * 批量获取物料的最新核算成本
     */
    public Map<Long, BigDecimal> batchGetLatestCalcCost(List<Long> materialIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (materialIds == null) return result;
        for (Long id : materialIds) {
            result.put(id, getLatestCalcCost(id));
        }
        return result;
    }

    /**
     * 批量获取物料的库存移动平均成本
     */
    public Map<Long, BigDecimal> batchGetStockAvgCost(List<Long> materialIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (materialIds == null) return result;
        for (Long id : materialIds) {
            result.put(id, getStockAvgCost(id));
        }
        return result;
    }

    /**
     * 清除本地缓存 (单次请求结束后可调用, 或在成本核算完成后调用以刷新)
     */
    public void clearCache() {
        calcCostCache.clear();
        stockAvgCostCache.clear();
    }
}
