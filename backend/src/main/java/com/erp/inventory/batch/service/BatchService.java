package com.erp.inventory.batch.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.inventory.batch.entity.BatchInfo;
import com.erp.inventory.batch.entity.BatchInventory;

import java.math.BigDecimal;
import java.util.List;

public interface BatchService extends IService<BatchInfo> {

    PageResult<BatchInfo> pageQuery(int current, int size, String keyword, Integer status, Long materialId, String sortBy, String sortOrder);

    BatchInfo getDetail(Long id);

    List<BatchInventory> getBatchInventory(Long batchId);

    BatchInfo createBatch(BatchInfo batchInfo);

    void inboundBatch(String batchNo, Long warehouseId, String warehouseName, Long locationId, String locationName, BigDecimal quantity);

    void outboundBatch(String batchNo, Long warehouseId, BigDecimal quantity);

    List<BatchInfo> getAvailableBatches(Long materialId, Long warehouseId);

    int checkExpiry();
}
