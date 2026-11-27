package com.erp.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.OperationExpense;
import com.erp.finance.mapper.AccountsPayableMapper;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.entity.PaymentVoucher;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.mapper.PaymentVoucherMapper;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.mapper.SaleOrderMapper;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 财务报表中心
 * 汇总端点：ar-ap-summary / aging / profit / expense-allocation
 * 明细端点：ar-ap-details / aging-details / profit-details / expense-allocation-details
 *
 * 数据权限：统一走 DataScopeResolver（角色 sys_role.data_scope：全部/本部门/仅本人）。
 *   应收 saler_id、应付 buyer_id、收款/付款单 created_by、运营费用 applicant_name（与各列表页同口径）。
 */
@Slf4j
@RestController
@RequestMapping("/api/report/finance")
@RequiredArgsConstructor
public class FinanceReportController {

    private final AccountsReceivableMapper arMapper;
    private final AccountsPayableMapper apMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final PaymentVoucherMapper voucherMapper;
    private final SaleOrderMapper saleOrderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final com.erp.finance.mapper.OperationExpenseMapper operationExpenseMapper;
    private final DataScopeResolver dataScopeResolver;

    // ==================== 汇总报表 ====================

    /** 1. 应收应付汇总：收支总览 */
    @GetMapping("/ar-ap-summary")
    public Result<Map<String, Object>> arApSummary(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<AccountsReceivable> arQw = new QueryWrapper<AccountsReceivable>()
                .between("ar_date", startDate, endDate);
        applyArDataPermission(req, arQw);
        List<AccountsReceivable> arList = arMapper.selectList(arQw);

        QueryWrapper<AccountsPayable> apQw = new QueryWrapper<AccountsPayable>()
                .between("ap_date", startDate, endDate);
        dataScopeResolver.resolve(req).applyTo(apQw, "buyer_id");
        List<AccountsPayable> apList = apMapper.selectList(apQw);
        QueryWrapper<PaymentReceipt> recQw = new QueryWrapper<PaymentReceipt>()
                .between("receipt_date", startDate, endDate);
        dataScopeResolver.resolve(req).applyTo(recQw, null);
        List<PaymentReceipt> receipts = receiptMapper.selectList(recQw);
        QueryWrapper<PaymentVoucher> vouQw = new QueryWrapper<PaymentVoucher>()
                .between("payment_date", startDate, endDate);
        dataScopeResolver.resolve(req).applyTo(vouQw, null);
        List<PaymentVoucher> vouchers = voucherMapper.selectList(vouQw);

        BigDecimal totalReceivable = arList.stream()
                .map(a -> a.getTotalAmount() == null ? BigDecimal.ZERO : a.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceived = receipts.stream()
                .map(r -> r.getAmount() == null ? BigDecimal.ZERO : r.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPayable = apList.stream()
                .map(a -> a.getTotalAmount() == null ? BigDecimal.ZERO : a.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = vouchers.stream()
                .map(v -> v.getAmount() == null ? BigDecimal.ZERO : v.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReceivable", totalReceivable);
        result.put("totalReceived", totalReceived);
        result.put("totalPayable", totalPayable);
        result.put("totalPaid", totalPaid);
        result.put("netCashFlow", totalReceived.subtract(totalPaid));
        result.put("arBalance", totalReceivable.subtract(totalReceived));
        result.put("apBalance", totalPayable.subtract(totalPaid));
        result.put("arCount", arList.size());
        result.put("apCount", apList.size());
        result.put("receiptCount", receipts.size());
        result.put("voucherCount", vouchers.size());
        return Result.success(result);
    }

    /** 2. 账龄分析：30/60/90/180天+逾期分布 */
    @GetMapping("/aging")
    public Result<Map<String, Object>> aging(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        QueryWrapper<AccountsReceivable> qw = new QueryWrapper<AccountsReceivable>()
                .ne("status", "已结清");
        applyArDataPermission(req, qw);
        if (startDate != null) qw.ge("ar_date", startDate);
        if (endDate != null) qw.le("ar_date", endDate);
        List<AccountsReceivable> arList = arMapper.selectList(qw);
        LocalDate today = LocalDate.now();

        // 账龄分桶：0-30, 31-60, 61-90, 91-180, 180+
        long[] buckets = new long[5];
        BigDecimal[] bucketAmounts = new BigDecimal[5];
        for (int i = 0; i < 5; i++) bucketAmounts[i] = BigDecimal.ZERO;

        List<Map<String, Object>> items = new ArrayList<>();
        for (AccountsReceivable ar : arList) {
            BigDecimal balance = ar.getBalanceAmount() == null ? BigDecimal.ZERO : ar.getBalanceAmount();
            if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;
            long ageDays = ar.getArDate() == null ? 0 : ChronoUnit.DAYS.between(ar.getArDate(), today);
            int idx;
            if (ageDays <= 30) idx = 0;
            else if (ageDays <= 60) idx = 1;
            else if (ageDays <= 90) idx = 2;
            else if (ageDays <= 180) idx = 3;
            else idx = 4;
            buckets[idx]++;
            bucketAmounts[idx] = bucketAmounts[idx].add(balance);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("arNo", ar.getArNo());
            m.put("customerName", ar.getCustomerName());
            m.put("arDate", ar.getArDate());
            m.put("dueDate", ar.getDueDate());
            m.put("totalAmount", ar.getTotalAmount());
            m.put("balanceAmount", balance);
            m.put("ageDays", ageDays);
            m.put("bucket", new String[]{"0-30天", "31-60天", "61-90天", "91-180天", "180天以上"}[idx]);
            m.put("isOverdue", ar.getDueDate() != null && ar.getDueDate().isBefore(today));
            items.add(m);
        }

        List<Map<String, Object>> bucketList = new ArrayList<>();
        String[] labels = {"0-30天", "31-60天", "61-90天", "91-180天", "180天以上"};
        for (int i = 0; i < 5; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bucket", labels[i]);
            m.put("count", buckets[i]);
            m.put("amount", bucketAmounts[i]);
            bucketList.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buckets", bucketList);
        result.put("totalBalance", Arrays.stream(bucketAmounts).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("totalCount", Arrays.stream(buckets).sum());
        result.put("overdueCount", items.stream().filter(i -> Boolean.TRUE.equals(i.get("isOverdue"))).count());
        result.put("overdueAmount", items.stream()
                .filter(i -> Boolean.TRUE.equals(i.get("isOverdue")))
                .map(i -> (BigDecimal) i.get("balanceAmount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("items", items);
        return Result.success(result);
    }

    /** 3. 利润分析：收入-成本-费用 */
    @GetMapping("/profit")
    public Result<Map<String, Object>> profit(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        // 收入：销售订单（已生效）
        QueryWrapper<SaleOrder> saleQw = new QueryWrapper<SaleOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        dataScopeResolver.resolve(req).applyTo(saleQw, "saler_id");
        List<SaleOrder> saleOrders = saleOrderMapper.selectList(saleQw);
        BigDecimal revenue = saleOrders.stream()
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxAmount = saleOrders.stream()
                .map(o -> o.getTaxAmount() == null ? BigDecimal.ZERO : o.getTaxAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 成本：采购订单（已生效）
        QueryWrapper<PurchaseOrder> costQw = new QueryWrapper<PurchaseOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        dataScopeResolver.resolve(req).applyTo(costQw, "buyer_id");
        List<PurchaseOrder> purchaseOrders = purchaseOrderMapper.selectList(costQw);
        BigDecimal purchaseCost = purchaseOrders.stream()
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 费用：运营费用
        QueryWrapper<OperationExpense> expQw = new QueryWrapper<OperationExpense>()
                .between("expense_date", startDate, endDate)
                .eq("status", "已生效");
        dataScopeResolver.resolve(req).applyTo(expQw, null, "applicant_name");
        List<OperationExpense> expenses = operationExpenseMapper.selectList(expQw);
        BigDecimal totalExpense = expenses.stream()
                .map(e -> e.getAmount() == null ? BigDecimal.ZERO : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 按费用类型分组
        Map<String, List<OperationExpense>> byType = expenses.stream()
                .filter(e -> e.getExpenseType() != null)
                .collect(Collectors.groupingBy(OperationExpense::getExpenseType));
        List<Map<String, Object>> expenseByType = byType.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("expenseType", e.getKey());
            m.put("count", e.getValue().size());
            m.put("amount", e.getValue().stream()
                    .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            return m;
        }).collect(Collectors.toList());

        BigDecimal grossProfit = revenue.subtract(purchaseCost);
        BigDecimal netProfit = grossProfit.subtract(totalExpense);
        BigDecimal grossMargin = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                grossProfit.multiply(new BigDecimal("100")).divide(revenue, 2, RoundingMode.HALF_UP);
        BigDecimal netMargin = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                netProfit.multiply(new BigDecimal("100")).divide(revenue, 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revenue", revenue);
        result.put("taxAmount", taxAmount);
        result.put("purchaseCost", purchaseCost);
        result.put("operatingExpense", totalExpense);
        result.put("grossProfit", grossProfit);
        result.put("netProfit", netProfit);
        result.put("grossMargin", grossMargin);
        result.put("netMargin", netMargin);
        result.put("expenseByType", expenseByType);
        result.put("saleOrderCount", saleOrders.size());
        result.put("purchaseOrderCount", purchaseOrders.size());
        return Result.success(result);
    }

    /** 4. 费用分摊统计：各订单/部门费用占比 */
    @GetMapping("/expense-allocation")
    public Result<Map<String, Object>> expenseAllocation(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<OperationExpense> expQw = new QueryWrapper<OperationExpense>()
                .between("expense_date", startDate, endDate)
                .eq("status", "已生效");
        dataScopeResolver.resolve(req).applyTo(expQw, null, "applicant_name");
        List<OperationExpense> expenses = operationExpenseMapper.selectList(expQw);

        BigDecimal total = expenses.stream()
                .map(e -> e.getAmount() == null ? BigDecimal.ZERO : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 按部门
        Map<Long, List<OperationExpense>> byDept = expenses.stream()
                .filter(e -> e.getDepartmentId() != null)
                .collect(Collectors.groupingBy(OperationExpense::getDepartmentId));
        List<Map<String, Object>> deptList = byDept.entrySet().stream().map(e -> {
            BigDecimal amt = e.getValue().stream()
                    .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("departmentId", e.getKey());
            m.put("departmentName", e.getValue().get(0).getDepartmentName());
            m.put("count", e.getValue().size());
            m.put("amount", amt);
            m.put("percentage", total.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    amt.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP));
            return m;
        }).sorted((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")))
                .collect(Collectors.toList());

        // 按费用类型
        Map<String, List<OperationExpense>> byType = expenses.stream()
                .filter(e -> e.getExpenseType() != null)
                .collect(Collectors.groupingBy(OperationExpense::getExpenseType));
        List<Map<String, Object>> typeList = byType.entrySet().stream().map(e -> {
            BigDecimal amt = e.getValue().stream()
                    .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("expenseType", e.getKey());
            m.put("count", e.getValue().size());
            m.put("amount", amt);
            m.put("percentage", total.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    amt.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP));
            return m;
        }).sorted((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")))
                .collect(Collectors.toList());

        // 按关联订单
        Map<String, List<OperationExpense>> byOrder = expenses.stream()
                .filter(e -> e.getRelatedOrderNo() != null && !e.getRelatedOrderNo().isEmpty())
                .collect(Collectors.groupingBy(OperationExpense::getRelatedOrderNo));
        List<Map<String, Object>> orderList = byOrder.entrySet().stream().map(e -> {
            BigDecimal amt = e.getValue().stream()
                    .map(x -> x.getAmount() == null ? BigDecimal.ZERO : x.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", e.getKey());
            m.put("orderType", e.getValue().get(0).getRelatedOrderType());
            m.put("count", e.getValue().size());
            m.put("amount", amt);
            return m;
        }).sorted((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalExpense", total);
        result.put("totalCount", expenses.size());
        result.put("byDepartment", deptList);
        result.put("byType", typeList);
        result.put("byOrder", orderList);
        return Result.success(result);
    }

    // ==================== 明细报表（可导出） ====================

    /** 1-明细. 应收应付明细列表（AR+AP 合并，支持 billType 筛选） */
    @GetMapping("/ar-ap-details")
    public Result<Map<String, Object>> arApDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Map<String, Object>> all = new ArrayList<>();

        // 应收记录
        if (billType == null || billType.isEmpty() || "AR".equalsIgnoreCase(billType)) {
            QueryWrapper<AccountsReceivable> qw = new QueryWrapper<>();
            applyArDataPermission(req, qw);
            if (startDate != null) qw.ge("ar_date", startDate);
            if (endDate != null) qw.le("ar_date", endDate);
            if (status != null && !status.isEmpty()) qw.eq("status", status);
            if (keyword != null && !keyword.isEmpty()) {
                qw.and(w -> w.like("ar_no", keyword)
                        .or().like("customer_name", keyword)
                        .or().like("source_bill_no", keyword));
            }
            qw.orderByDesc("ar_date");
            List<AccountsReceivable> arList = arMapper.selectList(qw);
            for (AccountsReceivable ar : arList) {
                all.add(toArApDetail(ar));
            }
        }

        // 应付记录
        if (billType == null || billType.isEmpty() || "AP".equalsIgnoreCase(billType)) {
            QueryWrapper<AccountsPayable> qw = new QueryWrapper<>();
            dataScopeResolver.resolve(req).applyTo(qw, "buyer_id");
            if (startDate != null) qw.ge("ap_date", startDate);
            if (endDate != null) qw.le("ap_date", endDate);
            if (status != null && !status.isEmpty()) qw.eq("status", status);
            if (keyword != null && !keyword.isEmpty()) {
                qw.and(w -> w.like("ap_no", keyword)
                        .or().like("supplier_name", keyword)
                        .or().like("source_bill_no", keyword));
            }
            qw.orderByDesc("ap_date");
            List<AccountsPayable> apList = apMapper.selectList(qw);
            for (AccountsPayable ap : apList) {
                all.add(toArApDetail(ap));
            }
        }

        // 合并后按单据日期倒序
        all.sort((a, b) -> {
            LocalDate d1 = (LocalDate) a.get("billDate");
            LocalDate d2 = (LocalDate) b.get("billDate");
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            return d2.compareTo(d1);
        });

        return paginate(all, page, size);
    }

    /** 2-明细. 账龄明细列表（4 档：0-30 / 31-60 / 61-90 / 90+） */
    @GetMapping("/aging-details")
    public Result<Map<String, Object>> agingDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String agingBucket,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<AccountsReceivable> qw = new QueryWrapper<>();
        applyArDataPermission(req, qw);
        if (startDate != null) qw.ge("ar_date", startDate);
        if (endDate != null) qw.le("ar_date", endDate);
        if (status != null && !status.isEmpty()) qw.eq("status", status);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("ar_no", keyword)
                    .or().like("customer_name", keyword)
                    .or().like("source_bill_no", keyword));
        }
        qw.orderByDesc("ar_date");
        List<AccountsReceivable> arList = arMapper.selectList(qw);

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> all = new ArrayList<>();
        for (AccountsReceivable ar : arList) {
            Map<String, Object> m = toAgingDetail(ar, today);
            if (agingBucket != null && !agingBucket.isEmpty()
                    && !agingBucket.equals(m.get("agingBucket"))) continue;
            all.add(m);
        }
        return paginate(all, page, size);
    }

    /** 3-明细. 利润明细列表（按月汇总：收入/成本/费用/利润/利润率） */
    @GetMapping("/profit-details")
    public Result<Map<String, Object>> profitDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1).minusMonths(11);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<SaleOrder> saleQw = new QueryWrapper<SaleOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        dataScopeResolver.resolve(req).applyTo(saleQw, "saler_id");
        List<SaleOrder> saleOrders = saleOrderMapper.selectList(saleQw);
        QueryWrapper<PurchaseOrder> costQw = new QueryWrapper<PurchaseOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        dataScopeResolver.resolve(req).applyTo(costQw, "buyer_id");
        List<PurchaseOrder> purchaseOrders = purchaseOrderMapper.selectList(costQw);
        QueryWrapper<OperationExpense> expQw = new QueryWrapper<OperationExpense>()
                .between("expense_date", startDate, endDate)
                .eq("status", "已生效");
        dataScopeResolver.resolve(req).applyTo(expQw, null, "applicant_name");
        List<OperationExpense> expenses = operationExpenseMapper.selectList(expQw);

        Map<String, BigDecimal> revenueByMonth = new HashMap<>();
        for (SaleOrder o : saleOrders) {
            if (o.getOrderDate() == null) continue;
            String ym = YearMonth.from(o.getOrderDate()).toString();
            revenueByMonth.merge(ym, o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount(), BigDecimal::add);
        }
        Map<String, BigDecimal> costByMonth = new HashMap<>();
        for (PurchaseOrder o : purchaseOrders) {
            if (o.getOrderDate() == null) continue;
            String ym = YearMonth.from(o.getOrderDate()).toString();
            costByMonth.merge(ym, o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount(), BigDecimal::add);
        }
        Map<String, BigDecimal> expenseByMonth = new HashMap<>();
        for (OperationExpense e : expenses) {
            if (e.getExpenseDate() == null) continue;
            String ym = YearMonth.from(e.getExpenseDate()).toString();
            expenseByMonth.merge(ym, e.getAmount() == null ? BigDecimal.ZERO : e.getAmount(), BigDecimal::add);
        }

        TreeSet<String> months = new TreeSet<>();
        months.addAll(revenueByMonth.keySet());
        months.addAll(costByMonth.keySet());
        months.addAll(expenseByMonth.keySet());

        List<Map<String, Object>> all = new ArrayList<>();
        for (String ym : months) {
            BigDecimal revenue = revenueByMonth.getOrDefault(ym, BigDecimal.ZERO);
            BigDecimal cost = costByMonth.getOrDefault(ym, BigDecimal.ZERO);
            BigDecimal expense = expenseByMonth.getOrDefault(ym, BigDecimal.ZERO);
            BigDecimal profit = revenue.subtract(cost).subtract(expense);
            BigDecimal profitRate = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                    profit.multiply(new BigDecimal("100")).divide(revenue, 2, RoundingMode.HALF_UP);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", ym);
            m.put("revenue", revenue);
            m.put("cost", cost);
            m.put("expense", expense);
            m.put("profit", profit);
            m.put("profitRate", profitRate);
            all.add(m);
        }
        // 月份倒序
        all.sort((a, b) -> ((String) b.get("month")).compareTo((String) a.get("month")));
        return paginate(all, page, size);
    }

    /** 4-明细. 费用明细列表 */
    @GetMapping("/expense-allocation-details")
    public Result<Map<String, Object>> expenseAllocationDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        QueryWrapper<OperationExpense> qw = new QueryWrapper<>();
        dataScopeResolver.resolve(req).applyTo(qw, null, "applicant_name");
        if (startDate != null) qw.ge("expense_date", startDate);
        if (endDate != null) qw.le("expense_date", endDate);
        if (expenseType != null && !expenseType.isEmpty()) qw.eq("expense_type", expenseType);
        if (status != null && !status.isEmpty()) qw.eq("status", status);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("expense_no", keyword)
                    .or().like("related_order_no", keyword)
                    .or().like("department_name", keyword));
        }
        qw.orderByDesc("expense_date");
        List<OperationExpense> list = operationExpenseMapper.selectList(qw);
        List<Map<String, Object>> all = list.stream().map(this::toExpenseDetail).collect(Collectors.toList());
        return paginate(all, page, size);
    }

    // ==================== 辅助方法 ====================

    /** 应收数据权限：统一走 DataScopeResolver（created_by OR saler_id=当前员工） */
    private void applyArDataPermission(HttpServletRequest req, QueryWrapper<AccountsReceivable> qw) {
        dataScopeResolver.resolve(req).applyTo(qw, "saler_id");
    }

    private Map<String, Object> toArApDetail(AccountsReceivable ar) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("billNo", ar.getArNo());
        m.put("billType", "AR");
        m.put("customerName", ar.getCustomerName());
        m.put("supplierName", null);
        m.put("billDate", ar.getArDate());
        m.put("dueDate", ar.getDueDate());
        m.put("totalAmount", ar.getTotalAmount());
        m.put("paidAmount", ar.getPaidAmount());
        m.put("balanceAmount", ar.getBalanceAmount());
        m.put("status", ar.getStatus());
        m.put("sourceBillNo", ar.getSourceBillNo());
        return m;
    }

    private Map<String, Object> toArApDetail(AccountsPayable ap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("billNo", ap.getApNo());
        m.put("billType", "AP");
        m.put("customerName", null);
        m.put("supplierName", ap.getSupplierName());
        m.put("billDate", ap.getApDate());
        m.put("dueDate", ap.getDueDate());
        m.put("totalAmount", ap.getTotalAmount());
        m.put("paidAmount", ap.getPaidAmount());
        m.put("balanceAmount", ap.getBalanceAmount());
        m.put("status", ap.getStatus());
        m.put("sourceBillNo", ap.getSourceBillNo());
        return m;
    }

    private Map<String, Object> toAgingDetail(AccountsReceivable ar, LocalDate today) {
        BigDecimal balance = ar.getBalanceAmount() == null ? BigDecimal.ZERO : ar.getBalanceAmount();
        long daysOverdue = 0;
        if (ar.getDueDate() != null && ar.getDueDate().isBefore(today)) {
            daysOverdue = ChronoUnit.DAYS.between(ar.getDueDate(), today);
        }
        long ageDays = ar.getArDate() == null ? 0 : ChronoUnit.DAYS.between(ar.getArDate(), today);
        String bucket;
        if (ageDays <= 30) bucket = "0-30";
        else if (ageDays <= 60) bucket = "31-60";
        else if (ageDays <= 90) bucket = "61-90";
        else bucket = "90+";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("arNo", ar.getArNo());
        m.put("customerName", ar.getCustomerName());
        m.put("arDate", ar.getArDate());
        m.put("dueDate", ar.getDueDate());
        m.put("totalAmount", ar.getTotalAmount());
        m.put("balanceAmount", balance);
        m.put("daysOverdue", daysOverdue);
        m.put("agingBucket", bucket);
        m.put("ageDays", ageDays);
        m.put("status", ar.getStatus());
        return m;
    }

    private Map<String, Object> toExpenseDetail(OperationExpense e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expenseNo", e.getExpenseNo());
        m.put("expenseType", e.getExpenseType());
        m.put("amount", e.getAmount());
        m.put("expenseDate", e.getExpenseDate());
        m.put("departmentId", e.getDepartmentId());
        m.put("departmentName", e.getDepartmentName());
        m.put("relatedOrderNo", e.getRelatedOrderNo());
        m.put("relatedOrderType", e.getRelatedOrderType());
        m.put("status", e.getStatus());
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
