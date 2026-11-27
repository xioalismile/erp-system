package com.erp.produce.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.scope.DataScopeResolver;
import com.erp.produce.dashboard.dto.*;
import com.erp.produce.dashboard.service.ProductionDashboardService;
import com.erp.produce.mrp.entity.MrpPlan;
import com.erp.produce.mrp.mapper.MrpPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionDashboardServiceImpl implements ProductionDashboardService {

    private final MrpPlanMapper mrpPlanMapper;
    private final DataScopeResolver dataScopeResolver;

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * MRP 计划查询的统一入口：始终套统一数据权限（与 MRP 计划列表页同口径，
     * 创建人列沿用本表现有列名 create_by）；非 Web 上下文/全部数据角色不追加条件。
     */
    private List<MrpPlan> scopedPlans() {
        QueryWrapper<MrpPlan> wrapper = new QueryWrapper<>();
        dataScopeResolver.applyIfLimited(wrapper, null, "create_by");
        return mrpPlanMapper.selectList(wrapper);
    }

    @Override
    public ProductionOverviewDTO getOverview() {
        ProductionOverviewDTO overview = new ProductionOverviewDTO();

        List<MrpPlan> plans = scopedPlans();
        
        BigDecimal todayOutputQty = BigDecimal.ZERO;
        BigDecimal todayOutputValue = BigDecimal.ZERO;
        int inProgressCount = 0;
        int pendingCount = 0;

        LocalDate today = LocalDate.now();
        for (MrpPlan plan : plans) {
            if (plan.getPlanStatus() != null) {
                if (plan.getPlanStatus() == 3) {
                    todayOutputQty = todayOutputQty.add(plan.getTotalProduceSuggestion() != null ? plan.getTotalProduceSuggestion() : BigDecimal.ZERO);
                    todayOutputValue = todayOutputValue.add(plan.getTotalProduceSuggestion() != null ? plan.getTotalProduceSuggestion().multiply(new BigDecimal("420")) : BigDecimal.ZERO);
                } else if (plan.getPlanStatus() == 2) {
                    inProgressCount++;
                } else if (plan.getPlanStatus() == 1) {
                    pendingCount++;
                }
            }
        }

        overview.setTodayOutputQty(todayOutputQty.intValue());
        overview.setTodayOutputValue(todayOutputValue.setScale(SCALE, RoundingMode.HALF_UP));
        overview.setInProgressOrderCount(inProgressCount);
        overview.setPendingOrderCount(pendingCount);

        overview.setTotalEfficiency(new BigDecimal("85.5").setScale(SCALE, RoundingMode.HALF_UP));
        overview.setOnTimeDeliveryRate(new BigDecimal("92.3").setScale(SCALE, RoundingMode.HALF_UP));
        overview.setWorkshopCount(3);
        overview.setEmployeeCount(28);

        return overview;
    }

    @Override
    public List<OrderStatusStatsDTO> getOrderStatusStats() {
        Map<String, Integer> statusCount = new HashMap<>();
        statusCount.put("草稿", 0);
        statusCount.put("已生效", 0);
        statusCount.put("执行中", 0);
        statusCount.put("已执行", 0);
        statusCount.put("已关闭", 0);

        List<MrpPlan> plans = scopedPlans();
        int total = plans.size();

        for (MrpPlan plan : plans) {
            if (plan.getPlanStatus() != null) {
                String status = switch (plan.getPlanStatus()) {
                    case 0 -> "草稿";
                    case 1 -> "已生效";
                    case 2 -> "执行中";
                    case 3 -> "已执行";
                    case 4 -> "已关闭";
                    default -> "草稿";
                };
                statusCount.merge(status, 1, Integer::sum);
            }
        }

        List<OrderStatusStatsDTO> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : statusCount.entrySet()) {
            BigDecimal percentage = total > 0 ?
                    new BigDecimal(entry.getValue()).multiply(HUNDRED).divide(new BigDecimal(total), SCALE, RoundingMode.HALF_UP) :
                    BigDecimal.ZERO;
            result.add(new OrderStatusStatsDTO(entry.getKey(), entry.getValue(), percentage));
        }

        return result;
    }

    @Override
    public PageResult<ProductionProgressDTO> getProductionProgress(int current, int size) {
        List<ProductionProgressDTO> progressList = new ArrayList<>();

        progressList.add(createProgressDTO(1L, "MO-2024-001", "低音扬声器-5寸", new BigDecimal("500"), new BigDecimal("420"), "执行中"));
        progressList.add(createProgressDTO(2L, "MO-2024-002", "高音扬声器-1寸", new BigDecimal("300"), new BigDecimal("180"), "执行中"));
        progressList.add(createProgressDTO(3L, "MO-2024-003", "分频器-X2", new BigDecimal("200"), new BigDecimal("200"), "已完工"));
        progressList.add(createProgressDTO(4L, "MO-2024-004", "箱体-A1", new BigDecimal("800"), new BigDecimal("600"), "执行中"));
        progressList.add(createProgressDTO(5L, "MO-2024-005", "低音扬声器-6寸", new BigDecimal("400"), new BigDecimal("0"), "已下达"));

        return PageResult.of(progressList, progressList.size(), current, size);
    }

    private ProductionProgressDTO createProgressDTO(Long orderId, String orderNo, String productName,
                                                     BigDecimal planQty, BigDecimal finishedQty, String status) {
        ProductionProgressDTO dto = new ProductionProgressDTO();
        dto.setOrderId(orderId);
        dto.setOrderNo(orderNo);
        dto.setProductName(productName);
        dto.setPlanQty(planQty);
        dto.setFinishedQty(finishedQty);
        dto.setStatus(status);
        
        if (planQty.compareTo(BigDecimal.ZERO) > 0) {
            dto.setProgressRate(finishedQty.multiply(HUNDRED).divide(planQty, SCALE, RoundingMode.HALF_UP));
        } else {
            dto.setProgressRate(BigDecimal.ZERO);
        }
        return dto;
    }

    @Override
    public List<WorkshopOutputStatsDTO> getWorkshopOutputStats() {
        List<WorkshopOutputStatsDTO> result = new ArrayList<>();

        WorkshopOutputStatsDTO workshop1 = new WorkshopOutputStatsDTO();
        workshop1.setWorkshopId(1L);
        workshop1.setWorkshopName("冲压车间");
        workshop1.setOutputQty(new BigDecimal("1250"));
        workshop1.setOutputValue(new BigDecimal("525000"));
        workshop1.setEfficiency(new BigDecimal("88.5"));
        result.add(workshop1);

        WorkshopOutputStatsDTO workshop2 = new WorkshopOutputStatsDTO();
        workshop2.setWorkshopId(2L);
        workshop2.setWorkshopName("焊接车间");
        workshop2.setOutputQty(new BigDecimal("980"));
        workshop2.setOutputValue(new BigDecimal("411600"));
        workshop2.setEfficiency(new BigDecimal("82.3"));
        result.add(workshop2);

        WorkshopOutputStatsDTO workshop3 = new WorkshopOutputStatsDTO();
        workshop3.setWorkshopId(3L);
        workshop3.setWorkshopName("装配车间");
        workshop3.setOutputQty(new BigDecimal("850"));
        workshop3.setOutputValue(new BigDecimal("680000"));
        workshop3.setEfficiency(new BigDecimal("91.2"));
        result.add(workshop3);

        return result;
    }

    @Override
    public List<EmployeeRankingDTO> getEmployeeRanking(String month) {
        List<EmployeeRankingDTO> result = new ArrayList<>();

        EmployeeRankingDTO emp1 = new EmployeeRankingDTO();
        emp1.setEmployeeId(1L);
        emp1.setEmployeeName("张三");
        emp1.setOutputQty(new BigDecimal("285"));
        emp1.setWorkHours(new BigDecimal("176"));
        emp1.setEfficiency(new BigDecimal("95.2"));
        emp1.setOrderCount(12);
        result.add(emp1);

        EmployeeRankingDTO emp2 = new EmployeeRankingDTO();
        emp2.setEmployeeId(2L);
        emp2.setEmployeeName("李四");
        emp2.setOutputQty(new BigDecimal("268"));
        emp2.setWorkHours(new BigDecimal("180"));
        emp2.setEfficiency(new BigDecimal("91.5"));
        emp2.setOrderCount(11);
        result.add(emp2);

        EmployeeRankingDTO emp3 = new EmployeeRankingDTO();
        emp3.setEmployeeId(3L);
        emp3.setEmployeeName("王五");
        emp3.setOutputQty(new BigDecimal("256"));
        emp3.setWorkHours(new BigDecimal("168"));
        emp3.setEfficiency(new BigDecimal("93.8"));
        emp3.setOrderCount(10);
        result.add(emp3);

        EmployeeRankingDTO emp4 = new EmployeeRankingDTO();
        emp4.setEmployeeId(4L);
        emp4.setEmployeeName("赵六");
        emp4.setOutputQty(new BigDecimal("242"));
        emp4.setWorkHours(new BigDecimal("172"));
        emp4.setEfficiency(new BigDecimal("89.6"));
        emp4.setOrderCount(9);
        result.add(emp4);

        EmployeeRankingDTO emp5 = new EmployeeRankingDTO();
        emp5.setEmployeeId(5L);
        emp5.setEmployeeName("钱七");
        emp5.setOutputQty(new BigDecimal("228"));
        emp5.setWorkHours(new BigDecimal("165"));
        emp5.setEfficiency(new BigDecimal("88.3"));
        emp5.setOrderCount(8);
        result.add(emp5);

        return result;
    }

    @Override
    public OutputTrendDTO getTodayOutputTrend() {
        OutputTrendDTO trend = new OutputTrendDTO();

        List<String> hours = new ArrayList<>();
        List<BigDecimal> quantities = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();

        for (int i = 0; i <= currentHour && i <= 23; i++) {
            hours.add(String.format("%02d:00", i));

            if (i <= currentHour) {
                BigDecimal qty = new BigDecimal((int) (Math.random() * 80 + 50));
                quantities.add(qty);
                values.add(qty.multiply(new BigDecimal("420")).setScale(SCALE, RoundingMode.HALF_UP));
            } else {
                quantities.add(BigDecimal.ZERO);
                values.add(BigDecimal.ZERO);
            }
        }

        trend.setHours(hours);
        trend.setQuantities(quantities);
        trend.setValues(values);

        return trend;
    }

    @Override
    public OutputTrendDTO getWeekOutputTrend() {
        OutputTrendDTO trend = new OutputTrendDTO();

        List<String> days = new ArrayList<>();
        List<BigDecimal> quantities = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        LocalDate today = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            days.add(date.format(formatter));

            BigDecimal qty = new BigDecimal((int) (Math.random() * 300 + 800));
            quantities.add(qty);
            values.add(qty.multiply(new BigDecimal("420")).setScale(SCALE, RoundingMode.HALF_UP));
        }

        trend.setHours(days);
        trend.setQuantities(quantities);
        trend.setValues(values);

        return trend;
    }
}
