package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.basedata.entity.Warehouse;

public interface WarehouseService extends IService<Warehouse> {
    Warehouse getByCode(String warehouseCode);
}