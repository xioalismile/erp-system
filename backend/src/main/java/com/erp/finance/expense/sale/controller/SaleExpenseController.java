package com.erp.finance.expense.sale.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.finance.expense.sale.entity.SaleExpense;
import com.erp.finance.expense.sale.service.SaleExpenseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/sale-expense")
@RequiredArgsConstructor
public class SaleExpenseController {

    private final SaleExpenseService saleExpenseService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<SaleExpense>> pageList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(saleExpenseService.pageQuery(current, size, keyword, status, scopeCtx, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<SaleExpense> getDetail(@PathVariable Long id) {
        return Result.success(saleExpenseService.getDetail(id));
    }

    @PostMapping
    public Result<SaleExpense> save(@RequestBody SaleExpense expense, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        expense.setCreateBy(operator);
        return Result.success(saleExpenseService.saveExpense(expense, expense.getItems()));
    }

    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        saleExpenseService.audit(id);
        return Result.success();
    }

    @PutMapping("/{id}/void")
    public Result<Void> voidExpense(@PathVariable Long id) {
        saleExpenseService.voidExpense(id);
        return Result.success();
    }

    @PostMapping("/{id}/allocate")
    public Result<Void> allocate(@PathVariable Long id) {
        saleExpenseService.allocateCost(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        saleExpenseService.delete(id);
        return Result.success();
    }
}
