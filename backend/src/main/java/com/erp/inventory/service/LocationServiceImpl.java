package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.inventory.entity.Location;
import com.erp.inventory.mapper.LocationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LocationServiceImpl extends ServiceImpl<LocationMapper, Location> implements LocationService {
    @Override
    public List<Location> getByWarehouse(String warehouseCode) {
        return baseMapper.selectByWarehouse(warehouseCode);
    }

    @Override
    public Location getByCode(String warehouseCode, String locationCode) {
        return getOne(new QueryWrapper<Location>()
                .eq("warehouse_code", warehouseCode)
                .eq("location_code", locationCode));
    }
}