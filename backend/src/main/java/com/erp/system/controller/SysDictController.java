package com.erp.system.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.system.entity.SysDictData;
import com.erp.system.entity.SysDictType;
import com.erp.system.service.SysDictDataService;
import com.erp.system.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据字典 Controller
 * 路由: /api/system/dict
 */
@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictTypeService sysDictTypeService;
    private final SysDictDataService sysDictDataService;

    // ==================== 字典类型 ====================

    @GetMapping("/type/page")
    public Result<PageResult<SysDictType>> typePage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(sysDictTypeService.pageQuery(current, size, keyword));
    }

    @GetMapping("/type/{id}")
    public Result<SysDictType> typeDetail(@PathVariable Long id) {
        return Result.success(sysDictTypeService.getDetail(id));
    }

    @PostMapping("/type")
    public Result<Void> createType(@RequestBody SysDictType dictType) {
        sysDictTypeService.saveDictType(dictType);
        return Result.success();
    }

    @PutMapping("/type/{id}")
    public Result<Void> updateType(@PathVariable Long id, @RequestBody SysDictType dictType) {
        dictType.setDictTypeId(id);
        sysDictTypeService.updateDictType(dictType);
        return Result.success();
    }

    @DeleteMapping("/type/{id}")
    public Result<Void> deleteType(@PathVariable Long id) {
        sysDictTypeService.deleteDictType(id);
        return Result.success();
    }

    // ==================== 字典数据 ====================

    @GetMapping("/data/{typeId}")
    public Result<List<SysDictData>> dataList(@PathVariable Long typeId) {
        return Result.success(sysDictDataService.listByTypeId(typeId));
    }

    @PostMapping("/data")
    public Result<Void> createData(@RequestBody SysDictData dictData) {
        sysDictDataService.saveDictData(dictData);
        return Result.success();
    }

    @PutMapping("/data/{id}")
    public Result<Void> updateData(@PathVariable Long id, @RequestBody SysDictData dictData) {
        dictData.setDictDataId(id);
        sysDictDataService.updateDictData(dictData);
        return Result.success();
    }

    @DeleteMapping("/data/{id}")
    public Result<Void> deleteData(@PathVariable Long id) {
        sysDictDataService.deleteDictData(id);
        return Result.success();
    }
}
