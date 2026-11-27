package com.erp.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.BillWriteoffDetail;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.finance.mapper.BillWriteoffDetailMapper;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.prepayment.sale.entity.SalePreReceipt;
import com.erp.finance.prepayment.sale.mapper.SalePreReceiptMapper;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleInvoiceMapper;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.mapper.SaleOrderMapper;
import com.erp.basedata.entity.Employee;
import com.erp.basedata.entity.SettlementMode;
import com.erp.basedata.mapper.EmployeeMapper;
import com.erp.basedata.mapper.SettlementModeMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.mapper.CustomerMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 财务月度分析报表（2026-09-07 立项，立项登记表见 .trae/documents/财务月度分析报表_立项登记表.md）
 *
 * 三类口径（已与用户确认）：
 *  1) 月度开票/未开票：开票=销售发票(approval_status=已生效，蓝字正/红字负)按开票月汇总；
 *     未开票=已生效销售订单(需要开票 is_invoiced=是 或已发生部分开票) 按订单行
 *     (qty - turned_invoice_quantity)/qty * net_amount 折算剩余未开票净额，按订单月归属。
 *  2) 同事回款/未收：单据 saler_id（应收单/收款核销到应收）；未收=应收单当前 balance_amount 余额（不做历史切分）。
 *  3) 月度收款渠道：收款单+预收款(定金)合并为收入，渠道按名称关键词归一（微信/支付宝/现金），
 *     其余归入公司账户（收款账户名/文本）；关键词映射数据字典预留（当前内置常量，见 normalizeChannel）。
 *
 * 仅做只读聚合，不写任何表。
 */
@Slf4j
@RestController
@RequestMapping("/api/report/finance/monthly")
@RequiredArgsConstructor
public class MonthlyFinanceReportController {

    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final Pattern YEAR_PATTERN = Pattern.compile("^\\d{4}$");
    /** 收款单有效状态：已确认/已生效/已结案/CONFIRMED（作废已排除） */
    private static final List<String> RECEIPT_EFFECTIVE_STATUS =
            List.of("已确认", "已生效", "已结案", "CONFIRMED");
    /** 预收款单有效状态：2=已生效 3=已核销（0草稿/1待审核/7已作废排除） */
    private static final List<Integer> PRE_RECEIPT_EFFECTIVE_STATUS = List.of(2, 3);

    private final AccountsReceivableMapper arMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final SalePreReceiptMapper preReceiptMapper;
    private final BillWriteoffDetailMapper writeoffMapper;
    private final SaleInvoiceMapper invoiceMapper;
    private final SaleOrderMapper orderMapper;
    private final SaleOrderItemMapper orderItemMapper;
    private final EmployeeMapper employeeMapper;
    private final SettlementModeMapper settlementModeMapper;
    private final CustomerMapper customerMapper;
    private final DataScopeResolver dataScopeResolver;

    // ==================== 卡1：月度开票与未开票 ====================

    /** 月度开票/未开票/回款汇总（行=月份），year=YYYY */
    @GetMapping("/invoice-summary")
    public Result<Map<String, Object>> invoiceSummary(
            HttpServletRequest req,
            @RequestParam(required = false) String year) {
        int y = normalizeYear(year);
        LocalDate yearStart = LocalDate.of(y, 1, 1);
        LocalDate yearEnd = LocalDate.of(y, 12, 31);

        // 1) 开票：销售发票（已生效）
        QueryWrapper<SaleInvoice> invQw = new QueryWrapper<SaleInvoice>()
                .between("invoice_date", yearStart, yearEnd)
                .eq("approval_status", "已生效");
        dataScopeResolver.resolve(req).applyTo(invQw, "saler_id");
        List<SaleInvoice> invoices = invoiceMapper.selectList(invQw);
        Map<String, BigDecimal> invAmountByYm = new HashMap<>();
        Map<String, BigDecimal> invTaxByYm = new HashMap<>();
        Map<String, Integer> invCountByYm = new HashMap<>();
        BigDecimal yearInvoiceAmount = BigDecimal.ZERO;
        BigDecimal yearInvoiceTax = BigDecimal.ZERO;
        int yearInvoiceCount = 0;
        for (SaleInvoice inv : invoices) {
            if (inv.getInvoiceDate() == null) continue;
            String ym = YearMonth.from(inv.getInvoiceDate()).toString();
            BigDecimal amt = nz(inv.getTotalAmount());
            BigDecimal tax = nz(inv.getTaxAmount());
            // 红字发票冲减蓝字
            boolean red = inv.getIsRedInvoice() != null && inv.getIsRedInvoice() == 1;
            BigDecimal signedAmt = red ? amt.negate() : amt;
            BigDecimal signedTax = red ? tax.negate() : tax;
            invAmountByYm.merge(ym, signedAmt, BigDecimal::add);
            invTaxByYm.merge(ym, signedTax, BigDecimal::add);
            invCountByYm.merge(ym, 1, Integer::sum);
            yearInvoiceAmount = yearInvoiceAmount.add(signedAmt);
            yearInvoiceTax = yearInvoiceTax.add(signedTax);
            yearInvoiceCount++;
        }

        // 2) 未开票：已生效销售订单（订单月归属），按明细行折算剩余未开票净额
        QueryWrapper<SaleOrder> orderQw = new QueryWrapper<SaleOrder>()
                .between("order_date", yearStart, yearEnd)
                .eq("approval_status", "已生效");
        dataScopeResolver.resolve(req).applyTo(orderQw, "saler_id");
        List<SaleOrder> yearOrders = orderMapper.selectList(orderQw);
        Map<String, BigDecimal> uninvByYm = new HashMap<>();
        if (!yearOrders.isEmpty()) {
            List<Long> orderIds = yearOrders.stream().map(SaleOrder::getOrderId).collect(Collectors.toList());
            List<SaleOrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<SaleOrderItem>().in(SaleOrderItem::getOrderId, orderIds));
            Map<Long, List<SaleOrderItem>> itemsByOrder = items.stream()
                    .filter(i -> i.getOrderId() != null)
                    .collect(Collectors.groupingBy(SaleOrderItem::getOrderId));
            Map<Long, SaleOrder> orderById = yearOrders.stream()
                    .collect(Collectors.toMap(SaleOrder::getOrderId, o -> o, (a, b) -> a));
            Map<String, BigDecimal> perYm = new HashMap<>();
            for (Map.Entry<Long, List<SaleOrderItem>> en : itemsByOrder.entrySet()) {
                SaleOrder o = orderById.get(en.getKey());
                if (o == null || o.getOrderDate() == null) continue;
                boolean needInvoice = isNeedInvoice(o, en.getValue());
                if (!needInvoice) continue;
                BigDecimal remain = remainingUninvoicedNet(en.getValue());
                if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
                String ym = YearMonth.from(o.getOrderDate()).toString();
                perYm.merge(ym, remain, BigDecimal::add);
            }
            uninvByYm.putAll(perYm);
        }

        // 3) 回款合计（收款单+预收款，货币实收）
        List<PaymentReceipt> receipts = effectiveReceipts(yearStart, yearEnd);
        List<SalePreReceipt> preReceipts = effectivePreReceipts(yearStart, yearEnd);
        Map<String, BigDecimal> receiptByYm = new HashMap<>();
        for (PaymentReceipt r : receipts) {
            if (r.getReceiptDate() == null) continue;
            String ym = YearMonth.from(r.getReceiptDate()).toString();
            receiptByYm.merge(ym, nz(r.getAmount()), BigDecimal::add);
        }
        for (SalePreReceipt p : preReceipts) {
            if (p.getReceiptDate() == null) continue;
            String ym = YearMonth.from(p.getReceiptDate()).toString();
            receiptByYm.merge(ym, nz(p.getAmount()), BigDecimal::add);
        }
        BigDecimal yearReceipt = receiptByYm.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4) 当前累计未收余额（实时口径，不做历史切分）
        BigDecimal openBalance = currentOpenBalance(req);

        List<Map<String, Object>> byMonth = new ArrayList<>();
        BigDecimal totalInvoice = BigDecimal.ZERO;
        BigDecimal totalUninv = BigDecimal.ZERO;
        BigDecimal totalReceiptMonth = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            String ym = String.format("%04d-%02d", y, m);
            BigDecimal ia = invAmountByYm.getOrDefault(ym, BigDecimal.ZERO);
            BigDecimal ut = uninvByYm.getOrDefault(ym, BigDecimal.ZERO);
            BigDecimal ra = receiptByYm.getOrDefault(ym, BigDecimal.ZERO);
            totalInvoice = totalInvoice.add(ia);
            totalUninv = totalUninv.add(ut);
            totalReceiptMonth = totalReceiptMonth.add(ra);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", ym);
            row.put("invoiceAmount", ia);
            row.put("invoiceTaxAmount", invTaxByYm.getOrDefault(ym, BigDecimal.ZERO));
            row.put("invoiceCount", invCountByYm.getOrDefault(ym, 0));
            row.put("uninvoicedAmount", ut);
            row.put("receiptAmount", ra);
            byMonth.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", y);
        result.put("byMonth", byMonth);
        result.put("invoiceAmount", totalInvoice);
        result.put("invoiceTaxAmount", yearInvoiceTax);
        result.put("invoiceCount", yearInvoiceCount);
        result.put("uninvoicedAmount", totalUninv);
        result.put("receiptAmount", totalReceiptMonth);
        result.put("openBalance", openBalance);
        return Result.success(result);
    }

    /** 开票/未开票明细；type=invoiced 已开票 / type=uninvoiced 未开票；支持 month=YYYY-MM 或 year=YYYY（全年） */
    @GetMapping("/invoice-details")
    public Result<Map<String, Object>> invoiceDetails(
            HttpServletRequest req,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String year,
            @RequestParam String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LocalDate start;
        LocalDate end;
        if (month != null && !month.isEmpty()) {
            YearMonth ym = parseMonth(month);
            start = ym.atDay(1);
            end = ym.atEndOfMonth();
        } else {
            int y = normalizeYear(year);
            start = LocalDate.of(y, 1, 1);
            end = LocalDate.of(y, 12, 31);
        }
        List<Map<String, Object>> all = new ArrayList<>();

        if ("invoiced".equalsIgnoreCase(type)) {
            QueryWrapper<SaleInvoice> invQw = new QueryWrapper<SaleInvoice>()
                    .between("invoice_date", start, end)
                    .eq("approval_status", "已生效");
            dataScopeResolver.resolve(req).applyTo(invQw, "saler_id");
            List<SaleInvoice> invoices = invoiceMapper.selectList(invQw);
            Map<Long, String> cusNames = loadCustomerNames(invoices.stream()
                    .map(SaleInvoice::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet()));
            Map<Long, String> empNames = loadEmployeeNames(invoices.stream()
                    .map(SaleInvoice::getSalerId).filter(Objects::nonNull).collect(Collectors.toSet()));
            for (SaleInvoice inv : invoices) {
                boolean red = inv.getIsRedInvoice() != null && inv.getIsRedInvoice() == 1;
                String sign = red ? "红字" : "蓝字";
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("billNo", inv.getInvoiceNo());
                m.put("billDate", inv.getInvoiceDate());
                m.put("customerName", cusNames.getOrDefault(inv.getCustomerId(), ""));
                m.put("salerId", inv.getSalerId());
                m.put("salerName", empNames.getOrDefault(inv.getSalerId(), ""));
                m.put("totalAmount", red && inv.getTotalAmount() != null ? inv.getTotalAmount().negate() : inv.getTotalAmount());
                m.put("taxAmount", red && inv.getTaxAmount() != null ? inv.getTaxAmount().negate() : inv.getTaxAmount());
                m.put("netAmount", inv.getNetAmount());
                m.put("invoiceType", inv.getInvoiceType());
                m.put("flag", sign);
                m.put("status", inv.getApprovalStatus());
                all.add(m);
            }
        } else if ("uninvoiced".equalsIgnoreCase(type)) {
            QueryWrapper<SaleOrder> moQw = new QueryWrapper<SaleOrder>()
                    .between("order_date", start, end)
                    .eq("approval_status", "已生效");
            dataScopeResolver.resolve(req).applyTo(moQw, "saler_id");
            List<SaleOrder> monthOrders = orderMapper.selectList(moQw);
            if (!monthOrders.isEmpty()) {
                List<Long> ids = monthOrders.stream().map(SaleOrder::getOrderId).collect(Collectors.toList());
                List<SaleOrderItem> items = orderItemMapper.selectList(
                        new LambdaQueryWrapper<SaleOrderItem>().in(SaleOrderItem::getOrderId, ids));
                Map<Long, List<SaleOrderItem>> byOrder = items.stream()
                        .filter(i -> i.getOrderId() != null)
                        .collect(Collectors.groupingBy(SaleOrderItem::getOrderId));
                Set<Long> empIds = monthOrders.stream().map(SaleOrder::getSalerId)
                        .filter(Objects::nonNull).collect(Collectors.toSet());
                Set<Long> cusIds = monthOrders.stream().map(SaleOrder::getCustomerId)
                        .filter(Objects::nonNull).collect(Collectors.toSet());
                Map<Long, String> empNames = loadEmployeeNames(empIds);
                Map<Long, String> cusNames = loadCustomerNames(cusIds);
                for (SaleOrder o : monthOrders) {
                    List<SaleOrderItem> oi = byOrder.getOrDefault(o.getOrderId(), Collections.emptyList());
                    if (!isNeedInvoice(o, oi)) continue;
                    BigDecimal remain = remainingUninvoicedNet(oi);
                    if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal orderNet = nz(o.getNetAmount());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("billNo", o.getOrderNo());
                    m.put("billDate", o.getOrderDate());
                    m.put("customerName", cusNames.getOrDefault(o.getCustomerId(),
                            o.getCustomerShortName() == null ? "" : o.getCustomerShortName()));
                    m.put("salerId", o.getSalerId());
                    m.put("salerName", empNames.getOrDefault(o.getSalerId(), ""));
                    m.put("totalAmount", orderNet);
                    m.put("invoicedAmount", orderNet.subtract(remain));
                    m.put("uninvoicedAmount", remain);
                    all.add(m);
                }
            }
        }
        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            all.removeIf(m -> {
                Object no = m.get("billNo");
                Object cus = m.get("customerName");
                Object saler = m.get("salerName");
                return (no == null || !no.toString().toLowerCase().contains(kw))
                        && (cus == null || !cus.toString().toLowerCase().contains(kw))
                        && (saler == null || !saler.toString().toLowerCase().contains(kw));
            });
        }
        all.sort((a, b) -> cmpDescDate(a.get("billDate"), b.get("billDate")));
        return paginate(all, page, size);
    }

    // ==================== 卡2：同事回款与未收 ====================

    /** 同事回款进度汇总（按所选月份）；salerKey=-1 表示未归属 */
    @GetMapping("/saler-summary")
    public Result<Map<String, Object>> salerSummary(
            HttpServletRequest req,
            @RequestParam String month) {
        YearMonth ym = parseMonth(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        // 本月新开应收（按 ar_date）
        QueryWrapper<AccountsReceivable> arQw = new QueryWrapper<AccountsReceivable>()
                .between("ar_date", start, end);
        applyArDataPermission(req, arQw);
        List<AccountsReceivable> monthAr = arMapper.selectList(arQw);
        Map<Long, List<AccountsReceivable>> arBySaler = monthAr.stream()
                .collect(Collectors.groupingBy(a -> keyOfSaler(a.getSalerId())));
        Map<Long, BigDecimal[]> newArMap = new HashMap<>();
        for (Map.Entry<Long, List<AccountsReceivable>> en : arBySaler.entrySet()) {
            BigDecimal sum = en.getValue().stream().map(a -> nz(a.getTotalAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            newArMap.put(en.getKey(), new BigDecimal[]{sum});
        }

        // 本月回款（收款单），核销到应收单找 saler
        Map<Long, BigDecimal> receivedMap = salerReceivedTotals(effectiveReceipts(start, end));

        // 当前累计未收余额（实时口径，saler 维度）
        QueryWrapper<AccountsReceivable> openQw = new QueryWrapper<AccountsReceivable>()
                .ne("status", "已结清")
                .gt("balance_amount", BigDecimal.ZERO);
        applyArDataPermission(req, openQw);
        List<AccountsReceivable> openAr = arMapper.selectList(openQw);
        Map<Long, BigDecimal[]> openMap = new HashMap<>();
        for (AccountsReceivable a : openAr) {
            Long k = keyOfSaler(a.getSalerId());
            BigDecimal[] cur = openMap.computeIfAbsent(k, x -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            cur[0] = cur[0].add(nz(a.getBalanceAmount()));
            cur[1] = cur[1].add(nz(a.getBalanceAmount()));
        }
        // openAr 个数统计
        Map<Long, Integer> openCount = new HashMap<>();
        for (AccountsReceivable a : openAr) {
            openCount.merge(keyOfSaler(a.getSalerId()), 1, Integer::sum);
        }

        Set<Long> salerKeys = new TreeSet<>();
        salerKeys.addAll(newArMap.keySet());
        salerKeys.addAll(receivedMap.keySet());
        salerKeys.addAll(openMap.keySet());
        Map<Long, String> empNames = loadEmployeeNames(salerKeys.stream().filter(k -> k > 0).collect(Collectors.toSet()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long k : salerKeys) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("salerKey", k);
            m.put("salerId", k > 0 ? k : null);
            m.put("salerName", k > 0 ? empNames.getOrDefault(k, "员工" + k) : "未归属");
            m.put("newArAmount", newArMap.getOrDefault(k, new BigDecimal[]{BigDecimal.ZERO})[0]);
            m.put("receivedAmount", receivedMap.getOrDefault(k, BigDecimal.ZERO));
            m.put("openBalance", openMap.getOrDefault(k, new BigDecimal[]{BigDecimal.ZERO})[0]);
            m.put("openCount", openCount.getOrDefault(k, 0));
            rows.add(m);
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("openBalance")).compareTo((BigDecimal) a.get("openBalance")));

        BigDecimal totalNewAr = rows.stream().map(r -> (BigDecimal) r.get("newArAmount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceived = rows.stream().map(r -> (BigDecimal) r.get("receivedAmount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOpen = openAr.stream().map(a -> nz(a.getBalanceAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthReceiptAll = effectiveReceipts(start, end).stream()
                .map(r -> nz(r.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", month);
        result.put("rows", rows);
        result.put("totalNewAr", totalNewAr);
        result.put("totalReceived", totalReceived);
        result.put("totalOpen", totalOpen);
        result.put("monthReceiptAll", monthReceiptAll);
        return Result.success(result);
    }

    /** 同事维度明细；type=new-ar 本月新开应收 / received 本月回款 / open 未收清应收；salerKey=-1 未归属 */
    @GetMapping("/saler-details")
    public Result<Map<String, Object>> salerDetails(
            HttpServletRequest req,
            @RequestParam String month,
            @RequestParam String type,
            @RequestParam(defaultValue = "-1") long salerKey,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        YearMonth ym = parseMonth(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<Map<String, Object>> all = new ArrayList<>();
        Long filterSaler = salerKey > 0 ? salerKey : null;

        if ("new-ar".equalsIgnoreCase(type)) {
            QueryWrapper<AccountsReceivable> qw = new QueryWrapper<AccountsReceivable>()
                    .between("ar_date", start, end);
            applyArDataPermission(req, qw);
            if (filterSaler != null) qw.eq("saler_id", filterSaler);
            else qw.isNull("saler_id");
            List<AccountsReceivable> list = arMapper.selectList(qw);
            for (AccountsReceivable a : list) all.add(toArRow(a, "new"));
        } else if ("open".equalsIgnoreCase(type)) {
            QueryWrapper<AccountsReceivable> qw = new QueryWrapper<AccountsReceivable>()
                    .ne("status", "已结清")
                    .gt("balance_amount", BigDecimal.ZERO);
            applyArDataPermission(req, qw);
            if (filterSaler != null) qw.eq("saler_id", filterSaler);
            else qw.isNull("saler_id");
            List<AccountsReceivable> list = arMapper.selectList(qw);
            for (AccountsReceivable a : list) all.add(toArRow(a, "open"));
        } else { // received：本月回款流水（核销到应收找 saler）
            List<PaymentReceipt> receipts = effectiveReceipts(start, end);
            Map<Long, Long> salerByReceipt = receiptSalerMap(receipts);
            Set<Long> relSalerIds = receipts.stream().map(salerByReceipt::get)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> empNames = loadEmployeeNames(relSalerIds);
            Map<Long, String> settleNames = loadSettleNames();
            for (PaymentReceipt r : receipts) {
                Long k = salerByReceipt.get(r.getReceiptId());
                long actualKey = k == null ? -1L : k;
                if (actualKey != salerKey) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("billNo", r.getReceiptNo());
                m.put("billDate", r.getReceiptDate());
                m.put("customerName", r.getCustomerName());
                m.put("salerId", k);
                m.put("salerName", k == null ? "" : empNames.getOrDefault(k, "员工" + k));
                m.put("channel", settleNames.getOrDefault(r.getSettleModeId(), ""));
                m.put("amount", nz(r.getAmount()));
                m.put("source", "收款单");
                all.add(m);
            }
        }
        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            all.removeIf(m -> {
                Object no = m.get("billNo");
                Object cus = m.get("customerName");
                return (no == null || !no.toString().toLowerCase().contains(kw))
                        && (cus == null || !cus.toString().toLowerCase().contains(kw));
            });
        }
        return paginate(all, page, size);
    }

    // ==================== 卡3：月度收款渠道统计 ====================

    /** 月度收款渠道矩阵：行=月份，列=微信/支付宝/现金/各公司账户/合计 */
    @GetMapping("/channel-summary")
    public Result<Map<String, Object>> channelSummary(
            HttpServletRequest req,
            @RequestParam(required = false) String year) {
        int y = normalizeYear(year);
        LocalDate start = LocalDate.of(y, 1, 1);
        LocalDate end = LocalDate.of(y, 12, 31);

        // 收集所有明细（收款单+预收），归一分类
        List<Map<String, Object>> sources = new ArrayList<>();
        Map<Long, String> settleNames = loadSettleNames();
        for (PaymentReceipt r : effectiveReceipts(start, end)) {
            String raw = r.getSettleModeId() == null ? "" : settleNames.getOrDefault(r.getSettleModeId(), "");
            String acct = trimTo(r.getBankAccount());
            Map<String, Object> m = classify(raw, acct);
            m.put("ym", r.getReceiptDate() == null ? null : YearMonth.from(r.getReceiptDate()).toString());
            m.put("amount", nz(r.getAmount()));
            m.put("count", 1);
            sources.add(m);
        }
        for (SalePreReceipt p : effectivePreReceipts(start, end)) {
            String raw = p.getPaymentMethod() == null ? "" : p.getPaymentMethod();
            String acct = trimTo(p.getAccountName());
            Map<String, Object> m = classify(raw, acct);
            m.put("ym", p.getReceiptDate() == null ? null : YearMonth.from(p.getReceiptDate()).toString());
            m.put("amount", nz(p.getAmount()));
            m.put("count", 1);
            sources.add(m);
        }

        // 列：固定 微信/支付宝/现金 + 动态账户列（按年合计金额倒序）
        List<Map<String, Object>> columns = new ArrayList<>();
        LinkedHashMap<String, String> colDefs = new LinkedHashMap<>();
        colDefs.put("wechat", "微信");
        colDefs.put("alipay", "支付宝");
        colDefs.put("cash", "现金");
        // 账户列
        Map<String, BigDecimal> accountTotals = new LinkedHashMap<>();
        for (Map<String, Object> s : sources) {
            if (!"account".equals(s.get("category"))) continue;
            accountTotals.merge((String) s.get("account"), (BigDecimal) s.get("amount"), BigDecimal::add);
        }
        List<Map.Entry<String, BigDecimal>> sortedAccounts = accountTotals.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toList());
        for (Map.Entry<String, BigDecimal> en : sortedAccounts) {
            String key = "acc:" + en.getKey();
            colDefs.put(key, en.getKey());
        }
        colDefs.forEach((k, v) -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("key", k);
            c.put("label", v);
            columns.add(c);
        });

        // 按月组装矩阵
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal[] yearColTotals = new BigDecimal[columns.size()];
        for (int i = 0; i < columns.size(); i++) yearColTotals[i] = BigDecimal.ZERO;
        BigDecimal yearTotal = BigDecimal.ZERO;
        int yearCount = 0;
        for (int m = 1; m <= 12; m++) {
            String ym = String.format("%04d-%02d", y, m);
            BigDecimal[] cells = new BigDecimal[columns.size()];
            for (int i = 0; i < columns.size(); i++) cells[i] = BigDecimal.ZERO;
            BigDecimal rowTotal = BigDecimal.ZERO;
            int rowCount = 0;
            for (Map<String, Object> s : sources) {
                if (!ym.equals(s.get("ym"))) continue;
                String cat = (String) s.get("category");
                String key = "wechat".equals(cat) ? "wechat"
                        : "alipay".equals(cat) ? "alipay"
                        : "cash".equals(cat) ? "cash" : "acc:" + s.get("account");
                int idx = idxOfColumn(columns, key);
                if (idx < 0) continue;
                cells[idx] = cells[idx].add((BigDecimal) s.get("amount"));
                rowTotal = rowTotal.add((BigDecimal) s.get("amount"));
                rowCount += (Integer) s.get("count");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", ym);
            row.put("cells", cells);
            row.put("total", rowTotal);
            row.put("count", rowCount);
            for (int i = 0; i < columns.size(); i++) {
                yearColTotals[i] = yearColTotals[i].add(cells[i]);
            }
            yearTotal = yearTotal.add(rowTotal);
            yearCount += rowCount;
            rows.add(row);
        }
        Map<String, Object> totalRow = new LinkedHashMap<>();
        totalRow.put("month", "合计");
        totalRow.put("cells", yearColTotals);
        totalRow.put("total", yearTotal);
        totalRow.put("count", yearCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", y);
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("totalRow", totalRow);
        result.put("totalAmount", yearTotal);
        result.put("totalCount", yearCount);
        return Result.success(result);
    }

    /** 渠道流水明细；categoryKey = wechat/alipay/cash 或 acc:{账户名}，空=全部渠道；支持 month=YYYY-MM 或 year=YYYY（全年） */
    @GetMapping("/channel-details")
    public Result<Map<String, Object>> channelDetails(
            HttpServletRequest req,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String categoryKey,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LocalDate start;
        LocalDate end;
        if (month != null && !month.isEmpty()) {
            YearMonth ym = parseMonth(month);
            start = ym.atDay(1);
            end = ym.atEndOfMonth();
        } else {
            int y = normalizeYear(year);
            start = LocalDate.of(y, 1, 1);
            end = LocalDate.of(y, 12, 31);
        }
        Map<Long, String> settleNames = loadSettleNames();
        List<Map<String, Object>> all = new ArrayList<>();

        for (PaymentReceipt r : effectiveReceipts(start, end)) {
            String raw = r.getSettleModeId() == null ? "" : settleNames.getOrDefault(r.getSettleModeId(), "");
            String acct = trimTo(r.getBankAccount());
            Map<String, Object> c = classify(raw, acct);
            if (!matchCategory(c, categoryKey)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", "收款单");
            m.put("billNo", r.getReceiptNo());
            m.put("billDate", r.getReceiptDate());
            m.put("customerName", r.getCustomerName());
            m.put("channel", c.get("categoryLabel"));
            m.put("account", "account".equals(c.get("category")) ? c.get("account") : "");
            m.put("amount", nz(r.getAmount()));
            all.add(m);
        }
        for (SalePreReceipt p : effectivePreReceipts(start, end)) {
            String raw = p.getPaymentMethod() == null ? "" : p.getPaymentMethod();
            String acct = trimTo(p.getAccountName());
            Map<String, Object> c = classify(raw, acct);
            if (!matchCategory(c, categoryKey)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", "预收款");
            m.put("billNo", p.getReceiptNo());
            m.put("billDate", p.getReceiptDate());
            m.put("customerName", p.getCustomerName());
            m.put("channel", c.get("categoryLabel"));
            m.put("account", "account".equals(c.get("category")) ? c.get("account") : "");
            m.put("amount", nz(p.getAmount()));
            all.add(m);
        }
        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            all.removeIf(m -> {
                Object no = m.get("billNo");
                Object cus = m.get("customerName");
                return (no == null || !no.toString().toLowerCase().contains(kw))
                        && (cus == null || !cus.toString().toLowerCase().contains(kw));
            });
        }
        all.sort((a, b) -> cmpDescDate(a.get("billDate"), b.get("billDate")));
        return paginate(all, page, size);
    }

    // ==================== 辅助方法 ====================

    /** 本月各业务员回款合计（按收款单金额） */
    private Map<Long, BigDecimal> salerReceivedTotals(List<PaymentReceipt> receipts) {
        Map<Long, BigDecimal> result = new HashMap<>();
        Map<Long, Long> map = receiptSalerMap(receipts);
        for (PaymentReceipt r : receipts) {
            Long saler = map.get(r.getReceiptId());
            result.merge(saler == null ? -1L : saler, nz(r.getAmount()), BigDecimal::add);
        }
        return result;
    }

    /**
     * 收款单 → 业务员 映射：优先收款单 head 上"应收单"来源单号，其次核销明细(source=应收单)，
     * 找不到对应应收或应收无 saler 视为未归属。
     */
    private Map<Long, Long> receiptSalerMap(List<PaymentReceipt> receipts) {
        Map<Long, Long> result = new HashMap<>();
        if (receipts.isEmpty()) return result;
        Map<Long, PaymentReceipt> byId = receipts.stream()
                .collect(Collectors.toMap(PaymentReceipt::getReceiptId, r -> r, (a, b) -> a));
        Set<String> arNos = new HashSet<>();
        for (PaymentReceipt r : receipts) {
            if ("应收单".equals(r.getSourceBillType()) && r.getSourceBillNo() != null && !r.getSourceBillNo().isEmpty()) {
                arNos.add(r.getSourceBillNo());
            }
        }
        // 核销明细表补充：bill_type=收款单，source_type=应收单
        List<Long> receiptIds = new ArrayList<>(byId.keySet());
        List<BillWriteoffDetail> wos = writeoffMapper.selectList(
                new LambdaQueryWrapper<BillWriteoffDetail>()
                        .eq(BillWriteoffDetail::getBillType, "收款单")
                        .in(BillWriteoffDetail::getBillId, receiptIds)
                        .eq(BillWriteoffDetail::getSourceType, "应收单"));
        for (BillWriteoffDetail w : wos) {
            if (w.getSourceNo() != null && !w.getSourceNo().isEmpty()) arNos.add(w.getSourceNo());
        }
        if (!arNos.isEmpty()) {
            Map<String, Long> arSaler = arMapper.selectList(
                            new LambdaQueryWrapper<AccountsReceivable>().in(AccountsReceivable::getArNo, arNos))
                    .stream().filter(a -> a.getSalerId() != null)
                    .collect(Collectors.toMap(AccountsReceivable::getArNo, AccountsReceivable::getSalerId, (a, b) -> a));
            for (PaymentReceipt r : receipts) {
                if ("应收单".equals(r.getSourceBillType()) && r.getSourceBillNo() != null) {
                    Long s = arSaler.get(r.getSourceBillNo());
                    if (s != null) result.put(r.getReceiptId(), s);
                }
            }
            for (BillWriteoffDetail w : wos) {
                Long s = arSaler.get(w.getSourceNo());
                if (s != null) result.putIfAbsent(w.getBillId(), s);
            }
        }
        return result;
    }

    /** 应收数据权限：统一走 DataScopeResolver（created_by OR saler_id=当前员工） */
    private void applyArDataPermission(HttpServletRequest req, QueryWrapper<AccountsReceivable> qw) {
        dataScopeResolver.resolve(req).applyTo(qw, "saler_id");
    }

    private List<PaymentReceipt> effectiveReceipts(LocalDate start, LocalDate end) {
        QueryWrapper<PaymentReceipt> qw = new QueryWrapper<PaymentReceipt>()
                .between("receipt_date", start, end)
                .and(w -> w.in("status", RECEIPT_EFFECTIVE_STATUS)
                        .or().eq("approval_status", "已生效"));
        dataScopeResolver.applyIfLimited(qw, null, "created_by");
        return receiptMapper.selectList(qw);
    }

    private List<SalePreReceipt> effectivePreReceipts(LocalDate start, LocalDate end) {
        QueryWrapper<SalePreReceipt> qw = new QueryWrapper<SalePreReceipt>()
                .between("receipt_date", start, end)
                .in("status", PRE_RECEIPT_EFFECTIVE_STATUS);
        dataScopeResolver.applyIfLimited(qw, null, "create_by");
        return preReceiptMapper.selectList(qw);
    }

    /** 当前累计未收余额（实时口径） */
    private BigDecimal currentOpenBalance(HttpServletRequest req) {
        QueryWrapper<AccountsReceivable> qw = new QueryWrapper<AccountsReceivable>()
                .ne("status", "已结清")
                .gt("balance_amount", BigDecimal.ZERO);
        applyArDataPermission(req, qw);
        return arMapper.selectList(qw).stream().map(a -> nz(a.getBalanceAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isNeedInvoice(SaleOrder o, List<SaleOrderItem> items) {
        if ("是".equals(o.getIsInvoiced())) return true;
        return items.stream().anyMatch(i -> nz(i.getTurnedInvoiceQuantity()).compareTo(BigDecimal.ZERO) > 0);
    }

    /** 订单行剩余未开票净额 = net_amount * (qty - turned)/qty 之和 */
    private BigDecimal remainingUninvoicedNet(List<SaleOrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (SaleOrderItem it : items) {
            BigDecimal qty = nz(it.getQuantity());
            BigDecimal turned = nz(it.getTurnedInvoiceQuantity());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal remainQty = qty.subtract(turned);
            if (remainQty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal ratio = remainQty.divide(qty, 6, BigDecimal.ROUND_HALF_UP);
            total = total.add(nz(it.getNetAmount()).multiply(ratio));
        }
        return total.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private Map<String, Object> classify(String raw, String account) {
        Map<String, Object> m = new LinkedHashMap<>();
        String name = raw == null ? "" : raw;
        String acct = account == null ? "" : account;
        if (name.contains("微信")) {
            m.put("category", "wechat");
            m.put("categoryLabel", "微信");
        } else if (name.contains("支付宝")) {
            m.put("category", "alipay");
            m.put("categoryLabel", "支付宝");
        } else if (name.contains("现金")) {
            m.put("category", "cash");
            m.put("categoryLabel", "现金");
        } else {
            // 转账/支票/汇票/在线等一律归入公司账户；无账户文本归未指定账户
            m.put("category", "account");
            m.put("categoryLabel", acct.isEmpty() ? "未指定账户" : acct);
            m.put("account", acct.isEmpty() ? "未指定账户" : acct);
        }
        return m;
    }

    private boolean matchCategory(Map<String, Object> c, String categoryKey) {
        if (categoryKey == null) return true;
        if ("wechat".equals(categoryKey) || "alipay".equals(categoryKey) || "cash".equals(categoryKey)) {
            return categoryKey.equals(c.get("category"));
        }
        if (categoryKey.startsWith("acc:")) {
            return "account".equals(c.get("category")) && categoryKey.substring(4).equals(c.get("account"));
        }
        return true;
    }

    private int idxOfColumn(List<Map<String, Object>> columns, String key) {
        for (int i = 0; i < columns.size(); i++) {
            if (key.equals(columns.get(i).get("key"))) return i;
        }
        return -1;
    }

    private Map<Long, String> loadEmployeeNames(Collection<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        List<Long> clean = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (clean.isEmpty()) return map;
        for (Employee e : employeeMapper.selectBatchIds(clean)) {
            if (e != null && e.getEmployeeId() != null) {
                map.put(e.getEmployeeId(), e.getEmployeeName() == null ? "" : e.getEmployeeName());
            }
        }
        return map;
    }

    private Map<Long, String> loadCustomerNames(Collection<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        List<Long> clean = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (clean.isEmpty()) return map;
        for (Customer c : customerMapper.selectBatchIds(clean)) {
            if (c != null && c.getCustomerId() != null) {
                map.put(c.getCustomerId(), c.getCustomerName() == null ? "" : c.getCustomerName());
            }
        }
        return map;
    }

    private Map<Long, String> loadSettleNames() {
        Map<Long, String> map = new HashMap<>();
        for (SettlementMode s : settlementModeMapper.selectList(null)) {
            if (s.getSettleModeId() != null) {
                map.put(s.getSettleModeId(), s.getSettleModeName() == null ? "" : s.getSettleModeName());
            }
        }
        return map;
    }

    private Map<String, Object> toArRow(AccountsReceivable a, String kind) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("billNo", a.getArNo());
        m.put("billDate", a.getArDate());
        m.put("customerName", a.getCustomerName());
        m.put("salerId", a.getSalerId());
        m.put("totalAmount", a.getTotalAmount());
        m.put("paidAmount", a.getPaidAmount());
        m.put("balanceAmount", a.getBalanceAmount());
        m.put("status", a.getStatus());
        m.put("sourceBillNo", a.getSourceBillNo());
        if ("open".equals(kind)) {
            m.put("invoiceNo", a.getInvoiceNo());
            m.put("dueDate", a.getDueDate());
        }
        return m;
    }

    private Long keyOfSaler(Long salerId) {
        return salerId == null ? -1L : salerId;
    }

    private String trimTo(String s) {
        return s == null ? "" : s.trim();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private int normalizeYear(String year) {
        if (year == null || !YEAR_PATTERN.matcher(year).matches()) {
            return LocalDate.now().getYear();
        }
        return Integer.parseInt(year);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || !MONTH_PATTERN.matcher(month).matches()) {
            throw new IllegalArgumentException("月份参数格式错误，应为 YYYY-MM: " + month);
        }
        return YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private int cmpDescDate(Object d1, Object d2) {
        LocalDate a = (LocalDate) d1;
        LocalDate b = (LocalDate) d2;
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }

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
