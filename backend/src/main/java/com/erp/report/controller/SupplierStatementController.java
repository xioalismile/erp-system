package com.erp.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.Result;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.entity.PaymentVoucher;
import com.erp.finance.mapper.AccountsPayableMapper;
import com.erp.finance.mapper.PaymentVoucherMapper;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.mapper.SupplierMapper;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInvoice;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.entity.PurchaseReturn;
import com.erp.purchase.mapper.PurchaseInboundMapper;
import com.erp.purchase.mapper.PurchaseInvoiceMapper;
import com.erp.purchase.mapper.PurchaseOrderMapper;
import com.erp.purchase.mapper.PurchaseReturnMapper;
import com.erp.report.util.StandardExcelUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商对账单（原标准台账子系统保留能力，URL 前缀保持不变）
 *
 * 端点：
 *   - GET /api/report/standard/purchase/supplier-statement/summary  按供应商汇总
 *   - GET /api/report/standard/purchase/supplier-statement/detail   单据级明细
 *   - GET /api/report/standard/purchase/supplier-statement/export   Excel 导出（应付账款口径）
 */
@Slf4j
@RestController
@RequestMapping("/api/report/standard/purchase")
@RequiredArgsConstructor
public class SupplierStatementController {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseInboundMapper purchaseInboundMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PurchaseInvoiceMapper purchaseInvoiceMapper;
    private final PaymentVoucherMapper paymentVoucherMapper;
    private final SupplierMapper supplierMapper;
    private final AccountsPayableMapper apMapper;

    /** 数据权限：对账单「报表页 + 导出」必须与应付账款列表同口径（否则导出比列表宽 = 越权） */
    @org.springframework.beans.factory.annotation.Autowired
    private com.erp.common.scope.DataScopeResolver dataScopeResolver;

    private static final String APPROVED = "已生效";

    // ==================== 供应商对账单：汇总 ====================

    @GetMapping("/supplier-statement/summary")
    public Result<Map<String, Object>> supplierStatementSummary(
            HttpServletRequest req,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String supplierName) {
        List<Map<String, Object>> all = buildSupplierStatementSummaryRecords(startDate, endDate, keyword, supplierCode, supplierName);
        return Result.success(paginate(all, 1, Integer.MAX_VALUE));
    }

    private List<Map<String, Object>> buildSupplierStatementSummaryRecords(
            LocalDate start, LocalDate end, String keyword, String supplierCode, String supplierName) {
        Map<Long, Map<String, Object>> acc = new LinkedHashMap<>();
        Set<Long> targetSup = resolveSupplierIds(supplierCode, supplierName);

        // 1) 采购订单金额（审批状态 approval_status）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseOrder> orderQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseOrder>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "order_date", start)
                        .le(end != null, "order_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "order_no", keyword)
                        .in(!targetSup.isEmpty(), "supplier_id", targetSup);
        // 数据权限：与导出端点逐字同口径 applyIfLimited(qw, "buyer_id", "created_by")
        dataScopeResolver.applyIfLimited(orderQw, "buyer_id", "created_by");
        Map<String, BigDecimal> apPaidOrder = apPaidByOrderNo();
        Map<String, BigDecimal> apPaidInvoice = apPaidByInvoiceNo();
        for (PurchaseOrder o : purchaseOrderMapper.selectList(orderQw)) {
            BigDecimal amt = nz(o.getTotalAmount());
            BigDecimal paid = capPaid(apPaidOrder.get(o.getOrderNo()), amt);
            accumulateSupplier(acc, o.getSupplierId(), "purchaseOrderAmount", o.getOrderDate(), amt, 1);
            accumulateSupplierPaid(acc, o.getSupplierId(), "purchaseOrderPaidAmount", "purchaseOrderUnpaidAmount",
                    paid, amt.subtract(paid).max(BigDecimal.ZERO));
        }
        // 2) 采购入库金额（状态 status，日期列 receipt_date）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInbound> inboundQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInbound>()
                        .eq("status", APPROVED)
                        .ge(start != null, "receipt_date", start)
                        .le(end != null, "receipt_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "receipt_no", keyword)
                        .in(!targetSup.isEmpty(), "supplier_id", targetSup);
        // 数据权限：与导出端点逐字同口径 applyIfLimited(qw, "buyer_id", "created_by")
        dataScopeResolver.applyIfLimited(inboundQw, "buyer_id", "created_by");
        List<PurchaseInbound> inbounds = purchaseInboundMapper.selectList(inboundQw);
        Map<String, BigDecimal> inboundPaid = apPaidByInboundNo(
                inbounds.stream().map(PurchaseInbound::getReceiptNo).collect(Collectors.toList()));
        for (PurchaseInbound in : inbounds) {
            BigDecimal amt = nz(in.getTotalAmount());
            // 采购入库单本身没有已付列：按由该入库单下推的采购发票所关联应付单的已付金额反算
            BigDecimal paid = capPaid(inboundPaid.get(in.getReceiptNo()), amt);
            accumulateSupplier(acc, in.getSupplierId(), "purchaseReceiptAmount", in.getInDate(), amt, 1);
            accumulateSupplierPaid(acc, in.getSupplierId(), "purchaseReceiptPaidAmount", "purchaseReceiptUnpaidAmount",
                    paid, amt.subtract(paid).max(BigDecimal.ZERO));
        }
        // 3) 采购发票金额（状态 status，红字发票冲减，金额字段 amount）
        // purchase_invoice 表无 buyer_id 列，与采购发票列表同口径：只按 created_by 过滤，归属列传 null
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInvoice> invoiceQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInvoice>()
                        .eq("status", APPROVED)
                        .ge(start != null, "invoice_date", start)
                        .le(end != null, "invoice_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "invoice_no", keyword)
                        .in(!targetSup.isEmpty(), "supplier_id", targetSup);
        dataScopeResolver.applyIfLimited(invoiceQw, null, "created_by");
        for (PurchaseInvoice inv : purchaseInvoiceMapper.selectList(invoiceQw)) {
            BigDecimal amt = inv.getAmount() == null ? BigDecimal.ZERO : inv.getAmount();
            if (inv.getIsRedInvoice() != null && inv.getIsRedInvoice() == 1) amt = amt.negate();
            BigDecimal paid = capPaid(apPaidInvoice.get(inv.getInvoiceNo()), amt);
            accumulateSupplier(acc, inv.getSupplierId(), "purchaseInvoiceAmount", inv.getInvoiceDate(), amt, 1);
            accumulateSupplierPaid(acc, inv.getSupplierId(), "purchaseInvoicePaidAmount", "purchaseInvoiceUnpaidAmount",
                    paid, amt.subtract(paid).max(BigDecimal.ZERO));
        }
        // 4) 采购退货金额（状态 status，冲减）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseReturn> returnQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseReturn>()
                        .eq("status", APPROVED)
                        .ge(start != null, "return_date", start)
                        .le(end != null, "return_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "return_no", keyword)
                        .in(!targetSup.isEmpty(), "supplier_id", targetSup);
        // 数据权限：与导出端点逐字同口径 applyIfLimited(qw, "buyer_id", "created_by")
        dataScopeResolver.applyIfLimited(returnQw, "buyer_id", "created_by");
        for (PurchaseReturn r : purchaseReturnMapper.selectList(returnQw)) {
            BigDecimal amt = r.getTotalAmount() == null ? BigDecimal.ZERO : r.getTotalAmount().negate();
            accumulateSupplier(acc, r.getSupplierId(), "purchaseReturnAmount", r.getReturnDate(), amt, 1);
        }
        // 5) 本期付款金额（付款单 approval_status，金额字段 amount）
        // cash_payment 表无 buyer_id 列，与付款单列表同口径：只按 created_by 过滤，归属列传 null
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PaymentVoucher> paymentQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PaymentVoucher>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "payment_date", start)
                        .le(end != null, "payment_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "payment_no", keyword)
                        .in(!targetSup.isEmpty(), "supplier_id", targetSup);
        dataScopeResolver.applyIfLimited(paymentQw, null, "created_by");
        for (PaymentVoucher pv : paymentVoucherMapper.selectList(paymentQw)) {
            accumulateSupplier(acc, pv.getSupplierId(), "currentPaid", pv.getPaymentDate(), pv.getAmount(), 1);
        }
        // 6) 期初应付余额 = 查询起始日之前已生效应付的余额累计
        Map<Long, BigDecimal> openingBalances = buildSupplierOpeningBalances(start, targetSup);
        for (Map.Entry<Long, BigDecimal> e : openingBalances.entrySet()) {
            Map<String, Object> row = acc.computeIfAbsent(e.getKey(), this::newSupplierRow);
            row.put("beginBalance", nz((BigDecimal) row.get("beginBalance")).add(e.getValue()));
        }
        // 7) 计算期末余额并补主档
        List<Map<String, Object>> result = new ArrayList<>();
        List<Long> supIds = new ArrayList<>(acc.keySet());
        Map<Long, Supplier> supplierMap = new HashMap<>();
        if (!supIds.isEmpty()) {
            supplierMapper.selectList(new LambdaQueryWrapper<Supplier>().in(Supplier::getSupplierId, supIds))
                    .forEach(s -> supplierMap.put(s.getSupplierId(), s));
        }
        for (Map.Entry<Long, Map<String, Object>> e : acc.entrySet()) {
            Map<String, Object> row = e.getValue();
            Supplier s = supplierMap.get(e.getKey());
            // 供应商主档已删除（历史悬空引用）时兜底显示，避免名称/编码空白
            row.put("supplierCode", s != null ? s.getSupplierCode() : "已删除");
            row.put("supplierName", s != null ? s.getSupplierName() : "已删除供应商(ID:" + e.getKey() + ")");
            BigDecimal begin = nz((BigDecimal) row.get("beginBalance"));
            BigDecimal cur = nz((BigDecimal) row.get("currentPayable"));
            BigDecimal paid = nz((BigDecimal) row.get("currentPaid"));
            // 本期未付 = 本期应付合计 − 本期付款金额；剩余未付 = 期初 + 本期未付（累计）
            BigDecimal currentUnpaid = cur.subtract(paid);
            BigDecimal remainingUnpaid = begin.add(currentUnpaid);
            row.put("currentUnpaid", currentUnpaid);
            row.put("endBalance", remainingUnpaid);
            row.put("remainingUnpaid", remainingUnpaid);
            result.add(row);
        }
        result.sort((a, b) -> nz((BigDecimal) b.get("endBalance")).compareTo(nz((BigDecimal) a.get("endBalance"))));
        return result;
    }

    private void accumulateSupplier(Map<Long, Map<String, Object>> acc, Long supId, String amountField,
                                    LocalDate docDate, BigDecimal amount, int count) {
        if (supId == null) return;
        Map<String, Object> row = acc.computeIfAbsent(supId, this::newSupplierRow);
        row.put(amountField, nz((BigDecimal) row.get(amountField)).add(nz(amount)));
        row.put("currentPayable", nz((BigDecimal) row.get("currentPayable")).add(nz(amount)));
        row.put("documentCount", ((Integer) row.get("documentCount")) + count);
        if (docDate != null) {
            LocalDate cur = (LocalDate) row.get("lastTransactionDate");
            if (cur == null || docDate.isAfter(cur)) row.put("lastTransactionDate", docDate);
        }
    }

    /** 累计某类业务来源的已付 / 未付（未付 = 单据金额 − 已付，负数按 0 计） */
    private void accumulateSupplierPaid(Map<Long, Map<String, Object>> acc, Long supId,
                                        String paidField, String unpaidField,
                                        BigDecimal paid, BigDecimal unpaid) {
        if (supId == null) return;
        Map<String, Object> row = acc.computeIfAbsent(supId, this::newSupplierRow);
        row.put(paidField, nz((BigDecimal) row.get(paidField)).add(nz(paid)));
        row.put(unpaidField, nz((BigDecimal) row.get(unpaidField)).add(nz(unpaid)));
    }

    /** 应付单已付金额：按来源采购订单号汇总（采购单据没有已付列，已付以应付单反算） */
    private Map<String, BigDecimal> apPaidByOrderNo() {
        Map<String, BigDecimal> map = new HashMap<>();
        List<AccountsPayable> aps = apMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AccountsPayable>());
        for (AccountsPayable ap : aps) {
            if ("已作废".equals(ap.getStatus())) continue;
            if (!"采购订单".equals(ap.getSourceBillType())) continue;
            if (ap.getSourceBillNo() == null || ap.getSourceBillNo().isEmpty()) continue;
            map.merge(ap.getSourceBillNo(), nz(ap.getPaidAmount()), BigDecimal::add);
        }
        return map;
    }

    /** 应付单已付金额：按发票号汇总 */
    private Map<String, BigDecimal> apPaidByInvoiceNo() {
        Map<String, BigDecimal> map = new HashMap<>();
        List<AccountsPayable> aps = apMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AccountsPayable>());
        for (AccountsPayable ap : aps) {
            if ("已作废".equals(ap.getStatus())) continue;
            if (ap.getInvoiceNo() == null || ap.getInvoiceNo().isEmpty()) continue;
            map.merge(ap.getInvoiceNo(), nz(ap.getPaidAmount()), BigDecimal::add);
        }
        return map;
    }

    /** 应付单已付金额：按采购入库单号汇总（该入库单下推的采购发票 → 应付单已付） */
    private Map<String, BigDecimal> apPaidByInboundNo(List<String> inboundNos) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (inboundNos == null || inboundNos.isEmpty()) return map;
        Map<String, BigDecimal> paidByInvoice = apPaidByInvoiceNo();
        List<PurchaseInvoice> invoices = purchaseInvoiceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInvoice>()
                        .eq("source_bill_type", "采购入库单")
                        .in("source_bill_no", inboundNos));
        for (PurchaseInvoice inv : invoices) {
            if (inv.getSourceBillNo() == null || inv.getInvoiceNo() == null) continue;
            BigDecimal paid = paidByInvoice.get(inv.getInvoiceNo());
            if (paid == null || paid.signum() == 0) continue;
            map.merge(inv.getSourceBillNo(), paid, BigDecimal::add);
        }
        return map;
    }

    /** 期初应付余额：起始日之前已生效应付的余额累计，按供应商汇总 */
    private Map<Long, BigDecimal> buildSupplierOpeningBalances(LocalDate start, Set<Long> targetSup) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (start == null) return result;
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AccountsPayable> apQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AccountsPayable>()
                        .eq("approval_status", APPROVED)
                        .lt("ap_date", start)
                        .in(!targetSup.isEmpty(), "supplier_id", targetSup);
        // 数据权限：与导出端点逐字同口径 applyIfLimited(qw, "buyer_id", "created_by")
        dataScopeResolver.applyIfLimited(apQw, "buyer_id", "created_by");
        for (AccountsPayable ap : apMapper.selectList(apQw)) {
            if (ap.getSupplierId() == null) continue;
            result.merge(ap.getSupplierId(), nz(ap.getBalanceAmount()), BigDecimal::add);
        }
        return result;
    }

    private Map<String, Object> newSupplierRow(Long supId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("supplierId", supId);
        r.put("supplierCode", "");
        r.put("supplierName", "");
        r.put("beginBalance", BigDecimal.ZERO);
        r.put("purchaseOrderAmount", BigDecimal.ZERO);
        r.put("purchaseOrderPaidAmount", BigDecimal.ZERO);
        r.put("purchaseOrderUnpaidAmount", BigDecimal.ZERO);
        r.put("purchaseReceiptAmount", BigDecimal.ZERO);
        r.put("purchaseReceiptPaidAmount", BigDecimal.ZERO);
        r.put("purchaseReceiptUnpaidAmount", BigDecimal.ZERO);
        r.put("purchaseInvoiceAmount", BigDecimal.ZERO);
        r.put("purchaseInvoicePaidAmount", BigDecimal.ZERO);
        r.put("purchaseInvoiceUnpaidAmount", BigDecimal.ZERO);
        r.put("purchaseReturnAmount", BigDecimal.ZERO);
        r.put("currentPayable", BigDecimal.ZERO);
        r.put("currentPaid", BigDecimal.ZERO);
        r.put("currentUnpaid", BigDecimal.ZERO);
        r.put("endBalance", BigDecimal.ZERO);
        r.put("remainingUnpaid", BigDecimal.ZERO);
        r.put("documentCount", 0);
        r.put("lastTransactionDate", null);
        return r;
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** 单据已付不超过单据金额（超额部分视为预付，不计入该单已付），避免已付大于应付 */
    private BigDecimal capPaid(BigDecimal paid, BigDecimal amount) {
        BigDecimal p = nz(paid).max(BigDecimal.ZERO);
        BigDecimal a = nz(amount).max(BigDecimal.ZERO);
        return p.min(a);
    }

    // 将供应商编号/名称解析为供应商ID集合（空条件返回空集合=不过滤）
    private Set<Long> resolveSupplierIds(String supplierCode, String supplierName) {
        Set<Long> ids = new HashSet<>();
        boolean hasCode = supplierCode != null && !supplierCode.trim().isEmpty();
        boolean hasName = supplierName != null && !supplierName.trim().isEmpty();
        if (!hasCode && !hasName) return ids;
        List<Supplier> list = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .like(hasCode, Supplier::getSupplierCode, supplierCode.trim())
                .like(hasName, Supplier::getSupplierName, supplierName.trim()));
        for (Supplier s : list) {
            if (s.getSupplierId() != null) ids.add(s.getSupplierId());
        }
        return ids;
    }

    // ==================== 供应商对账单：明细 ====================

    @GetMapping("/supplier-statement/detail")
    public Result<Map<String, Object>> supplierStatementDetail(
            HttpServletRequest req,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String supplierName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Map<String, Object>> all = buildSupplierStatementDetailRecords(startDate, endDate, keyword, supplierCode, supplierName);
        return Result.success(paginate(all, page, size));
    }

    private List<Map<String, Object>> buildSupplierStatementDetailRecords(
            LocalDate start, LocalDate end, String keyword, String supplierCode, String supplierName) {
        Set<Long> targetSup = resolveSupplierIds(supplierCode, supplierName);
        // 1) 主表：采购订单（与汇总同期间、同供应商、同数据权限）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseOrder> orderQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseOrder>()
                        .eq("approval_status", APPROVED)
                        .ge(start != null, "order_date", start)
                        .le(end != null, "order_date", end)
                        .like(keyword != null && !keyword.isEmpty(), "order_no", keyword)
                        .in(!targetSup.isEmpty(), "supplier_id", targetSup);
        dataScopeResolver.applyIfLimited(orderQw, "buyer_id", "created_by");
        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(orderQw);

        // 2) 下推单据：采购入库 / 采购发票 / 采购退货（同一期间与数据权限）
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInbound> inboundQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInbound>()
                        .eq("status", APPROVED)
                        .ge(start != null, "receipt_date", start)
                        .le(end != null, "receipt_date", end)
                        .in(!targetSup.isEmpty(), "supplier_id", targetSup);
        dataScopeResolver.applyIfLimited(inboundQw, "buyer_id", "created_by");
        List<PurchaseInbound> inbounds = purchaseInboundMapper.selectList(inboundQw);

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInvoice> invoiceQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInvoice>()
                        .eq("status", APPROVED)
                        .ge(start != null, "invoice_date", start)
                        .le(end != null, "invoice_date", end)
                        .in(!targetSup.isEmpty(), "supplier_id", targetSup);
        dataScopeResolver.applyIfLimited(invoiceQw, null, "created_by");
        List<PurchaseInvoice> invoices = purchaseInvoiceMapper.selectList(invoiceQw);

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseReturn> returnQw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseReturn>()
                        .eq("status", APPROVED)
                        .ge(start != null, "return_date", start)
                        .le(end != null, "return_date", end)
                        .in(!targetSup.isEmpty(), "supplier_id", targetSup);
        dataScopeResolver.applyIfLimited(returnQw, "buyer_id", "created_by");
        List<PurchaseReturn> returns = purchaseReturnMapper.selectList(returnQw);

        // 3) 入库单号 → 订单号（发票/退货可能挂在入库单上）
        Map<String, String> inboundOrderNo = new HashMap<>();
        for (PurchaseInbound in : inbounds) {
            String orderNo = "采购订单".equals(in.getSourceType()) ? in.getSourceBillNo() : null;
            if (orderNo != null && in.getReceiptNo() != null) inboundOrderNo.put(in.getReceiptNo(), orderNo);
        }
        Set<String> refReceiptNos = new HashSet<>();
        for (PurchaseInvoice inv : invoices) {
            if ("采购入库单".equals(inv.getSourceType()) && inv.getSourceBillNo() != null) refReceiptNos.add(inv.getSourceBillNo());
        }
        for (PurchaseReturn r : returns) {
            if ("采购入库单".equals(r.getSourceType()) && r.getSourceBillNo() != null) refReceiptNos.add(r.getSourceBillNo());
        }
        refReceiptNos.removeAll(inboundOrderNo.keySet());
        if (!refReceiptNos.isEmpty()) {
            for (PurchaseInbound in : purchaseInboundMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PurchaseInbound>()
                            .eq("status", APPROVED)
                            .in("receipt_no", refReceiptNos))) {
                String orderNo = "采购订单".equals(in.getSourceType()) ? in.getSourceBillNo() : null;
                if (orderNo != null && in.getReceiptNo() != null) inboundOrderNo.put(in.getReceiptNo(), orderNo);
            }
        }

        // 4) 按订单汇总下推金额与已付/未付（采购入库、采购发票的已付按应付单已付反算）
        Map<String, BigDecimal> inboundPaidByNo = apPaidByInboundNo(
                inbounds.stream().map(PurchaseInbound::getReceiptNo).collect(Collectors.toList()));
        Map<String, BigDecimal> invoicePaidByNo = apPaidByInvoiceNo();
        Map<String, BigDecimal> receiptByOrder = new HashMap<>();
        Map<String, BigDecimal> receiptPaidByOrder = new HashMap<>();
        Map<String, BigDecimal> receiptUnpaidByOrder = new HashMap<>();
        for (PurchaseInbound in : inbounds) {
            String orderNo = inboundOrderNo.get(in.getReceiptNo());
            if (orderNo == null) continue;
            BigDecimal amt = nz(in.getTotalAmount());
            BigDecimal paid = capPaid(inboundPaidByNo.get(in.getReceiptNo()), amt);
            receiptByOrder.merge(orderNo, amt, BigDecimal::add);
            receiptPaidByOrder.merge(orderNo, paid, BigDecimal::add);
            receiptUnpaidByOrder.merge(orderNo, amt.subtract(paid).max(BigDecimal.ZERO), BigDecimal::add);
        }
        Map<String, BigDecimal> invoiceByOrder = new HashMap<>();
        Map<String, BigDecimal> invoicePaidByOrder = new HashMap<>();
        Map<String, BigDecimal> invoiceUnpaidByOrder = new HashMap<>();
        for (PurchaseInvoice inv : invoices) {
            String orderNo = "采购订单".equals(inv.getSourceType()) ? inv.getSourceBillNo() : null;
            if (orderNo == null) orderNo = inboundOrderNo.get(inv.getSourceBillNo());
            if (orderNo == null) continue;
            BigDecimal amt = nz(inv.getAmount());
            if (inv.getIsRedInvoice() != null && inv.getIsRedInvoice() == 1) amt = amt.negate();
            BigDecimal paid = capPaid(invoicePaidByNo.get(inv.getInvoiceNo()), amt);
            invoiceByOrder.merge(orderNo, amt, BigDecimal::add);
            invoicePaidByOrder.merge(orderNo, paid, BigDecimal::add);
            invoiceUnpaidByOrder.merge(orderNo, amt.subtract(paid).max(BigDecimal.ZERO), BigDecimal::add);
        }
        Map<String, BigDecimal> returnByOrder = new HashMap<>();
        for (PurchaseReturn r : returns) {
            String orderNo = "采购订单".equals(r.getSourceType()) ? r.getSourceBillNo() : null;
            if (orderNo == null) orderNo = inboundOrderNo.get(r.getSourceBillNo());
            if (orderNo == null) continue;
            returnByOrder.merge(orderNo, nz(r.getTotalAmount()).negate(), BigDecimal::add);
        }

        // 4.1) 未关联到本期间订单的下推单据：按供应商汇总成一行
        Set<String> orderNos = orders.stream().map(PurchaseOrder::getOrderNo).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Map<String, BigDecimal>> unlinked = new HashMap<>();
        for (PurchaseInbound in : inbounds) {
            String orderNo = inboundOrderNo.get(in.getReceiptNo());
            if (orderNo != null && orderNos.contains(orderNo)) continue;
            if (in.getSupplierId() == null) continue;
            BigDecimal amt = nz(in.getTotalAmount());
            BigDecimal paid = capPaid(inboundPaidByNo.get(in.getReceiptNo()), amt);
            Map<String, BigDecimal> u = unlinked.computeIfAbsent(in.getSupplierId(), k -> new HashMap<>());
            u.merge("receipt", amt, BigDecimal::add);
            u.merge("receiptPaid", paid, BigDecimal::add);
            u.merge("receiptUnpaid", amt.subtract(paid).max(BigDecimal.ZERO), BigDecimal::add);
        }
        for (PurchaseInvoice inv : invoices) {
            String orderNo = "采购订单".equals(inv.getSourceType()) ? inv.getSourceBillNo() : null;
            if (orderNo == null) orderNo = inboundOrderNo.get(inv.getSourceBillNo());
            if (orderNo != null && orderNos.contains(orderNo)) continue;
            if (inv.getSupplierId() == null) continue;
            BigDecimal amt = nz(inv.getAmount());
            if (inv.getIsRedInvoice() != null && inv.getIsRedInvoice() == 1) amt = amt.negate();
            BigDecimal paid = capPaid(invoicePaidByNo.get(inv.getInvoiceNo()), amt);
            Map<String, BigDecimal> u = unlinked.computeIfAbsent(inv.getSupplierId(), k -> new HashMap<>());
            u.merge("invoice", amt, BigDecimal::add);
            u.merge("invoicePaid", paid, BigDecimal::add);
            u.merge("invoiceUnpaid", amt.subtract(paid).max(BigDecimal.ZERO), BigDecimal::add);
        }
        for (PurchaseReturn r : returns) {
            String orderNo = "采购订单".equals(r.getSourceType()) ? r.getSourceBillNo() : null;
            if (orderNo == null) orderNo = inboundOrderNo.get(r.getSourceBillNo());
            if (orderNo != null && orderNos.contains(orderNo)) continue;
            if (r.getSupplierId() == null) continue;
            unlinked.computeIfAbsent(r.getSupplierId(), k -> new HashMap<>())
                    .merge("return", nz(r.getTotalAmount()).negate(), BigDecimal::add);
        }

        // 5) 组装订单行：一张采购订单一行，下推金额挂在订单上
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, BigDecimal> apPaidOrder = apPaidByOrderNo();
        for (PurchaseOrder o : orders) {
            BigDecimal orderAmt = nz(o.getTotalAmount());
            BigDecimal paid = capPaid(apPaidOrder.get(o.getOrderNo()), orderAmt);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("billDate", o.getOrderDate());
            m.put("billType", "采购订单");
            m.put("billNo", o.getOrderNo());
            // 单据跳转信息：前端据此把「单据编号」渲染成可点链接（见 frontend/src/utils/billRoute.js）
            m.put("billDocType", "PURCHASE_ORDER");
            m.put("billId", o.getOrderId());
            m.put("supplierId", o.getSupplierId());
            m.put("supplierCode", "");
            m.put("supplierName", "");
            m.put("orderAmount", orderAmt);
            m.put("paidAmount", paid);
            m.put("unpaidAmount", orderAmt.subtract(paid).max(BigDecimal.ZERO));
            m.put("receiptAmount", receiptByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("receiptPaidAmount", receiptPaidByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("receiptUnpaidAmount", receiptUnpaidByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("invoiceAmount", invoiceByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("invoicePaidAmount", invoicePaidByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("invoiceUnpaidAmount", invoiceUnpaidByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("returnAmount", returnByOrder.getOrDefault(o.getOrderNo(), BigDecimal.ZERO));
            m.put("remark", o.getRemark());
            list.add(m);
        }
        // 未关联订单的下推单据：每个供应商汇总一行（订单编号显示“未关联订单”）
        for (Map.Entry<Long, Map<String, BigDecimal>> e : unlinked.entrySet()) {
            Map<String, BigDecimal> u = e.getValue();
            BigDecimal rv = u.getOrDefault("receipt", BigDecimal.ZERO);
            BigDecimal iv = u.getOrDefault("invoice", BigDecimal.ZERO);
            BigDecimal ret = u.getOrDefault("return", BigDecimal.ZERO);
            if (rv.signum() == 0 && iv.signum() == 0 && ret.signum() == 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("billDate", null);
            m.put("billType", "未关联订单");
            m.put("billNo", "未关联订单");
            m.put("billDocType", null);
            m.put("billId", null);
            m.put("supplierId", e.getKey());
            m.put("supplierCode", "");
            m.put("supplierName", "");
            m.put("orderAmount", BigDecimal.ZERO);
            m.put("paidAmount", BigDecimal.ZERO);
            m.put("unpaidAmount", BigDecimal.ZERO);
            m.put("receiptAmount", rv);
            m.put("receiptPaidAmount", u.getOrDefault("receiptPaid", BigDecimal.ZERO));
            m.put("receiptUnpaidAmount", u.getOrDefault("receiptUnpaid", BigDecimal.ZERO));
            m.put("invoiceAmount", iv);
            m.put("invoicePaidAmount", u.getOrDefault("invoicePaid", BigDecimal.ZERO));
            m.put("invoiceUnpaidAmount", u.getOrDefault("invoiceUnpaid", BigDecimal.ZERO));
            m.put("returnAmount", ret);
            m.put("remark", "");
            list.add(m);
        }

        List<Long> supIds = list.stream().map(r -> (Long) r.get("supplierId")).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Supplier> supplierMap = new HashMap<>();
        if (!supIds.isEmpty()) {
            supplierMapper.selectList(new LambdaQueryWrapper<Supplier>().in(Supplier::getSupplierId, supIds))
                    .forEach(s -> supplierMap.put(s.getSupplierId(), s));
        }
        for (Map<String, Object> row : list) {
            Supplier s = supplierMap.get((Long) row.get("supplierId"));
            // 供应商主档已删除（历史悬空引用）时兜底显示，避免名称/编码空白
            row.put("supplierCode", s != null ? s.getSupplierCode() : "已删除");
            row.put("supplierName", s != null ? s.getSupplierName()
                    : "已删除供应商(ID:" + row.get("supplierId") + ")");
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

    // ==================== 导出（应付账款口径） ====================

    @GetMapping("/supplier-statement/export")
    public void supplierStatementExport(
            HttpServletResponse response,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        List<String[]> headers = Arrays.asList(
                new String[]{"供应商ID", "supplierId"},
                new String[]{"供应商名称", "supplierName"},
                new String[]{"应付总额", "totalPayable"},
                new String[]{"已付总额", "totalPaid"},
                new String[]{"余额", "balance"},
                new String[]{"单据数", "apCount"},
                new String[]{"最后应付日期", "lastApDate"}
        );
        StandardExcelUtil.export(response, "供应商对账单", headers, querySupplierStatement(startDate, endDate, keyword));
    }

    private List<Map<String, Object>> querySupplierStatement(LocalDate startDate, LocalDate endDate, String keyword) {
        // 改用 QueryWrapper（字符串列名）以便复用统一数据权限：LambdaQueryWrapper 与 DataScopeResolver 的
        // QueryWrapper 属两个平行体系，无法互相传递条件
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AccountsPayable> aw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (startDate != null) aw.ge("ap_date", startDate);
        if (endDate != null) aw.le("ap_date", endDate);
        if (keyword != null && !keyword.trim().isEmpty()) {
            aw.like("supplier_name", keyword.trim());
        }
        // 数据权限：与「应付账款」列表同口径（buyer_id）；报表页与导出共用本方法 → 两边同时生效
        dataScopeResolver.applyIfLimited(aw, "buyer_id", "created_by");
        aw.orderByDesc("ap_date");
        List<AccountsPayable> aps = apMapper.selectList(aw);
        if (aps.isEmpty()) return new ArrayList<>();

        // 按 supplierId 汇总
        Map<Long, Map<String, Object>> bySupplier = new LinkedHashMap<>();
        for (AccountsPayable ap : aps) {
            Long sid = ap.getSupplierId();
            if (sid == null) continue;
            Map<String, Object> s = bySupplier.get(sid);
            if (s == null) {
                s = new LinkedHashMap<>();
                s.put("supplierId", sid);
                s.put("supplierName", ap.getSupplierName());
                s.put("totalPayable", BigDecimal.ZERO);
                s.put("totalPaid", BigDecimal.ZERO);
                s.put("balance", BigDecimal.ZERO);
                s.put("apCount", 0);
                s.put("lastApDate", null);
                bySupplier.put(sid, s);
            }
            BigDecimal ta = ap.getTotalAmount() == null ? BigDecimal.ZERO : ap.getTotalAmount();
            BigDecimal pa = ap.getPaidAmount() == null ? BigDecimal.ZERO : ap.getPaidAmount();
            BigDecimal ba = ap.getBalanceAmount() == null ? BigDecimal.ZERO : ap.getBalanceAmount();
            s.put("totalPayable", ((BigDecimal) s.get("totalPayable")).add(ta));
            s.put("totalPaid", ((BigDecimal) s.get("totalPaid")).add(pa));
            s.put("balance", ((BigDecimal) s.get("balance")).add(ba));
            s.put("apCount", (Integer) s.get("apCount") + 1);
            LocalDate lastDate = (LocalDate) s.get("lastApDate");
            if (ap.getApDate() != null && (lastDate == null || ap.getApDate().isAfter(lastDate))) {
                s.put("lastApDate", ap.getApDate());
            }
        }
        return new ArrayList<>(bySupplier.values());
    }

    // ==================== 通用分页工具 ====================

    private Map<String, Object> paginate(List<Map<String, Object>> all, int page, int size) {
        int total = all.size();
        int safeSize = size <= 0 ? 20 : size;
        int safePage = page <= 0 ? 1 : page;
        int from = Math.max(0, (safePage - 1) * safeSize);
        int to = Math.min(total, from + safeSize);
        List<Map<String, Object>> pageList = from < to ? all.subList(from, to) : new ArrayList<>();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("records", pageList);
        r.put("total", total);
        r.put("page", safePage);
        r.put("size", safeSize);
        return r;
    }
}
