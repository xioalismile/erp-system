package com.erp.inventory.batch.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.inventory.batch.entity.BatchInfo;
import com.erp.inventory.batch.entity.BatchInventory;
import com.erp.inventory.batch.service.BatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @GetMapping
    public Result<PageResult<BatchInfo>> pageQuery(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(batchService.pageQuery(current, size, keyword, status, materialId, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<BatchInfo> getDetail(@PathVariable Long id) {
        return Result.success(batchService.getDetail(id));
    }

    @GetMapping("/{id}/inventory")
    public Result<List<BatchInventory>> getBatchInventory(@PathVariable Long id) {
        return Result.success(batchService.getBatchInventory(id));
    }

    @GetMapping("/available")
    public Result<List<BatchInfo>> getAvailableBatches(
            @RequestParam Long materialId,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(batchService.getAvailableBatches(materialId, warehouseId));
    }

    @PostMapping
    public Result<BatchInfo> createBatch(@RequestBody BatchInfo batchInfo) {
        return Result.success(batchService.createBatch(batchInfo));
    }

    @PostMapping("/check-expiry")
    public Result<Integer> checkExpiry() {
        return Result.success(batchService.checkExpiry());
    }

    @PostMapping("/inbound")
    public Result<Void> inboundBatch(@RequestBody Map<String, Object> params) {
        String batchNo = (String) params.get("batchNo");
        Long warehouseId = params.get("warehouseId") != null ? Long.valueOf(params.get("warehouseId").toString()) : null;
        String warehouseName = (String) params.get("warehouseName");
        Long locationId = params.get("locationId") != null ? Long.valueOf(params.get("locationId").toString()) : null;
        String locationName = (String) params.get("locationName");
        BigDecimal quantity = params.get("quantity") != null ? new BigDecimal(params.get("quantity").toString()) : null;
        batchService.inboundBatch(batchNo, warehouseId, warehouseName, locationId, locationName, quantity);
        return Result.success();
    }

    @PostMapping("/outbound")
    public Result<Void> outboundBatch(@RequestBody Map<String, Object> params) {
        String batchNo = (String) params.get("batchNo");
        Long warehouseId = params.get("warehouseId") != null ? Long.valueOf(params.get("warehouseId").toString()) : null;
        BigDecimal quantity = params.get("quantity") != null ? new BigDecimal(params.get("quantity").toString()) : null;
        batchService.outboundBatch(batchNo, warehouseId, quantity);
        return Result.success();
    }
}
