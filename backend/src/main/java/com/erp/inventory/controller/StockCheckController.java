package com.erp.inventory.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.inventory.entity.StockCheck;
import com.erp.inventory.service.StockCheckService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/stock-checks")
@RequiredArgsConstructor
public class StockCheckController {

    private final StockCheckService stockCheckService;

    /** 分页查询 */
    @GetMapping("/page")
    public Result<PageResult<StockCheck>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(stockCheckService.pageQuery(current, size, keyword, status));
    }

    /** 全量列表 */
    @GetMapping("/list")
    public Result<List<StockCheck>> list() {
        return Result.success(stockCheckService.list());
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<StockCheck> detail(@PathVariable Long id) {
        return Result.success(stockCheckService.getDetail(id));
    }

    /** 新增 */
    @PostMapping
    public Result<StockCheck> create(@RequestBody StockCheck check) {
        stockCheckService.saveCheckWithItems(check, check.getItems());
        return Result.success(check);
    }

    /** 编辑 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody StockCheck check) {
        check.setCheckId(id);
        stockCheckService.saveCheckWithItems(check, check.getItems());
        return Result.success();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        stockCheckService.removeById(id);
        return Result.success();
    }

    /** 审核: 草稿 → 已审核 (盘盈入库 + 盘亏出库) */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest req) {
        stockCheckService.approve(id, (String) req.getAttribute("currentUsername"));
        return Result.success();
    }

    /** 关闭: 已审核 → 已关闭 */
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              HttpServletRequest req) {
        stockCheckService.close(id, (String) req.getAttribute("currentUsername"), reason);
        return Result.success();
    }

    /** 取消: 草稿 → 已取消 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        stockCheckService.cancel(id, reason);
        return Result.success();
    }
}
