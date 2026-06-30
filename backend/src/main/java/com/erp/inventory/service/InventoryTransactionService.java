package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.inventory.entity.InventoryTransaction;

import java.util.List;

public interface InventoryTransactionService extends IService<InventoryTransaction> {

    /**
     * 记录库存台账流水
     * @param transNo 单据编号 (业务单号)
     * @param transType 单据类型 (PURCHASE_IN/SALE_OUT/OTHER_IN/OTHER_OUT/STOCK_TRANSFER/STOCK_CHECK等)
     * @param stockId 仓库ID
     * @param binId 货位ID (可空)
     * @param materialId 物料ID
     * @param batchNo 批次号 (可空)
     * @param inQuantity 入库数量 (出库时传null或0)
     * @param inAmount 入库金额 (出库时传null或0)
     * @param outQuantity 出库数量 (入库时传null或0)
     * @param outAmount 出库金额 (入库时传null或0)
     * @param unitCost 单位成本
     * @param sourceBillType 来源单据类型
     * @param sourceBillNo 来源单据号
     * @param operator 操作人
     * @param remark 备注
     */
    void recordTransaction(String transNo, String transType,
                          Long stockId, Long binId, Long materialId, String batchNo,
                          java.math.BigDecimal inQuantity, java.math.BigDecimal inAmount,
                          java.math.BigDecimal outQuantity, java.math.BigDecimal outAmount,
                          java.math.BigDecimal unitCost,
                          String sourceBillType, String sourceBillNo,
                          String operator, String remark);

    /**
     * 查询物料的库存流水
     */
    List<InventoryTransaction> getMaterialTransactions(Long materialId, Long stockId);

    /**
     * 查询单据的库存流水
     */
    List<InventoryTransaction> getDocumentTransactions(String transNo);
}
