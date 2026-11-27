package com.erp.finance.expense.purchase.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.finance.expense.purchase.entity.PurchaseExpense;
import com.erp.finance.expense.purchase.service.PurchaseExpenseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/purchase-expense")
@RequiredArgsConstructor
public class PurchaseExpenseController {

    private final PurchaseExpenseService purchaseExpenseService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<PurchaseExpense>> pageList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(purchaseExpenseService.pageQuery(current, size, keyword, status, scopeCtx, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<PurchaseExpense> getDetail(@PathVariable Long id) {
        return Result.success(purchaseExpenseService.getDetail(id));
    }

    @PostMapping
    public Result<PurchaseExpense> save(@RequestBody PurchaseExpense expense, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        expense.setCreateBy(operator);
        return Result.success(purchaseExpenseService.saveExpense(expense, expense.getItems()));
    }

    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        purchaseExpenseService.audit(id);
        return Result.success();
    }

    @PutMapping("/{id}/void")
    public Result<Void> voidExpense(@PathVariable Long id) {
        purchaseExpenseService.voidExpense(id);
        return Result.success();
    }

    @PostMapping("/{id}/allocate")
    public Result<Void> allocate(@PathVariable Long id) {
        purchaseExpenseService.allocateCost(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseExpenseService.delete(id);
        return Result.success();
    }
}
