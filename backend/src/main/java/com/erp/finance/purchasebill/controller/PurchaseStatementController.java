package com.erp.finance.purchasebill.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.finance.purchasebill.entity.PurchaseStatement;
import com.erp.finance.purchasebill.service.PurchaseStatementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/purchase-statement")
@RequiredArgsConstructor
public class PurchaseStatementController {

    private final PurchaseStatementService purchaseStatementService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<PurchaseStatement>> pageList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(purchaseStatementService.pageQuery(current, size, keyword, status, scopeCtx, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<PurchaseStatement> getDetail(@PathVariable Long id) {
        return Result.success(purchaseStatementService.getDetail(id));
    }

    @PostMapping
    public Result<PurchaseStatement> save(@RequestBody PurchaseStatement statement, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        statement.setCreateBy(operator);
        return Result.success(purchaseStatementService.saveStatement(statement, statement.getItems()));
    }

    @PostMapping("/generate")
    public Result<PurchaseStatement> generateStatement(@RequestParam Long supplierId,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(purchaseStatementService.createStatement(supplierId, startDate, endDate));
    }

    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        purchaseStatementService.confirm(id);
        return Result.success();
    }

    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        purchaseStatementService.close(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseStatementService.delete(id);
        return Result.success();
    }
}
