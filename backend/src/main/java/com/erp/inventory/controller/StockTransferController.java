package com.erp.inventory.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.inventory.entity.StockTransfer;
import com.erp.inventory.service.StockTransferService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @GetMapping("/page")
    public Result<PageResult<StockTransfer>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(stockTransferService.pageQuery(current, size, keyword, status));
    }

    @GetMapping("/list")
    public Result<List<StockTransfer>> list() {
        return Result.success(stockTransferService.list());
    }

    @GetMapping("/{id}")
    public Result<StockTransfer> detail(@PathVariable Long id) {
        return Result.success(stockTransferService.getDetail(id));
    }

    @PostMapping
    public Result<StockTransfer> create(@RequestBody StockTransfer transfer) {
        stockTransferService.saveTransferWithItems(transfer, transfer.getItems());
        return Result.success(transfer);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody StockTransfer transfer) {
        transfer.setTransferId(id);
        stockTransferService.saveTransferWithItems(transfer, transfer.getItems());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        stockTransferService.removeById(id);
        return Result.success();
    }

    /** 审核: 草稿 → 已审核 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        stockTransferService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已审核 → 已关闭 */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              HttpServletRequest req) {
        stockTransferService.close(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        stockTransferService.cancel(id, reason);
        return Result.success();
    }
}
