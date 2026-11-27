package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.inventory.dto.MaterialStockSummaryDTO;
import com.erp.inventory.entity.Inventory;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface InventoryService extends IService<Inventory> {

    /**
     * 增加库存 (入库)
     * @param stockId 仓库ID
     * @param binId 货位ID (可空)
     * @param materialId 物料ID
     * @param batchNo 批次号 (可空)
     * @param quantity 数量 (正数)
     * @param unitCost 单位成本 (可空, 用于移动加权平均)
     * @param transNo 单据编号
     * @param transType 单据类型 (PURCHASE_IN/SALE_RETURN/OTHER_IN/STOCK_TRANSFER_IN/STOCK_CHECK_PROFIT等)
     * @param sourceBillType 来源单据类型
     * @param sourceBillNo 来源单据号
     * @param operator 操作人
     */
    void addStock(Long stockId, Long binId, Long materialId, String batchNo,
                  BigDecimal quantity, BigDecimal unitCost,
                  String transNo, String transType,
                  String sourceBillType, String sourceBillNo, String operator);

    /**
     * 扣减库存 (出库)
     * @param quantity 数量 (正数, 方法内部转负数)
     */
    void deductStock(Long stockId, Long binId, Long materialId, String batchNo,
                     BigDecimal quantity,
                     String transNo, String transType,
                     String sourceBillType, String sourceBillNo, String operator);

    /**
     * 按指定单位成本扣减库存（入库红字冲回专用）。
     * 数量与库存总成本同时按原入库成本冲减，剩余单位成本按“剩余总成本 ÷ 剩余数量”重算。
     */
    void deductStockAtCost(Long stockId, Long binId, Long materialId, String batchNo,
                           BigDecimal quantity, BigDecimal unitCost,
                           String transNo, String transType,
                           String sourceBillType, String sourceBillNo, String operator);

    /**
     * 查询物料在某仓库的库存
     */
    List<Inventory> getByMaterialAndStock(Long materialId, Long stockId);

    /**
     * 【2026-09-11 材料成本归集】预取本次出库的单位成本。
     * deductStock 内部虽已算出出库单价，但不返回给调用方；领料/出库需要把该成本回写到单据明细与工单用料。
     * 注意： FIFO/LIFO 依赖扣减前的批次结存，因此必须在 deductStock 之前调用。
     */
    BigDecimal getOutUnitCost(Long stockId, Long binId, Long materialId, String batchNo);

    /**
     * 查询物料在全部仓库的实际可用库存合计（available_quantity 汇总）
     */
    BigDecimal getTotalAvailableQuantity(Long materialId);

    /**
     * 【2026-09-15 单据明细行库存展示】批量查询物料库存汇总（跨仓库合计，一次查询避免 N+1）。
     *
     * @param materialIds     物料ID集合（调用方已去重）
     * @param visibleStockIds 可见仓库ID集合（数据权限）：null=不过滤（全部仓库）；空集合=无可见仓库（返回空）
     * @return materialId -> 汇总；未涉及的物料不返回，调用方按 0 处理
     */
    Map<Long, MaterialStockSummaryDTO> batchGetStockSummary(Collection<Long> materialIds, List<Long> visibleStockIds);

    /**
     * 库存预警列表 (库存数量 < 物料最小库存)
     */
    List<Inventory> getWarningList();
}
