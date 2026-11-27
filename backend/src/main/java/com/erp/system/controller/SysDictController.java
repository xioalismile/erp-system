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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(sysDictTypeService.pageQuery(current, size, keyword, sortBy, sortOrder));
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

    /** 按类型编码查字典数据（前端下拉框直接用 code 取选项） */
    @GetMapping("/data/code/{typeCode}")
    public Result<List<SysDictData>> dataListByCode(@PathVariable String typeCode) {
        return Result.success(sysDictDataService.listByTypeCode(typeCode));
    }

    /** 按类型编码新增字典选项（前端"回车保存新选项"，label 已存在则幂等返回已有记录） */
    @PostMapping("/data/code/{typeCode}")
    public Result<SysDictData> createDataByCode(@PathVariable String typeCode,
                                                @RequestBody java.util.Map<String, String> body) {
        return Result.success(sysDictDataService.saveOptionByTypeCode(typeCode, body.get("label")));
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
