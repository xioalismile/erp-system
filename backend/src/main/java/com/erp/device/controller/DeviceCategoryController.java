package com.erp.device.controller;

import com.erp.common.Result;
import com.erp.device.entity.DeviceCategory;
import com.erp.device.service.DeviceCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 设备分类（树形主档） */
@RestController
@RequestMapping("/api/device/category")
@RequiredArgsConstructor
public class DeviceCategoryController {

    private final DeviceCategoryService categoryService;

    @GetMapping("/tree")
    public Result<List<DeviceCategory>> tree() {
        return Result.success(categoryService.tree());
    }

    @GetMapping("/{id}")
    public Result<DeviceCategory> detail(@PathVariable Long id) {
        return Result.success(categoryService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody DeviceCategory category) {
        categoryService.saveCategory(category);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DeviceCategory category) {
        categoryService.updateCategory(id, category);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }
}
