package com.erp.finance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.erp.common.PageResult;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.entity.PaymentVoucher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface FinanceService extends IService<AccountsReceivable> {

    // ========== 应收账款 ==========

    /**
     * 销售订单确认时生成应收
     */
    AccountsReceivable createArFromSaleOrder(Long saleOrderId);

    /**
     * 收款时更新应收
     */
    void receivePayment(Long arId, BigDecimal amount, String paymentMethod, String transactionNo, String operator);

    /**
     * 应收账款列表
     */
    PageResult<AccountsReceivable> pageAr(int current, int size, String customerName, String status, String arNo);

    /**
     * 获取应收详情
     */
    AccountsReceivable getArDetail(Long id);

    // ========== 应付账款 ==========

    /**
     * 采购订单确认时生成应付
     */
    AccountsPayable createApFromPurchaseOrder(Long purchaseOrderId);

    /**
     * 付款时更新应付
     */
    void makePayment(Long apId, BigDecimal amount, String paymentMethod, String transactionNo, String operator);

    /**
     * 应付账款列表
     */
    PageResult<AccountsPayable> pageAp(int current, int size, String supplierName, String status, String apNo);

    /**
     * 获取应付详情
     */
    AccountsPayable getApDetail(Long id);

    // ========== 收款记录 ==========

    /**
     * 新增收款记录
     */
    PaymentReceipt createReceipt(PaymentReceipt receipt);

    /**
     * 收款记录列表
     */
    PageResult<PaymentReceipt> pageReceipt(int current, int size, String customerName, String status, String receiptNo);

    /**
     * 确认收款
     */
    void confirmReceipt(Long id, String confirmedBy);

    // ========== 付款记录 ==========

    /**
     * 新增付款记录
     */
    PaymentVoucher createVoucher(PaymentVoucher voucher);

    /**
     * 付款记录列表
     */
    PageResult<PaymentVoucher> pageVoucher(int current, int size, String supplierName, String status, String voucherNo);

    /**
     * 确认付款
     */
    void confirmVoucher(Long id, String confirmedBy);

    // ========== 财务报表 ==========

    /**
     * 应收应付汇总
     */
    Map<String, Object> getSummary();

    /**
     * 账龄分析
     */
    List<Map<String, Object>> getAgingAnalysis();
}
