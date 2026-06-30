package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.inventory.entity.Location;
import com.erp.inventory.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/list")
    public Result<List<Location>> list(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String locationCode,
            @RequestParam(required = false) String status) {
        QueryWrapper<Location> wrapper = new QueryWrapper<>();
        if (warehouseCode != null && !warehouseCode.isBlank()) wrapper.eq("warehouse_code", warehouseCode);
        if (locationCode != null && !locationCode.isBlank()) wrapper.like("location_code", locationCode);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        wrapper.orderByAsc("warehouse_code").orderByAsc("location_code");
        return Result.success(locationService.list(wrapper));
    }

    @GetMapping("/warehouse/{warehouseCode}")
    public Result<List<Location>> getByWarehouse(@PathVariable String warehouseCode) {
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
        location.setId(id);
        locationService.updateById(location);
        return Result.success(location);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        locationService.removeById(id);
        return Result.success();
    }
}