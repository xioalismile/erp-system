package com.erp.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.Result;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.BillWriteoffDetail;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.finance.mapper.BillWriteoffDetailMapper;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.mapper.CustomerMapper;
import com.erp.report.util.StandardExcelUtil;
import com.erp.sale.entity.SaleDelivery;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleReturn;
import com.erp.sale.mapper.SaleDeliveryMapper;
import com.erp.sale.mapper.SaleInvoiceMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import com.erp.sale.mapper.SaleReturnMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户对账单（原标准台账子系统保留能力，URL 前缀保持不变）
 *
 * 端点：
 *   - GET /api/report/standard/sale/customer-statement/summary  按客户汇总
 *   - GET /api/report/standard/sale/customer-statement/detail   单据级明细
 *   - GET /api/report/standard/sale/customer-statement/export   Excel 导出（应收账款口径）
 */
@Slf4j
@RestController
@RequestMapping("/api/report/standard/sale")
@RequiredArgsConstructor
public class CustomerStatementController {

    private final SaleOrderMapper saleOrderMapper;
    private final SaleDeliveryMapper saleDeliveryMapper;
    private final SaleReturnMapper saleReturnMapper;
    private final SaleInvoiceMapper saleInvoiceMapper;
    private final AccountsReceivableMapper arMapper;
    private final PaymentReceiptMapper paymentReceiptMapper;
    private final BillWriteoffDetailMapper billWriteoffDetailMapper;
    private final CustomerMapper customerMapper;

    /** 数据权限：对账单「报表页 + 导出」必须与应收账款列表同口径（否则导出比列表宽 = 越权） */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    private static final String APPROVED = "已生效";

    // ==================== 客户对账单：汇总 ====================

    @GetMapping("/customer-statement/summary")
    public Result<Map<String, Object>> customerStatementSummary(
            HttpServletRequest req,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String customerName) {
        List<Map<String, Object>> all = buildCustomerStatementSummaryRecords(startDate, endDate, keyword, customerCode, customerName);
        return Result.success(paginate(all, 1, Integer.MAX_VALUE));
    }

    private List<Map<String, Object>> buildCustomerStatementSummaryRecords(
            LocalDate start, LocalDate end, String keyword, String customerCode, String customerName) {
        Map<Long, Map<String, Object>> acc = new LinkedHashMap<>();
        Set<Long> targetCust = resolveCustomerIds(customerCode, customerName);

        // 1) 销售订单金额
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder> orderQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "order_date", start)
                        .le(end != null, "order_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "order_no", keyword)
                        .in(!targetCust.isEmpty(), "customer_id", targetCust);
        // 数据权限：与导出端点逐字同口径 applyIfLimited(qw, "saler_id", "created_by")
        dataScopeResolver.applyIfLimited(orderQw, "saler_id", "created_by");
        for (SaleOrder o : saleOrderMapper.selectList(orderQw)) {
            BigDecimal amt = nz(o.getTotalAmount());
            BigDecimal paid = capPaid(o.getReceivedAmount(), amt);
            accumulateCustomer(acc, o.getCustomerId(), "saleOrderAmount", o.getOrderDate(), amt, 1);
            accumulateCustomerPaid(acc, o.getCustomerId(), "saleOrderPaidAmount", "saleOrderUnpaidAmount",
                    paid, amt.subtract(paid).max(BigDecimal.ZERO));
        }
        // 2) 销售出库金额
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery> deliveryQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "delivery_date", start)
                        .le(end != null, "delivery_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "delivery_no", keyword)
                        .in(!targetCust.isEmpty(), "customer_id", targetCust);
        // 数据权限：与导出端点逐字同口径 applyIfLimited(qw, "saler_id", "created_by")
        dataScopeResolver.applyIfLimited(deliveryQw, "saler_id", "created_by");
        List<SaleDelivery> deliveries = saleDeliveryMapper.selectList(deliveryQw);
        Map<Long, BigDecimal> deliveryPaidMap = paidBySource("销售出库单",
                deliveries.stream().map(SaleDelivery::getDeliveryId).collect(Collectors.toList()));
        for (SaleDelivery d : deliveries) {
            BigDecimal amt = nz(d.getTotalAmount());
            BigDecimal paid = capPaid(deliveryPaidMap.get(d.getDeliveryId()), amt);
            accumulateCustomer(acc, d.getCustomerId(), "saleDeliveryAmount", d.getDeliveryDate(), amt, 1);
            accumulateCustomerPaid(acc, d.getCustomerId(), "saleDeliveryPaidAmount", "saleDeliveryUnpaidAmount",
                    paid, amt.subtract(paid).max(BigDecimal.ZERO));
        }
        // 3) 销售发票金额（红字发票冲减）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice> invoiceQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "invoice_date", start)
                        .le(end != null, "invoice_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "invoice_no", keyword)
                        .in(!targetCust.isEmpty(), "customer_id", targetCust);
        // 数据权限：与导出端点逐字同口径 applyIfLimited(qw, "saler_id", "created_by")
        dataScopeResolver.applyIfLimited(invoiceQw, "saler_id", "created_by");
        List<SaleInvoice> invoices = saleInvoiceMapper.selectList(invoiceQw);
        Map<Long, BigDecimal> invoicePaidMap = paidBySource("销售发票",
                invoices.stream().map(SaleInvoice::getInvoiceId).collect(Collectors.toList()));
        for (SaleInvoice inv : invoices) {
            BigDecimal amt = inv.getTotalAmount() == null ? BigDecimal.ZERO : inv.getTotalAmount();
            if (inv.getIsRedInvoice() != null && inv.getIsRedInvoice() == 1) amt = amt.negate();
            BigDecimal paid = capPaid(invoicePaidMap.get(inv.getInvoiceId()), amt);
            accumulateCustomer(acc, inv.getCustomerId(), "saleInvoiceAmount", inv.getInvoiceDate(), amt, 1);
            accumulateCustomerPaid(acc, inv.getCustomerId(), "saleInvoicePaidAmount", "saleInvoiceUnpaidAmount",
                    paid, amt.subtract(paid).max(BigDecimal.ZERO));
        }
        // 4) 销售退货金额（冲减）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn> returnQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "return_date", start)
                        .le(end != null, "return_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "return_no", keyword)
                        .in(!targetCust.isEmpty(), "customer_id", targetCust);
        // 数据权限：与导出端点逐字同口径 applyIfLimited(qw, "saler_id", "created_by")
        dataScopeResolver.applyIfLimited(returnQw, "saler_id", "created_by");
        for (SaleReturn r : saleReturnMapper.selectList(returnQw)) {
            BigDecimal amt = r.getTotalAmount() == null ? BigDecimal.ZERO : r.getTotalAmount().negate();
            accumulateCustomer(acc, r.getCustomerId(), "saleReturnAmount", r.getReturnDate(), amt, 1);
        }
        // 5) 本期收款金额（cash_receipt 表无 saler_id 列，与收款单列表同口径：只按 created_by 过滤，归属列传 null）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PaymentReceipt> receiptQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PaymentReceipt>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "receipt_date", start)
                        .le(end != null, "receipt_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "receipt_no", keyword)
                        .in(!targetCust.isEmpty(), "customer_id", targetCust);
        dataScopeResolver.applyIfLimited(receiptQw, null, "created_by");
        for (PaymentReceipt rc : paymentReceiptMapper.selectList(receiptQw)) {
            accumulateCustomer(acc, rc.getCustomerId(), "currentPaid", rc.getReceiptDate(), rc.getAmount(), 1);
        }
        // 6) 期初应收余额（系统无应收单：按销售订单口径 = 起始日之前已生效订单的未收余额累计，单据总额−已收）
        Map<Long, BigDecimal> openingBalances = buildCustomerOpeningBalances(start, targetCust);
        for (Map.Entry<Long, BigDecimal> e : openingBalances.entrySet()) {
            Map<String, Object> row = acc.computeIfAbsent(e.getKey(), this::newCustomerRow);
            row.put("beginBalance", nz((BigDecimal) row.get("beginBalance")).add(e.getValue()));
        }
        // 7) 计算期末余额并补主档
        List<Map<String, Object>> result = new ArrayList<>();
        List<Long> custIds = new ArrayList<>(acc.keySet());
        Map<Long, Customer> customerMap = new HashMap<>();
        if (!custIds.isEmpty()) {
            customerMapper.selectList(new LambdaQueryWrapper<Customer>().in(Customer::getCustomerId, custIds))
                    .forEach(c -> customerMap.put(c.getCustomerId(), c));
        }
        for (Map.Entry<Long, Map<String, Object>> e : acc.entrySet()) {
            Map<String, Object> row = e.getValue();
            Customer c = customerMap.get(e.getKey());
            // 客户主档已删除（历史悬空引用）时兜底显示，避免名称/编码空白
            row.put("customerCode", c != null ? c.getCustomerCode() : "已删除");
            row.put("customerName", c != null ? c.getCustomerName() : "已删除客户(ID:" + e.getKey() + ")");
            BigDecimal begin = nz((BigDecimal) row.get("beginBalance"));
            BigDecimal cur = nz((BigDecimal) row.get("currentReceivable"));
            BigDecimal paid = nz((BigDecimal) row.get("currentPaid"));
            // 本期未收 = 本期应收合计 − 本期收款金额；剩余未收 = 期初 + 本期未收（累计）
            BigDecimal currentUnpaid = cur.subtract(paid);
            BigDecimal remainingUnpaid = begin.add(currentUnpaid);
            row.put("currentUnpaid", currentUnpaid);
            row.put("endBalance", remainingUnpaid);
            row.put("remainingUnpaid", remainingUnpaid);
            if (customerName != null && !customerName.isEmpty()
                    && !customerName.equals(row.get("customerName"))) continue;
            result.add(row);
        }
        result.sort((a, b) -> nz((BigDecimal) b.get("endBalance")).compareTo(nz((BigDecimal) a.get("endBalance"))));
        return result;
    }

    private void accumulateCustomer(Map<Long, Map<String, Object>> acc, Long custId, String amountField,
                                    LocalDate docDate, BigDecimal amount, int count) {
        if (custId == null) return;
        Map<String, Object> row = acc.computeIfAbsent(custId, this::newCustomerRow);
        row.put(amountField, nz((BigDecimal) row.get(amountField)).add(nz(amount)));
        row.put("currentReceivable", nz((BigDecimal) row.get("currentReceivable")).add(nz(amount)));
        row.put("documentCount", ((Integer) row.get("documentCount")) + count);
        if (docDate != null) {
            LocalDate cur = (LocalDate) row.get("lastTransactionDate");
            if (cur == null || docDate.isAfter(cur)) row.put("lastTransactionDate", docDate);
        }
    }

    /** 累计某类业务来源的已收 / 未收（未收 = 单据金额 − 已收，负数按 0 计） */
    private void accumulateCustomerPaid(Map<Long, Map<String, Object>> acc, Long custId,
                                        String paidField, String unpaidField,
                                        BigDecimal paid, BigDecimal unpaid) {
        if (custId == null) return;
        Map<String, Object> row = acc.computeIfAbsent(custId, this::newCustomerRow);
        row.put(paidField, nz((BigDecimal) row.get(paidField)).add(nz(paid)));
        row.put(unpaidField, nz((BigDecimal) row.get(unpaidField)).add(nz(unpaid)));
    }

    /** 按来源单汇总已生效收款单的核销金额：key = 来源单ID（销售出库单 / 销售发票没有已收列，只能按核销明细反算） */
    private Map<Long, BigDecimal> paidBySource(String sourceType, List<Long> sourceIds) {
        Map<Long, BigDecimal> paidMap = new HashMap<>();
        if (sourceIds == null || sourceIds.isEmpty()) return paidMap;
        List<BillWriteoffDetail> rows = billWriteoffDetailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BillWriteoffDetail>()
                        .eq("bill_type", "收款单")
                        .eq("settled_qty", "已冲销")
                        .eq("source_type", sourceType)
                        .gt("writeoff_amount", 0)
                        .in("source_id", sourceIds));
        for (BillWriteoffDetail d : rows) {
            if (d.getSourceId() == null) continue;
            paidMap.merge(d.getSourceId(), nz(d.getWriteoffAmount()), BigDecimal::add);
        }
        return paidMap;
    }

    /** 期初应收余额：起始日之前已生效销售订单的未收余额（单据总额 − 已收），按客户累计 */
    private Map<Long, BigDecimal> buildCustomerOpeningBalances(LocalDate start, Set<Long> targetCust) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (start == null) return result;
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder> soQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder>()
                        .eq("status", "已生效")
                        .lt("order_date", start)
                        .in(!targetCust.isEmpty(), "customer_id", targetCust);
        // 数据权限：与导出端点逐字同口径 applyIfLimited(qw, "saler_id", "created_by")
        dataScopeResolver.applyIfLimited(soQw, "saler_id", "created_by");
        for (SaleOrder so : saleOrderMapper.selectList(soQw)) {
            if (so.getCustomerId() == null) continue;
            BigDecimal bal = nz(so.getTotalAmount()).subtract(nz(so.getReceivedAmount()));
            if (bal.compareTo(BigDecimal.ZERO) <= 0) continue;
            result.merge(so.getCustomerId(), bal, BigDecimal::add);
        }
        return result;
    }

    private Map<String, Object> newCustomerRow(Long custId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("customerId", custId);
        r.put("customerCode", "");
        r.put("customerName", "");
        r.put("beginBalance", BigDecimal.ZERO);
        r.put("saleOrderAmount", BigDecimal.ZERO);
        r.put("saleOrderPaidAmount", BigDecimal.ZERO);
        r.put("saleOrderUnpaidAmount", BigDecimal.ZERO);
        r.put("saleDeliveryAmount", BigDecimal.ZERO);
        r.put("saleDeliveryPaidAmount", BigDecimal.ZERO);
        r.put("saleDeliveryUnpaidAmount", BigDecimal.ZERO);
        r.put("saleInvoiceAmount", BigDecimal.ZERO);
        r.put("saleInvoicePaidAmount", BigDecimal.ZERO);
        r.put("saleInvoiceUnpaidAmount", BigDecimal.ZERO);
        r.put("saleReturnAmount", BigDecimal.ZERO);
        r.put("currentReceivable", BigDecimal.ZERO);
        r.put("currentPaid", BigDecimal.ZERO);
        r.put("currentUnpaid", BigDecimal.ZERO);
        r.put("endBalance", BigDecimal.ZERO);
        r.put("remainingUnpaid", BigDecimal.ZERO);
        r.put("documentCount", 0);
        r.put("lastTransactionDate", null);
        return r;
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** 单据已收不超过单据金额（超额部分视为预收，不计入该单已收），避免已收大于应收 */
    private BigDecimal capPaid(BigDecimal paid, BigDecimal amount) {
        BigDecimal p = nz(paid).max(BigDecimal.ZERO);
        BigDecimal a = nz(amount).max(BigDecimal.ZERO);
        return p.min(a);
    }

    // 将客户编号/名称解析为客户ID集合（空条件返回空集合=不过滤）
    private Set<Long> resolveCustomerIds(String customerCode, String customerName) {
        Set<Long> ids = new HashSet<>();
        boolean hasCode = customerCode != null && !customerCode.trim().isEmpty();
        boolean hasName = customerName != null && !customerName.trim().isEmpty();
        if (!hasCode && !hasName) return ids;
        List<Customer> list = customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .like(hasCode, Customer::getCustomerCode, customerCode.trim())
                .like(hasName, Customer::getCustomerName, customerName.trim()));
        for (Customer c : list) {
            if (c.getCustomerId() != null) ids.add(c.getCustomerId());
        }
        return ids;
    }

    // ==================== 客户对账单：明细 ====================

    @GetMapping("/customer-statement/detail")
    public Result<Map<String, Object>> customerStatementDetail(
            HttpServletRequest req,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String customerName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Map<String, Object>> all = buildCustomerStatementDetailRecords(startDate, endDate, keyword, customerCode, customerName);
        return Result.success(paginate(all, page, size));
    }

    private List<Map<String, Object>> buildCustomerStatementDetailRecords(
            LocalDate start, LocalDate end, String keyword, String customerCode, String customerName) {
        Set<Long> targetCust = resolveCustomerIds(customerCode, customerName);
        // 1) 主表：销售订单（与汇总同期间、同客户、同数据权限）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder> orderQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleOrder>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "order_date", start)
                        .le(end != null, "order_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "order_no", keyword)
                        .in(!targetCust.isEmpty(), "customer_id", targetCust);
        dataScopeResolver.applyIfLimited(orderQw, "saler_id", "created_by");
        List<SaleOrder> orders = saleOrderMapper.selectList(orderQw);

        // 2) 下推单据：销售出库 / 销售发票 / 销售退货（同一期间与数据权限）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery> deliveryQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "delivery_date", start)
                        .le(end != null, "delivery_date", end)
                        .in(!targetCust.isEmpty(), "customer_id", targetCust);
        dataScopeResolver.applyIfLimited(deliveryQw, "saler_id", "created_by");
        List<SaleDelivery> deliveries = saleDeliveryMapper.selectList(deliveryQw);

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice> invoiceQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleInvoice>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "invoice_date", start)
                        .le(end != null, "invoice_date", end)
                        .in(!targetCust.isEmpty(), "customer_id", targetCust);
        dataScopeResolver.applyIfLimited(invoiceQw, "saler_id", "created_by");
        List<SaleInvoice> invoices = saleInvoiceMapper.selectList(invoiceQw);

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn> returnQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleReturn>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "return_date", start)
                        .le(end != null, "return_date", end)
                        .in(!targetCust.isEmpty(), "customer_id", targetCust);
        dataScopeResolver.applyIfLimited(returnQw, "saler_id", "created_by");
        List<SaleReturn> returns = saleReturnMapper.selectList(returnQw);

        // 3) 出库单号 → 订单号（发票/退货可能挂在出库单上）
        Map<String, String> deliveryOrderNo = new HashMap<>();
        for (SaleDelivery d : deliveries) {
            String orderNo = d.getOrderNo();
            if (orderNo == null || orderNo.isBlank()) {
                orderNo = "销售订单".equals(d.getSourceBillType()) ? d.getSourceBillNo() : null;
            }
            if (d.getDeliveryNo() != null && orderNo != null) deliveryOrderNo.put(d.getDeliveryNo(), orderNo);
        }
        // 发票/退货引用到、但不在本期间的出库单，补查其订单号
        Set<String> refDeliveryNos = new HashSet<>();
        for (SaleInvoice inv : invoices) {
            if (inv.getDeliveryNo() != null) refDeliveryNos.add(inv.getDeliveryNo());
        }
        for (SaleReturn r : returns) {
            if (r.getDeliveryNo() != null) refDeliveryNos.add(r.getDeliveryNo());
            if (r.getSourceDeliveryNo() != null) refDeliveryNos.add(r.getSourceDeliveryNo());
            if ("销售出库单".equals(r.getSourceBillType()) && r.getSourceBillNo() != null) refDeliveryNos.add(r.getSourceBillNo());
        }
        refDeliveryNos.removeAll(deliveryOrderNo.keySet());
        if (!refDeliveryNos.isEmpty()) {
            for (SaleDelivery d : saleDeliveryMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SaleDelivery>()
                            .eq("approval_status", APPROVED)
                            .in("delivery_no", refDeliveryNos))) {
                String orderNo = d.getOrderNo();
                if (orderNo == null || orderNo.isBlank()) {
                    orderNo = "销售订单".equals(d.getSourceBillType()) ? d.getSourceBillNo() : null;
                }
                if (d.getDeliveryNo() != null && orderNo != null) deliveryOrderNo.put(d.getDeliveryNo(), orderNo);
            }
        }
        // 退货单号 → 订单号（发票可能由退货单下推）
        Map<String, String> returnOrderNo = new HashMap<>();
        for (SaleReturn r : returns) {
            String orderNo = "销售订单".equals(r.getSourceBillType()) ? r.getSourceBillNo() : null;
            if (orderNo == null) {
                String dn = r.getDeliveryNo() != null ? r.getDeliveryNo() : r.getSourceDeliveryNo();
                if (dn == null && "销售出库单".equals(r.getSourceBillType())) dn = r.getSourceBillNo();
                orderNo = dn != null ? deliveryOrderNo.get(dn) : null;
            }
            if (r.getReturnNo() != null && orderNo != null) returnOrderNo.put(r.getReturnNo(), orderNo);
        }

        // 4) 按订单汇总下推金额与已收/未收（出库、发票的已收按已生效收款单核销明细反算）
        Map<Long, BigDecimal> deliveryPaidById = paidBySource("销售出库单",
                deliveries.stream().map(SaleDelivery::getDeliveryId).collect(Collectors.toList()));
        Map<Long, BigDecimal> invoicePaidById = paidBySource("销售发票",
                invoices.stream().map(SaleInvoice::getInvoiceId).collect(Collectors.toList()));
        Map<String, BigDecimal> deliveryByOrder = new HashMap<>();
        Map<String, BigDecimal> deliveryPaidByOrder = new HashMap<>();
        Map<String, BigDecimal> deliveryUnpaidByOrder = new HashMap<>();
        for (SaleDelivery d : deliveries) {
            String orderNo = deliveryOrderNo.get(d.getDeliveryNo());
            if (orderNo == null) {
                orderNo = d.getOrderNo();
                if (orderNo == null || orderNo.isBlank()) {
                    orderNo = "销售订单".equals(d.getSourceBillType()) ? d.getSourceBillNo() : null;
                }
            }
            if (orderNo == null) continue;
            BigDecimal amt = nz(d.getTotalAmount());
            BigDecimal paid = capPaid(deliveryPaidById.get(d.getDeliveryId()), amt);
            deliveryByOrder.merge(orderNo, amt, BigDecimal::add);
            deliveryPaidByOrder.merge(orderNo, paid, BigDecimal::add);
            deliveryUnpaidByOrder.merge(orderNo, amt.subtract(paid).max(BigDecimal.ZERO), BigDecimal::add);
        }
        Map<String, BigDecimal> invoiceByOrder = new HashMap<>();
        Map<String, BigDecimal> invoicePaidByOrder = new HashMap<>();
        Map<String, BigDecimal> invoiceUnpaidByOrder = new HashMap<>();
        for (SaleInvoice inv : invoices) {
            String orderNo = "销售订单".equals(inv.getSourceBillType()) ? inv.getSourceBillNo() : null;
            if (orderNo == null && "销售退货单".equals(inv.getSourceBillType())) {
                orderNo = returnOrderNo.get(inv.getSourceBillNo());
            }
            if (orderNo == null && inv.getDeliveryNo() != null) orderNo = deliveryOrderNo.get(inv.getDeliveryNo());
            if (orderNo == null) continue;
            BigDecimal amt = nz(inv.getTotalAmount());
            if (inv.getIsRedInvoice() != null && inv.getIsRedInvoice() == 1) amt = amt.negate();
            BigDecimal paid = capPaid(invoicePaidById.get(inv.getInvoiceId()), amt);
            invoiceByOrder.merge(orderNo, amt, BigDecimal::add);
            invoicePaidByOrder.merge(orderNo, paid, BigDecimal::add);
            invoiceUnpaidByOrder.merge(orderNo, amt.subtract(paid).max(BigDecimal.ZERO), BigDecimal::add);
        }
        Map<String, BigDecimal> returnByOrder = new HashMap<>();
        for (SaleReturn r : returns) {
            String orderNo = "销售订单".equals(r.getSourceBillType()) ? r.getSourceBillNo() : returnOrderNo.get(r.getReturnNo());
            if (orderNo == null) continue;
            returnByOrder.merge(orderNo, nz(r.getTotalAmount()).negate(), BigDecimal::add);
        }

        // 4.1) 未关联到本期间订单的下推单据：按客户汇总成一行，避免明细看不到发票/出库/退货金额
        Set<String> orderNos = orders.stream().map(SaleOrder::getOrderNo).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Map<String, BigDecimal>> unlinked = new HashMap<>();
        for (SaleDelivery d : deliveries) {
            String orderNo = deliveryOrderNo.get(d.getDeliveryNo());
            if (orderNo == null) orderNo = d.getOrderNo();
            if (orderNo == null || orderNo.isBlank()) {
                orderNo = "销售订单".equals(d.getSourceBillType()) ? d.getSourceBillNo() : null;
            }
            if (orderNo != null && orderNos.contains(orderNo)) continue;
            if (d.getCustomerId() == null) continue;
            BigDecimal amt = nz(d.getTotalAmount());
            BigDecimal paid = capPaid(deliveryPaidById.get(d.getDeliveryId()), amt);
            Map<String, BigDecimal> u = unlinked.computeIfAbsent(d.getCustomerId(), k -> new HashMap<>());
            u.merge("delivery", amt, BigDecimal::add);
            u.merge("deliveryPaid", paid, BigDecimal::add);
            u.merge("deliveryUnpaid", amt.subtract(paid).max(BigDecimal.ZERO), BigDecimal::add);
        }
        for (SaleInvoice inv : invoices) {
            String orderNo = "销售订单".equals(inv.getSourceBillType()) ? inv.getSourceBillNo() : null;
            if (orderNo == null && "销售退货单".equals(inv.getSourceBillType())) {
                orderNo = returnOrderNo.get(inv.getSourceBillNo());
            }
            if (orderNo == null && inv.getDeliveryNo() != null) orderNo = deliveryOrderNo.get(inv.getDeliveryNo());
            if (orderNo != null && orderNos.contains(orderNo)) continue;
            if (inv.getCustomerId() == null) continue;
            BigDecimal amt = nz(inv.getTotalAmount());
            if (inv.getIsRedInvoice() != null && inv.getIsRedInvoice() == 1) amt = amt.negate();
            BigDecimal paid = capPaid(invoicePaidById.get(inv.getInvoiceId()), amt);
            Map<String, BigDecimal> u = unlinked.computeIfAbsent(inv.getCustomerId(), k -> new HashMap<>());
            u.merge("invoice", amt, BigDecimal::add);
            u.merge("invoicePaid", paid, BigDecimal::add);
            u.merge("invoiceUnpaid", amt.subtract(paid).max(BigDecimal.ZERO), BigDecimal::add);
        }
        for (SaleReturn r : returns) {
            String orderNo = "销售订单".equals(r.getSourceBillType()) ? r.getSourceBillNo() : returnOrderNo.get(r.getReturnNo());
            if (orderNo != null && orderNos.contains(orderNo)) continue;
            if (r.getCustomerId() == null) continue;
            unlinked.computeIfAbsent(r.getCustomerId(), k -> new HashMap<>())
                    .merge("return", nz(r.getTotalAmount()).negate(), BigDecimal::add);
        }

        // 5) 组装订单行：一张订单一行，下推金额挂在订单上
        List<Map<String, Object>> list = new ArrayList<>();
        for (SaleOrder o : orders) {
            BigDecimal orderAmt = nz(o.getTotalAmount());
            BigDecimal paid = capPaid(o.getReceivedAmount(), orderAmt);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("billDate", o.getOrderDate());
            m.put("billType", "销售订单");
            m.put("billNo", o.getOrderNo());
            // 单据跳转信息：前端据此把「单据编号」渲染成可点链接（见 frontend/src/utils/billRoute.js）
            m.put("billDocType", "SALE_ORDER");
            m.put("billId", o.getOrderId());
            m.put("customerId", o.getCustomerId());
            m.put("customerCode", "");
            m.put("customerName", "");
            m.put("orderAmount", orderAmt);
            m.put("paidAmount", paid);
            m.put("unpaidAmount", orderAmt.subtract(paid).max(BigDecimal.ZERO));
            m.put("deliveryAmount", deliveryByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("deliveryPaidAmount", deliveryPaidByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("deliveryUnpaidAmount", deliveryUnpaidByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("invoiceAmount", invoiceByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("invoicePaidAmount", invoicePaidByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("invoiceUnpaidAmount", invoiceUnpaidByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("returnAmount", returnByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("remark", o.getRemark());
            list.add(m);
        }
        // 未关联订单的下推单据：每个客户汇总一行（订单编号显示“未关联订单”）
        for (Map.Entry<Long, Map<String, BigDecimal>> e : unlinked.entrySet()) {
            Map<String, BigDecimal> u = e.getValue();
            BigDecimal dv = u.getOrDefault("delivery", BigDecimal.ZERO);
            BigDecimal iv = u.getOrDefault("invoice", BigDecimal.ZERO);
            BigDecimal rv = u.getOrDefault("return", BigDecimal.ZERO);
            if (dv.signum() == 0 && iv.signum() == 0 && rv.signum() == 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("billDate", null);
            m.put("billType", "未关联订单");
            m.put("billNo", "未关联订单");
            m.put("billDocType", null);
            m.put("billId", null);
            m.put("customerId", e.getKey());
            m.put("customerCode", "");
            m.put("customerName", "");
            m.put("orderAmount", BigDecimal.ZERO);
            m.put("paidAmount", BigDecimal.ZERO);
            m.put("unpaidAmount", BigDecimal.ZERO);
            m.put("deliveryAmount", dv);
            m.put("deliveryPaidAmount", u.getOrDefault("deliveryPaid", BigDecimal.ZERO));
            m.put("deliveryUnpaidAmount", u.getOrDefault("deliveryUnpaid", BigDecimal.ZERO));
            m.put("invoiceAmount", iv);
            m.put("invoicePaidAmount", u.getOrDefault("invoicePaid", BigDecimal.ZERO));
            m.put("invoiceUnpaidAmount", u.getOrDefault("invoiceUnpaid", BigDecimal.ZERO));
            m.put("returnAmount", rv);
            m.put("remark", "");
            list.add(m);
        }

        // 补客户编号/名称
        List<Long> custIds = list.stream().map(r -> (Long) r.get("customerId")).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Customer> customerMap = new HashMap<>();
        if (!custIds.isEmpty()) {
            customerMapper.selectList(new LambdaQueryWrapper<Customer>().in(Customer::getCustomerId, custIds))
                    .forEach(c -> customerMap.put(c.getCustomerId(), c));
        }
        for (Map<String, Object> row : list) {
            Customer c = customerMap.get((Long) row.get("customerId"));
            // 客户主档已删除（历史悬空引用）时兜底显示，避免名称/编码空白
            row.put("customerCode", c != null ? c.getCustomerCode() : "已删除");
            row.put("customerName", c != null ? c.getCustomerName()
                    : "已删除客户(ID:" + row.get("customerId") + ")");
        }

        list.sort((a, b) -> {
            LocalDate da = (LocalDate) a.get("billDate");
            LocalDate db = (LocalDate) b.get("billDate");
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });
        return list;
    }

    // ==================== 导出（应收账款口径） ====================

    @GetMapping("/customer-statement/export")
    public void customerStatementExport(
            HttpServletResponse response,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        List<Map<String, Object>> all = buildCustomerStatementRecords(startDate, endDate, keyword);
        StandardExcelUtil.export(response, "客户对账单", CUSTOMER_STATEMENT_HEADERS, all);
    }

    private List<Map<String, Object>> buildCustomerStatementRecords(LocalDate start, LocalDate end, String keyword) {
        // 【2026-10-19 设计图 v3】系统无应收单：对账单汇总改按销售订单口径
        // （应收总额=订单价税合计、已收总额=订单累计已收、余额=价税合计−已收），不再读应收单表。
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.erp.sale.entity.SaleOrder> qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        qw.eq("status", "已生效");
        if (start != null) qw.ge("order_date", start);
        if (end != null) qw.le("order_date", end);
        if (keyword != null && !keyword.isEmpty()) {
            qw.like("customer_name", keyword);
        }
        // 数据权限：与销售订单列表同口径（saler_id）；报表页与导出共用本方法 → 两边同时生效
        dataScopeResolver.applyIfLimited(qw, "saler_id", "created_by");
        qw.orderByDesc("order_date");
        List<com.erp.sale.entity.SaleOrder> soList = saleOrderMapper.selectList(qw);

        // 按客户聚合
        Map<Long, List<com.erp.sale.entity.SaleOrder>> byCustomer = soList.stream()
                .filter(o -> o.getCustomerId() != null)
                .collect(Collectors.groupingBy(com.erp.sale.entity.SaleOrder::getCustomerId));

        List<Map<String, Object>> records = new ArrayList<>();
        for (Map.Entry<Long, List<com.erp.sale.entity.SaleOrder>> e : byCustomer.entrySet()) {
            List<com.erp.sale.entity.SaleOrder> list = e.getValue();
            BigDecimal totalReceivable = list.stream()
                    .map(o -> o.getNetAmount() == null ? BigDecimal.ZERO : o.getNetAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalPaid = list.stream()
                    .map(o -> o.getReceivedAmount() == null ? BigDecimal.ZERO : o.getReceivedAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = totalReceivable.subtract(totalPaid);
            LocalDate lastArDate = list.stream()
                    .map(com.erp.sale.entity.SaleOrder::getOrderDate)
                    .filter(Objects::nonNull)
                    .max(java.util.Comparator.naturalOrder())
                    .orElse(null);
            String customerName = list.stream()
                    .map(com.erp.sale.entity.SaleOrder::getCustomerName)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isBlank())
                    .findFirst()
                    .orElse("");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customerId", e.getKey());
            m.put("customerName", customerName);
            m.put("totalReceivable", totalReceivable);
            m.put("totalPaid", totalPaid);
            m.put("balance", balance);
            m.put("arCount", list.size());
            m.put("lastArDate", lastArDate);
            records.add(m);
        }
        // 按余额降序
        records.sort((a, b) -> ((BigDecimal) b.get("balance")).compareTo((BigDecimal) a.get("balance")));
        return records;
    }

    private static final List<String[]> CUSTOMER_STATEMENT_HEADERS = List.of(
            new String[]{"客户ID", "customerId"},
            new String[]{"客户名称", "customerName"},
            new String[]{"应收总额", "totalReceivable"},
            new String[]{"已收总额", "totalPaid"},
            new String[]{"余额", "balance"},
            new String[]{"单据数", "arCount"},
            new String[]{"最后应收日期", "lastArDate"}
    );

    // ==================== 通用分页 ====================

    private Map<String, Object> paginate(List<Map<String, Object>> all, int page, int size) {
        int total = all.size();
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(total, from + size);
        List<Map<String, Object>> pageList = from < to ? all.subList(from, to) : new ArrayList<>();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("records", pageList);
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        return r;
    }
}
