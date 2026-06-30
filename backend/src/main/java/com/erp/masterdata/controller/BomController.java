package com.erp.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.Material;
import com.erp.masterdata.service.BomService;
import com.erp.masterdata.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bom")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;
    private final MaterialService materialService;

    /** 查询某物料的BOM列表 */
    @GetMapping("/list")
    public Result<List<Bom>> list(@RequestParam String parentMaterialCode) {
        return Result.success(bomService.list(
                new QueryWrapper<Bom>().eq("parent_material_code", parentMaterialCode)));
    }

    /** 新增BOM项 */
    @PostMapping
    public Result<Bom> create(@RequestBody Bom bom) {
        if (bom.getVersion() == null) bom.setVersion("1.0");
        bomService.save(bom);
        return Result.success(bom);
    }

    /** 更新BOM项 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Bom bom) {
        bom.setId(id);
        bomService.updateById(bom);
        return Result.success();
    }

    /** 删除BOM项 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bomService.removeById(id);
        return Result.success();
    }

    /** BOM树形展开 */
    @GetMapping("/expand")
    public Result<Map<String, Object>> expand(
            @RequestParam String materialCode,
            @RequestParam(defaultValue = "1") BigDecimal quantity) {
        return Result.success(bomService.expandBom(materialCode, quantity));
    }

    /** BOM扁平化展开(按物料聚合) */
    @GetMapping("/flatten")
    public Result<List<Map<String, Object>>> flatten(
            @RequestParam String materialCode,
            @RequestParam(defaultValue = "1") BigDecimal quantity) {
        return Result.success(bomService.flattenBom(materialCode, quantity));
    }
}
