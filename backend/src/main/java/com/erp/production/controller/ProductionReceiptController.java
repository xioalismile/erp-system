package com.erp.production.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.service.ProductionReceiptService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production/receipts")
@RequiredArgsConstructor
public class ProductionReceiptController {

    private final ProductionReceiptService productionReceiptService;

    @GetMapping("/page")
    public Result<PageResult<ProductionReceipt>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(productionReceiptService.pageQuery(current, size, keyword, status));
    }

    @GetMapping("/list")
    public Result<List<ProductionReceipt>> list() {
        return Result.success(productionReceiptService.list());
    }

    @GetMapping("/{id}")
    public Result<ProductionReceipt> detail(@PathVariable Long id) {
        return Result.success(productionReceiptService.getDetail(id));
    }

    @PostMapping
    public Result<ProductionReceipt> create(@RequestBody ProductionReceipt receipt) {
        productionReceiptService.saveReceiptWithItems(receipt, receipt.getItems());
        return Result.success(receipt);
    }

    @PutMapping("/{id}")
    public Result<ProductionReceipt> update(@PathVariable Long id, @RequestBody ProductionReceipt receipt) {
        receipt.setReceiptId(id);
        productionReceiptService.saveReceiptWithItems(receipt, receipt.getItems());
        return Result.success(receipt);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productionReceiptService.removeById(id);
        return Result.success();
    }

    /** 审核: 草稿 → 已审核 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        productionReceiptService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已审核 → 已关闭 */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              HttpServletRequest req) {
        productionReceiptService.close(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestParam(required = false) String reason,
                               HttpServletRequest req) {
        productionReceiptService.cancel(id, reason);
        return Result.success();
    }
}
