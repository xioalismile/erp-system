package com.erp.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.crm.entity.Opportunity;
import com.erp.crm.mapper.OpportunityMapper;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.masterdata.mapper.CustomerMapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.mapper.SaleOrderMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CRM报表中心
 * 汇总端点：funnel / sales-ranking / customer-analysis / payment-analysis
 * 明细端点：funnel-details / sales-ranking-details / customer-analysis-details / payment-analysis-details
 *
 * 数据权限：统一走 DataScopeResolver（角色 sys_role.data_scope：全部/本部门/仅本人），
 *   商机/线索归属列 assigned_sales_id、销售订单 saler_id、应收 saler_id（与各列表页同口径）。
 */
@Slf4j
@RestController
@RequestMapping("/api/report/crm")
@RequiredArgsConstructor
public class CrmReportController {

    private final OpportunityMapper opportunityMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final CustomerMapper customerMapper;
    private final AccountsReceivableMapper arMapper;
    private final DataScopeResolver dataScopeResolver;

    // ==================== 汇总报表 ====================

    /** 1. 销售漏斗分析：各阶段商机数量/金额/转化率 */
    @GetMapping("/funnel")
    public Result<Map<String, Object>> funnel(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        QueryWrapper<Opportunity> qw = new QueryWrapper<>();
        applyOppDataPermission(req, qw);
        if (startDate != null) qw.ge("create_date", startDate.atStartOfDay());
        if (endDate != null) qw.le("create_date", endDate.plusDays(1).atStartOfDay());
        List<Opportunity> list = opportunityMapper.selectList(qw);

        List<String> stageOrder = Arrays.asList("初步接触", "需求确认", "方案报价", "谈判", "成交", "失败");
        Map<String, List<Opportunity>> grouped = list.stream()
                .collect(Collectors.groupingBy(o -> o.getSalesStage() == null ? "未分类" : o.getSalesStage()));

        List<Map<String, Object>> stages = new ArrayList<>();
        int prevCount = 0;
        for (String stage : stageOrder) {
            List<Opportunity> items = grouped.getOrDefault(stage, Collections.emptyList());
            int count = items.size();
            BigDecimal amount = items.stream()
                    .map(o -> o.getEstimatedAmount() == null ? BigDecimal.ZERO : o.getEstimatedAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stage", stage);
            m.put("count", count);
            m.put("amount", amount);
            m.put("conversionRate", prevCount == 0 ? 100 : Math.round(count * 10000.0 / prevCount) / 100.0);
            stages.add(m);
            prevCount = count;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stages", stages);
        result.put("total", list.size());
        result.put("totalAmount", list.stream()
                .map(o -> o.getEstimatedAmount() == null ? BigDecimal.ZERO : o.getEstimatedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("wonCount", grouped.getOrDefault("成交", Collections.emptyList()).size());
        result.put("winRate", list.isEmpty() ? 0 : Math.round(grouped.getOrDefault("成交", Collections.emptyList()).size() * 10000.0 / list.size()) / 100.0);
        return Result.success(result);
    }

    /** 2. 销售业绩排行：销售员开单金额/成交商机数（支持月份或起止日期） */
    @GetMapping("/sales-ranking")
    public Result<List<Map<String, Object>>> salesRanking(
            HttpServletRequest req,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start, end;
        if (startDate != null && endDate != null) {
            start = startDate; end = endDate;
        } else {
            YearMonth ym = month != null ? YearMonth.parse(month) : YearMonth.now();
            start = ym.atDay(1); end = ym.atEndOfMonth();
        }

        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .between("order_date", start, end)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);
        List<SaleOrder> orders = saleOrderMapper.selectList(qw);

        Map<Long, List<SaleOrder>> bySaler = orders.stream()
                .filter(o -> o.getSalerId() != null)
                .collect(Collectors.groupingBy(SaleOrder::getSalerId));

        // 成交商机数
        QueryWrapper<Opportunity> oqw = new QueryWrapper<Opportunity>().eq("sales_stage", "成交");
        applyOppDataPermission(req, oqw);
        List<Opportunity> wonOpps = opportunityMapper.selectList(oqw);
        Map<Long, Long> wonBySaler = wonOpps.stream()
                .filter(o -> o.getAssignedSalesId() != null)
                .collect(Collectors.groupingBy(Opportunity::getAssignedSalesId, Collectors.counting()));

        List<Map<String, Object>> result = bySaler.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("salerId", e.getKey());
                    m.put("salerName", e.getValue().get(0).getSalerName());
                    m.put("orderCount", e.getValue().size());
                    m.put("orderAmount", e.getValue().stream()
                            .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    m.put("wonOpportunityCount", wonBySaler.getOrDefault(e.getKey(), 0L));
                    return m;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("orderAmount")).compareTo((BigDecimal) a.get("orderAmount")))
                .collect(Collectors.toList());
        return Result.success(result);
    }

    /** 3. 客户分析：销售额占比、TOP客户、复购率 */
    @GetMapping("/customer-analysis")
    public Result<Map<String, Object>> customerAnalysis(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);
        List<SaleOrder> orders = saleOrderMapper.selectList(qw);

        Map<Long, List<SaleOrder>> byCustomer = orders.stream()
                .filter(o -> o.getCustomerId() != null)
                .collect(Collectors.groupingBy(SaleOrder::getCustomerId));

        BigDecimal totalAmount = orders.stream()
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> topCustomers = byCustomer.entrySet().stream()
                .map(e -> {
                    BigDecimal amt = e.getValue().stream()
                            .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("customerId", e.getKey());
                    m.put("customerName", e.getValue().get(0).getCustomerShortName());
                    m.put("orderCount", e.getValue().size());
                    m.put("totalAmount", amt);
                    m.put("percentage", totalAmount.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                            amt.multiply(new BigDecimal("100")).divide(totalAmount, 2, java.math.RoundingMode.HALF_UP));
                    return m;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("totalAmount")).compareTo((BigDecimal) a.get("totalAmount")))
                .limit(20)
                .collect(Collectors.toList());

        long totalCustomers = byCustomer.size();
        long repeatCustomers = byCustomer.values().stream().filter(l -> l.size() > 1).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topCustomers", topCustomers);
        result.put("totalCustomers", totalCustomers);
        result.put("repeatCustomers", repeatCustomers);
        result.put("repeatRate", totalCustomers == 0 ? 0 : Math.round(repeatCustomers * 10000.0 / totalCustomers) / 100.0);
        result.put("totalAmount", totalAmount);
        result.put("totalOrders", orders.size());
        return Result.success(result);
    }

    /** 4. 回款分析：应收/已收/逾期分布 */
    @GetMapping("/payment-analysis")
    public Result<Map<String, Object>> paymentAnalysis(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<AccountsReceivable> qw = new QueryWrapper<AccountsReceivable>()
                .between("ar_date", startDate, endDate);
        applyArDataPermission(req, qw);
        List<AccountsReceivable> arList = arMapper.selectList(qw);

        BigDecimal totalReceivable = arList.stream()
                .map(a -> a.getTotalAmount() == null ? BigDecimal.ZERO : a.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceived = arList.stream()
                .map(a -> a.getPaidAmount() == null ? BigDecimal.ZERO : a.getPaidAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOverdue = arList.stream()
                .filter(a -> "逾期".equalsIgnoreCase(a.getStatus()) ||
                        (a.getDueDate() != null && a.getDueDate().isBefore(LocalDate.now()) && !"已结清".equalsIgnoreCase(a.getStatus())))
                .map(a -> a.getBalanceAmount() == null ? BigDecimal.ZERO : a.getBalanceAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<AccountsReceivable>> byStatus = arList.stream()
                .collect(Collectors.groupingBy(a -> a.getStatus() == null ? "未知" : a.getStatus()));
        List<Map<String, Object>> statusDist = byStatus.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("status", e.getKey());
                    m.put("count", e.getValue().size());
                    m.put("amount", e.getValue().stream()
                            .map(a -> a.getTotalAmount() == null ? BigDecimal.ZERO : a.getTotalAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    return m;
                }).collect(Collectors.toList());

        Map<String, List<AccountsReceivable>> byCustomer = arList.stream()
                .filter(a -> a.getCustomerName() != null)
                .collect(Collectors.groupingBy(AccountsReceivable::getCustomerName));
        List<Map<String, Object>> topArCustomer = byCustomer.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("customerName", e.getKey());
                    m.put("count", e.getValue().size());
                    m.put("totalAmount", e.getValue().stream()
                            .map(a -> a.getTotalAmount() == null ? BigDecimal.ZERO : a.getTotalAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    m.put("balanceAmount", e.getValue().stream()
                            .map(a -> a.getBalanceAmount() == null ? BigDecimal.ZERO : a.getBalanceAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    return m;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("balanceAmount")).compareTo((BigDecimal) a.get("balanceAmount")))
                .limit(10)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReceivable", totalReceivable);
        result.put("totalReceived", totalReceived);
        result.put("totalOverdue", totalOverdue);
        result.put("receiveRate", totalReceivable.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                totalReceived.multiply(new BigDecimal("100")).divide(totalReceivable, 2, java.math.RoundingMode.HALF_UP));
        result.put("statusDistribution", statusDist);
        result.put("topArCustomer", topArCustomer);
        return Result.success(result);
    }

    // ==================== 明细报表（可导出） ====================

    /** 1-明细. 商机明细列表（支持时间/关键字/阶段/销售员筛选，分页） */
    @GetMapping("/funnel-details")
    public Result<Map<String, Object>> funnelDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) Long salerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<Opportunity> qw = new QueryWrapper<>();
        applyOppDataPermission(req, qw);
        if (startDate != null) qw.ge("create_date", startDate.atStartOfDay());
        if (endDate != null) qw.le("create_date", endDate.plusDays(1).atStartOfDay());
        if (stage != null && !stage.isEmpty()) qw.eq("sales_stage", stage);
        if (salerId != null) qw.eq("assigned_sales_id", salerId);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("opportunity_no", keyword)
                    .or().like("opportunity_name", keyword)
                    .or().like("customer_name", keyword));
        }
        qw.orderByDesc("create_date");
        List<Opportunity> all = opportunityMapper.selectList(qw);
        // CRM↔销售联动：商机已包含 saleOrderId/saleOrderNo/actualAmount 回写字段，toOppDetail 直接输出
        return paginate(all.stream().map(this::toOppDetail).collect(Collectors.toList()), page, size);
    }

    /** 2-明细. 销售订单明细列表（联动商机：显示订单关联的商机号/名称/阶段） */
    @GetMapping("/sales-ranking-details")
    public Result<Map<String, Object>> salesRankingDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long salerId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<SaleOrder> qw = new QueryWrapper<>();
        applyOrderDataPermission(req, qw);
        if (startDate != null) qw.ge("order_date", startDate);
        if (endDate != null) qw.le("order_date", endDate);
        if (salerId != null) qw.eq("saler_id", salerId);
        if (approvalStatus != null && !approvalStatus.isEmpty()) qw.eq("approval_status", approvalStatus);
        if (keyword != null && !keyword.isEmpty()) {
            // 注意：SaleOrder.salerName 为 @TableField(exist=false) 非数据库列，不能作为查询列
            qw.and(w -> w.like("order_no", keyword)
                    .or().like("customer_short_name", keyword));
        }
        qw.orderByDesc("order_date");
        List<SaleOrder> all = saleOrderMapper.selectList(qw);
        // 批量查询关联商机，避免 N+1
        Map<Long, Opportunity> oppMap = batchLoadOpportunities(all);
        return paginate(all.stream().map(o -> toOrderDetail(o, oppMap)).collect(Collectors.toList()), page, size);
    }

    /** 3-明细. 客户订单明细列表（联动商机：显示订单关联的商机号/名称/阶段） */
    @GetMapping("/customer-analysis-details")
    public Result<Map<String, Object>> customerAnalysisDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<SaleOrder> qw = new QueryWrapper<SaleOrder>()
                .eq("approval_status", "已生效");
        applyOrderDataPermission(req, qw);
        if (startDate != null) qw.ge("order_date", startDate);
        if (endDate != null) qw.le("order_date", endDate);
        if (customerId != null) qw.eq("customer_id", customerId);
        if (keyword != null && !keyword.isEmpty()) {
            // 注意：SaleOrder.salerName 为 @TableField(exist=false) 非数据库列，不能作为查询列
            qw.and(w -> w.like("order_no", keyword)
                    .or().like("customer_short_name", keyword));
        }
        qw.orderByDesc("order_date");
        List<SaleOrder> all = saleOrderMapper.selectList(qw);
        // 批量查询关联商机
        Map<Long, Opportunity> oppMap = batchLoadOpportunities(all);
        return paginate(all.stream().map(o -> toOrderDetail(o, oppMap)).collect(Collectors.toList()), page, size);
    }

    /** 4-明细. 应收明细列表（联动销售订单+商机：通过客户名反查关联商机） */
    @GetMapping("/payment-analysis-details")
    public Result<Map<String, Object>> paymentAnalysisDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<AccountsReceivable> qw = new QueryWrapper<>();
        applyArDataPermission(req, qw);
        if (startDate != null) qw.ge("ar_date", startDate);
        if (endDate != null) qw.le("ar_date", endDate);
        if (status != null && !status.isEmpty()) qw.eq("status", status);
        if (customerName != null && !customerName.isEmpty()) qw.like("customer_name", customerName);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("ar_no", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("source_bill_no", keyword));
        }
        qw.orderByDesc("ar_date");
        List<AccountsReceivable> all = arMapper.selectList(qw);
        // 联动：通过 sourceBillNo 反查销售订单，再反查商机；同时按客户名批量查商机作为参考
        Map<String, SaleOrder> orderBySourceNo = batchLoadOrdersBySourceNo(all);
        Map<Long, Opportunity> oppMap = batchLoadOpportunities(new ArrayList<>(orderBySourceNo.values()));
        Map<String, List<Opportunity>> oppByCustomer = batchLoadOppByCustomer(all);
        return paginate(all.stream().map(a -> toArDetail(a, orderBySourceNo, oppMap, oppByCustomer)).collect(Collectors.toList()), page, size);
    }

    /** 批量加载销售订单关联的商机（基于 SaleOrder.opportunityId），返回 opportunityId → Opportunity 映射 */
    private Map<Long, Opportunity> batchLoadOpportunities(List<SaleOrder> orders) {
        Set<Long> oppIds = orders.stream()
                .map(SaleOrder::getOpportunityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (oppIds.isEmpty()) return Collections.emptyMap();
        return opportunityMapper.selectList(new LambdaQueryWrapper<Opportunity>()
                .in(Opportunity::getOpportunityId, oppIds))
                .stream().collect(Collectors.toMap(Opportunity::getOpportunityId, o -> o));
    }

    /** 通过应收 sourceBillNo 反查销售订单（sourceBillNo 可能是销售出库/发票单号，需先反查出库/发票→saleOrderId）。
     *  简化策略：直接用 sourceBillNo 在 sale_order 表匹配 order_no（部分场景应 sourceBillType=sale_delivery 时 sourceBillNo 为出库单号，
     *  无法直接匹配订单号；此处改为通过 sale_order_no 字段在订单表查询，匹配不上的返回空 Map）。 */
    private Map<String, SaleOrder> batchLoadOrdersBySourceNo(List<AccountsReceivable> arList) {
        Set<String> sourceNos = arList.stream()
                .map(AccountsReceivable::getSourceBillNo)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (sourceNos.isEmpty()) return Collections.emptyMap();
        // 直接尝试用 sourceBillNo 匹配销售订单号（适用于 AR.sourceBillNo 直接存订单号的场景）
        List<SaleOrder> orders = saleOrderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                .in(SaleOrder::getOrderNo, sourceNos));
        return orders.stream().collect(Collectors.toMap(SaleOrder::getOrderNo, o -> o, (a, b) -> a));
    }

    /** 按客户名批量加载商机，返回 customerName → 商机列表 映射（用于应收明细显示该客户关联的商机） */
    private Map<String, List<Opportunity>> batchLoadOppByCustomer(List<AccountsReceivable> arList) {
        Set<String> customerNames = arList.stream()
                .map(AccountsReceivable::getCustomerName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (customerNames.isEmpty()) return Collections.emptyMap();
        return opportunityMapper.selectList(new LambdaQueryWrapper<Opportunity>()
                .in(Opportunity::getCustomerName, customerNames))
                .stream().collect(Collectors.groupingBy(Opportunity::getCustomerName));
    }

    // ==================== 辅助方法 ====================

    /** 商机数据权限：统一走 DataScopeResolver（created_by OR assigned_sales_id=当前员工） */
    private void applyOppDataPermission(HttpServletRequest req, QueryWrapper<Opportunity> qw) {
        dataScopeResolver.resolve(req).applyTo(qw, "assigned_sales_id");
    }

    /** 销售订单数据权限：统一走 DataScopeResolver（created_by OR saler_id=当前员工） */
    private void applyOrderDataPermission(HttpServletRequest req, QueryWrapper<SaleOrder> qw) {
        dataScopeResolver.resolve(req).applyTo(qw, "saler_id");
    }

    /** 应收数据权限：统一走 DataScopeResolver（created_by OR saler_id=当前员工） */
    private void applyArDataPermission(HttpServletRequest req, QueryWrapper<AccountsReceivable> qw) {
        dataScopeResolver.resolve(req).applyTo(qw, "saler_id");
    }

    /** 商机明细（含→销售订单联动字段：saleOrderId/saleOrderNo/actualAmount 销售订单审核时回写） */
    private Map<String, Object> toOppDetail(Opportunity o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("opportunityNo", o.getOpportunityNo());
        m.put("opportunityName", o.getOpportunityName());
        m.put("customerName", o.getCustomerName());
        m.put("salesStage", o.getSalesStage());
        m.put("estimatedAmount", o.getEstimatedAmount());
        m.put("actualAmount", o.getActualAmount());
        // CRM→销售 联动字段
        m.put("saleOrderId", o.getSaleOrderId());
        m.put("saleOrderNo", o.getSaleOrderNo());
        m.put("isClosed", o.getIsClosed());
        m.put("actualCloseDate", o.getActualCloseDate());
        m.put("assignedSalesId", o.getAssignedSalesId());
        m.put("expectedCloseDate", o.getExpectedCloseDate());
        m.put("createDate", o.getCreateDate());
        return m;
    }

    /** 销售订单明细（含→商机联动字段：opportunityId/opportunityNo，并补全商机名称/阶段） */
    private Map<String, Object> toOrderDetail(SaleOrder o, Map<Long, Opportunity> oppMap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", o.getOrderNo());
        m.put("customerName", o.getCustomerShortName());
        m.put("orderDate", o.getOrderDate());
        m.put("totalAmount", o.getTotalAmount());
        m.put("salerId", o.getSalerId());
        m.put("salerName", o.getSalerName());
        m.put("approvalStatus", o.getApprovalStatus());
        // 销售→CRM 联动字段
        m.put("opportunityId", o.getOpportunityId());
        m.put("opportunityNo", o.getOpportunityNo());
        if (o.getOpportunityId() != null && oppMap.containsKey(o.getOpportunityId())) {
            Opportunity opp = oppMap.get(o.getOpportunityId());
            m.put("opportunityName", opp.getOpportunityName());
            m.put("opportunityStage", opp.getSalesStage());
            m.put("opportunityEstimatedAmount", opp.getEstimatedAmount());
        }
        return m;
    }

    /** 应收明细（含→销售订单→商机联动：通过 sourceBillNo 反查订单，再通过客户名反查商机） */
    private Map<String, Object> toArDetail(AccountsReceivable a,
                                           Map<String, SaleOrder> orderBySourceNo,
                                           Map<Long, Opportunity> oppMap,
                                           Map<String, List<Opportunity>> oppByCustomer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("arNo", a.getArNo());
        m.put("customerName", a.getCustomerName());
        m.put("arDate", a.getArDate());
        m.put("dueDate", a.getDueDate());
        m.put("totalAmount", a.getTotalAmount());
        m.put("paidAmount", a.getPaidAmount());
        m.put("balanceAmount", a.getBalanceAmount());
        m.put("status", a.getStatus());
        m.put("sourceBillType", a.getSourceBillType());
        m.put("sourceBillNo", a.getSourceBillNo());
        m.put("salerId", a.getSalerId());
        // 应收→销售订单 联动（若 sourceBillNo 直接存订单号则可匹配）
        SaleOrder linkedOrder = a.getSourceBillNo() != null ? orderBySourceNo.get(a.getSourceBillNo()) : null;
        if (linkedOrder != null) {
            m.put("saleOrderNo", linkedOrder.getOrderNo());
            m.put("saleOrderAmount", linkedOrder.getTotalAmount());
            // 销售→CRM 联动
            if (linkedOrder.getOpportunityId() != null && oppMap.containsKey(linkedOrder.getOpportunityId())) {
                Opportunity opp = oppMap.get(linkedOrder.getOpportunityId());
                m.put("opportunityNo", opp.getOpportunityNo());
                m.put("opportunityName", opp.getOpportunityName());
                m.put("opportunityStage", opp.getSalesStage());
            }
        }
        // 兜底：若通过 sourceBillNo 未匹配到订单，则按客户名显示该客户关联的商机列表
        if (!m.containsKey("opportunityNo") && a.getCustomerName() != null) {
            List<Opportunity> custOpps = oppByCustomer.getOrDefault(a.getCustomerName(), Collections.emptyList());
            if (!custOpps.isEmpty()) {
                Opportunity latest = custOpps.get(custOpps.size() - 1);
                m.put("opportunityNo", latest.getOpportunityNo());
                m.put("opportunityName", latest.getOpportunityName());
                m.put("opportunityStage", latest.getSalesStage());
                m.put("customerOpportunityCount", custOpps.size());
            }
        }
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
