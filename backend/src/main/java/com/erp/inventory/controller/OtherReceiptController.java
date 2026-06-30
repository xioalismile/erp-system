package com.erp.inventory.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.inventory.entity.OtherReceipt;
import com.erp.inventory.service.OtherReceiptService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/other-receipts")
@RequiredArgsConstructor
public class OtherReceiptController {

    private final OtherReceiptService otherReceiptService;

    @GetMapping("/page")
    public Result<PageResult<OtherReceipt>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(otherReceiptService.pageQuery(current, size, keyword, status));
    }

    @GetMapping("/list")
    public Result<List<OtherReceipt>> list() {
        return Result.success(otherReceiptService.list());
    }

    @GetMapping("/{id}")
    public Result<OtherReceipt> detail(@PathVariable Long id) {
        return Result.success(otherReceiptService.getDetail(id));
    }

    @PostMapping
    public Result<OtherReceipt> create(@RequestBody OtherReceipt receipt) {
        otherReceiptService.saveReceiptWithItems(receipt, receipt.getItems());
        return Result.success(receipt);
    }

    @PutMapping("/{id}")
    public Result<OtherReceipt> update(@PathVariable Long id, @RequestBody OtherReceipt receipt) {
        receipt.setReceiptId(id);
        otherReceiptService.saveReceiptWithItems(receipt, receipt.getItems());
        return Result.success(receipt);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        otherReceiptService.removeById(id);
        return Result.success();
    }

    /** 审核: 草稿 → 已审核 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        otherReceiptService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已审核 → 已关闭 */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              HttpServletRequest req) {
        otherReceiptService.close(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestParam(required = false) String reason,
                               HttpServletRequest req) {
        otherReceiptService.cancel(id, reason);
        return Result.success();
    }
}
