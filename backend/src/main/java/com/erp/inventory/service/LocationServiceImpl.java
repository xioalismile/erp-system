package com.erp.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.inventory.entity.Location;
import com.erp.basedata.entity.Warehouse;
import com.erp.inventory.mapper.LocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("inventoryLocationServiceImpl")
@RequiredArgsConstructor
public class LocationServiceImpl extends ServiceImpl<LocationMapper, Location> implements LocationService {

    private final WarehouseService warehouseService;

    @Override
    public List<Location> getByWarehouse(String warehouseCode) {
        return baseMapper.selectByWarehouse(warehouseCode);
    }

    @Override
    public Location getByCode(String warehouseCode, String locationCode) {
        // 先查 warehouse 拿 stock_id, 再按 stock_id + bin_code 查 location
        Warehouse wh = warehouseService.getByCode(warehouseCode);
        if (wh == null) return null;
        return getOne(new QueryWrapper<Location>()
                .eq("stock_id", wh.getStockId())
                .eq("bin_code", locationCode));
    }
}
