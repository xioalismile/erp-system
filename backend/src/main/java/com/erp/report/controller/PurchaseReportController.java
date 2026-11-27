package com.erp.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.mapper.AccountsPayableMapper;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInvoice;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseOrderItem;
import com.erp.purchase.mapper.PurchaseInboundMapper;
import com.erp.purchase.mapper.PurchaseInvoiceMapper;
import com.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 采购报表中心
 * 汇总端点：price-trend / supplier-analysis / execution
 * 明细端点：price-trend-details / supplier-analysis-details / execution-details
 *
 * 数据权限：统一走 DataScopeResolver（角色 sys_role.data_scope：全部/本部门/仅本人），
 *   采购类单据归属列 buyer_id（created_by OR buyer_id=当前员工，与采购订单等列表页同口径）。
 */
@Slf4j
@RestController
@RequestMapping("/api/report/purchase")
@RequiredArgsConstructor
public class PurchaseReportController {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseInvoiceMapper purchaseInvoiceMapper;
    private final AccountsPayableMapper accountsPayableMapper;
    private final DataScopeResolver dataScopeResolver;

    // ==================== 汇总报表 ====================

    /** 1. 采购价格走势：物料采购价随时间变化（支持 startDate/endDate，months 作为回退） */
    @GetMapping("/price-trend")
    public Result<Map<String, Object>> priceTrend(
            HttpServletRequest req,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "12") int months) {
        LocalDate start, end;
        if (startDate != null && endDate != null) {
            start = startDate; end = endDate;
        } else {
            end = LocalDate.now();
            start = end.minusMonths(months - 1).withDayOfMonth(1);
        }

        QueryWrapper<PurchaseOrder> oqw = new QueryWrapper<>();
        applyPurchaseDataPermission(req, oqw);
        oqw.between("order_date", start, end);
        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(oqw);

        Map<Long, String> orderMonthMap = orders.stream()
                .collect(Collectors.toMap(PurchaseOrder::getOrderId,
                        o -> o.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))));

        List<PurchaseOrderItem> items;
        if (orders.isEmpty()) {
            items = Collections.emptyList();
        } else {
            LambdaQueryWrapper<PurchaseOrderItem> qw = new LambdaQueryWrapper<>();
            if (materialId != null) qw.eq(PurchaseOrderItem::getMaterialId, materialId);
            qw.in(PurchaseOrderItem::getOrderId, orderMonthMap.keySet());
            items = purchaseOrderItemMapper.selectList(qw);
        }

        // 按物料+月份分组
        Map<Long, Map<String, List<PurchaseOrderItem>>> byMaterialMonth = items.stream()
                .filter(i -> orderMonthMap.containsKey(i.getOrderId()))
                .collect(Collectors.groupingBy(PurchaseOrderItem::getMaterialId,
                        Collectors.groupingBy(i -> orderMonthMap.get(i.getOrderId()))));

        List<Map<String, Object>> materials = byMaterialMonth.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialId", e.getKey());
            // 取物料名称
            String materialName = e.getValue().values().stream().flatMap(List::stream)
                    .map(PurchaseOrderItem::getMaterialName).filter(Objects::nonNull).findFirst().orElse("未知");
            String materialCode = e.getValue().values().stream().flatMap(List::stream)
                    .map(PurchaseOrderItem::getMaterialCode).filter(Objects::nonNull).findFirst().orElse("");
            m.put("materialCode", materialCode);
            m.put("materialName", materialName);
            // 各月份平均单价
            Map<String, BigDecimal> monthPrices = new TreeMap<>();
            for (Map.Entry<String, List<PurchaseOrderItem>> me : e.getValue().entrySet()) {
                BigDecimal totalQty = me.getValue().stream()
                        .map(i -> i.getQuantity() == null ? BigDecimal.ZERO : i.getQuantity())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalAmt = me.getValue().stream()
                        .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
                    monthPrices.put(me.getKey(), totalAmt.divide(totalQty, 4, RoundingMode.HALF_UP));
                }
            }
            m.put("monthPrices", monthPrices);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("materials", materials);
        result.put("startDate", start);
        result.put("endDate", end);
        return Result.success(result);
    }

    /** 2. 供应商分析：采购额占比、交期准时率 */
    @GetMapping("/supplier-analysis")
    public Result<Map<String, Object>> supplierAnalysis(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<PurchaseOrder> qw = new QueryWrapper<PurchaseOrder>()
                .between("order_date", startDate, endDate)
                .eq("approval_status", "已生效");
        applyPurchaseDataPermission(req, qw);
        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(qw);

        BigDecimal totalAmount = orders.stream()
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, List<PurchaseOrder>> bySupplier = orders.stream()
                .filter(o -> o.getSupplierId() != null)
                .collect(Collectors.groupingBy(PurchaseOrder::getSupplierId));

        // 入库单（用于交期准时率：入库日期<=采购单交货日期视为准时）
        List<Long> orderIds = orders.stream().map(PurchaseOrder::getOrderId).collect(Collectors.toList());
        final Map<String, List<PurchaseInbound>> inboundByOrderNo;
        if (!orderIds.isEmpty()) {
            // 通过 sourceBillNo 关联
            List<PurchaseInbound> inbounds = purchaseInboundMapper.selectList(new LambdaQueryWrapper<>());
            inboundByOrderNo = inbounds.stream()
                    .filter(i -> i.getSourceBillNo() != null)
                    .collect(Collectors.groupingBy(PurchaseInbound::getSourceBillNo));
        } else {
            inboundByOrderNo = Collections.emptyMap();
        }

        List<Map<String, Object>> suppliers = bySupplier.entrySet().stream().map(e -> {
            BigDecimal amt = e.getValue().stream()
                    .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int totalOrders = e.getValue().size();
            // 准时率
            int onTime = 0, delivered = 0;
            for (PurchaseOrder o : e.getValue()) {
                List<PurchaseInbound> inbs = inboundByOrderNo.getOrDefault(o.getOrderNo(), Collections.emptyList());
                if (!inbs.isEmpty()) {
                    delivered++;
                    PurchaseInbound latest = inbs.get(inbs.size() - 1);
                    if (latest.getInDate() != null && o.getDeliveryDate() != null
                            && !latest.getInDate().isAfter(o.getDeliveryDate())) {
                        onTime++;
                    }
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("supplierId", e.getKey());
            m.put("supplierName", e.getValue().get(0).getSupplierName());
            m.put("orderCount", totalOrders);
            m.put("totalAmount", amt);
            m.put("percentage", totalAmount.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    amt.multiply(new BigDecimal("100")).divide(totalAmount, 2, RoundingMode.HALF_UP));
            m.put("deliveredCount", delivered);
            m.put("onTimeCount", onTime);
            m.put("onTimeRate", delivered == 0 ? 0 : Math.round(onTime * 10000.0 / delivered) / 100.0);
            return m;
        }).sorted((a, b) -> ((BigDecimal) b.get("totalAmount")).compareTo((BigDecimal) a.get("totalAmount")))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suppliers", suppliers);
        result.put("totalAmount", totalAmount);
        result.put("totalSuppliers", bySupplier.size());
        result.put("totalOrders", orders.size());
        return Result.success(result);
    }

    /** 3. 采购执行统计：下单→入库→发票执行情况 */
    @GetMapping("/execution")
    public Result<Map<String, Object>> execution(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();

        QueryWrapper<PurchaseOrder> qw = new QueryWrapper<PurchaseOrder>()
                .between("order_date", startDate, endDate);
        applyPurchaseDataPermission(req, qw);
        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(qw);

        // 状态统计
        Map<String, Long> byStatus = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getStatus() == null ? "未知" : o.getStatus(), Collectors.counting()));
        Map<String, Long> byApproval = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getApprovalStatus() == null ? "未知" : o.getApprovalStatus(), Collectors.counting()));

        BigDecimal totalOrderAmount = orders.stream()
                .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 入库情况
        Set<String> orderNos = orders.stream().map(PurchaseOrder::getOrderNo).collect(Collectors.toSet());
        List<PurchaseInbound> matchedInbounds = Collections.emptyList();
        BigDecimal totalInboundAmount = BigDecimal.ZERO;
        if (!orderNos.isEmpty()) {
            List<PurchaseInbound> inbounds = purchaseInboundMapper.selectList(new LambdaQueryWrapper<>());
            matchedInbounds = inbounds.stream()
                    .filter(i -> i.getSourceBillNo() != null && orderNos.contains(i.getSourceBillNo()))
                    .collect(Collectors.toList());
            totalInboundAmount = matchedInbounds.stream()
                    .map(i -> i.getTotalAmount() == null ? BigDecimal.ZERO : i.getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // 发票情况
        List<PurchaseInvoice> matchedInvoices = Collections.emptyList();
        BigDecimal totalInvoiceAmount = BigDecimal.ZERO;
        if (!orderNos.isEmpty()) {
            List<PurchaseInvoice> invoices = purchaseInvoiceMapper.selectList(new LambdaQueryWrapper<>());
            matchedInvoices = invoices.stream()
                    .filter(i -> i.getSourceBillNo() != null && orderNos.contains(i.getSourceBillNo()))
                    .collect(Collectors.toList());
            totalInvoiceAmount = matchedInvoices.stream()
                    .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOrders", orders.size());
        result.put("totalOrderAmount", totalOrderAmount);
        result.put("statusDistribution", byStatus);
        result.put("approvalDistribution", byApproval);
        result.put("inboundCount", matchedInbounds.size());
        result.put("inboundAmount", totalInboundAmount);
        result.put("invoiceCount", matchedInvoices.size());
        result.put("invoiceAmount", totalInvoiceAmount);
        result.put("inboundRate", orders.isEmpty() ? 0 : Math.round(matchedInbounds.size() * 10000.0 / orders.size()) / 100.0);
        result.put("invoiceRate", orders.isEmpty() ? 0 : Math.round(matchedInvoices.size() * 10000.0 / orders.size()) / 100.0);
        return Result.success(result);
    }

    // ==================== 明细报表（可导出） ====================

    /** 1-明细. 采购价格明细列表（orderNo/materialCode/materialName/supplierName/unitPrice/orderDate/quantity/amount） */
    @GetMapping("/price-trend-details")
    public Result<Map<String, Object>> priceTrendDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long materialId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        // 1. 查订单（带权限+时间）
        QueryWrapper<PurchaseOrder> oqw = new QueryWrapper<>();
        applyPurchaseDataPermission(req, oqw);
        oqw.between("order_date", start, end);
        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(oqw);
        Map<Long, PurchaseOrder> orderMap = orders.stream()
                .collect(Collectors.toMap(PurchaseOrder::getOrderId, o -> o));

        List<Map<String, Object>> details;
        if (orderMap.isEmpty()) {
            details = Collections.emptyList();
        } else {
            // 2. 查明细行
            LambdaQueryWrapper<PurchaseOrderItem> qw = new LambdaQueryWrapper<>();
            if (materialId != null) qw.eq(PurchaseOrderItem::getMaterialId, materialId);
            qw.in(PurchaseOrderItem::getOrderId, orderMap.keySet());
            List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectList(qw);
            // 3. 组装 + keyword 过滤（orderNo/supplierName/materialName/materialCode）
            details = items.stream()
                    .filter(i -> orderMap.containsKey(i.getOrderId()))
                    .map(i -> toPriceTrendDetail(i, orderMap.get(i.getOrderId())))
                    .filter(m -> matchKeyword(keyword,
                            (String) m.get("orderNo"),
                            (String) m.get("supplierName"),
                            (String) m.get("materialName"),
                            (String) m.get("materialCode")))
                    .collect(Collectors.toList());
        }
        return paginate(details, page, size);
    }

    /** 2-明细. 供应商订单明细列表（orderNo/supplierName/orderDate/totalAmount/status/approvalStatus/deliveryDate） */
    @GetMapping("/supplier-analysis-details")
    public Result<Map<String, Object>> supplierAnalysisDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        QueryWrapper<PurchaseOrder> qw = new QueryWrapper<>();
        applyPurchaseDataPermission(req, qw);
        qw.between("order_date", start, end);
        if (supplierId != null) qw.eq("supplier_id", supplierId);
        if (approvalStatus != null && !approvalStatus.isEmpty()) qw.eq("approval_status", approvalStatus);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("order_no", keyword)
                    .or().like("supplier_name", keyword));
        }
        qw.orderByDesc("order_date");
        List<PurchaseOrder> all = purchaseOrderMapper.selectList(qw);
        return paginate(all.stream().map(this::toSupplierDetail).collect(Collectors.toList()), page, size);
    }

    /** 3-明细. 采购执行明细列表（orderNo/supplierName/orderDate/orderAmount/inboundNo/inboundDate/inboundAmount/invoiceNo/invoiceDate/invoiceAmount） */
    @GetMapping("/execution-details")
    public Result<Map<String, Object>> executionDetails(
            HttpServletRequest req,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : LocalDate.now();

        QueryWrapper<PurchaseOrder> qw = new QueryWrapper<>();
        applyPurchaseDataPermission(req, qw);
        qw.between("order_date", start, end);
        if (supplierId != null) qw.eq("supplier_id", supplierId);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("order_no", keyword)
                    .or().like("supplier_name", keyword));
        }
        qw.orderByDesc("order_date");
        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(qw);

        // 关联入库单/发票（通过 sourceBillNo = orderNo）
        Set<String> orderNos = orders.stream().map(PurchaseOrder::getOrderNo).collect(Collectors.toSet());
        Map<String, List<PurchaseInbound>> inboundByOrderNo;
        Map<String, List<PurchaseInvoice>> invoiceByOrderNo;
        if (orderNos.isEmpty()) {
            inboundByOrderNo = Collections.emptyMap();
            invoiceByOrderNo = Collections.emptyMap();
        } else {
            List<PurchaseInbound> inbounds = purchaseInboundMapper.selectList(new LambdaQueryWrapper<>());
            inboundByOrderNo = inbounds.stream()
                    .filter(i -> i.getSourceBillNo() != null && orderNos.contains(i.getSourceBillNo()))
                    .collect(Collectors.groupingBy(PurchaseInbound::getSourceBillNo));
            List<PurchaseInvoice> invoices = purchaseInvoiceMapper.selectList(new LambdaQueryWrapper<>());
            invoiceByOrderNo = invoices.stream()
                    .filter(i -> i.getSourceBillNo() != null && orderNos.contains(i.getSourceBillNo()))
                    .collect(Collectors.groupingBy(PurchaseInvoice::getSourceBillNo));
        }

        List<Map<String, Object>> details = orders.stream().map(o -> {
            PurchaseInbound latestInb = pickLatestInbound(inboundByOrderNo.getOrDefault(o.getOrderNo(), Collections.emptyList()));
            PurchaseInvoice latestInv = pickLatestInvoice(invoiceByOrderNo.getOrDefault(o.getOrderNo(), Collections.emptyList()));
            return toExecutionDetail(o, latestInb, latestInv);
        }).collect(Collectors.toList());
        return paginate(details, page, size);
    }

    // ==================== 采购发票报表 ====================

    /** 4. 采购供应商发票分析：TopN供应商占比（其余合并"其他供应商"）+ 排名表（金额统计排除"已作废"） */
    @GetMapping("/invoice/supplier-analysis")
    public Result<Map<String, Object>> invoiceSupplierAnalysis(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "5") int topN) {
        LocalDate[] range = resolveInvoiceRange(year, startDate, endDate);
        List<PurchaseInvoice> invoices = listPurchaseInvoices(range[0], range[1]);
        List<PurchaseInvoice> valid = filterValidPurchaseInvoices(filterPurchaseInvoicesByKeyword(invoices, keyword));

        // 按供应商分组（优先supplierId，无ID按名称，再无归"未知供应商"）
        Map<String, List<PurchaseInvoice>> bySupplier = valid.stream()
                .collect(Collectors.groupingBy(i -> {
                    if (i.getSupplierId() != null) return "id:" + i.getSupplierId();
                    if (i.getSupplierName() != null) return "name:" + i.getSupplierName();
                    return "name:未知供应商";
                }, LinkedHashMap::new, Collectors.toList()));
        BigDecimal totalAmount = sumPayable(valid);

        List<Map.Entry<String, List<PurchaseInvoice>>> sorted = bySupplier.entrySet().stream()
                .sorted((a, b) -> sumPayable(b.getValue()).compareTo(sumPayable(a.getValue())))
                .collect(Collectors.toList());
        int n = Math.max(1, topN);

        List<Map<String, Object>> supplierDistribution = new ArrayList<>();
        List<Map<String, Object>> supplierRanking = new ArrayList<>();
        for (int i = 0; i < Math.min(n, sorted.size()); i++) {
            List<PurchaseInvoice> list = sorted.get(i).getValue();
            BigDecimal amt = sumPayable(list);
            BigDecimal netAmount = list.stream().map(inv -> nvl(inv.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tax = list.stream().map(inv -> nvl(inv.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            String name = supplierDisplayName(list.get(0));
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("name", name);
            dm.put("value", amt);
            dm.put("percentage", percentOf(amt, totalAmount));
            supplierDistribution.add(dm);

            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("rank", i + 1);
            rm.put("name", name);
            rm.put("amount", amt);
            rm.put("invoices", list.size());
            rm.put("avgAmount", list.isEmpty() ? BigDecimal.ZERO
                    : amt.divide(new BigDecimal(list.size()), 2, RoundingMode.HALF_UP));
            // 平均税率 = 税额合计/金额合计（真实推算）
            rm.put("avgTaxRate", netAmount.compareTo(BigDecimal.ZERO) > 0
                    ? tax.multiply(new BigDecimal("100")).divide(netAmount, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            supplierRanking.add(rm);
        }
        // 其余供应商合并为"其他供应商"
        if (sorted.size() > n) {
            BigDecimal otherAmount = BigDecimal.ZERO;
            int otherCount = 0;
            for (int i = n; i < sorted.size(); i++) {
                otherAmount = otherAmount.add(sumPayable(sorted.get(i).getValue()));
                otherCount += sorted.get(i).getValue().size();
            }
            if (otherAmount.compareTo(BigDecimal.ZERO) > 0 || otherCount > 0) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("name", "其他供应商");
                dm.put("value", otherAmount);
                dm.put("percentage", percentOf(otherAmount, totalAmount));
                supplierDistribution.add(dm);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supplierDistribution", supplierDistribution);
        result.put("supplierRanking", supplierRanking);
        result.put("totalAmount", totalAmount);
        result.put("totalInvoices", valid.size());
        result.put("totalSuppliers", bySupplier.size());
        return Result.success(result);
    }

    /** 5. 采购发票付款状态：KPI卡（应付/已付/未付/逾期，真实环比上一等长周期）+ 状态分布 + 逾期分桶。
     *  付款数据源 account_payable 按 invoice_no 关联；未关联AP的发票按"未付款"处理。 */
    @GetMapping("/invoice/payment-status")
    public Result<Map<String, Object>> invoicePaymentStatus(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        LocalDate[] range = resolveInvoiceRange(year, startDate, endDate);
        LocalDate today = LocalDate.now();
        List<PurchaseInvoice> invoices = listPurchaseInvoices(range[0], range[1]);
        List<PurchaseInvoice> valid = filterValidPurchaseInvoices(filterPurchaseInvoicesByKeyword(invoices, keyword));
        Map<String, List<AccountsPayable>> apByInvoiceNo = mapPayablesByInvoiceNo(valid);

        // 应付 = 关联AP合计 + 未关联AP发票价税合计；已付 = AP已付合计
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (PurchaseInvoice i : valid) {
            List<AccountsPayable> aps = i.getInvoiceNo() == null
                    ? Collections.emptyList() : apByInvoiceNo.getOrDefault(i.getInvoiceNo(), Collections.emptyList());
            if (aps.isEmpty()) {
                payable = payable.add(payableAmount(i));
            } else {
                payable = payable.add(aps.stream().map(a -> nvl(a.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
                paid = paid.add(aps.stream().map(a -> nvl(a.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }
        BigDecimal unpaid = payable.subtract(paid).max(BigDecimal.ZERO);
        BigDecimal overdue = BigDecimal.ZERO;
        for (List<AccountsPayable> aps : apByInvoiceNo.values()) {
            for (AccountsPayable ap : aps) {
                if (isOverdueAp(ap, today)) overdue = overdue.add(nvl(ap.getBalanceAmount()));
            }
        }

        // KPI环比：与上一等长周期对比（真实计算，无上期数据记0%）
        long days = ChronoUnit.DAYS.between(range[0], range[1]) + 1;
        Map<String, BigDecimal> prev = computePaymentStats(range[0].minusDays(days), range[0].minusDays(1), keyword, today);
        List<Map<String, Object>> kpiCards = new ArrayList<>();
        kpiCards.add(buildKpiCard("应付总额", payable, prev.get("payable")));
        kpiCards.add(buildKpiCard("已付款", paid, prev.get("paid")));
        kpiCards.add(buildKpiCard("未付款", unpaid, prev.get("unpaid")));
        kpiCards.add(buildKpiCard("逾期金额", overdue, prev.get("overdue")));

        // 每张发票付款状态：无AP→未付款；AP余额<=0→已付款；有已付→部分付款
        Map<String, List<PurchaseInvoice>> byPayStatus = new LinkedHashMap<>();
        byPayStatus.put("已付款", new ArrayList<>());
        byPayStatus.put("部分付款", new ArrayList<>());
        byPayStatus.put("未付款", new ArrayList<>());
        for (PurchaseInvoice i : valid) {
            byPayStatus.get(resolveInvoicePaymentStatus(i, apByInvoiceNo)).add(i);
        }
        BigDecimal invoiceTotal = sumPayable(valid);
        List<Map<String, Object>> paymentStatus = new ArrayList<>();
        for (Map.Entry<String, List<PurchaseInvoice>> e : byPayStatus.entrySet()) {
            BigDecimal amt = sumPayable(e.getValue());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", e.getKey());
            m.put("count", e.getValue().size());
            m.put("amount", amt);
            m.put("percentage", percentOf(amt, invoiceTotal));
            paymentStatus.add(m);
        }

        // 逾期分析：AP余额>0且到期日早于今天，按逾期天数分桶（0-30/31-60/61-90/90以上）
        String[] bucketLabels = {"0-30天", "31-60天", "61-90天", "90天以上"};
        List<List<AccountsPayable>> buckets = new ArrayList<>();
        for (int i = 0; i < 4; i++) buckets.add(new ArrayList<>());
        for (List<AccountsPayable> aps : apByInvoiceNo.values()) {
            for (AccountsPayable ap : aps) {
                if (isOverdueAp(ap, today)) {
                    long overdueDays = ChronoUnit.DAYS.between(ap.getDueDate(), today);
                    int idx = overdueDays <= 30 ? 0 : overdueDays <= 60 ? 1 : overdueDays <= 90 ? 2 : 3;
                    buckets.get(idx).add(ap);
                }
            }
        }
        List<Map<String, Object>> overdueAnalysis = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<AccountsPayable> rows = buckets.get(i);
            BigDecimal amt = rows.stream().map(a -> nvl(a.getBalanceAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("days", bucketLabels[i]);
            m.put("count", rows.size());
            m.put("amount", amt);
            m.put("percentage", percentOf(amt, overdue));
            overdueAnalysis.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kpiCards", kpiCards);
        result.put("paymentStatus", paymentStatus);
        result.put("overdueAnalysis", overdueAnalysis);
        return Result.success(result);
    }

    /** 6. 采购税务分析：实体无平均税率字段，按 税额/金额*100 推算分组（如13→'13%'，无法推算归'未税率'） */
    @GetMapping("/invoice/tax-analysis")
    public Result<Map<String, Object>> invoiceTaxAnalysis(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        LocalDate[] range = resolveInvoiceRange(year, startDate, endDate);
        List<PurchaseInvoice> invoices = listPurchaseInvoices(range[0], range[1]);
        List<PurchaseInvoice> valid = filterValidPurchaseInvoices(filterPurchaseInvoicesByKeyword(invoices, keyword));

        // 按推算税率分组
        Map<String, List<PurchaseInvoice>> byRate = valid.stream()
                .collect(Collectors.groupingBy(this::deriveTaxRateLabel, LinkedHashMap::new, Collectors.toList()));
        // 税率档排序：数值降序，"未税率"最后
        List<String> rateLabels = byRate.keySet().stream()
                .sorted((a, b) -> {
                    if ("未税率".equals(a)) return 1;
                    if ("未税率".equals(b)) return -1;
                    return parseRate(b).compareTo(parseRate(a));
                }).collect(Collectors.toList());

        List<Map<String, Object>> taxDistribution = new ArrayList<>();
        for (String label : rateLabels) {
            List<PurchaseInvoice> list = byRate.get(label);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rate", label);
            m.put("amount", list.stream().map(i -> nvl(i.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
            m.put("tax", list.stream().map(i -> nvl(i.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
            m.put("count", list.size());
            taxDistribution.add(m);
        }

        // 月度税额（按税率细分，无数据月份补0）
        List<Map<String, Object>> monthlyTax = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            final int month = m;
            List<PurchaseInvoice> monthInvoices = valid.stream()
                    .filter(i -> i.getInvoiceDate() != null && i.getInvoiceDate().getMonthValue() == month)
                    .collect(Collectors.toList());
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("month", m + "月");
            mm.put("tax", monthInvoices.stream().map(i -> nvl(i.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
            Map<String, Object> rates = new LinkedHashMap<>();
            for (String label : rateLabels) rates.put(label, BigDecimal.ZERO);
            for (PurchaseInvoice i : monthInvoices) {
                String label = deriveTaxRateLabel(i);
                BigDecimal old = rates.get(label) instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
                rates.put(label, old.add(nvl(i.getTaxAmount())));
            }
            mm.put("rates", rates);
            monthlyTax.add(mm);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taxDistribution", taxDistribution);
        result.put("monthlyTax", monthlyTax);
        result.put("totalAmount", valid.stream().map(i -> nvl(i.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.put("totalTax", valid.stream().map(i -> nvl(i.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
        return Result.success(result);
    }

    /** 7. 采购发票明细：金额/税额/价税合计 + 付款状态与到期日（AP关联），采购员=审核人（缺省创建人），内存分页（含作废发票） */
    @GetMapping("/invoice/details")
    public Result<Map<String, Object>> invoiceDetails(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        LocalDate[] range = resolveInvoiceRange(year, startDate, endDate);
        List<PurchaseInvoice> invoices = listPurchaseInvoices(range[0], range[1]);
        List<PurchaseInvoice> filtered = filterPurchaseInvoicesByKeyword(invoices, keyword);
        filtered.sort(Comparator.comparing(PurchaseInvoice::getInvoiceDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, List<AccountsPayable>> apByInvoiceNo = mapPayablesByInvoiceNo(filtered);

        int total = filtered.size();
        int from = Math.max(0, (current - 1) * size);
        int to = Math.min(total, from + size);
        List<PurchaseInvoice> pageList = from < total ? filtered.subList(from, to) : Collections.emptyList();

        List<Map<String, Object>> list = pageList.stream().map(i -> {
            List<AccountsPayable> aps = i.getInvoiceNo() == null
                    ? Collections.emptyList() : apByInvoiceNo.getOrDefault(i.getInvoiceNo(), Collections.emptyList());
            // 到期日：优先取未结清AP中最早的到期日，无未结清取最早AP到期日
            LocalDate dueDate = aps.stream()
                    .filter(a -> nvl(a.getBalanceAmount()).compareTo(BigDecimal.ZERO) > 0 && a.getDueDate() != null)
                    .map(AccountsPayable::getDueDate).min(Comparator.naturalOrder())
                    .orElse(aps.stream().map(AccountsPayable::getDueDate).filter(Objects::nonNull)
                            .min(Comparator.naturalOrder()).orElse(null));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getInvoiceId());
            m.put("invoiceNo", i.getInvoiceNo());
            m.put("supplierName", supplierDisplayName(i));
            m.put("amount", i.getAmount());
            m.put("tax", i.getTaxAmount());
            m.put("total", i.getTaxTotalAmount());
            m.put("date", i.getInvoiceDate());
            m.put("status", i.getStatus());
            m.put("purchaser", i.getApprovedBy() != null ? i.getApprovedBy() : i.getCreatedBy());
            m.put("paymentStatus", resolveInvoicePaymentStatus(i, apByInvoiceNo));
            m.put("dueDate", dueDate);
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

    // ---------- 采购发票报表辅助方法 ----------

    /** 发票报表通用区间解析：显式起止日期优先，否则按年份（默认当年） */
    private LocalDate[] resolveInvoiceRange(int year, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return new LocalDate[]{startDate, endDate};
        }
        int y = year == 0 ? LocalDate.now().getYear() : year;
        return new LocalDate[]{LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)};
    }

    /** 查询区间内采购发票（发票表无采购员字段，统一按 created_by 过滤；汇总/明细/环比共用本方法保证同口径） */
    private List<PurchaseInvoice> listPurchaseInvoices(LocalDate start, LocalDate end) {
        QueryWrapper<PurchaseInvoice> qw = new QueryWrapper<PurchaseInvoice>()
                .between("invoice_date", start, end);
        dataScopeResolver.applyIfLimited(qw, null, "created_by");
        return purchaseInvoiceMapper.selectList(qw);
    }

    /** 金额统计口径：排除"已作废"发票（状态为空的保留） */
    private List<PurchaseInvoice> filterValidPurchaseInvoices(List<PurchaseInvoice> invoices) {
        return invoices.stream().filter(i -> !"已作废".equals(i.getStatus())).collect(Collectors.toList());
    }

    /** 关键字过滤（发票号/供应商名称/源单据号；供应商名称为库字段可直接匹配） */
    private List<PurchaseInvoice> filterPurchaseInvoicesByKeyword(List<PurchaseInvoice> invoices, String keyword) {
        if (keyword == null || keyword.isEmpty()) return invoices;
        return invoices.stream().filter(i ->
                (i.getInvoiceNo() != null && i.getInvoiceNo().contains(keyword))
                        || (i.getSupplierName() != null && i.getSupplierName().contains(keyword))
                        || (i.getSourceBillNo() != null && i.getSourceBillNo().contains(keyword)))
                .collect(Collectors.toList());
    }

    /** 采购发票应付口径金额：价税合计（net_amount），为空时用金额+税额兜底 */
    private BigDecimal payableAmount(PurchaseInvoice i) {
        if (i.getTaxTotalAmount() != null) return i.getTaxTotalAmount();
        return nvl(i.getAmount()).add(nvl(i.getTaxAmount()));
    }

    private BigDecimal sumPayable(List<PurchaseInvoice> list) {
        return list.stream().map(this::payableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 供应商显示名：优先库字段供应商名称，缺省用ID，再缺省"未知供应商" */
    private String supplierDisplayName(PurchaseInvoice i) {
        if (i.getSupplierName() != null && !i.getSupplierName().isEmpty()) return i.getSupplierName();
        return i.getSupplierId() != null ? "供应商" + i.getSupplierId() : "未知供应商";
    }

    /** 推算采购发票税率标签：税额/金额*100（四舍五入取整），金额为0或空归'未税率' */
    private String deriveTaxRateLabel(PurchaseInvoice i) {
        if (i.getAmount() == null || i.getAmount().compareTo(BigDecimal.ZERO) <= 0 || i.getTaxAmount() == null) {
            return "未税率";
        }
        return i.getTaxAmount().multiply(new BigDecimal("100"))
                .divide(i.getAmount(), 0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** 税率标签转数值（'13%'→13），解析失败返回0 */
    private BigDecimal parseRate(String label) {
        try {
            return new BigDecimal(label.replace("%", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** 按发票号关联应付（排除已作废应付），未匹配到的发票视为未付款 */
    private Map<String, List<AccountsPayable>> mapPayablesByInvoiceNo(List<PurchaseInvoice> invoices) {
        List<String> invoiceNos = invoices.stream().map(PurchaseInvoice::getInvoiceNo)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (invoiceNos.isEmpty()) return Collections.emptyMap();
        return accountsPayableMapper.selectList(
                        new LambdaQueryWrapper<AccountsPayable>().in(AccountsPayable::getInvoiceNo, invoiceNos))
                .stream().filter(a -> !"已作废".equals(a.getStatus()))
                .collect(Collectors.groupingBy(AccountsPayable::getInvoiceNo));
    }

    /** 单张发票付款状态：无AP→未付款；AP余额<=0→已付款；有已付→部分付款；否则未付款 */
    private String resolveInvoicePaymentStatus(PurchaseInvoice i, Map<String, List<AccountsPayable>> apByInvoiceNo) {
        List<AccountsPayable> aps = i.getInvoiceNo() == null
                ? Collections.emptyList() : apByInvoiceNo.getOrDefault(i.getInvoiceNo(), Collections.emptyList());
        if (aps.isEmpty()) return "未付款";
        BigDecimal balance = aps.stream().map(a -> nvl(a.getBalanceAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = aps.stream().map(a -> nvl(a.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (balance.compareTo(BigDecimal.ZERO) <= 0) return "已付款";
        if (paid.compareTo(BigDecimal.ZERO) > 0) return "部分付款";
        return "未付款";
    }

    /** AP是否逾期：余额>0且到期日早于今天 */
    private boolean isOverdueAp(AccountsPayable ap, LocalDate today) {
        return nvl(ap.getBalanceAmount()).compareTo(BigDecimal.ZERO) > 0
                && ap.getDueDate() != null && ap.getDueDate().isBefore(today);
    }

    /** 付款口径统计（用于KPI环比）：应付/已付/未付/逾期（口径与 payment-status 端点一致） */
    private Map<String, BigDecimal> computePaymentStats(LocalDate start, LocalDate end, String keyword, LocalDate today) {
        List<PurchaseInvoice> invoices = listPurchaseInvoices(start, end);
        List<PurchaseInvoice> valid = filterValidPurchaseInvoices(filterPurchaseInvoicesByKeyword(invoices, keyword));
        Map<String, List<AccountsPayable>> apByInvoiceNo = mapPayablesByInvoiceNo(valid);
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (PurchaseInvoice i : valid) {
            List<AccountsPayable> aps = i.getInvoiceNo() == null
                    ? Collections.emptyList() : apByInvoiceNo.getOrDefault(i.getInvoiceNo(), Collections.emptyList());
            if (aps.isEmpty()) {
                payable = payable.add(payableAmount(i));
            } else {
                payable = payable.add(aps.stream().map(a -> nvl(a.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
                paid = paid.add(aps.stream().map(a -> nvl(a.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }
        BigDecimal overdue = BigDecimal.ZERO;
        for (List<AccountsPayable> aps : apByInvoiceNo.values()) {
            for (AccountsPayable ap : aps) {
                if (isOverdueAp(ap, today)) overdue = overdue.add(nvl(ap.getBalanceAmount()));
            }
        }
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        m.put("payable", payable);
        m.put("paid", paid);
        m.put("unpaid", payable.subtract(paid).max(BigDecimal.ZERO));
        m.put("overdue", overdue);
        return m;
    }

    /** KPI卡片：金额千分位字符串 + 真实环比百分比（上期为0或无数据记+0.0%） */
    private Map<String, Object> buildKpiCard(String title, BigDecimal current, BigDecimal prev) {
        BigDecimal change = BigDecimal.ZERO;
        if (prev != null && prev.compareTo(BigDecimal.ZERO) != 0) {
            change = current.subtract(prev).multiply(new BigDecimal("100"))
                    .divide(prev, 1, RoundingMode.HALF_UP);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("value", formatAmountWithCommas(current));
        m.put("change", (change.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + change.toPlainString() + "%");
        m.put("trend", change.compareTo(BigDecimal.ZERO) >= 0 ? "up" : "down");
        return m;
    }

    /** 金额千分位格式化（保留2位小数），用于KPI卡片直接展示 */
    private String formatAmountWithCommas(BigDecimal amount) {
        String plain = nvl(amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
        String[] parts = plain.split("\\.");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = sb.length() - 3; i > 0; i -= 3) {
            sb.insert(i, ',');
        }
        return sb + "." + parts[1];
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal percentOf(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return nvl(part).multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP);
    }

    // ==================== 辅助方法 ====================

    /** 采购订单数据权限：统一走 DataScopeResolver（created_by OR buyer_id=当前员工） */
    private void applyPurchaseDataPermission(HttpServletRequest req, QueryWrapper<PurchaseOrder> qw) {
        dataScopeResolver.resolve(req).applyTo(qw, "buyer_id");
    }

    /** 关键字匹配（任一字段包含即命中，关键字为空时恒真） */
    private boolean matchKeyword(String keyword, String... fields) {
        if (keyword == null || keyword.isEmpty()) return true;
        String kw = keyword.toLowerCase();
        for (String f : fields) {
            if (f != null && f.toLowerCase().contains(kw)) return true;
        }
        return false;
    }

    /** 取入库日期最新的一条入库单 */
    private PurchaseInbound pickLatestInbound(List<PurchaseInbound> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .filter(i -> i.getInDate() != null)
                .max(Comparator.comparing(PurchaseInbound::getInDate))
                .orElse(list.get(0));
    }

    /** 取发票日期最新的一条发票 */
    private PurchaseInvoice pickLatestInvoice(List<PurchaseInvoice> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .filter(i -> i.getInvoiceDate() != null)
                .max(Comparator.comparing(PurchaseInvoice::getInvoiceDate))
                .orElse(list.get(0));
    }

    private Map<String, Object> toPriceTrendDetail(PurchaseOrderItem item, PurchaseOrder order) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", order.getOrderNo());
        m.put("materialCode", item.getMaterialCode());
        m.put("materialName", item.getMaterialName());
        m.put("supplierName", order.getSupplierName());
        m.put("unitPrice", item.getUnitPrice());
        m.put("orderDate", order.getOrderDate());
        m.put("quantity", item.getQuantity());
        m.put("amount", item.getAmount());
        return m;
    }

    private Map<String, Object> toSupplierDetail(PurchaseOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", o.getOrderNo());
        m.put("supplierName", o.getSupplierName());
        m.put("orderDate", o.getOrderDate());
        m.put("totalAmount", o.getTotalAmount());
        m.put("status", o.getStatus());
        m.put("approvalStatus", o.getApprovalStatus());
        m.put("deliveryDate", o.getDeliveryDate());
        return m;
    }

    private Map<String, Object> toExecutionDetail(PurchaseOrder o, PurchaseInbound inb, PurchaseInvoice inv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", o.getOrderNo());
        m.put("supplierName", o.getSupplierName());
        m.put("orderDate", o.getOrderDate());
        m.put("orderAmount", o.getTotalAmount());
        m.put("inboundNo", inb != null ? inb.getReceiptNo() : null);
        m.put("inboundDate", inb != null ? inb.getInDate() : null);
        m.put("inboundAmount", inb != null ? inb.getTotalAmount() : null);
        m.put("invoiceNo", inv != null ? inv.getInvoiceNo() : null);
        m.put("invoiceDate", inv != null ? inv.getInvoiceDate() : null);
        m.put("invoiceAmount", inv != null ? inv.getAmount() : null);
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
