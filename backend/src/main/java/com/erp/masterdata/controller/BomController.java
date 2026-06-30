package com.erp.masterdata.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.service.BomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/masterdata/bom")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<Bom>> page(@RequestParam(defaultValue = "1") int current,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String status) {
        return Result.success(bomService.pageQuery(current, size, keyword, status));
    }

    /** 全量列表 */
    @GetMapping("/list")
    public Result<List<Bom>> list() {
        return Result.success(bomService.list());
    }

    /** 详情(含明细) */
    @GetMapping("/{id}")
    public Result<Bom> detail(@PathVariable Long id) {
        return Result.success(bomService.getDetail(id));
    }

    /** 新增 */
    @PostMapping
    public Result<Bom> create(@RequestBody Bom bom) {
        bomService.saveBomWithItems(bom, bom.getItems());
        return Result.success(bom);
    }

    /** 编辑 */
    @PutMapping("/{id}")
    public Result<Bom> update(@PathVariable Long id, @RequestBody Bom bom) {
        bom.setBomId(id);
        bomService.saveBomWithItems(bom, bom.getItems());
        return Result.success(bom);
    }

    /** 删除 */
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
