package com.erp.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.finance.cost.calculation.entity.CostCalculation;
import com.erp.finance.cost.calculation.mapper.CostCalculationMapper;
import com.erp.production.entity.OperationReport;
import com.erp.production.entity.WorkCenter;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderOperation;
import com.erp.production.mapper.OperationReportMapper;
import com.erp.production.mapper.WorkCenterMapper;
import com.erp.production.mapper.WorkOrderMapper;
import com.erp.production.mapper.WorkOrderOperationMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生产报表中心
 * 汇总端点：work-order-completion / efficiency / cost-analysis
 * 明细端点：work-order-completion-details / efficiency-details / cost-analysis-details
 *
 * 数据权限：统一走 DataScopeResolver（角色 sys_role.data_scope：全部/本部门/仅本人），
 *   生产类单据按 created_by 过滤（与生产工单/报工单列表页同口径）。
 */
@Slf4j
@RestController
@RequestMapping("/api/report/production")
@RequiredArgsConstructor
public class ProductionReportController {

    private final WorkOrderMapper workOrderMapper;
    private final OperationReportMapper operationReportMapper;
    private final CostCalculationMapper costCalculationMapper;
    private final WorkOrderOperationMapper workOrderOperationMapper;
    private final WorkCenterMapper workCenterMapper;
    private final DataScopeResolver dataScopeResolver;

    // ==================== 汇总报表 ====================

    /** 1. 工单完成率：按期完成率、延期工单清单 */
    @GetMapping("/work-order-completion")
    public Result<Map<String, Object>> workOrderCompletion(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<WorkOrder> qw = new QueryWrapper<WorkOrder>()
                .between("order_date", startDate, endDate);
        applyWorkOrderDataPermission(req, qw);
        List<WorkOrder> orders = workOrderMapper.selectList(qw);

        int total = orders.size();
        int completed = (int) orders.stream().filter(o -> "已完成".equalsIgnoreCase(o.getStatus())
                || o.getCloseStatus() != null && o.getCloseStatus() == 1).count();
        int inProgress = (int) orders.stream().filter(o -> "进行中".equalsIgnoreCase(o.getStatus())
                || "生产中".equalsIgnoreCase(o.getStatus())).count();

        // 按期完成率：actual_end_date <= plan_end_date
        List<Map<String, Object>> delayed = new ArrayList<>();
        int onTime = 0, totalFinished = 0;
        for (WorkOrder o : orders) {
            if (o.getActualEndDate() != null && o.getPlanEndDate() != null) {
                totalFinished++;
                if (!o.getActualEndDate().isAfter(o.getPlanEndDate())) {
                    onTime++;
                } else {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("orderNo", o.getOrderNo());
                    m.put("materialName", o.getMaterialName());
                    m.put("quantity", o.getQuantity());
                    m.put("planEndDate", o.getPlanEndDate());
                    m.put("actualEndDate", o.getActualEndDate());
                    m.put("delayDays", ChronoUnit.DAYS.between(o.getPlanEndDate(), o.getActualEndDate()));
                    delayed.add(m);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOrders", total);
        result.put("completed", completed);
        result.put("inProgress", inProgress);
        result.put("completionRate", total == 0 ? 0 : Math.round(completed * 10000.0 / total) / 100.0);
        result.put("onTimeCompletion", totalFinished == 0 ? 0 : Math.round(onTime * 10000.0 / totalFinished) / 100.0);
        result.put("onTimeCount", onTime);
        result.put("delayedCount", delayed.size());
        result.put("delayedOrders", delayed);
        return Result.success(result);
    }

    /** 2. 生产效率分析：工时/产量/良率 */
    @GetMapping("/efficiency")
    public Result<Map<String, Object>> efficiency(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<OperationReport> qw = new QueryWrapper<OperationReport>()
                .between("report_date", startDate, endDate);
        applyOperationDataPermission(req, qw);
        List<OperationReport> reports = operationReportMapper.selectList(qw);

        // 按员工分组
        Map<Long, List<OperationReport>> byEmployee = reports.stream()
                .filter(r -> r.getEmployeeId() != null)
                .collect(Collectors.groupingBy(OperationReport::getEmployeeId));

        List<Map<String, Object>> empList = byEmployee.entrySet().stream().map(e -> {
            BigDecimal totalQty = e.getValue().stream()
                    .map(r -> r.getTotalQuantity() == null ? BigDecimal.ZERO : r.getTotalQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal qualifiedQty = e.getValue().stream()
                    .map(r -> r.getQualifiedQuantity() == null ? BigDecimal.ZERO : r.getQualifiedQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal rejectQty = e.getValue().stream()
                    .map(r -> r.getRejectedQuantity() == null ? BigDecimal.ZERO : r.getRejectedQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal workHours = e.getValue().stream()
                    .map(r -> r.getWorkHours() == null ? BigDecimal.ZERO : r.getWorkHours())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", e.getKey());
            m.put("employeeName", e.getValue().get(0).getEmployeeName());
            m.put("reportCount", e.getValue().size());
            m.put("totalQuantity", totalQty);
            m.put("qualifiedQuantity", qualifiedQty);
            m.put("rejectedQuantity", rejectQty);
            m.put("yieldRate", totalQty.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    qualifiedQty.multiply(new BigDecimal("100")).divide(totalQty, 2, RoundingMode.HALF_UP));
            m.put("workHours", workHours);
            m.put("efficiency", workHours.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    totalQty.divide(workHours, 2, RoundingMode.HALF_UP));
            return m;
        }).sorted((a, b) -> ((BigDecimal) b.get("totalQuantity")).compareTo((BigDecimal) a.get("totalQuantity")))
                .collect(Collectors.toList());

        // 按工单汇总
        Map<Long, List<OperationReport>> byOrder = reports.stream()
                .filter(r -> r.getProductionOrderId() != null)
                .collect(Collectors.groupingBy(OperationReport::getProductionOrderId));
        List<Map<String, Object>> orderList = byOrder.entrySet().stream().map(e -> {
            BigDecimal totalQty = e.getValue().stream()
                    .map(r -> r.getTotalQuantity() == null ? BigDecimal.ZERO : r.getTotalQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal qualifiedQty = e.getValue().stream()
                    .map(r -> r.getQualifiedQuantity() == null ? BigDecimal.ZERO : r.getQualifiedQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal workHours = e.getValue().stream()
                    .map(r -> r.getWorkHours() == null ? BigDecimal.ZERO : r.getWorkHours())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productionOrderId", e.getKey());
            m.put("productionOrderNo", e.getValue().get(0).getProductionOrderNo());
            m.put("totalQuantity", totalQty);
            m.put("qualifiedQuantity", qualifiedQty);
            m.put("yieldRate", totalQty.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    qualifiedQty.multiply(new BigDecimal("100")).divide(totalQty, 2, RoundingMode.HALF_UP));
            m.put("workHours", workHours);
            return m;
        }).collect(Collectors.toList());

        BigDecimal totalYieldRate = reports.isEmpty() ? BigDecimal.ZERO :
                reports.stream().map(r -> r.getQualifiedQuantity() == null ? BigDecimal.ZERO : r.getQualifiedQuantity())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .multiply(new BigDecimal("100"))
                        .divide(reports.stream().map(r -> r.getTotalQuantity() == null ? BigDecimal.ZERO : r.getTotalQuantity())
                                .reduce(BigDecimal.ZERO, BigDecimal::add), 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReports", reports.size());
        result.put("totalYieldRate", totalYieldRate);
        result.put("byEmployee", empList);
        result.put("byOrder", orderList);
        return Result.success(result);
    }

    /** 3. 生产成本分析：订单成本构成、实际vs标准成本差异 */
    @GetMapping("/cost-analysis")
    public Result<Map<String, Object>> costAnalysis(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<CostCalculation> qw = new QueryWrapper<CostCalculation>()
                .between("calc_date", startDate, endDate);
        dataScopeResolver.resolve(req).applyTo(qw, null);
        List<CostCalculation> calcs = costCalculationMapper.selectList(qw);

        BigDecimal totalMaterial = calcs.stream()
                .map(c -> c.getDirectMaterialCost() == null ? BigDecimal.ZERO : c.getDirectMaterialCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLabor = calcs.stream()
                .map(c -> c.getDirectLaborCost() == null ? BigDecimal.ZERO : c.getDirectLaborCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOverhead = calcs.stream()
                .map(c -> c.getManufacturingOverhead() == null ? BigDecimal.ZERO : c.getManufacturingOverhead())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = calcs.stream()
                .map(c -> c.getTotalCost() == null ? BigDecimal.ZERO : c.getTotalCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> items = calcs.stream().map(c -> {
            BigDecimal cost = c.getTotalCost() == null ? BigDecimal.ZERO : c.getTotalCost();
            BigDecimal qty = c.getQuantity() == null ? BigDecimal.ZERO : c.getQuantity();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("calcNo", c.getCalcNo());
            m.put("materialCode", c.getMaterialCode());
            m.put("materialName", c.getMaterialName());
            m.put("quantity", qty);
            m.put("directMaterialCost", c.getDirectMaterialCost());
            m.put("directLaborCost", c.getDirectLaborCost());
            m.put("manufacturingOverhead", c.getManufacturingOverhead());
            m.put("totalCost", cost);
            m.put("unitCost", c.getUnitCost());
            m.put("status", c.getStatus());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCalculations", calcs.size());
        result.put("totalMaterialCost", totalMaterial);
        result.put("totalLaborCost", totalLabor);
        result.put("totalOverheadCost", totalOverhead);
        result.put("totalCost", totalCost);
        result.put("materialCostRatio", totalCost.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                totalMaterial.multiply(new BigDecimal("100")).divide(totalCost, 2, RoundingMode.HALF_UP));
        result.put("laborCostRatio", totalCost.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                totalLabor.multiply(new BigDecimal("100")).divide(totalCost, 2, RoundingMode.HALF_UP));
        result.put("overheadCostRatio", totalCost.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                totalOverhead.multiply(new BigDecimal("100")).divide(totalCost, 2, RoundingMode.HALF_UP));
        result.put("items", items);
        return Result.success(result);
    }

    // ==================== 明细报表（可导出） ====================

    /** 1-明细. 工单明细列表（支持时间/关键字/状态筛选，分页） */
    @GetMapping("/work-order-completion-details")
    public Result<Map<String, Object>> workOrderCompletionDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) Long managerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<WorkOrder> qw = new QueryWrapper<>();
        applyWorkOrderDataPermission(req, qw);
        if (startDate != null) qw.ge("order_date", startDate);
        if (endDate != null) qw.le("order_date", endDate);
        if (status != null && !status.isEmpty()) qw.eq("status", status);
        if (approvalStatus != null && !approvalStatus.isEmpty()) qw.eq("approval_status", approvalStatus);
        if (managerId != null) qw.eq("manager_id", managerId);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("order_no", keyword)
                    .or().like("material_code", keyword)
                    .or().like("material_name", keyword));
        }
        qw.orderByDesc("order_date");
        List<WorkOrder> all = workOrderMapper.selectList(qw);
        return paginate(all.stream().map(this::toWorkOrderDetail).collect(Collectors.toList()), page, size);
    }

    /** 2-明细. 报工明细列表（支持时间/关键字/员工/工序筛选，分页） */
    @GetMapping("/efficiency-details")
    public Result<Map<String, Object>> efficiencyDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String operationName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<OperationReport> qw = new QueryWrapper<>();
        applyOperationDataPermission(req, qw);
        if (startDate != null) qw.ge("report_date", startDate);
        if (endDate != null) qw.le("report_date", endDate);
        if (employeeId != null) qw.eq("employee_id", employeeId);
        if (operationName != null && !operationName.isEmpty()) qw.like("operation_name", operationName);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("report_no", keyword)
                    .or().like("production_order_no", keyword)
                    .or().like("employee_name", keyword));
        }
        qw.orderByDesc("report_date");
        List<OperationReport> all = operationReportMapper.selectList(qw);
        return paginate(all.stream().map(this::toOperationDetail).collect(Collectors.toList()), page, size);
    }

    /** 3-明细. 成本核算明细列表（支持时间/关键字/状态筛选，分页） */
    @GetMapping("/cost-analysis-details")
    public Result<Map<String, Object>> costAnalysisDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<CostCalculation> qw = new QueryWrapper<>();
        dataScopeResolver.resolve(req).applyTo(qw, null);
        if (startDate != null) qw.ge("calc_date", startDate);
        if (endDate != null) qw.le("calc_date", endDate);
        if (status != null && !status.isEmpty()) qw.eq("status", status);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("calc_no", keyword)
                    .or().like("material_code", keyword)
                    .or().like("material_name", keyword));
        }
        qw.orderByDesc("calc_date");
        List<CostCalculation> all = costCalculationMapper.selectList(qw);
        return paginate(all.stream().map(this::toCostDetail).collect(Collectors.toList()), page, size);
    }

    // ==================== 【工时体系 2026-10-07】工时与计件分析 ====================

    /**
     * 工时与计件分析：
     *  ① 工时误差（计划工时 = 准备+加工；实际工时 = 工序自动累计的 actual_hours），按维度分组，
     *     给出 误差率、中位数、P90、常态区间与判定（**分布法，不用固定阈值**；样本 &lt; 5 不判）。
     *  ② 计件与工时（按员工 / 工序）：合格数量、计件工资（合格数量 × 计件单价）、计划/实际工时。
     *
     * @param groupBy material（默认）/ operation / workcenter / employee
     */
    @GetMapping("/work-hours")
    public Result<Map<String, Object>> workHours(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "material") String groupBy) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        // 1) 时间范围内的工单（沿用生产报表同一数据权限口径）
        QueryWrapper<WorkOrder> oqw = new QueryWrapper<WorkOrder>().between("order_date", startDate, endDate);
        applyWorkOrderDataPermission(req, oqw);
        List<WorkOrder> orders = workOrderMapper.selectList(oqw);

        Map<String, Object> result = new LinkedHashMap<>();
        if (orders.isEmpty()) {
            result.put("kpi", emptyWorkHoursKpi());
            result.put("groups", List.of());
            result.put("piece", List.of());
            return Result.success(result);
        }
        Map<Long, WorkOrder> orderMap = orders.stream()
                .collect(Collectors.toMap(WorkOrder::getOrderId, o -> o, (a, b) -> a));

        QueryWrapper<WorkOrderOperation> qw = new QueryWrapper<WorkOrderOperation>()
                .in("order_id", orderMap.keySet());
        List<WorkOrderOperation> ops = workOrderOperationMapper.selectList(qw);

        // 工作中心名称（分组维度之一）
        Map<Long, String> wcNameMap = workCenterMapper.selectList(new QueryWrapper<WorkCenter>())
                .stream().collect(Collectors.toMap(WorkCenter::getWorkCenterId,
                        w -> w.getWorkCenterName() == null ? "" : w.getWorkCenterName(), (a, b) -> a));

        // 2) 按维度分组：计划/实际工时、误差率样本（仅计划>0 的行参与统计）
        Map<String, List<WorkOrderOperation>> grouped = new LinkedHashMap<>();
        for (WorkOrderOperation op : ops) {
            BigDecimal plan = nvlHours(op.getSetupHours()).add(nvlHours(op.getProcessHours()));
            BigDecimal actual = nvlHours(op.getActualHours());
            if (plan.compareTo(BigDecimal.ZERO) <= 0 && actual.compareTo(BigDecimal.ZERO) <= 0) continue;
            grouped.computeIfAbsent(workHoursGroupKey(op, groupBy, orderMap, wcNameMap), k -> new ArrayList<>()).add(op);
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        BigDecimal kPlan = BigDecimal.ZERO, kActual = BigDecimal.ZERO;
        int abnormalGroups = 0;
        for (Map.Entry<String, List<WorkOrderOperation>> e : grouped.entrySet()) {
            List<BigDecimal> rates = new ArrayList<>();
            BigDecimal planSum = BigDecimal.ZERO, actualSum = BigDecimal.ZERO;
            for (WorkOrderOperation op : e.getValue()) {
                BigDecimal plan = nvlHours(op.getSetupHours()).add(nvlHours(op.getProcessHours()));
                BigDecimal actual = nvlHours(op.getActualHours());
                planSum = planSum.add(plan);
                actualSum = actualSum.add(actual);
                if (plan.compareTo(BigDecimal.ZERO) > 0 && actual.compareTo(BigDecimal.ZERO) > 0) {
                    rates.add(actual.subtract(plan)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(plan, 2, RoundingMode.HALF_UP));
                }
            }
            BigDecimal variance = actualSum.subtract(planSum);
            BigDecimal varianceRate = planSum.compareTo(BigDecimal.ZERO) > 0
                    ? variance.multiply(BigDecimal.valueOf(100)).divide(planSum, 2, RoundingMode.HALF_UP) : null;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("group", e.getKey());
            m.put("sampleCount", e.getValue().size());
            m.put("statSampleCount", rates.size());
            m.put("planHours", planSum);
            m.put("actualHours", actualSum);
            m.put("variance", variance);
            m.put("varianceRate", varianceRate);

            // 常态判定：分布法（中位数 + P90），样本 < 5 不判
            if (rates.size() < 5) {
                m.put("medianRate", null);
                m.put("p90Rate", null);
                m.put("normalLow", null);
                m.put("normalHigh", null);
                m.put("verdict", "样本不足");
            } else {
                List<BigDecimal> sorted = new ArrayList<>(rates);
                Collections.sort(sorted);
                BigDecimal median = percentile(sorted, 50);
                BigDecimal p90 = percentile(sorted, 90);
                // 常态区间 = [中位数 − (P90 − 中位数), P90]（下侧对称延伸到上侧 P90 的同样宽度）
                BigDecimal low = median.subtract(p90.subtract(median));
                m.put("medianRate", median);
                m.put("p90Rate", p90);
                m.put("normalLow", low);
                m.put("normalHigh", p90);
                String verdict = "正常";
                if (varianceRate != null) {
                    if (varianceRate.compareTo(p90) > 0) verdict = "异常偏高";
                    else if (varianceRate.compareTo(low) < 0) verdict = "异常偏低";
                }
                m.put("verdict", verdict);
                if (!"正常".equals(verdict)) abnormalGroups++;
            }
            groups.add(m);
            kPlan = kPlan.add(planSum);
            kActual = kActual.add(actualSum);
        }
        groups.sort(Comparator.comparing(g -> String.valueOf(g.get("group"))));

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("planHours", kPlan);
        kpi.put("actualHours", kActual);
        kpi.put("variance", kActual.subtract(kPlan));
        kpi.put("varianceRate", kPlan.compareTo(BigDecimal.ZERO) > 0
                ? kActual.subtract(kPlan).multiply(BigDecimal.valueOf(100)).divide(kPlan, 2, RoundingMode.HALF_UP) : null);
        kpi.put("abnormalGroups", abnormalGroups);

        // 3) 计件与工时（按员工 / 工序）
        Map<String, BigDecimal[]> pieceAcc = new LinkedHashMap<>();   // key → [合格数量, 计件工资, 计划工时, 实际工时]
        for (WorkOrderOperation op : ops) {
            String key = "operation".equals(groupBy)
                    ? (op.getOperationName() == null ? "未命名工序" : op.getOperationName())
                    : (op.getAssigneeName() == null || op.getAssigneeName().isBlank() ? "未派工" : op.getAssigneeName());
            BigDecimal[] acc = pieceAcc.computeIfAbsent(key, k -> new BigDecimal[]{
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal qty = nvlHours(op.getOutputQuantity());
            BigDecimal rate = nvlHours(op.getPieceRate());
            acc[0] = acc[0].add(qty);
            acc[1] = acc[1].add(qty.multiply(rate));
            acc[2] = acc[2].add(nvlHours(op.getSetupHours()).add(nvlHours(op.getProcessHours())));
            acc[3] = acc[3].add(nvlHours(op.getActualHours()));
        }
        List<Map<String, Object>> piece = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : pieceAcc.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("group", e.getKey());
            m.put("qualifiedQty", e.getValue()[0]);
            m.put("pieceAmount", e.getValue()[1].setScale(2, RoundingMode.HALF_UP));
            m.put("planHours", e.getValue()[2]);
            m.put("actualHours", e.getValue()[3]);
            piece.add(m);
        }
        piece.sort(Comparator.comparing(m -> String.valueOf(m.get("group"))));

        result.put("kpi", kpi);
        result.put("groups", groups);
        result.put("piece", piece);
        result.put("groupBy", groupBy);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        return Result.success(result);
    }

    private static Map<String, Object> emptyWorkHoursKpi() {
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("planHours", BigDecimal.ZERO);
        kpi.put("actualHours", BigDecimal.ZERO);
        kpi.put("variance", BigDecimal.ZERO);
        kpi.put("varianceRate", null);
        kpi.put("abnormalGroups", 0);
        return kpi;
    }

    private static BigDecimal nvlHours(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 分组维度取值（分组为空时给中文兜底，不出现 null 分组名） */
    private static String workHoursGroupKey(WorkOrderOperation op, String groupBy,
                                             Map<Long, WorkOrder> orderMap, Map<Long, String> wcNameMap) {
        return switch (groupBy == null ? "material" : groupBy) {
            case "operation" -> op.getOperationName() == null || op.getOperationName().isBlank()
                    ? "未命名工序" : op.getOperationName();
            case "workcenter" -> op.getWorkCenterId() == null
                    ? "未分配工作中心"
                    : (wcNameMap.getOrDefault(op.getWorkCenterId(), "未命名工作中心"));
            case "employee" -> op.getAssigneeName() == null || op.getAssigneeName().isBlank()
                    ? "未派工" : op.getAssigneeName();
            default -> {
                WorkOrder o = orderMap.get(op.getOrderId());
                if (o == null) yield "未知物料";
                String code = o.getMaterialCode() == null ? "" : o.getMaterialCode() + " ";
                String name = o.getMaterialName() == null ? "" : o.getMaterialName();
                yield (code + name).isBlank() ? "未知物料" : (code + name).trim();
            }
        };
    }

    /** 分位数（最近邻法，输入须已升序）；percentile 取 0~100 */
    private static BigDecimal percentile(List<BigDecimal> sorted, int percentile) {
        if (sorted.isEmpty()) return null;
        int idx = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }

    // ==================== 辅助方法 ====================

    /** 工单数据权限：统一走 DataScopeResolver（work_order 按 created_by） */
    private void applyWorkOrderDataPermission(HttpServletRequest req, QueryWrapper<WorkOrder> qw) {
        dataScopeResolver.resolve(req).applyTo(qw, null);
    }

    /** 报工数据权限：统一走 DataScopeResolver（operation_report 按 created_by） */
    private void applyOperationDataPermission(HttpServletRequest req, QueryWrapper<OperationReport> qw) {
        dataScopeResolver.resolve(req).applyTo(qw, null);
    }

    private Map<String, Object> toWorkOrderDetail(WorkOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", o.getOrderNo());
        m.put("materialCode", o.getMaterialCode());
        m.put("materialName", o.getMaterialName());
        m.put("quantity", o.getQuantity());
        m.put("completedQuantity", o.getCompletedQuantity());
        m.put("orderDate", o.getOrderDate());
        m.put("planEndDate", o.getPlanEndDate());
        m.put("actualEndDate", o.getActualEndDate());
        m.put("status", o.getStatus());
        m.put("approvalStatus", o.getApprovalStatus());
        m.put("managerId", o.getManagerId());
        m.put("delayDays", (o.getActualEndDate() != null && o.getPlanEndDate() != null)
                ? ChronoUnit.DAYS.between(o.getPlanEndDate(), o.getActualEndDate()) : null);
        return m;
    }

    private Map<String, Object> toOperationDetail(OperationReport r) {
        BigDecimal totalQty = r.getTotalQuantity() == null ? BigDecimal.ZERO : r.getTotalQuantity();
        BigDecimal qualifiedQty = r.getQualifiedQuantity() == null ? BigDecimal.ZERO : r.getQualifiedQuantity();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportNo", r.getReportNo());
        m.put("orderNo", r.getProductionOrderNo());
        m.put("reportDate", r.getReportDate());
        m.put("operationName", r.getOperationName());
        m.put("employeeId", r.getEmployeeId());
        m.put("employeeName", r.getEmployeeName());
        m.put("totalQuantity", totalQty);
        m.put("qualifiedQuantity", qualifiedQty);
        m.put("rejectedQuantity", r.getRejectedQuantity());
        m.put("workHours", r.getWorkHours());
        m.put("yieldRate", totalQty.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                qualifiedQty.multiply(new BigDecimal("100")).divide(totalQty, 2, RoundingMode.HALF_UP));
        return m;
    }

    private Map<String, Object> toCostDetail(CostCalculation c) {
        BigDecimal totalCost = c.getTotalCost() == null ? BigDecimal.ZERO : c.getTotalCost();
        BigDecimal qty = c.getQuantity() == null ? BigDecimal.ZERO : c.getQuantity();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("calcNo", c.getCalcNo());
        m.put("orderNo", null);
        m.put("materialCode", c.getMaterialCode());
        m.put("materialName", c.getMaterialName());
        m.put("calcDate", c.getCalcDate());
        m.put("directMaterialCost", c.getDirectMaterialCost());
        m.put("directLaborCost", c.getDirectLaborCost());
        m.put("manufacturingOverhead", c.getManufacturingOverhead());
        m.put("totalCost", totalCost);
        m.put("quantity", qty);
        m.put("unitCost", c.getUnitCost());
        return m;
    }

    /** 内存分页（size<=0 表示不分页，返回全部，用于导出） */
    private <T> Result<Map<String, Object>> paginate(List<T> records, int page, int size) {
        int total = records.size();
        List<T> pageRecords;
        if (size <= 0) {
            pageRecords = records;
        } else {
            int from = Math.max(0, (page - 1) * size);
            int to = Math.min(total, from + size);
            pageRecords = from < total ? records.subList(from, to) : Collections.emptyList();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", pageRecords);
        data.put("total", total);
        data.put("current", page);
        data.put("size", size <= 0 ? total : size);
        return Result.success(data);
    }
}
