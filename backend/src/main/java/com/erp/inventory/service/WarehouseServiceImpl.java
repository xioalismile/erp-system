package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.inventory.entity.Warehouse;
import com.erp.inventory.mapper.WarehouseMapper;
import org.springframework.stereotype.Service;

@Service
public class WarehouseServiceImpl extends ServiceImpl<WarehouseMapper, Warehouse> implements WarehouseService {
    @Override
    public Warehouse getByCode(String warehouseCode) {
        return getOne(new QueryWrapper<Warehouse>().eq("stock_code", warehouseCode));
    }
}