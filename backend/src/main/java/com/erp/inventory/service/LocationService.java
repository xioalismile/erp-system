package com.erp.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.inventory.entity.Location;

import java.util.List;

public interface LocationService extends IService<Location> {
    List<Location> getByWarehouse(String warehouseCode);
    Location getByCode(String warehouseCode, String locationCode);
}