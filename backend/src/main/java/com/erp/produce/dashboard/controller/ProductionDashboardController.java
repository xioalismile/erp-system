package com.erp.produce.dashboard.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.produce.dashboard.dto.*;
import com.erp.produce.dashboard.service.ProductionDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production-dashboard")
@RequiredArgsConstructor
public class ProductionDashboardController {

    private final ProductionDashboardService dashboardService;

    @GetMapping("/overview")
    public Result<ProductionOverviewDTO> getOverview() {
        return Result.success(dashboardService.getOverview());
    }

    @GetMapping("/order-status-stats")
    public Result<List<OrderStatusStatsDTO>> getOrderStatusStats() {
        return Result.success(dashboardService.getOrderStatusStats());
    }

    @GetMapping("/production-progress")
    public Result<PageResult<ProductionProgressDTO>> getProductionProgress(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(dashboardService.getProductionProgress(current, size));
    }

    @GetMapping("/workshop-output-stats")
    public Result<List<WorkshopOutputStatsDTO>> getWorkshopOutputStats() {
        return Result.success(dashboardService.getWorkshopOutputStats());
    }

    @GetMapping("/employee-ranking")
    public Result<List<EmployeeRankingDTO>> getEmployeeRanking(
            @RequestParam(required = false) String month) {
        return Result.success(dashboardService.getEmployeeRanking(month));
    }

    @GetMapping("/today-output-trend")
    public Result<OutputTrendDTO> getTodayOutputTrend() {
        return Result.success(dashboardService.getTodayOutputTrend());
    }

    @GetMapping("/week-output-trend")
    public Result<OutputTrendDTO> getWeekOutputTrend() {
        return Result.success(dashboardService.getWeekOutputTrend());
    }
}