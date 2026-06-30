package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.inventory.entity.InventoryBatch;
import com.erp.inventory.service.InventoryBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/batch")
@RequiredArgsConstructor
public class InventoryBatchController {

    private final InventoryBatchService batchService;

    @GetMapping("/list")
    public Result<List<InventoryBatch>> list(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String batchNo,
            @RequestParam(defaultValue = "false") Boolean onlyExpired) {
        if (Boolean.TRUE.equals(onlyExpired)) {
            return Result.success(batchService.getExpiredBatches());
        }
        QueryWrapper<InventoryBatch> wrapper = new QueryWrapper<>();
        if (materialId != null) wrapper.eq("material_id", materialId);
        if (batchNo != null && !batchNo.isBlank()) wrapper.like("batch_no", batchNo);
        wrapper.orderByAsc("expiry_date");
        return Result.success(batchService.list(wrapper));
    }

    @GetMapping("/material/{materialId}")
    public Result<List<InventoryBatch>> getByMaterial(@PathVariable Long materialId) {
        return Result.success(batchService.getByMaterial(materialId));
    }

    @GetMapping("/{id}")
    public Result<InventoryBatch> getById(@PathVariable Long id) {
        return Result.success(batchService.getById(id));
    }

    @GetMapping("/expired")
    public Result<List<InventoryBatch>> getExpired() {
        return Result.success(batchService.getExpiredBatches());
    }

    @GetMapping("/expiring")
    public Result<List<InventoryBatch>> getExpiring(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(batchService.getExpiringBatches(days));
    }

    @PostMapping("/allocate")
    public Result<Map<String, Object>> allocate(
            @RequestParam Long materialId,
            @RequestParam BigDecimal quantity) {
        return Result.success(batchService.allocateByFifo(materialId, quantity));
    }

    @PostMapping
    public Result<InventoryBatch> create(@RequestBody InventoryBatch batch) {
        batchService.addBatch(batch);
        return Result.success(batch);
    }

    @PutMapping("/{id}")
    public Result<InventoryBatch> update(@PathVariable Long id, @RequestBody InventoryBatch batch) {
        batch.setBatchId(id);
        batchService.updateById(batch);
        return Result.success(batch);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        batchService.removeById(id);
        return Result.success();
    }
}
