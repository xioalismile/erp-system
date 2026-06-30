package com.erp.inventory.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.inventory.entity.Inventory;
import com.erp.inventory.entity.InventoryTransaction;
import com.erp.inventory.service.InventoryService;
import com.erp.inventory.service.InventoryTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryTransactionService transactionService;

    @GetMapping("/list")
    public Result<PageResult<Inventory>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String warehouse) {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        if (materialCode != null && !materialCode.isBlank()) wrapper.like("material_code", materialCode);
        if (materialName != null && !materialName.isBlank()) wrapper.like("material_name", materialName);
        if (warehouse != null && !warehouse.isBlank()) wrapper.eq("warehouse_code", warehouse);
        wrapper.orderByAsc("material_code");
        Page<Inventory> page = new Page<>(current, size);
        Page<Inventory> result = inventoryService.page(page, wrapper);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /**
     * 库存预警查询(低于安全库存)
     */
    @GetMapping("/warning")
    public Result<List<Inventory>> warning() {
        return Result.success(inventoryService.getWarningList());
    }

    /**
     * 物料库存流水
     */
    @GetMapping("/transactions")
    public Result<List<InventoryTransaction>> transactions(
            @RequestParam String materialCode,
            @RequestParam(required = false) String warehouse) {
        return Result.success(transactionService.getMaterialTransactions(materialCode, warehouse));
    }

    /**
     * 单据库存流水
     */
    @GetMapping("/transactions/{documentNo}")
    public Result<List<InventoryTransaction>> documentTransactions(@PathVariable String documentNo) {
        return Result.success(transactionService.getDocumentTransactions(documentNo));
    }
}
