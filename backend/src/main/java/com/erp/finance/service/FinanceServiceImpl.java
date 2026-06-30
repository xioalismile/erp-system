package com.erp.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.common.PageResult;
import com.erp.config.BusinessException;
import com.erp.finance.entity.AccountsPayable;
import com.erp.finance.entity.AccountsReceivable;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.entity.PaymentVoucher;
import com.erp.finance.mapper.AccountsPayableMapper;
import com.erp.finance.mapper.AccountsReceivableMapper;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.mapper.PaymentVoucherMapper;
import com.erp.masterdata.entity.Customer;
import com.erp.masterdata.entity.Supplier;
import com.erp.masterdata.service.CustomerService;
import com.erp.masterdata.service.SupplierService;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.service.SaleOrderService;
import com.erp.purchase.entity.PurchaseOrder;
import com.erp.purchase.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 财务服务 - 已对齐PG新实体字段
 * AccountsReceivable: arId/arNo/customerId/customerName/currencyId/totalAmount/paidAmount/balanceAmount/dueDate/sourceBillType/sourceBillNo/status/approvalStatus
 * AccountsPayable: apId/apNo/supplierId/supplierName/...
 * PaymentReceipt: receiptId/receiptNo/customerId/customerName/...
 * PaymentVoucher: paymentId/paymentNo/supplierId/supplierName/...
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl extends ServiceImpl<AccountsReceivableMapper, AccountsReceivable>
        implements FinanceService {

    private final AccountsReceivableMapper arMapper;
    private final AccountsPayableMapper apMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final PaymentVoucherMapper voucherMapper;
    private final SaleOrderService saleOrderService;
    private final PurchaseOrderService purchaseOrderService;
    private final CustomerService customerService;
    private final SupplierService supplierService;

    // ========== 应收账款 ==========

    @Override
    @Transactional
    public AccountsReceivable createArFromSaleOrder(Long saleOrderId) {
        SaleOrder order = saleOrderService.getDetail(saleOrderId);
        if (order == null) throw new BusinessException("销售订单不存在");

        // 检查是否已生成应收
        AccountsReceivable existing = arMapper.selectOne(
                new QueryWrapper<AccountsReceivable>().eq("source_bill_no", order.getOrderNo()).eq("source_bill_type", "销售订单"));
        if (existing != null) {
            throw new BusinessException("该销售订单已生成应收单");
        }

        // 通过customerId查询客户名
        String customerName = "未知客户";
        if (order.getCustomerId() != null) {
            Customer customer = customerService.getById(order.getCustomerId());
            if (customer != null) customerName = customer.getCustomerName();
        }

        AccountsReceivable ar = new AccountsReceivable();
        ar.setArNo(generateArNo());
        ar.setArDate(LocalDate.now());
        ar.setCustomerId(order.getCustomerId());
        ar.setCustomerName(customerName);
        ar.setCurrencyId(order.getCurrencyId());
        ar.setExchangeRate(order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE);
        ar.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        ar.setTaxAmount(order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO);
        ar.setNetAmount(order.getNetAmount() != null ? order.getNetAmount() : BigDecimal.ZERO);
        ar.setPaidAmount(BigDecimal.ZERO);
        ar.setBalanceAmount(ar.getTotalAmount());
        ar.setDueDate(order.getDeliveryDate());
        ar.setPaymentTermId(order.getPaymentTermId());
        ar.setSourceBillType("销售订单");
        ar.setSourceBillNo(order.getOrderNo());
        ar.setSalerId(order.getSalerId());
        ar.setIsAutoGen((short) 1);
        ar.setIsClosed((short) 0);
        ar.setStatus("待收款");
        ar.setApprovalStatus("已审核");

        arMapper.insert(ar);
        log.info("应收账款生成: {} 来源: {} 金额: {}", ar.getArNo(), order.getOrderNo(), ar.getTotalAmount());
        return ar;
    }

    @Override
    @Transactional
    public void receivePayment(Long arId, BigDecimal amount, Long settleModeId, String documentNo, String operator) {
        AccountsReceivable ar = arMapper.selectById(arId);
        if (ar == null) throw new BusinessException("应收单不存在");

        BigDecimal newReceived = ar.getPaidAmount().add(amount);
        ar.setPaidAmount(newReceived);
        ar.setBalanceAmount(ar.getTotalAmount().subtract(newReceived));

        if (newReceived.compareTo(ar.getTotalAmount()) >= 0) {
            ar.setStatus("已结清");
            ar.setIsClosed((short) 1);
        } else if (newReceived.compareTo(BigDecimal.ZERO) > 0) {
            ar.setStatus("部分收款");
        }

        arMapper.updateById(ar);

        // 生成收款记录
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setReceiptNo(generateReceiptNo());
        receipt.setReceiptDate(LocalDate.now());
        receipt.setCustomerId(ar.getCustomerId());
        receipt.setCustomerName(ar.getCustomerName());
        receipt.setSettleModeId(settleModeId);
        receipt.setCurrencyId(ar.getCurrencyId());
        receipt.setExchangeRate(ar.getExchangeRate());
        receipt.setTotalAmount(ar.getTotalAmount());
        receipt.setAmount(amount);
        receipt.setDiscountAmount(BigDecimal.ZERO);
        receipt.setReceivedAmount(newReceived);
        receipt.setDocumentNo(documentNo);
        receipt.setSourceBillType("应收单");
        receipt.setSourceBillNo(ar.getArNo());
        receipt.setStatus("已确认");
        receipt.setApprovalStatus("已审核");
        receiptMapper.insert(receipt);

        log.info("收款登记: {} 金额: {} 累计: {}", ar.getArNo(), amount, newReceived);
    }

    @Override
    public PageResult<AccountsReceivable> pageAr(int current, int size, String customerName, String status, String arNo) {
        QueryWrapper<AccountsReceivable> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (customerName != null && !customerName.isBlank()) wrapper.like("customer_name", customerName);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        if (arNo != null && !arNo.isBlank()) wrapper.like("ar_no", arNo);
        Page<AccountsReceivable> page = new Page<>(current, size);
        Page<AccountsReceivable> result = arMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public AccountsReceivable getArDetail(Long id) {
        return arMapper.selectById(id);
    }

    // ========== 应付账款 ==========

    @Override
    @Transactional
    public AccountsPayable createApFromPurchaseOrder(Long purchaseOrderId) {
        PurchaseOrder order = purchaseOrderService.getDetail(purchaseOrderId);
        if (order == null) throw new BusinessException("采购订单不存在");

        // 检查是否已生成应付
        AccountsPayable existing = apMapper.selectOne(
                new QueryWrapper<AccountsPayable>().eq("source_bill_no", order.getOrderNo()).eq("source_bill_type", "采购订单"));
        if (existing != null) {
            throw new BusinessException("该采购订单已生成应付单");
        }

        // 通过supplierId查询供应商名
        String supplierName = "未知供应商";
        if (order.getSupplierId() != null) {
            Supplier supplier = supplierService.getById(order.getSupplierId());
            if (supplier != null) supplierName = supplier.getSupplierName();
        }

        AccountsPayable ap = new AccountsPayable();
        ap.setApNo(generateApNo());
        ap.setApDate(LocalDate.now());
        ap.setSupplierId(order.getSupplierId());
        ap.setSupplierName(supplierName);
        ap.setCurrencyId(order.getCurrencyId());
        ap.setExchangeRate(order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE);
        ap.setTotalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        ap.setTaxAmount(order.getTaxAmount() != null ? order.getTaxAmount() : BigDecimal.ZERO);
        ap.setNetAmount(order.getNetAmount() != null ? order.getNetAmount() : BigDecimal.ZERO);
        ap.setPaidAmount(BigDecimal.ZERO);
        ap.setBalanceAmount(ap.getTotalAmount());
        ap.setDueDate(order.getDeliveryDate());
        ap.setPaymentTermId(order.getPaymentTermId());
        ap.setSourceBillType("采购订单");
        ap.setSourceBillNo(order.getOrderNo());
        ap.setBuyerId(order.getBuyerId());
        ap.setIsAutoGen((short) 1);
        ap.setIsClosed((short) 0);
        ap.setStatus("待付款");
        ap.setApprovalStatus("已审核");

        apMapper.insert(ap);
        log.info("应付账款生成: {} 来源: {} 金额: {}", ap.getApNo(), order.getOrderNo(), ap.getTotalAmount());
        return ap;
    }

    @Override
    @Transactional
    public void makePayment(Long apId, BigDecimal amount, Long settleModeId, String documentNo, String operator) {
        AccountsPayable ap = apMapper.selectById(apId);
        if (ap == null) throw new BusinessException("应付单不存在");

        BigDecimal newPaid = ap.getPaidAmount().add(amount);
        ap.setPaidAmount(newPaid);
        ap.setBalanceAmount(ap.getTotalAmount().subtract(newPaid));

        if (newPaid.compareTo(ap.getTotalAmount()) >= 0) {
            ap.setStatus("已结清");
            ap.setIsClosed((short) 1);
        } else if (newPaid.compareTo(BigDecimal.ZERO) > 0) {
            ap.setStatus("部分付款");
        }

        apMapper.updateById(ap);

        // 生成付款记录
        PaymentVoucher voucher = new PaymentVoucher();
        voucher.setPaymentNo(generateVoucherNo());
        voucher.setPaymentDate(LocalDate.now());
        voucher.setSupplierId(ap.getSupplierId());
        voucher.setSupplierName(ap.getSupplierName());
        voucher.setSettleModeId(settleModeId);
        voucher.setCurrencyId(ap.getCurrencyId());
        voucher.setExchangeRate(ap.getExchangeRate());
        voucher.setTotalAmount(ap.getTotalAmount());
        voucher.setAmount(amount);
        voucher.setDiscountAmount(BigDecimal.ZERO);
        voucher.setPaidAmount(newPaid);
        voucher.setDocumentNo(documentNo);
        voucher.setSourceBillType("应付单");
        voucher.setSourceBillNo(ap.getApNo());
        voucher.setStatus("已确认");
        voucher.setApprovalStatus("已审核");
        voucherMapper.insert(voucher);

        log.info("付款登记: {} 金额: {} 累计: {}", ap.getApNo(), amount, newPaid);
    }

    @Override
    public PageResult<AccountsPayable> pageAp(int current, int size, String supplierName, String status, String apNo) {
        QueryWrapper<AccountsPayable> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (supplierName != null && !supplierName.isBlank()) wrapper.like("supplier_name", supplierName);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        if (apNo != null && !apNo.isBlank()) wrapper.like("ap_no", apNo);
        Page<AccountsPayable> page = new Page<>(current, size);
        Page<AccountsPayable> result = apMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    public AccountsPayable getApDetail(Long id) {
        return apMapper.selectById(id);
    }

    // ========== 收款记录 ==========

    @Override
    @Transactional
    public PaymentReceipt createReceipt(PaymentReceipt receipt) {
        if (receipt.getReceiptNo() == null || receipt.getReceiptNo().isBlank()) {
            receipt.setReceiptNo(generateReceiptNo());
        }
        if (receipt.getStatus() == null) receipt.setStatus("待确认");
        if (receipt.getApprovalStatus() == null) receipt.setApprovalStatus("待审核");
        if (receipt.getReceiptDate() == null) receipt.setReceiptDate(LocalDate.now());
        if (receipt.getExchangeRate() == null) receipt.setExchangeRate(BigDecimal.ONE);
        if (receipt.getDiscountAmount() == null) receipt.setDiscountAmount(BigDecimal.ZERO);
        receiptMapper.insert(receipt);
        return receipt;
    }

    @Override
    public PageResult<PaymentReceipt> pageReceipt(int current, int size, String customerName, String status, String receiptNo) {
        QueryWrapper<PaymentReceipt> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (customerName != null && !customerName.isBlank()) wrapper.like("customer_name", customerName);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        if (receiptNo != null && !receiptNo.isBlank()) wrapper.like("receipt_no", receiptNo);
        Page<PaymentReceipt> page = new Page<>(current, size);
        Page<PaymentReceipt> result = receiptMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    @Transactional
    public void confirmReceipt(Long id, String confirmedBy) {
        PaymentReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) throw new BusinessException("收款记录不存在");
        if (!"待确认".equals(receipt.getStatus())) {
            throw new BusinessException("只有待确认状态可确认");
        }

        receipt.setStatus("已确认");
        receipt.setApprovalStatus("已审核");
        receipt.setApprovedBy(confirmedBy);
        receipt.setApprovedAt(java.time.LocalDateTime.now());
        receiptMapper.updateById(receipt);

        // 若关联应收单，更新应收
        if (receipt.getSourceBillNo() != null && "应收单".equals(receipt.getSourceBillType())) {
            AccountsReceivable ar = arMapper.selectOne(new QueryWrapper<AccountsReceivable>().eq("ar_no", receipt.getSourceBillNo()));
            if (ar != null) {
                receivePayment(ar.getArId(), receipt.getAmount(), receipt.getSettleModeId(), receipt.getDocumentNo(), confirmedBy);
            }
        }

        log.info("收款确认: {}", receipt.getReceiptNo());
    }

    // ========== 付款记录 ==========

    @Override
    @Transactional
    public PaymentVoucher createVoucher(PaymentVoucher voucher) {
        if (voucher.getPaymentNo() == null || voucher.getPaymentNo().isBlank()) {
            voucher.setPaymentNo(generateVoucherNo());
        }
        if (voucher.getStatus() == null) voucher.setStatus("待确认");
        if (voucher.getApprovalStatus() == null) voucher.setApprovalStatus("待审核");
        if (voucher.getPaymentDate() == null) voucher.setPaymentDate(LocalDate.now());
        if (voucher.getExchangeRate() == null) voucher.setExchangeRate(BigDecimal.ONE);
        if (voucher.getDiscountAmount() == null) voucher.setDiscountAmount(BigDecimal.ZERO);
        voucherMapper.insert(voucher);
        return voucher;
    }

    @Override
    public PageResult<PaymentVoucher> pageVoucher(int current, int size, String supplierName, String status, String voucherNo) {
        QueryWrapper<PaymentVoucher> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (supplierName != null && !supplierName.isBlank()) wrapper.like("supplier_name", supplierName);
        if (status != null && !status.isBlank()) wrapper.eq("status", status);
        if (voucherNo != null && !voucherNo.isBlank()) wrapper.like("payment_no", voucherNo);
        Page<PaymentVoucher> page = new Page<>(current, size);
        Page<PaymentVoucher> result = voucherMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), current, size);
    }

    @Override
    @Transactional
    public void confirmVoucher(Long id, String confirmedBy) {
        PaymentVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("付款记录不存在");
        if (!"待确认".equals(voucher.getStatus())) {
            throw new BusinessException("只有待确认状态可确认");
        }

        voucher.setStatus("已确认");
        voucher.setApprovalStatus("已审核");
        voucher.setApprovedBy(confirmedBy);
        voucher.setApprovedAt(java.time.LocalDateTime.now());
        voucherMapper.updateById(voucher);

        // 若关联应付单，更新应付
        if (voucher.getSourceBillNo() != null && "应付单".equals(voucher.getSourceBillType())) {
            AccountsPayable ap = apMapper.selectOne(new QueryWrapper<AccountsPayable>().eq("ap_no", voucher.getSourceBillNo()));
            if (ap != null) {
                makePayment(ap.getApId(), voucher.getAmount(), voucher.getSettleModeId(), voucher.getDocumentNo(), confirmedBy);
            }
        }

        log.info("付款确认: {}", voucher.getPaymentNo());
    }

    // ========== 财务报表 ==========

    @Override
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();

        List<AccountsReceivable> arList = arMapper.selectList(new QueryWrapper<>());
        BigDecimal totalAr = BigDecimal.ZERO;
        BigDecimal receivedAr = BigDecimal.ZERO;
        int arCount = 0;
        int overdueCount = 0;
        for (AccountsReceivable ar : arList) {
            totalAr = totalAr.add(ar.getTotalAmount() != null ? ar.getTotalAmount() : BigDecimal.ZERO);
            receivedAr = receivedAr.add(ar.getPaidAmount() != null ? ar.getPaidAmount() : BigDecimal.ZERO);
            arCount++;
            if (ar.getDueDate() != null && ar.getDueDate().isBefore(LocalDate.now())
                    && !"已结清".equals(ar.getStatus())) {
                overdueCount++;
            }
        }

        List<AccountsPayable> apList = apMapper.selectList(new QueryWrapper<>());
        BigDecimal totalAp = BigDecimal.ZERO;
        BigDecimal paidAp = BigDecimal.ZERO;
        int apCount = 0;
        for (AccountsPayable ap : apList) {
            totalAp = totalAp.add(ap.getTotalAmount() != null ? ap.getTotalAmount() : BigDecimal.ZERO);
            paidAp = paidAp.add(ap.getPaidAmount() != null ? ap.getPaidAmount() : BigDecimal.ZERO);
            apCount++;
        }

        summary.put("totalAr", totalAr);
        summary.put("balanceAr", totalAr.subtract(receivedAr));
        summary.put("receivedAr", receivedAr);
        summary.put("arCount", arCount);
        summary.put("overdueArCount", overdueCount);

        summary.put("totalAp", totalAp);
        summary.put("balanceAp", totalAp.subtract(paidAp));
        summary.put("paidAp", paidAp);
        summary.put("apCount", apCount);

        summary.put("netReceivable", totalAr.subtract(receivedAr).subtract(totalAp.subtract(paidAp)));

        return summary;
    }

    @Override
    public List<Map<String, Object>> getAgingAnalysis() {
        List<Map<String, Object>> result = new ArrayList<>();

        QueryWrapper<AccountsReceivable> arWrapper = new QueryWrapper<>();
        arWrapper.ne("status", "已结清");
        List<AccountsReceivable> arList = arMapper.selectList(arWrapper);

        int bucket0_30 = 0, bucket30_60 = 0, bucket60_90 = 0, bucket90_180 = 0, bucket180_plus = 0;
        BigDecimal amt0_30 = BigDecimal.ZERO, amt30_60 = BigDecimal.ZERO, amt60_90 = BigDecimal.ZERO,
                  amt90_180 = BigDecimal.ZERO, amt180_plus = BigDecimal.ZERO;

        for (AccountsReceivable ar : arList) {
            if (ar.getDueDate() == null) continue;
            long days = java.time.temporal.ChronoUnit.DAYS.between(ar.getDueDate(), LocalDate.now());
            BigDecimal bal = ar.getBalanceAmount() != null ? ar.getBalanceAmount() : BigDecimal.ZERO;

            if (days <= 0) {
                amt0_30 = amt0_30.add(bal);
                bucket0_30++;
            } else if (days <= 30) {
                amt30_60 = amt30_60.add(bal);
                bucket30_60++;
            } else if (days <= 60) {
                amt60_90 = amt60_90.add(bal);
                bucket60_90++;
            } else if (days <= 180) {
                amt90_180 = amt90_180.add(bal);
                bucket90_180++;
            } else {
                amt180_plus = amt180_plus.add(bal);
                bucket180_plus++;
            }
        }

        result.add(Map.of("bucket", "1-30天", "count", bucket0_30, "amount", amt0_30));
        result.add(Map.of("bucket", "31-60天", "count", bucket30_60, "amount", amt30_60));
        result.add(Map.of("bucket", "61-90天", "count", bucket60_90, "amount", amt60_90));
        result.add(Map.of("bucket", "91-180天", "count", bucket90_180, "amount", amt90_180));
        result.add(Map.of("bucket", "180天以上", "count", bucket180_plus, "amount", amt180_plus));

        return result;
    }

    // ========== 单号生成 ==========

    private String generateArNo() {
        String prefix = "AR" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<AccountsReceivable> wrapper = new QueryWrapper<>();
        wrapper.likeRight("ar_no", prefix).orderByDesc("ar_no").last("LIMIT 1");
        AccountsReceivable last = arMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null && last.getArNo() != null) {
            try { seq = Integer.parseInt(last.getArNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
        }
        return prefix + String.format("%04d", seq);
    }

    private String generateApNo() {
        String prefix = "AP" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<AccountsPayable> wrapper = new QueryWrapper<>();
        wrapper.likeRight("ap_no", prefix).orderByDesc("ap_no").last("LIMIT 1");
        AccountsPayable last = apMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null && last.getApNo() != null) {
            try { seq = Integer.parseInt(last.getApNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
        }
        return prefix + String.format("%04d", seq);
    }

    private String generateReceiptNo() {
        String prefix = "REC" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<PaymentReceipt> wrapper = new QueryWrapper<>();
        wrapper.likeRight("receipt_no", prefix).orderByDesc("receipt_no").last("LIMIT 1");
        PaymentReceipt last = receiptMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null && last.getReceiptNo() != null) {
            try { seq = Integer.parseInt(last.getReceiptNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
        }
        return prefix + String.format("%04d", seq);
    }

    private String generateVoucherNo() {
        String prefix = "PAY" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        QueryWrapper<PaymentVoucher> wrapper = new QueryWrapper<>();
        wrapper.likeRight("payment_no", prefix).orderByDesc("payment_no").last("LIMIT 1");
        PaymentVoucher last = voucherMapper.selectOne(wrapper);
        int seq = 1;
        if (last != null && last.getPaymentNo() != null) {
            try { seq = Integer.parseInt(last.getPaymentNo().substring(prefix.length())) + 1; } catch (Exception ignored) {}
        }
        return prefix + String.format("%04d", seq);
    }
}
