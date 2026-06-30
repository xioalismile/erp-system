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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryTransactionService transactionService;

    /**
     * 分页查询即时库存
     * 简化处理: 仅支持按 materialId 精确查询; 物料编码/名称模糊搜索请前端先调 material 接口选物料后传 materialId
     */
    @GetMapping("/list")
    public Result<PageResult<Inventory>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long stockId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String batchNo,
            @RequestParam(defaultValue = "false") Boolean onlyPositive) {
        QueryWrapper<Inventory> wrapper = new QueryWrapper<>();
        if (stockId != null) {
            wrapper.eq("stock_id", stockId);
        }
        if (materialId != null) {
            wrapper.eq("material_id", materialId);
        }
        if (batchNo != null && !batchNo.isBlank()) {
            wrapper.eq("batch_no", batchNo);
        }
        if (Boolean.TRUE.equals(onlyPositive)) {
            wrapper.gt("quantity", 0);
        }
        wrapper.orderByDesc("inventory_id");
        Page<Inventory> page = new Page<>(current, size);
        Page<Inventory> result = inventoryService.page(page, wrapper);
        return Result.success(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /**
     * 库存预警列表 (库存数量 < 物料最小库存)
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
            @RequestParam Long materialId,
            @RequestParam(required = false) Long stockId) {
        return Result.success(transactionService.getMaterialTransactions(materialId, stockId));
    }

    /**
     * 单据库存流水
     */
    @GetMapping("/transactions/{transNo}")
    public Result<List<InventoryTransaction>> documentTransactions(@PathVariable String transNo) {
        return Result.success(transactionService.getDocumentTransactions(transNo));
    }
}
