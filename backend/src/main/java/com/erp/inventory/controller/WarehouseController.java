package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.inventory.entity.Warehouse;
import com.erp.inventory.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping("/list")
    public Result<List<Warehouse>> list(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String stockName,
            @RequestParam(required = false) String status) {
        QueryWrapper<Warehouse> wrapper = new QueryWrapper<>();
        if (stockCode != null && !stockCode.isBlank()) wrapper.like("stock_code", stockCode);
        if (stockName != null && !stockName.isBlank()) wrapper.like("stock_name", stockName);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        wrapper.orderByAsc("stock_code");
        return Result.success(warehouseService.list(wrapper));
    }

    @GetMapping("/{id}")
    public Result<Warehouse> getById(@PathVariable Long id) {
        return Result.success(warehouseService.getById(id));
    }

    @GetMapping("/code/{code}")
    public Result<Warehouse> getByCode(@PathVariable String code) {
        return Result.success(warehouseService.getByCode(code));
    }

    @PostMapping
    public Result<Warehouse> create(@RequestBody Warehouse warehouse) {
        warehouseService.save(warehouse);
        return Result.success(warehouse);
    }

    @PutMapping("/{id}")
    public Result<Warehouse> update(@PathVariable Long id, @RequestBody Warehouse warehouse) {
        warehouse.setStockId(id);
        warehouseService.updateById(warehouse);
        return Result.success(warehouse);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        warehouseService.removeById(id);
        return Result.success();
    }
}