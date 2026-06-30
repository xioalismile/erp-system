package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.inventory.entity.InventoryBatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface InventoryBatchService extends IService<InventoryBatch> {
    List<InventoryBatch> getByMaterial(Long materialId);
    InventoryBatch getByBatchNo(String batchNo);
    List<InventoryBatch> getExpiredBatches();
    List<InventoryBatch> getExpiringBatches(Integer days);
    String generateBatchNo(Long materialId);
    /** FIFO 分配批次 */
    Map<String, Object> allocateByFifo(Long materialId, BigDecimal quantity);
    /** 扣减批次库存 */
    void deductBatch(Long batchId, BigDecimal quantity);
    /** 新增批次 */
    void addBatch(InventoryBatch batch);
}
