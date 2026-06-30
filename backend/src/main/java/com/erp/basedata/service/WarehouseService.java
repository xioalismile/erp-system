package com.erp.basedata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Warehouse;

public interface WarehouseService extends IService<Warehouse> {
    Warehouse getByCode(String stockCode);
    boolean existsByCode(String stockCode);
    boolean existsByCodeExcludeId(String stockCode, Long stockId);
}
