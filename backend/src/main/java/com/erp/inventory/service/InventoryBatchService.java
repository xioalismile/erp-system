package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.inventory.entity.InventoryBatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface InventoryBatchService extends IService<InventoryBatch> {
    List<InventoryBatch> getByMaterialAndWarehouse(String materialCode, String warehouseCode);
    InventoryBatch getByBatchNo(String batchNo);
    List<InventoryBatch> getByWarehouse(String warehouseCode);
    List<InventoryBatch> getExpiredBatches();
    List<InventoryBatch> getExpiringBatches(Integer days);
    String generateBatchNo(String materialCode);
    Map<String, Object> allocateByFifo(String materialCode, String warehouseCode, BigDecimal quantity);
    void deductBatch(Long batchId, BigDecimal quantity);
    void addBatch(InventoryBatch batch);
}