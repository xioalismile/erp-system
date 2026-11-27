package com.erp.produce.dashboard.service;

import com.erp.common.PageResult;
import com.erp.produce.dashboard.dto.*;

import java.util.List;

public interface ProductionDashboardService {

    ProductionOverviewDTO getOverview();

    List<OrderStatusStatsDTO> getOrderStatusStats();

    PageResult<ProductionProgressDTO> getProductionProgress(int current, int size);

    List<WorkshopOutputStatsDTO> getWorkshopOutputStats();

    List<EmployeeRankingDTO> getEmployeeRanking(String month);

    OutputTrendDTO getTodayOutputTrend();

    OutputTrendDTO getWeekOutputTrend();
}