package com.erp.finance.salebill.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.finance.salebill.entity.SaleStatement;
import com.erp.finance.salebill.service.SaleStatementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/sale-statement")
@RequiredArgsConstructor
public class SaleStatementController {

    private final SaleStatementService saleStatementService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    @GetMapping
    public Result<PageResult<SaleStatement>> pageList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        // 数据权限（2026-09-02 配置驱动）：按角色 data_scope 决定可见范围
        com.erp.common.scope.DataScopeResolver.DataScopeContext scopeCtx = dataScopeResolver.resolve(request);
        return Result.success(saleStatementService.pageQuery(current, size, keyword, status, scopeCtx, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<SaleStatement> getDetail(@PathVariable Long id) {
        return Result.success(saleStatementService.getDetail(id));
    }

    @PostMapping
    public Result<SaleStatement> save(@RequestBody SaleStatement statement, HttpServletRequest req) {
        String operator = (String) req.getAttribute("currentUsername");
        statement.setCreateBy(operator);
        return Result.success(saleStatementService.saveStatement(statement, statement.getItems()));
    }

    @PostMapping("/generate")
    public Result<SaleStatement> generateStatement(@RequestParam Long customerId,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(saleStatementService.createStatement(customerId, startDate, endDate));
    }

    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        saleStatementService.confirm(id);
        return Result.success();
    }

    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        saleStatementService.close(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        saleStatementService.delete(id);
        return Result.success();
    }
}
