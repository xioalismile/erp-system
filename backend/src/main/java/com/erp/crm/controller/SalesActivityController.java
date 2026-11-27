package com.erp.crm.controller;

import com.erp.common.Result;
import com.erp.crm.service.SalesActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 销售过程动作量化（2026-10-07）
 *
 * <p>每日任务 / 本周任务 / 今日任务清单 / 过程奖金核算。全部走数据权限（本人 / 本部门 / 全部）。</p>
 */
@RestController
@RequestMapping("/api/crm/sales-activity")
@RequiredArgsConstructor
public class SalesActivityController {

    private final SalesActivityService salesActivityService;
    private final com.erp.common.scope.DataScopeResolver dataScopeResolver;

    /** 每日任务 + 本周任务 + 今日任务清单 */
    @GetMapping("/daily")
    public Result<Map<String, Object>> daily(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long staffId,
            jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext ctx = dataScopeResolver.resolve(request);
        LocalDate day = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return Result.success(salesActivityService.daily(day, staffId, ctx));
    }

    /** 过程奖金核算（月度） */
    @GetMapping("/bonus")
    public Result<Map<String, Object>> bonus(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Long staffId,
            jakarta.servlet.http.HttpServletRequest request) {
        com.erp.common.scope.DataScopeResolver.DataScopeContext ctx = dataScopeResolver.resolve(request);
        LocalDate now = LocalDate.now();
        int y = year == null ? now.getYear() : year;
        int m = month == null ? now.getMonthValue() : month;
        return Result.success(salesActivityService.bonus(y, m, staffId, ctx));
    }
}
