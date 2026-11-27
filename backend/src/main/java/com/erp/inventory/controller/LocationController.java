package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.inventory.entity.Location;
import com.erp.inventory.service.LocationService;
import com.erp.inventory.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("inventoryLocationController")
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final WarehouseService warehouseService;
    private final DataScopeResolver dataScopeResolver;

    /** 货位下拉（2026-10-09 权限收口：按可见仓库收敛，货位表 stock_id 即所属仓库） */
    @GetMapping("/list")
    public Result<List<Location>> list(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String binCode,
            @RequestParam(required = false) String status,
            jakarta.servlet.http.HttpServletRequest req) {
        QueryWrapper<Location> wrapper = new QueryWrapper<>();
        if (binCode != null && !binCode.isBlank()) wrapper.like("bin_code", binCode);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        dataScopeResolver.applyVisibleWarehouses(wrapper, req);
        wrapper.orderByAsc("bin_code");
        return Result.success(locationService.list(wrapper));
    }

    /** 按仓库取货位（2026-10-09 权限收口：请求的仓库不在可见范围内 → 返回空，不泄露其它仓库货位） */
    @GetMapping("/warehouse/{warehouseCode}")
    public Result<List<Location>> getByWarehouse(@PathVariable String warehouseCode) {
        List<Long> visibleWh = dataScopeResolver.visibleWarehouseIdsForCurrentRequest();
        if (visibleWh != null) {
            com.erp.basedata.entity.Warehouse wh = warehouseService.getByCode(warehouseCode);
            if (wh == null || wh.getStockId() == null || !visibleWh.contains(wh.getStockId())) {
                return Result.success(List.of());
            }
        }
        return Result.success(locationService.getByWarehouse(warehouseCode));
    }

    @GetMapping("/{id}")
    public Result<Location> getById(@PathVariable Long id) {
        return Result.success(locationService.getById(id));
    }

    @PostMapping
    public Result<Location> create(@RequestBody Location location) {
        locationService.save(location);
        return Result.success(location);
    }

    @PutMapping("/{id}")
    public Result<Location> update(@PathVariable Long id, @RequestBody Location location) {
        location.setBinId(id);
        locationService.updateById(location);
        return Result.success(location);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        locationService.removeById(id);
        return Result.success();
    }
}