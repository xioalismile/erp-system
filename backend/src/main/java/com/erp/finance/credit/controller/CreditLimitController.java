package com.erp.finance.credit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.Result;
import com.erp.finance.credit.dto.CreditAdjustRequest;
import com.erp.finance.credit.dto.CreditCheckRequest;
import com.erp.finance.credit.dto.CreditCheckResult;
import com.erp.finance.credit.dto.CreditStatistics;
import com.erp.finance.credit.entity.CreditLimit;
import com.erp.finance.credit.entity.CreditLimitLog;
import com.erp.finance.credit.service.CreditLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/credit-limit")
@RequiredArgsConstructor
public class CreditLimitController {

    private final CreditLimitService creditLimitService;

    @GetMapping
    public Result<Page<CreditLimit>> pageQuery(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(creditLimitService.pageQuery(current, size, keyword, status, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<CreditLimit> getDetail(@PathVariable Long id) {
        return Result.success(creditLimitService.getDetail(id));
    }

    @GetMapping("/customer/{customerId}")
    public Result<CreditLimit> getByCustomerId(@PathVariable Long customerId) {
        return Result.success(creditLimitService.getByCustomerId(customerId));
    }

    @PostMapping
    public Result<CreditLimit> create(@RequestBody CreditLimit creditLimit) {
        return Result.success(creditLimitService.createCreditLimit(creditLimit));
    }

    @PutMapping
    public Result<CreditLimit> update(@RequestBody CreditLimit creditLimit) {
        return Result.success(creditLimitService.updateCreditLimit(creditLimit));
    }

    @PutMapping("/{id}/adjust")
    public Result<CreditLimit> adjust(@PathVariable Long id,
                                      @RequestBody CreditAdjustRequest request) {
        return Result.success(creditLimitService.adjustCreditLimit(
                id, request.getChangeAmount(), request.getReason()));
    }

    @PutMapping("/{id}/freeze")
    public Result<Void> freeze(@PathVariable Long id) {
        creditLimitService.freezeCreditLimit(id);
        return Result.success();
    }

    @PutMapping("/{id}/unfreeze")
    public Result<Void> unfreeze(@PathVariable Long id) {
        creditLimitService.unfreezeCreditLimit(id);
        return Result.success();
    }

    @GetMapping("/{id}/log")
    public Result<Page<CreditLimitLog>> getLog(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(creditLimitService.getCreditLog(id, current, size));
    }

    @PostMapping("/check")
    public Result<CreditCheckResult> check(@RequestBody CreditCheckRequest request) {
        return Result.success(creditLimitService.checkCreditLimit(
                request.getCustomerId(), request.getAmount()));
    }

    @GetMapping("/statistics")
    public Result<CreditStatistics> getStatistics() {
        return Result.success(creditLimitService.getCreditStatistics());
    }
}
