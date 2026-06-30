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
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String batchNo) {
        QueryWrapper<InventoryBatch> wrapper = new QueryWrapper<>();
        if (materialCode != null && !materialCode.isBlank()) wrapper.like("material_code", materialCode);
        if (warehouseCode != null && !warehouseCode.isBlank()) wrapper.eq("warehouse_code", warehouseCode);
        if (batchNo != null && !batchNo.isBlank()) wrapper.like("batch_no", batchNo);
        wrapper.orderByAsc("warehouse_code").orderByAsc("material_code").orderByAsc("production_date");
        return Result.success(batchService.list(wrapper));
    }

    @GetMapping("/warehouse/{warehouseCode}")
    public Result<List<InventoryBatch>> getByWarehouse(@PathVariable String warehouseCode) {
        return Result.success(batchService.getByWarehouse(warehouseCode));
    }

    @GetMapping("/material/{materialCode}/warehouse/{warehouseCode}")
    public Result<List<InventoryBatch>> getByMaterialAndWarehouse(
            @PathVariable String materialCode,
            @PathVariable String warehouseCode) {
        return Result.success(batchService.getByMaterialAndWarehouse(materialCode, warehouseCode));
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
            @RequestParam String materialCode,
            @RequestParam String warehouseCode,
            @RequestParam BigDecimal quantity) {
        return Result.success(batchService.allocateByFifo(materialCode, warehouseCode, quantity));
    }

    @PostMapping
    public Result<InventoryBatch> create(@RequestBody InventoryBatch batch) {
        batchService.addBatch(batch);
        return Result.success(batch);
    }

    @PutMapping("/{id}")
    public Result<InventoryBatch> update(@PathVariable Long id, @RequestBody InventoryBatch batch) {
        batch.setId(id);
        batchService.updateById(batch);
        return Result.success(batch);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        batchService.removeById(id);
        return Result.success();
    }
}