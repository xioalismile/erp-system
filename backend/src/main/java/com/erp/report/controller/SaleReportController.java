package com.erp.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.mapper.EmployeeMapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.crm.entity.SalesTargetCompany;
import com.erp.crm.entity.SalesTargetEmployee;
import com.erp.crm.mapper.SalesTargetCompanyMapper;
import com.erp.crm.mapper.SalesTargetEmployeeMapper;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.mapper.CustomerMapper;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleInvoiceMapper;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import com.erp.system.entity.CommissionRate;
import com.erp.system.service.CommissionRateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 销售报表中心
 * 汇总端点：target-completion / order-trend / sales-share / gross-profit / commission
 * 明细端点：target-completion-details / order-trend-details / sales-share-details / gross-profit-details / commission-details
 *
 * 数据权限：统一走 DataScopeResolver（角色 sys_role.data_scope：全部/本部门/仅本人），
 *   销售类单据归属列 saler_id（created_by OR saler_id=当前员工，与销售订单等列表页同口径）。
 *
 * 提成率配置：
 *   自 2026-07 起提成率改为历史留痕（commission_rate 表），commission/commission-details
 *   端点不再接受 baseRate/overAchievementMultiplier/overAchievementBonus 参数，
 *   而是按每条订单的 orderDate 自动匹配当时生效的提成率版本。
 */
@Slf4j
@RestController
@RequestMapping("/api/report/sale")
@RequiredArgsConstructor
public class SaleReportController {

    private final SaleOrderMapper saleOrderMapper;
    private final SaleOrderItemMapper saleOrderItemMapper;
    private final SalesTargetCompanyMapper targetCompanyMapper;
    private final SalesTargetEmployeeMapper targetEmployeeMapper;
    private final CommissionRateService commissionRateService;
    private final SaleInvoiceMapper saleInvoiceMapper;
    private final CustomerMapper customerMapper;
    private final EmployeeMapper employeeMapper;
    private final AccountsReceivableMapper accountsReceivableMapper;
    private final DataScopeResolver dataScopeResolver;

    // ==================== 汇总报表 ====================

    /** 1. 销售目标完成率：年度目标→季度分解→个人完成情况 */
    @GetMapping("/target-completion")
    public Result<Map<String, Object>> targetCompletion(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (year == 0) year = LocalDate.now().getYear();

        // 公司年度目标
        // 数据权限口径：公司级目标公共可见（不按创建人过滤）；员工级目标按被考核员工归属过滤
        QueryWrapper<SalesTargetCompany> companyQw = new QueryWrapper<SalesTargetCompany>()
                .eq("target_year", year);
        SalesTargetCompany companyTarget = targetCompanyMapper.selectOne(companyQw);
        BigDecimal companyAnnual = companyTarget != null && companyTarget.getAnnualAmount() != null
                ? companyTarget.getAnnualAmount() : BigDecimal.ZERO;

        // 各销售员目标分解
        // 数据权限口径：员工级目标按被考核员工归属过滤
        QueryWrapper<SalesTargetEmployee> empQw = new QueryWrapper<SalesTargetEmployee>()
                .eq("target_year", year);
        dataScopeResolver.resolve(req).applyTo(empQw, "employee_id", "create_by");
        List<SalesTargetEmployee> employees = targetEmployeeMapper.selectList(empQw);

        // 实际完成（按销售员统计本年已生效订单金额）
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        QueryWrapper<SaleOrder> oqw = new QueryWrapper<SaleOrder>()
                .between("order_date", yearStart, yearEnd)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, oqw);
        // 若显式传入日期范围，进一步收窄
        if (startDate != null) oqw.ge("order_date", startDate);
        if (endDate != null) oqw.le("order_date", endDate);
        List<SaleOrder> orders = saleOrderMapper.selectList(oqw);
        Map<Long, BigDecimal> actualBySaler = orders.stream()
                .filter(o -> o.getSalerId() != null)
                .collect(Collectors.groupingBy(SaleOrder::getSalerId,
                        Collectors.reducing(BigDecimal.ZERO,
                                o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount(),
                                BigDecimal::add)));

        // 按季度统计公司实际
        Map<Integer, BigDecimal> quarterActual = new HashMap<>();
        for (int q = 1; q <= 4; q++) {
            int m1 = (q - 1) * 3 + 1, m2 = (q - 1) * 3 + 3;
            BigDecimal qAmt = orders.stream()
                    .filter(o -> o.getOrderDate() != null
                            && o.getOrderDate().getMonthValue() >= m1
                            && o.getOrderDate().getMonthValue() <= m2)
                    .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            quarterActual.put(q, qAmt);
        }

        List<Map<String, Object>> empList = employees.stream().map(e -> {
            BigDecimal actual = actualBySaler.getOrDefault(e.getEmployeeId(), BigDecimal.ZERO);
            BigDecimal target = e.getAnnualTargetAmount() == null ? BigDecimal.ZERO : e.getAnnualTargetAmount();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", e.getEmployeeId());
            m.put("employeeName", e.getEmployeeName());
            m.put("deptName", e.getDeptName());
            m.put("annualTarget", target);
            m.put("actualAmount", actual);
            m.put("completionRate", target.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    actual.multiply(new BigDecimal("100")).divide(target, 2, RoundingMode.HALF_UP));
            m.put("gap", target.subtract(actual));
            return m;
        }).sorted((a, b) -> ((BigDecimal) b.get("actualAmount")).compareTo((BigDecimal) a.get("actualAmount")))
                .collect(Collectors.toList());

        BigDecimal totalActual = actualBySaler.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("companyAnnualTarget", companyAnnual);
        result.put("companyActualAmount", totalActual);
        result.put("companyCompletionRate", companyAnnual.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                totalActual.multiply(new BigDecimal("100")).divide(companyAnnual, 2, RoundingMode.HALF_UP));
        // 季度分解
        List<Map<String, Object>> quarters = new ArrayList<>();
        for (int q = 1; q <= 4; q++) {
            final int quarter = q;
            BigDecimal qTarget = employees.stream()
                    .map(e -> {
                        switch (quarter) {
                            case 1: return e.getQ1Target() == null ? BigDecimal.ZERO : e.getQ1Target();
                            case 2: return e.getQ2Target() == null ? BigDecimal.ZERO : e.getQ2Target();
                            case 3: return e.getQ3Target() == null ? BigDecimal.ZERO : e.getQ3Target();
                            default: return e.getQ4Target() == null ? BigDecimal.ZERO : e.getQ4Target();
                        }
                    }).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("quarter", "Q" + quarter);
            qm.put("target", qTarget);
            qm.put("actual", quarterActual.get(quarter));
            qm.put("completionRate", qTarget.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    quarterActual.get(quarter).multiply(new BigDecimal("100")).divide(qTarget, 2, RoundingMode.HALF_UP));
            quarters.add(qm);
        }
        result.put("quarters", quarters);
        result.put("employees", empList);
        return Result.success(result);
    }

    /** 2. 订单趋势分析：按月/季/年统计订单量、金额 */
    @GetMapping("/order-trend")
    public Result<Map<String, Object>> orderTrend(
            HttpServletRequest req,
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusMonths(months - 1).withDayOfMonth(1);

        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);
        List<SaleOrder> orders = saleOrderMapper.selectList(qw);

        // 按月分组
        Map<String, List<SaleOrder>> byMonth = orders.stream()
                .filter(o -> o.getOrderDate() != null)
                .collect(Collectors.groupingBy(o -> o.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))));

        List<String> months2 = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        LocalDate cursor = startDate.withDayOfMonth(1);
        while (!cursor.isAfter(endDate)) {
            String key = cursor.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            months2.add(key);
            List<SaleOrder> list = byMonth.getOrDefault(key, Collections.emptyList());
            counts.add((long) list.size());
            amounts.add(list.stream()
                    .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            cursor = cursor.plusMonths(1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("months", months2);
        result.put("orderCounts", counts);
        result.put("orderAmounts", amounts);
        result.put("totalOrders", counts.stream().mapToLong(Long::longValue).sum());
        result.put("totalAmount", amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        return Result.success(result);
    }

    /** 3. 销售额占比：按客户/物料/销售员/区域维度 */
    @GetMapping("/sales-share")
    public Result<Map<String, Object>> salesShare(
            HttpServletRequest req,
            @RequestParam(defaultValue = "customer") String dimension,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);
        List<SaleOrder> orders = saleOrderMapper.selectList(qw);

        BigDecimal totalAmount = orders.stream()
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> items = new ArrayList<>();
        switch (dimension) {
            case "customer":
                items = orders.stream().filter(o -> o.getCustomerId() != null)
                        .collect(Collectors.groupingBy(SaleOrder::getCustomerId))
                        .entrySet().stream()
                        .map(e -> buildShareItem(String.valueOf(e.getKey()),
                                e.getValue().get(0).getCustomerShortName(), e.getValue(), totalAmount))
                        .sorted((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")))
                        .collect(Collectors.toList());
                break;
            case "saler":
                items = orders.stream().filter(o -> o.getSalerId() != null)
                        .collect(Collectors.groupingBy(SaleOrder::getSalerId))
                        .entrySet().stream()
                        .map(e -> buildShareItem(String.valueOf(e.getKey()),
                                e.getValue().get(0).getSalerName(), e.getValue(), totalAmount))
                        .sorted((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")))
                        .collect(Collectors.toList());
                break;
            case "material":
                List<Long> orderIds = orders.stream().map(SaleOrder::getOrderId).collect(Collectors.toList());
                if (!orderIds.isEmpty()) {
                    List<SaleOrderItem> orderItems = saleOrderItemMapper.selectList(
                            new LambdaQueryWrapper<SaleOrderItem>().in(SaleOrderItem::getOrderId, orderIds));
                    items = orderItems.stream().filter(i -> i.getMaterialId() != null)
                            .collect(Collectors.groupingBy(SaleOrderItem::getMaterialId))
                            .entrySet().stream()
                            .map(e -> buildMaterialShareItem(e.getKey(),
                                    e.getValue().get(0).getMaterialName(), e.getValue(), totalAmount))
                            .sorted((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")))
                            .limit(20)
                            .collect(Collectors.toList());
                }
                break;
            default:
                items = orders.stream().filter(o -> o.getCustomerId() != null)
                        .collect(Collectors.groupingBy(SaleOrder::getCustomerId))
                        .entrySet().stream()
                        .map(e -> buildShareItem(String.valueOf(e.getKey()),
                                e.getValue().get(0).getCustomerShortName(), e.getValue(), totalAmount))
                        .sorted((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")))
                        .collect(Collectors.toList());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", dimension);
        result.put("items", items);
        result.put("totalAmount", totalAmount);
        result.put("totalOrders", orders.size());
        return Result.success(result);
    }

    private Map<String, Object> buildShareItem(String id, String name, List<SaleOrder> list, BigDecimal total) {
        BigDecimal amt = list.stream()
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("orderCount", list.size());
        m.put("amount", amt);
        m.put("percentage", total.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                amt.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP));
        return m;
    }

    private Map<String, Object> buildMaterialShareItem(Long materialId, String materialName, List<SaleOrderItem> list, BigDecimal total) {
        BigDecimal amt = list.stream()
                .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(materialId));
        m.put("name", materialName);
        m.put("quantity", list.stream()
                .map(i -> i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        m.put("amount", amt);
        m.put("percentage", total.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                amt.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP));
        return m;
    }

    /** 4. 毛利分析：订单毛利率、低于底线订单清单 */
    @GetMapping("/gross-profit")
    public Result<Map<String, Object>> grossProfit(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "30") BigDecimal minProfitRate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);
        List<SaleOrder> orders = saleOrderMapper.selectList(qw);

        List<Long> orderIds = orders.stream().map(SaleOrder::getOrderId).collect(Collectors.toList());
        Map<Long, List<SaleOrderItem>> itemMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<SaleOrderItem> items = saleOrderItemMapper.selectList(
                    new LambdaQueryWrapper<SaleOrderItem>().in(SaleOrderItem::getOrderId, orderIds));
            itemMap = items.stream().collect(Collectors.groupingBy(SaleOrderItem::getOrderId));
        }

        List<Map<String, Object>> lowProfitOrders = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (SaleOrder o : orders) {
            List<SaleOrderItem> items = itemMap.getOrDefault(o.getOrderId(), Collections.emptyList());
            BigDecimal revenue = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
            // 成本 = Σ(数量 * 平均成本)
            BigDecimal cost = items.stream()
                    .map(i -> {
                        BigDecimal qty = i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity();
                        BigDecimal avgCost = i.getAvgCost() == null ? BigDecimal.ZERO : i.getAvgCost();
                        return qty.multiply(avgCost);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal profit = revenue.subtract(cost);
            BigDecimal profitRate = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                    profit.multiply(new BigDecimal("100")).divide(revenue, 2, RoundingMode.HALF_UP);
            totalRevenue = totalRevenue.add(revenue);
            totalCost = totalCost.add(cost);
            if (profitRate.compareTo(minProfitRate) < 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderNo", o.getOrderNo());
                m.put("customerName", o.getCustomerShortName());
                m.put("orderDate", o.getOrderDate());
                m.put("revenue", revenue);
                m.put("cost", cost);
                m.put("profit", profit);
                m.put("profitRate", profitRate);
                lowProfitOrders.add(m);
            }
        }
        BigDecimal totalProfit = totalRevenue.subtract(totalCost);
        BigDecimal totalProfitRate = totalRevenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                totalProfit.multiply(new BigDecimal("100")).divide(totalRevenue, 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRevenue", totalRevenue);
        result.put("totalCost", totalCost);
        result.put("totalProfit", totalProfit);
        result.put("totalProfitRate", totalProfitRate);
        result.put("minProfitRate", minProfitRate);
        result.put("lowProfitCount", lowProfitOrders.size());
        result.put("lowProfitOrders", lowProfitOrders);
        return Result.success(result);
    }

    /** 5. 提成计算表：阶梯式提成（按开单/回款/利润口径）。
     *  提成率自 2026-07 起改为历史留痕（commission_rate 表），按每条订单 orderDate 自动匹配生效版本。 */
    @GetMapping("/commission")
    public Result<Map<String, Object>> commission(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int month,
            @RequestParam(defaultValue = "order") String basis,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (year == 0) {
            year = LocalDate.now().getYear();
            if (month == 0) month = LocalDate.now().getMonthValue();
        }

        LocalDate start, end;
        if (startDate != null && endDate != null) {
            start = startDate; end = endDate;
        } else {
            start = LocalDate.of(year, month, 1);
            end = YearMonth.of(year, month).atEndOfMonth();
        }
        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .between("order_date", start, end)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);
        List<SaleOrder> orders = saleOrderMapper.selectList(qw);

        // 预取区间内所有生效的提成率版本（按生效日期升序）
        List<CommissionRate> rateVersionsTmp = commissionRateService.getRangeVersions(start, end);
        if (rateVersionsTmp.isEmpty()) {
            // 兜底：区间内无版本时取当前生效版本
            CommissionRate current = commissionRateService.getCurrent();
            if (current != null) rateVersionsTmp = Collections.singletonList(current);
        }
        final List<CommissionRate> rateVersions = rateVersionsTmp;

        // 按销售员分组
        Map<Long, List<SaleOrder>> bySaler = orders.stream()
                .filter(o -> o.getSalerId() != null)
                .collect(Collectors.groupingBy(SaleOrder::getSalerId));

        // 销售员月度目标（季度目标/3 估算）
        // 数据权限口径：员工级目标按被考核员工归属过滤
        QueryWrapper<SalesTargetEmployee> tgtQw = new QueryWrapper<SalesTargetEmployee>()
                .eq("target_year", year);
        dataScopeResolver.resolve(req).applyTo(tgtQw, "employee_id", "create_by");
        List<SalesTargetEmployee> targets = targetEmployeeMapper.selectList(tgtQw);
        Map<Long, BigDecimal> monthlyTarget = new HashMap<>();
        int quarter = (month - 1) / 3 + 1;
        for (SalesTargetEmployee e : targets) {
            BigDecimal q = BigDecimal.ZERO;
            switch (quarter) {
                case 1: q = e.getQ1Target() == null ? BigDecimal.ZERO : e.getQ1Target(); break;
                case 2: q = e.getQ2Target() == null ? BigDecimal.ZERO : e.getQ2Target(); break;
                case 3: q = e.getQ3Target() == null ? BigDecimal.ZERO : e.getQ3Target(); break;
                case 4: q = e.getQ4Target() == null ? BigDecimal.ZERO : e.getQ4Target(); break;
            }
            monthlyTarget.put(e.getEmployeeId(), q.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP));
        }

        // 预取订单明细（用于 profit 口径计算成本）
        List<Long> allOrderIds = orders.stream().map(SaleOrder::getOrderId).collect(Collectors.toList());
        final Map<Long, List<SaleOrderItem>> itemMap;
        if (!allOrderIds.isEmpty()) {
            itemMap = saleOrderItemMapper.selectList(
                    new LambdaQueryWrapper<SaleOrderItem>().in(SaleOrderItem::getOrderId, allOrderIds))
                    .stream().collect(Collectors.groupingBy(SaleOrderItem::getOrderId));
        } else {
            itemMap = Collections.emptyMap();
        }

        List<Map<String, Object>> list = bySaler.entrySet().stream().map(e -> {
            BigDecimal amount;
            switch (basis) {
                case "profit":
                    BigDecimal revenue = e.getValue().stream()
                            .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal cost = e.getValue().stream()
                            .flatMap(o -> itemMap.getOrDefault(o.getOrderId(), Collections.emptyList()).stream())
                            .map(i -> (i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity())
                                    .multiply(i.getAvgCost() == null ? BigDecimal.ZERO : i.getAvgCost()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    amount = revenue.subtract(cost);
                    break;
                case "payment":
                    // 简化：用订单净额估算（实际应从财务模块取回款）
                    amount = e.getValue().stream()
                            .map(o -> o.getNetAmount() == null ? BigDecimal.ZERO : o.getNetAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    break;
                default: // order
                    amount = e.getValue().stream()
                            .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            BigDecimal target = monthlyTarget.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal completionRate = target.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                    amount.divide(target, 4, RoundingMode.HALF_UP);

            // 按订单 orderDate 匹配当时生效的提成率，逐单计算后汇总
            BigDecimal commission = BigDecimal.ZERO;
            // 用于报表展示的代表性提成率（取该销售员最新一单对应版本）
            BigDecimal repBaseRate = BigDecimal.ZERO;
            BigDecimal repMultiplier = BigDecimal.ZERO;
            BigDecimal repBonus = BigDecimal.ZERO;
            Integer repVersionNo = null;
            for (SaleOrder o : e.getValue()) {
                CommissionRate rate = matchRateByVersion(o.getOrderDate(), rateVersions);
                if (rate == null) continue;
                BigDecimal orderAmt;
                switch (basis) {
                    case "profit":
                        BigDecimal r = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
                        BigDecimal c = itemMap.getOrDefault(o.getOrderId(), Collections.emptyList()).stream()
                                .map(i -> (i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity())
                                        .multiply(i.getAvgCost() == null ? BigDecimal.ZERO : i.getAvgCost()))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        orderAmt = r.subtract(c);
                        break;
                    case "payment":
                        orderAmt = o.getNetAmount() == null ? BigDecimal.ZERO : o.getNetAmount();
                        break;
                    default:
                        orderAmt = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
                }
                // 阶梯提成：基础提成 + 超额奖励（按销售员月度完成率判断是否超额）
                BigDecimal orderCommission = orderAmt.multiply(rate.getBaseRate());
                if (completionRate.compareTo(rate.getOverAchievementMultiplier()) >= 0) {
                    orderCommission = orderCommission.add(orderAmt.multiply(rate.getOverAchievementBonus()));
                }
                commission = commission.add(orderCommission);
                // 记录最新一单的版本信息作为代表
                repBaseRate = rate.getBaseRate();
                repMultiplier = rate.getOverAchievementMultiplier();
                repBonus = rate.getOverAchievementBonus();
                repVersionNo = rate.getVersionNo();
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("salerId", e.getKey());
            m.put("salerName", e.getValue().get(0).getSalerName());
            m.put("basisAmount", amount);
            m.put("monthlyTarget", target);
            m.put("completionRate", completionRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
            m.put("baseRate", repBaseRate);
            m.put("overAchievementMultiplier", repMultiplier);
            m.put("overAchievementBonus", repBonus);
            m.put("rateVersionNo", repVersionNo);
            m.put("overAchieved", repMultiplier.compareTo(BigDecimal.ZERO) != 0
                    && completionRate.compareTo(repMultiplier) >= 0);
            m.put("commission", commission);
            return m;
        }).sorted((a, b) -> ((BigDecimal) b.get("commission")).compareTo((BigDecimal) a.get("commission")))
                .collect(Collectors.toList());

        // 汇总区间内涉及的提成率版本（用于报表头展示）
        List<Map<String, Object>> rateVersionInfo = rateVersions.stream().map(v -> {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("versionNo", v.getVersionNo());
            vm.put("baseRate", v.getBaseRate());
            vm.put("overAchievementMultiplier", v.getOverAchievementMultiplier());
            vm.put("overAchievementBonus", v.getOverAchievementBonus());
            vm.put("effectiveDate", v.getEffectiveDate());
            vm.put("expiryDate", v.getExpiryDate());
            return vm;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("month", month);
        result.put("basis", basis);
        result.put("rateVersions", rateVersionInfo);
        result.put("list", list);
        result.put("totalCommission", list.stream()
                .map(m -> (BigDecimal) m.get("commission"))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return Result.success(result);
    }

    /** 按订单日期匹配生效的提成率版本（从预取的版本列表中查找，避免 N+1） */
    private CommissionRate matchRateByVersion(LocalDate orderDate, List<CommissionRate> versions) {
        if (orderDate == null || versions.isEmpty()) return null;
        // 从后往前找第一个 effective_date <= orderDate 的版本
        for (int i = versions.size() - 1; i >= 0; i--) {
            CommissionRate v = versions.get(i);
            if (!orderDate.isBefore(v.getEffectiveDate())) {
                // 检查是否在失效日期之前（含）
                if (v.getExpiryDate() == null || !orderDate.isAfter(v.getExpiryDate())) {
                    return v;
                }
            }
        }
        // 兜底：返回最早的版本
        return versions.get(0);
    }

    // ==================== 明细报表（可导出） ====================

    /** 1-明细. 销售员年度目标完成明细列表 */
    @GetMapping("/target-completion-details")
    public Result<Map<String, Object>> targetCompletionDetails(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (year == 0) year = LocalDate.now().getYear();

        // 数据权限口径：员工级目标按被考核员工归属过滤
        QueryWrapper<SalesTargetEmployee> empQw = new QueryWrapper<SalesTargetEmployee>()
                .eq("target_year", year);
        dataScopeResolver.resolve(req).applyTo(empQw, "employee_id", "create_by");
        List<SalesTargetEmployee> employees = targetEmployeeMapper.selectList(empQw);

        // 年度实际订单
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        QueryWrapper<SaleOrder> oqw = new QueryWrapper<SaleOrder>()
                .between("order_date", yearStart, yearEnd)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, oqw);
        if (startDate != null) oqw.ge("order_date", startDate);
        if (endDate != null) oqw.le("order_date", endDate);
        List<SaleOrder> orders = saleOrderMapper.selectList(oqw);

        // 按销售员+季度汇总实际
        Map<Long, BigDecimal> annualBySaler = new HashMap<>();
        Map<Long, Map<Integer, BigDecimal>> quarterBySaler = new HashMap<>();
        for (SaleOrder o : orders) {
            if (o.getSalerId() == null || o.getOrderDate() == null) continue;
            BigDecimal amt = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
            annualBySaler.merge(o.getSalerId(), amt, BigDecimal::add);
            int q = (o.getOrderDate().getMonthValue() - 1) / 3 + 1;
            quarterBySaler.computeIfAbsent(o.getSalerId(), k -> new HashMap<>())
                    .merge(q, amt, BigDecimal::add);
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (SalesTargetEmployee e : employees) {
            BigDecimal actual = annualBySaler.getOrDefault(e.getEmployeeId(), BigDecimal.ZERO);
            BigDecimal target = e.getAnnualTargetAmount() == null ? BigDecimal.ZERO : e.getAnnualTargetAmount();
            Map<Integer, BigDecimal> qMap = quarterBySaler.getOrDefault(e.getEmployeeId(), Collections.emptyMap());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", e.getEmployeeId());
            m.put("employeeName", e.getEmployeeName());
            m.put("deptName", e.getDeptName());
            m.put("annualTarget", target);
            m.put("actualAmount", actual);
            m.put("completionRate", target.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    actual.multiply(new BigDecimal("100")).divide(target, 2, RoundingMode.HALF_UP));
            m.put("gap", target.subtract(actual));
            m.put("quarter1", qMap.getOrDefault(1, BigDecimal.ZERO));
            m.put("quarter2", qMap.getOrDefault(2, BigDecimal.ZERO));
            m.put("quarter3", qMap.getOrDefault(3, BigDecimal.ZERO));
            m.put("quarter4", qMap.getOrDefault(4, BigDecimal.ZERO));
            records.add(m);
        }

        // 关键字过滤（employeeName / deptName）
        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword;
            records = records.stream()
                    .filter(m -> {
                        Object name = m.get("employeeName");
                        Object dept = m.get("deptName");
                        return (name != null && name.toString().contains(kw))
                                || (dept != null && dept.toString().contains(kw));
                    })
                    .collect(Collectors.toList());
        }
        // 按实际金额降序
        records.sort((a, b) -> ((BigDecimal) b.get("actualAmount")).compareTo((BigDecimal) a.get("actualAmount")));
        return paginate(records, page, size);
    }

    /** 2-明细. 订单明细列表 */
    @GetMapping("/order-trend-details")
    public Result<Map<String, Object>> orderTrendDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);
        if (keyword != null && !keyword.isEmpty()) {
            // 注意：SaleOrder.salerName 标记为 @TableField(exist=false) 非数据库字段，
            // 不能用查询列过滤。销售员名称匹配改为内存过滤（见下方 all.stream().filter）。
            qw.and(w -> w.like("order_no", keyword)
                    .or().like("customer_short_name", keyword));
        }
        qw.orderByDesc("order_date");
        List<SaleOrder> all = saleOrderMapper.selectList(qw);
        return paginate(all.stream().map(this::toOrderDetail).collect(Collectors.toList()), page, size);
    }

    /** 3-明细. 销售额占比明细列表（按 dimension 过滤：customer/saler/material） */
    @GetMapping("/sales-share-details")
    public Result<Map<String, Object>> salesShareDetails(
            HttpServletRequest req,
            @RequestParam(defaultValue = "customer") String dimension,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long salerId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);

        // 按 dimension 应用对应过滤
        if (customerId != null) qw.eq("customer_id", customerId);
        if (salerId != null) qw.eq("saler_id", salerId);

        List<SaleOrder> orders = saleOrderMapper.selectList(qw);

        // material 维度：进一步按 materialId 过滤包含该物料的订单
        if ("material".equalsIgnoreCase(dimension) && materialId != null && !orders.isEmpty()) {
            List<Long> orderIds = orders.stream().map(SaleOrder::getOrderId).collect(Collectors.toList());
            List<SaleOrderItem> items = saleOrderItemMapper.selectList(
                    new LambdaQueryWrapper<SaleOrderItem>()
                            .in(SaleOrderItem::getOrderId, orderIds)
                            .eq(SaleOrderItem::getMaterialId, materialId));
            Set<Long> matchedOrderIds = items.stream()
                    .map(SaleOrderItem::getOrderId)
                    .collect(Collectors.toSet());
            orders = orders.stream()
                    .filter(o -> matchedOrderIds.contains(o.getOrderId()))
                    .collect(Collectors.toList());
        }

        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword;
            orders = orders.stream()
                    .filter(o -> (o.getOrderNo() != null && o.getOrderNo().contains(kw))
                            || (o.getCustomerShortName() != null && o.getCustomerShortName().contains(kw))
                            || (o.getSalerName() != null && o.getSalerName().contains(kw)))
                    .collect(Collectors.toList());
        }
        orders.sort(Comparator.comparing(SaleOrder::getOrderDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return paginate(orders.stream().map(this::toOrderDetail).collect(Collectors.toList()), page, size);
    }

    /** 4-明细. 订单毛利明细列表（需查询 SaleOrderItem 计算成本） */
    @GetMapping("/gross-profit-details")
    public Result<Map<String, Object>> grossProfitDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);
        if (keyword != null && !keyword.isEmpty()) {
            // 注意：SaleOrder.salerName 为 @TableField(exist=false) 非数据库列，不能作为查询列
            qw.and(w -> w.like("order_no", keyword)
                    .or().like("customer_short_name", keyword));
        }
        qw.orderByDesc("order_date");
        List<SaleOrder> orders = saleOrderMapper.selectList(qw);

        List<Long> orderIds = orders.stream().map(SaleOrder::getOrderId).collect(Collectors.toList());
        Map<Long, List<SaleOrderItem>> itemMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            itemMap = saleOrderItemMapper.selectList(
                    new LambdaQueryWrapper<SaleOrderItem>().in(SaleOrderItem::getOrderId, orderIds))
                    .stream().collect(Collectors.groupingBy(SaleOrderItem::getOrderId));
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (SaleOrder o : orders) {
            List<SaleOrderItem> items = itemMap.getOrDefault(o.getOrderId(), Collections.emptyList());
            BigDecimal revenue = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
            BigDecimal cost = items.stream()
                    .map(i -> {
                        BigDecimal qty = i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity();
                        BigDecimal avgCost = i.getAvgCost() == null ? BigDecimal.ZERO : i.getAvgCost();
                        return qty.multiply(avgCost);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal profit = revenue.subtract(cost);
            BigDecimal profitRate = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                    profit.multiply(new BigDecimal("100")).divide(revenue, 2, RoundingMode.HALF_UP);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", o.getOrderNo());
            m.put("customerName", o.getCustomerShortName());
            m.put("orderDate", o.getOrderDate());
            m.put("revenue", revenue);
            m.put("cost", cost);
            m.put("profit", profit);
            m.put("profitRate", profitRate);
            records.add(m);
        }
        return paginate(records, page, size);
    }

    /** 5-明细. 销售员提成构成明细列表（按订单展开） */
    @GetMapping("/commission-details")
    public Result<Map<String, Object>> commissionDetails(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "1") int month,
            @RequestParam(defaultValue = "order") String basis,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (year == 0) {
            year = LocalDate.now().getYear();
            if (month == 0) month = LocalDate.now().getMonthValue();
        }

        LocalDate start, end;
        if (startDate != null && endDate != null) {
            start = startDate; end = endDate;
        } else {
            start = LocalDate.of(year, month, 1);
            end = YearMonth.of(year, month).atEndOfMonth();
        }
        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .between("order_date", start, end)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);
        if (keyword != null && !keyword.isEmpty()) {
            // 注意：SaleOrder.salerName 为 @TableField(exist=false)，不能作为查询列
            qw.and(w -> w.like("order_no", keyword)
                    .or().like("customer_short_name", keyword));
        }
        qw.orderByDesc("order_date");
        List<SaleOrder> orders = saleOrderMapper.selectList(qw);

        // 预取订单明细（profit 口径需要成本）
        List<Long> orderIds = orders.stream().map(SaleOrder::getOrderId).collect(Collectors.toList());
        Map<Long, List<SaleOrderItem>> itemMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            itemMap = saleOrderItemMapper.selectList(
                    new LambdaQueryWrapper<SaleOrderItem>().in(SaleOrderItem::getOrderId, orderIds))
                    .stream().collect(Collectors.groupingBy(SaleOrderItem::getOrderId));
        }

        // 预取区间内所有生效的提成率版本（按订单 orderDate 自动匹配，留痕）
        List<CommissionRate> rateVersions = commissionRateService.getRangeVersions(start, end);
        if (rateVersions.isEmpty()) {
            CommissionRate current = commissionRateService.getCurrent();
            if (current != null) rateVersions = Collections.singletonList(current);
        }

        // 按销售员汇总（用于判断是否超额）
        Map<Long, BigDecimal> amountBySaler = new HashMap<>();
        Map<Long, BigDecimal> targetBySaler = new HashMap<>();
        QueryWrapper<SalesTargetEmployee> tgtQw = new QueryWrapper<SalesTargetEmployee>()
                .eq("target_year", year);
        // 数据权限（按角色 data_scope）：员工级目标按"被考核员工"归属过滤（与其它目标端点同口径）
        dataScopeResolver.resolve(req).applyTo(tgtQw, "employee_id", "create_by");
        List<SalesTargetEmployee> targets = targetEmployeeMapper.selectList(tgtQw);
        int quarter = (month - 1) / 3 + 1;
        for (SalesTargetEmployee e : targets) {
            BigDecimal q = BigDecimal.ZERO;
            switch (quarter) {
                case 1: q = e.getQ1Target() == null ? BigDecimal.ZERO : e.getQ1Target(); break;
                case 2: q = e.getQ2Target() == null ? BigDecimal.ZERO : e.getQ2Target(); break;
                case 3: q = e.getQ3Target() == null ? BigDecimal.ZERO : e.getQ3Target(); break;
                case 4: q = e.getQ4Target() == null ? BigDecimal.ZERO : e.getQ4Target(); break;
            }
            targetBySaler.put(e.getEmployeeId(), q.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP));
        }
        // 先计算每单 basis 金额，并按销售员汇总
        Map<Long, BigDecimal> orderAmount = new LinkedHashMap<>();
        for (SaleOrder o : orders) {
            BigDecimal amt;
            switch (basis) {
                case "profit":
                    BigDecimal revenue = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
                    BigDecimal cost = itemMap.getOrDefault(o.getOrderId(), Collections.emptyList()).stream()
                            .map(i -> (i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity())
                                    .multiply(i.getAvgCost() == null ? BigDecimal.ZERO : i.getAvgCost()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    amt = revenue.subtract(cost);
                    break;
                case "payment":
                    amt = o.getNetAmount() == null ? BigDecimal.ZERO : o.getNetAmount();
                    break;
                default:
                    amt = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
            }
            orderAmount.put(o.getOrderId(), amt);
            if (o.getSalerId() != null) {
                amountBySaler.merge(o.getSalerId(), amt, BigDecimal::add);
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (SaleOrder o : orders) {
            if (o.getSalerId() == null) continue;
            BigDecimal amt = orderAmount.getOrDefault(o.getOrderId(), BigDecimal.ZERO);
            BigDecimal salerTotal = amountBySaler.getOrDefault(o.getSalerId(), BigDecimal.ZERO);
            BigDecimal target = targetBySaler.getOrDefault(o.getSalerId(), BigDecimal.ZERO);
            BigDecimal completionRate = target.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                    salerTotal.divide(target, 4, RoundingMode.HALF_UP);
            // 按订单 orderDate 自动匹配当时生效的提成率版本
            CommissionRate rate = matchRateByVersion(o.getOrderDate(), rateVersions);
            BigDecimal rateBaseRate = rate != null ? rate.getBaseRate() : BigDecimal.ZERO;
            BigDecimal rateMultiplier = rate != null ? rate.getOverAchievementMultiplier() : new BigDecimal("1.20");
            BigDecimal rateBonus = rate != null ? rate.getOverAchievementBonus() : BigDecimal.ZERO;
            Integer rateVersionNo = rate != null ? rate.getVersionNo() : null;
            LocalDate rateEffectiveDate = rate != null ? rate.getEffectiveDate() : null;

            boolean overAchieved = completionRate.compareTo(rateMultiplier) >= 0;
            // 提成率：超额则 baseRate + overAchievementBonus，否则 baseRate
            BigDecimal commissionRate = overAchieved ? rateBaseRate.add(rateBonus) : rateBaseRate;
            BigDecimal commission = amt.multiply(commissionRate);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("salerId", o.getSalerId());
            m.put("salerName", o.getSalerName());
            m.put("orderNo", o.getOrderNo());
            m.put("orderDate", o.getOrderDate());
            m.put("amount", amt);
            m.put("basis", basis);
            m.put("rateVersionNo", rateVersionNo);
            m.put("rateEffectiveDate", rateEffectiveDate);
            m.put("baseRate", rateBaseRate);
            m.put("overAchievementMultiplier", rateMultiplier);
            m.put("overAchievementBonus", rateBonus);
            m.put("commissionRate", commissionRate);
            m.put("commission", commission);
            records.add(m);
        }
        return paginate(records, page, size);
    }

    // ==================== 销售发票报表 ====================

    /** 6. 销售发票统计概览：金额/税额汇总、12个月趋势、状态分布、回款率。
     *  口径：金额统计排除"已作废"发票；状态分布含已作废（供饼图）；空数据一律返回0。 */
    @GetMapping("/invoice/overview")
    public Result<Map<String, Object>> invoiceOverview(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        LocalDate[] range = resolveInvoiceRange(year, startDate, endDate);
        List<SaleInvoice> invoices = listSaleInvoices(req, range[0], range[1]);
        Map<Long, String> customerNames = mapCustomerNames(invoices);
        List<SaleInvoice> filtered = filterSaleInvoicesByKeyword(invoices, customerNames, keyword);
        List<SaleInvoice> valid = filterValidSaleInvoices(filtered);

        BigDecimal totalAmount = sumInvoiceField(valid, SaleInvoice::getTotalAmount);
        BigDecimal totalTax = sumInvoiceField(valid, SaleInvoice::getTaxAmount);
        int totalInvoices = valid.size();
        BigDecimal avgAmount = totalInvoices == 0 ? BigDecimal.ZERO
                : totalAmount.divide(new BigDecimal(totalInvoices), 2, RoundingMode.HALF_UP);

        // 按月聚合（无数据月份补0）
        Map<Integer, List<SaleInvoice>> byMonth = valid.stream()
                .filter(i -> i.getInvoiceDate() != null)
                .collect(Collectors.groupingBy(i -> i.getInvoiceDate().getMonthValue()));
        List<Map<String, Object>> monthData = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            List<SaleInvoice> list = byMonth.getOrDefault(m, Collections.emptyList());
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("month", m + "月");
            mm.put("amount", sumInvoiceField(list, SaleInvoice::getTotalAmount));
            mm.put("tax", sumInvoiceField(list, SaleInvoice::getTaxAmount));
            mm.put("count", list.size());
            monthData.add(mm);
        }

        // 状态分布（含已作废，供饼图）
        Map<String, Long> statusDist = filtered.stream().collect(Collectors.groupingBy(
                i -> i.getStatus() == null ? "未知" : i.getStatus(), LinkedHashMap::new, Collectors.counting()));

        // 回款率：按发票号关联应收，已收合计/应收合计（排除已作废应收），无数据为0
        BigDecimal paymentRate = calcSalePaymentRate(valid);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalAmount", totalAmount);
        result.put("totalTax", totalTax);
        result.put("totalInvoices", totalInvoices);
        result.put("avgAmount", avgAmount);
        result.put("monthData", monthData);
        result.put("statusDist", statusDist);
        result.put("paymentRate", paymentRate);
        return Result.success(result);
    }

    /** 7. 销售发票趋势：周度聚合 + Top5客户与上一等长周期的环比（上期为0且本期有金额记100，本期也为0记0） */
    @GetMapping("/invoice/trend")
    public Result<Map<String, Object>> invoiceTrend(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        LocalDate[] range = resolveInvoiceRange(year, startDate, endDate);
        List<SaleInvoice> invoices = listSaleInvoices(req, range[0], range[1]);
        Map<Long, String> customerNames = mapCustomerNames(invoices);
        List<SaleInvoice> valid = filterValidSaleInvoices(filterSaleInvoicesByKeyword(invoices, customerNames, keyword));

        // 周度聚合（ISO周，跨年用"周所在年*100+周号"作排序键）
        Map<Long, List<SaleInvoice>> byWeek = valid.stream()
                .filter(i -> i.getInvoiceDate() != null)
                .collect(Collectors.groupingBy(i -> {
                    int weekYear = i.getInvoiceDate().get(WeekFields.ISO.weekBasedYear());
                    int weekNo = i.getInvoiceDate().get(WeekFields.ISO.weekOfWeekBasedYear());
                    return (long) (weekYear * 100 + weekNo);
                }));
        List<Map<String, Object>> weeklyTrend = byWeek.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("week", "第" + (e.getKey() % 100) + "周");
                    m.put("amount", sumInvoiceField(e.getValue(), SaleInvoice::getTotalAmount));
                    m.put("tax", sumInvoiceField(e.getValue(), SaleInvoice::getTaxAmount));
                    m.put("count", e.getValue().size());
                    return m;
                }).collect(Collectors.toList());

        // Top5客户：与上一等长周期对比
        long days = ChronoUnit.DAYS.between(range[0], range[1]) + 1;
        List<SaleInvoice> prevInvoices = listSaleInvoices(req, range[0].minusDays(days), range[0].minusDays(1));
        Map<Long, String> prevNames = mapCustomerNames(prevInvoices);
        List<SaleInvoice> prevValid = filterValidSaleInvoices(filterSaleInvoicesByKeyword(prevInvoices, prevNames, keyword));
        Map<Long, BigDecimal> curByCustomer = valid.stream().filter(i -> i.getCustomerId() != null)
                .collect(Collectors.groupingBy(SaleInvoice::getCustomerId,
                        Collectors.reducing(BigDecimal.ZERO, i -> nvl(i.getTotalAmount()), BigDecimal::add)));
        Map<Long, BigDecimal> prevByCustomer = prevValid.stream().filter(i -> i.getCustomerId() != null)
                .collect(Collectors.groupingBy(SaleInvoice::getCustomerId,
                        Collectors.reducing(BigDecimal.ZERO, i -> nvl(i.getTotalAmount()), BigDecimal::add)));

        List<Map<String, Object>> customerTrend = curByCustomer.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    BigDecimal cur = e.getValue();
                    BigDecimal prev = prevByCustomer.getOrDefault(e.getKey(), BigDecimal.ZERO);
                    BigDecimal trend;
                    if (prev.compareTo(BigDecimal.ZERO) == 0) {
                        trend = cur.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("100") : BigDecimal.ZERO;
                    } else {
                        trend = cur.subtract(prev).multiply(new BigDecimal("100")).divide(prev, 2, RoundingMode.HALF_UP);
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", customerNames.getOrDefault(e.getKey(), "未知客户"));
                    m.put("amount", cur);
                    m.put("trend", trend);
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weeklyTrend", weeklyTrend);
        result.put("customerTrend", customerTrend);
        return Result.success(result);
    }

    /** 8. 客户发票分析：TopN客户占比（其余合并"其他客户"）+ 客户排名表 */
    @GetMapping("/invoice/customer-analysis")
    public Result<Map<String, Object>> invoiceCustomerAnalysis(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "5") int topN) {
        LocalDate[] range = resolveInvoiceRange(year, startDate, endDate);
        List<SaleInvoice> invoices = listSaleInvoices(req, range[0], range[1]);
        Map<Long, String> customerNames = mapCustomerNames(invoices);
        List<SaleInvoice> valid = filterValidSaleInvoices(filterSaleInvoicesByKeyword(invoices, customerNames, keyword));

        // 按客户分组（无客户ID归"未知客户"）
        Map<Long, List<SaleInvoice>> byCustomer = valid.stream()
                .collect(Collectors.groupingBy(i -> i.getCustomerId() == null ? -1L : i.getCustomerId(),
                        LinkedHashMap::new, Collectors.toList()));
        BigDecimal totalAmount = sumInvoiceField(valid, SaleInvoice::getTotalAmount);

        List<Map.Entry<Long, List<SaleInvoice>>> sorted = byCustomer.entrySet().stream()
                .sorted((a, b) -> sumInvoiceField(b.getValue(), SaleInvoice::getTotalAmount)
                        .compareTo(sumInvoiceField(a.getValue(), SaleInvoice::getTotalAmount)))
                .collect(Collectors.toList());
        int n = Math.max(1, topN);

        List<Map<String, Object>> customerDistribution = new ArrayList<>();
        List<Map<String, Object>> customerRanking = new ArrayList<>();
        for (int i = 0; i < Math.min(n, sorted.size()); i++) {
            Map.Entry<Long, List<SaleInvoice>> e = sorted.get(i);
            BigDecimal amt = sumInvoiceField(e.getValue(), SaleInvoice::getTotalAmount);
            String name = e.getKey() == -1L ? "未知客户" : customerNames.getOrDefault(e.getKey(), "未知客户");
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("name", name);
            dm.put("value", amt);
            dm.put("percentage", percentOf(amt, totalAmount));
            customerDistribution.add(dm);

            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("rank", i + 1);
            rm.put("name", name);
            rm.put("amount", amt);
            rm.put("invoices", e.getValue().size());
            rm.put("avgAmount", e.getValue().isEmpty() ? BigDecimal.ZERO
                    : amt.divide(new BigDecimal(e.getValue().size()), 2, RoundingMode.HALF_UP));
            customerRanking.add(rm);
        }
        // 其余客户合并为"其他客户"
        if (sorted.size() > n) {
            BigDecimal otherAmount = BigDecimal.ZERO;
            int otherCount = 0;
            for (int i = n; i < sorted.size(); i++) {
                otherAmount = otherAmount.add(sumInvoiceField(sorted.get(i).getValue(), SaleInvoice::getTotalAmount));
                otherCount += sorted.get(i).getValue().size();
            }
            if (otherAmount.compareTo(BigDecimal.ZERO) > 0 || otherCount > 0) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("name", "其他客户");
                dm.put("value", otherAmount);
                dm.put("percentage", percentOf(otherAmount, totalAmount));
                customerDistribution.add(dm);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerDistribution", customerDistribution);
        result.put("customerRanking", customerRanking);
        result.put("totalAmount", totalAmount);
        result.put("totalInvoices", valid.size());
        result.put("totalCustomers", byCustomer.size());
        return Result.success(result);
    }

    /** 9. 销售税务分析：按平均税率分组（如13显示为'13%'，空归'未税率'）+ 月度税额（按税率细分） */
    @GetMapping("/invoice/tax-analysis")
    public Result<Map<String, Object>> invoiceTaxAnalysis(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        LocalDate[] range = resolveInvoiceRange(year, startDate, endDate);
        List<SaleInvoice> invoices = listSaleInvoices(req, range[0], range[1]);
        Map<Long, String> customerNames = mapCustomerNames(invoices);
        List<SaleInvoice> valid = filterValidSaleInvoices(filterSaleInvoicesByKeyword(invoices, customerNames, keyword));

        // 按税率分组（key归一化后分组，null用-1哨兵值，排序时最后）
        Map<BigDecimal, List<SaleInvoice>> byRate = valid.stream()
                .collect(Collectors.groupingBy(i -> i.getTaxRateAvg() == null
                        ? BigDecimal.valueOf(-1) : i.getTaxRateAvg().stripTrailingZeros()));
        List<Map.Entry<BigDecimal, List<SaleInvoice>>> sortedRates = byRate.entrySet().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .collect(Collectors.toList());

        List<Map<String, Object>> taxDistribution = new ArrayList<>();
        for (Map.Entry<BigDecimal, List<SaleInvoice>> e : sortedRates) {
            List<SaleInvoice> list = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rate", saleTaxRateLabel(e.getKey()));
            // 金额取净额（不含税），保证 税额/金额=税率
            m.put("amount", sumInvoiceField(list, SaleInvoice::getNetAmount));
            m.put("tax", sumInvoiceField(list, SaleInvoice::getTaxAmount));
            m.put("count", list.size());
            taxDistribution.add(m);
        }

        // 月度税额（按税率细分，无数据月份补0）
        List<String> rateLabels = taxDistribution.stream()
                .map(m -> (String) m.get("rate")).collect(Collectors.toList());
        List<Map<String, Object>> monthlyTax = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            final int month = m;
            List<SaleInvoice> monthInvoices = valid.stream()
                    .filter(i -> i.getInvoiceDate() != null && i.getInvoiceDate().getMonthValue() == month)
                    .collect(Collectors.toList());
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("month", m + "月");
            mm.put("tax", sumInvoiceField(monthInvoices, SaleInvoice::getTaxAmount));
            Map<String, Object> rates = new LinkedHashMap<>();
            for (String label : rateLabels) rates.put(label, BigDecimal.ZERO);
            for (SaleInvoice i : monthInvoices) {
                String label = i.getTaxRateAvg() == null ? "未税率"
                        : i.getTaxRateAvg().stripTrailingZeros().toPlainString() + "%";
                BigDecimal old = rates.get(label) instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
                rates.put(label, old.add(nvl(i.getTaxAmount())));
            }
            mm.put("rates", rates);
            monthlyTax.add(mm);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taxDistribution", taxDistribution);
        result.put("monthlyTax", monthlyTax);
        result.put("totalAmount", sumInvoiceField(valid, SaleInvoice::getTotalAmount));
        result.put("totalTax", sumInvoiceField(valid, SaleInvoice::getTaxAmount));
        return Result.success(result);
    }

    /** 10. 销售发票明细：净额/税额/价税合计 + 客户与销售员批量解析，内存分页（含作废发票） */
    @GetMapping("/invoice/details")
    public Result<Map<String, Object>> invoiceDetails(
            HttpServletRequest req,
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        LocalDate[] range = resolveInvoiceRange(year, startDate, endDate);
        List<SaleInvoice> invoices = listSaleInvoices(req, range[0], range[1]);
        Map<Long, String> customerNames = mapCustomerNames(invoices);
        Set<Long> salerIds = invoices.stream().map(SaleInvoice::getSalerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> salerNames = salerIds.isEmpty() ? Collections.emptyMap()
                : employeeMapper.selectBatchIds(salerIds).stream()
                .collect(Collectors.toMap(Employee::getEmployeeId,
                        e -> e.getEmployeeName() == null ? "" : e.getEmployeeName(), (a, b) -> a));

        List<SaleInvoice> filtered = filterSaleInvoicesByKeyword(invoices, customerNames, keyword);
        filtered.sort(Comparator.comparing(SaleInvoice::getInvoiceDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int total = filtered.size();
        int from = Math.max(0, (current - 1) * size);
        int to = Math.min(total, from + size);
        List<SaleInvoice> pageList = from < total ? filtered.subList(from, to) : Collections.emptyList();

        List<Map<String, Object>> list = pageList.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getInvoiceId());
            m.put("invoiceNo", i.getInvoiceNo());
            m.put("customerName", i.getCustomerId() == null ? "" : customerNames.getOrDefault(i.getCustomerId(), ""));
            m.put("amount", i.getNetAmount());
            m.put("tax", i.getTaxAmount());
            m.put("total", i.getTotalAmount());
            m.put("date", i.getInvoiceDate());
            m.put("status", i.getStatus());
            m.put("salesman", i.getSalerId() == null ? "" : salerNames.getOrDefault(i.getSalerId(), ""));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("total", total);
        pagination.put("page", current);
        pagination.put("pageSize", size);
        pagination.put("totalPages", size > 0 ? (total + size - 1) / size : 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("pagination", pagination);
        return Result.success(result);
    }

    // ---------- 销售发票报表辅助方法 ----------

    /** 发票报表通用区间解析：显式起止日期优先，否则按年份（默认当年） */
    private LocalDate[] resolveInvoiceRange(int year, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return new LocalDate[]{startDate, endDate};
        }
        int y = year == 0 ? LocalDate.now().getYear() : year;
        return new LocalDate[]{LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)};
    }

    /** 查询区间内销售发票（统一数据权限：created_by OR saler_id=当前员工，与订单报表口径一致） */
    private List<SaleInvoice> listSaleInvoices(HttpServletRequest req, LocalDate start, LocalDate end) {
        QueryWrapper<SaleInvoice> qw = new QueryWrapper<SaleInvoice>()
                .between("invoice_date", start, end);
        dataScopeResolver.resolve(req).applyTo(qw, "saler_id");
        return saleInvoiceMapper.selectList(qw);
    }

    /** 批量解析客户名称（customerName为非库字段，先收集ID再查档案） */
    private Map<Long, String> mapCustomerNames(List<SaleInvoice> invoices) {
        Set<Long> ids = invoices.stream().map(SaleInvoice::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return customerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Customer::getCustomerId,
                        c -> c.getCustomerName() == null ? "" : c.getCustomerName(), (a, b) -> a));
    }

    /** 关键字过滤（发票号/客户名称/源单据号；客户名称为非库字段需先解析后内存匹配） */
    private List<SaleInvoice> filterSaleInvoicesByKeyword(List<SaleInvoice> invoices, Map<Long, String> customerNames, String keyword) {
        if (keyword == null || keyword.isEmpty()) return invoices;
        return invoices.stream().filter(i ->
                (i.getInvoiceNo() != null && i.getInvoiceNo().contains(keyword))
                        || (i.getCustomerId() != null && customerNames.getOrDefault(i.getCustomerId(), "").contains(keyword))
                        || (i.getSourceBillNo() != null && i.getSourceBillNo().contains(keyword)))
                .collect(Collectors.toList());
    }

    /** 金额统计口径：排除"已作废"发票（状态为空的保留） */
    private List<SaleInvoice> filterValidSaleInvoices(List<SaleInvoice> invoices) {
        return invoices.stream().filter(i -> !"已作废".equals(i.getStatus())).collect(Collectors.toList());
    }

    /** 金额字段空值安全的合计 */
    private BigDecimal sumInvoiceField(List<SaleInvoice> list, Function<SaleInvoice, BigDecimal> getter) {
        return list.stream().map(i -> nvl(getter.apply(i))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 税率key转显示标签：如13→'13%'，-1哨兵值→'未税率' */
    private String saleTaxRateLabel(BigDecimal key) {
        if (key.compareTo(BigDecimal.valueOf(-1)) == 0) return "未税率";
        return key.toPlainString() + "%";
    }

    /** 销售回款率：按发票号关联应收，已收合计/应收合计（排除已作废应收），无数据返回0 */
    private BigDecimal calcSalePaymentRate(List<SaleInvoice> invoices) {
        List<String> invoiceNos = invoices.stream().map(SaleInvoice::getInvoiceNo)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (invoiceNos.isEmpty()) return BigDecimal.ZERO;
        List<AccountsReceivable> ars = accountsReceivableMapper.selectList(
                new LambdaQueryWrapper<AccountsReceivable>().in(AccountsReceivable::getInvoiceNo, invoiceNos));
        List<AccountsReceivable> validArs = ars.stream()
                .filter(a -> !"已作废".equals(a.getStatus())).collect(Collectors.toList());
        if (validArs.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = validArs.stream().map(a -> nvl(a.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal paid = validArs.stream().map(a -> nvl(a.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return paid.divide(total, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal percentOf(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return nvl(part).multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP);
    }

    // ==================== 辅助方法 ====================

    /** 销售订单数据权限：统一走 DataScopeResolver（created_by OR saler_id=当前员工） */
    private void applyOrderDataPermission(HttpServletRequest req, QueryWrapper<SaleOrder> qw) {
        dataScopeResolver.resolve(req).applyTo(qw, "saler_id");
    }

    private Map<String, Object> toOrderDetail(SaleOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", o.getOrderNo());
        m.put("customerName", o.getCustomerShortName());
        m.put("orderDate", o.getOrderDate());
        m.put("totalAmount", o.getTotalAmount());
        m.put("salerId", o.getSalerId());
        m.put("salerName", o.getSalerName());
        m.put("approvalStatus", o.getApprovalStatus());
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
