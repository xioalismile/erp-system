package com.erp.inventory.serial.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.inventory.serial.entity.SerialNumber;
import com.erp.inventory.serial.entity.SerialNumberTrace;

import java.util.List;
import java.util.Map;

public interface SerialNumberService extends IService<SerialNumber> {

    PageResult<SerialNumber> pageQuery(int current, int size, String keyword, Integer status, Long materialId, Long warehouseId, String sortBy, String sortOrder);

    SerialNumber getDetail(Long id);

    List<SerialNumberTrace> getTraceList(Long serialNumberId);

    void createSerialNumbers(List<SerialNumber> list);

    void outboundSerialNumbers(List<String> serialNos, Map<String, Object> outboundDoc);

    void transferSerialNumbers(List<String> serialNos, Long targetWarehouseId, String targetWarehouseName, Long targetLocationId, String targetLocationName);

    void scrapSerialNumbers(List<String> serialNos, String remark);

    SerialNumber getSerialNumberByNo(String serialNo);

    boolean checkSerialNumberExists(String serialNo);
}
