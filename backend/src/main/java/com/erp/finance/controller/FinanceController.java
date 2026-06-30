package com.erp.finance.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.entity.PaymentVoucher;
import com.erp.finance.service.FinanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    // ========== 应收账款 ==========

    @GetMapping("/ar")
    public Result<PageResult<AccountsReceivable>> pageAr(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String arNo) {
        return Result.success(financeService.pageAr(current, size, customerName, status, arNo));
    }

    @GetMapping("/ar/{id}")
    public Result<AccountsReceivable> arDetail(@PathVariable Long id) {
        return Result.success(financeService.getArDetail(id));
    }

    @PostMapping("/ar/from-sale-order/{saleOrderId}")
    public Result<AccountsReceivable> createArFromSaleOrder(@PathVariable Long saleOrderId) {
        return Result.success(financeService.createArFromSaleOrder(saleOrderId));
    }

    @PostMapping("/ar/{id}/receive")
    public Result<Void> receivePayment(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String paymentMethod = (String) body.get("paymentMethod");
        String transactionNo = (String) body.get("transactionNo");
        String operator = (String) req.getAttribute("currentUsername");
        financeService.receivePayment(id, amount, paymentMethod, transactionNo, operator);
        return Result.success();
    }

    // ========== 应付账款 ==========

    @GetMapping("/ap")
    public Result<PageResult<AccountsPayable>> pageAp(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String apNo) {
        return Result.success(financeService.pageAp(current, size, supplierName, status, apNo));
    }

    @GetMapping("/ap/{id}")
    public Result<AccountsPayable> apDetail(@PathVariable Long id) {
        return Result.success(financeService.getApDetail(id));
    }

    @PostMapping("/ap/from-purchase-order/{purchaseOrderId}")
    public Result<AccountsPayable> createApFromPurchaseOrder(@PathVariable Long purchaseOrderId) {
        return Result.success(financeService.createApFromPurchaseOrder(purchaseOrderId));
    }

    @PostMapping("/ap/{id}/pay")
    public Result<Void> makePayment(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String paymentMethod = (String) body.get("paymentMethod");
        String transactionNo = (String) body.get("transactionNo");
        String operator = (String) req.getAttribute("currentUsername");
        financeService.makePayment(id, amount, paymentMethod, transactionNo, operator);
        return Result.success();
    }

    // ========== 收款记录 ==========

    @GetMapping("/receipts")
    public Result<PageResult<PaymentReceipt>> pageReceipt(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String receiptNo) {
        return Result.success(financeService.pageReceipt(current, size, customerName, status, receiptNo));
    }

    @PostMapping("/receipts")
    public Result<PaymentReceipt> createReceipt(@RequestBody PaymentReceipt receipt) {
        return Result.success(financeService.createReceipt(receipt));
    }

    @PostMapping("/receipts/{id}/confirm")
    public Result<Void> confirmReceipt(@PathVariable Long id, HttpServletRequest req) {
        String confirmedBy = (String) req.getAttribute("currentUsername");
        financeService.confirmReceipt(id, confirmedBy);
        return Result.success();
    }

    // ========== 付款记录 ==========

    @GetMapping("/vouchers")
    public Result<PageResult<PaymentVoucher>> pageVoucher(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String voucherNo) {
        return Result.success(financeService.pageVoucher(current, size, supplierName, status, voucherNo));
    }

    @PostMapping("/vouchers")
    public Result<PaymentVoucher> createVoucher(@RequestBody PaymentVoucher voucher) {
        return Result.success(financeService.createVoucher(voucher));
    }

    @PostMapping("/vouchers/{id}/confirm")
    public Result<Void> confirmVoucher(@PathVariable Long id, HttpServletRequest req) {
        String confirmedBy = (String) req.getAttribute("currentUsername");
        financeService.confirmVoucher(id, confirmedBy);
        return Result.success();
    }

    // ========== 财务报表 ==========

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(financeService.getSummary());
    }

    @GetMapping("/aging")
    public Result<List<Map<String, Object>>> aging() {
        return Result.success(financeService.getAgingAnalysis());
    }
}
