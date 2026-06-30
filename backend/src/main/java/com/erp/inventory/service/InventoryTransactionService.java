package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.inventory.entity.InventoryTransaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface InventoryTransactionService extends IService<InventoryTransaction> {

    /**
     * 记录库存台账
     * @param documentNo 单据编号
     * @param documentType 单据类型
     * @param materialCode 物料编码
     * @param materialName 物料名称
     * @param warehouseCode 仓库编码
     * @param quantity 数量变化(正数入库,负数出库)
     * @param sourceId 关联单据ID
     * @param sourceType 关联单据类型
     * @param operator 操作人
     * @param remark 备注
     */
    void recordTransaction(String documentNo, String documentType, String materialCode, String materialName,
                          String warehouseCode, BigDecimal quantity, Long sourceId, String sourceType,
                          String operator, String remark);

    /**
     * 查询物料的库存流水
     */
    List<InventoryTransaction> getMaterialTransactions(String materialCode, String warehouseCode);

    /**
     * 查询单据的库存流水
     */
    List<InventoryTransaction> getDocumentTransactions(String documentNo);
}
