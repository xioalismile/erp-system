package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.inventory.entity.Inventory;

import java.math.BigDecimal;
import java.util.List;

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
     * 查询物料在某仓库的库存
     */
    List<Inventory> getByMaterialAndStock(Long materialId, Long stockId);

    /**
     * 库存预警列表 (库存数量 < 物料最小库存)
     */
    List<Inventory> getWarningList();
}
