package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.finance.entity.BillWriteoffDetail;
import com.erp.finance.entity.PaymentVoucher;
import com.erp.finance.entity.PaymentVoucherItem;
import com.erp.finance.mapper.BillWriteoffDetailMapper;
import com.erp.finance.mapper.PaymentVoucherItemMapper;
import com.erp.finance.mapper.PaymentVoucherMapper;
import com.erp.finance.service.FinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 付款单明细行（付款明细）适配器（2026-09-29 通用插件适配：财务模块第 2 种单据）。
 *
 * <p>保存链路 = {@code FinanceService.createVoucher(voucher)} —— 与付款单编辑页点"保存"完全同一条路：
 * 更新主表 + 先删后插付款明细 + 同步核销来源明细（bill_writeoff_detail），拦"非草稿不可改 / 已结案不可改"。
 *
 * <p><b>必须整单带回核销明细</b>：整单保存会"先删核销明细再按传入集合重插"，若不带回核销明细，
 * 草稿上已录的核销来源会被删掉且不重插（数据丢失）。故 {@link #loadBill} 同时装载付款明细与核销明细。
 *
 * <p><b>可改字段只有 1 个</b>：金额（付款明细行的实付金额，编辑页"金额"列可直接录入）。
 *
 * <p>明细主键 = {@code itemId}（列 item_id）；行序字段 = {@code sortNo}（列 sort_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentVoucherItemHandler implements BillItemHandler {

    private final FinanceService financeService;
    private final PaymentVoucherMapper paymentVoucherMapper;
    private final PaymentVoucherItemMapper paymentVoucherItemMapper;
    private final BillWriteoffDetailMapper billWriteoffDetailMapper;

    @Override
    public String billType() {
        return "PAYMENT_VOUCHER";
    }

    @Override
    public String itemLabel() {
        return "付款单付款明细";
    }

    @Override
    public String itemIdField() {
        return "itemId";
    }

    @Override
    public String lineNoField() {
        return "sortNo";
    }

    @Override
    public List<String> editableFields() {
        return List.of("amount");
    }

    @Override
    public Object loadBill(Long billId) {
        PaymentVoucher v = paymentVoucherMapper.selectById(billId);
        if (v == null) {
            return null;
        }
        // 必须带回核销明细，否则整单保存会把已录核销来源删掉且不重插
        v.setWriteOffItems(billWriteoffDetailMapper.selectList(new QueryWrapper<BillWriteoffDetail>()
                .eq("bill_type", "付款单").eq("bill_id", billId).orderByAsc("writeoff_id")));
        return v;
    }

    @Override
    public List<?> loadItems(Long billId) {
        return paymentVoucherItemMapper.selectList(new QueryWrapper<PaymentVoucherItem>()
                .eq("payment_id", billId)
                .orderByAsc("sort_no", "item_id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof PaymentVoucher v ? v.getPaymentNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        PaymentVoucher v = (PaymentVoucher) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", v.getPaymentNo());
        m.put("供应商", v.getSupplierName());
        m.put("状态", v.getStatus());
        m.put("付款金额", v.getAmount());
        m.put("合计金额", v.getAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof PaymentVoucherItem it ? it.getItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof PaymentVoucherItem it ? it.getSortNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        PaymentVoucherItem it = (PaymentVoucherItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getSortNo() != null ? it.getSortNo() : fallbackLineNo);
        m.put("明细行ID", it.getItemId());
        m.put("账户", it.getAccountName());
        m.put("银行账号", it.getBankAccount() != null ? it.getBankAccount() : it.getAccountNo());
        m.put("金额", it.getAmount());
        m.put("本位币金额", it.getBaseAmount());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        PaymentVoucherItem it = (PaymentVoucherItem) item;
        if (patch.containsKey("amount")) {
            it.setAmount(toDecimal(patch.get("amount"), "金额"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        PaymentVoucher v = (PaymentVoucher) bill;
        v.setItems((List<PaymentVoucherItem>) items);
        financeService.createVoucher(v);
    }

    /** 数值解析：允许 0，不允许负数/空/非数字 */
    private BigDecimal toDecimal(Object v, String fieldCn) {
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException(fieldCn + "不能为空");
        }
        BigDecimal d;
        try {
            d = new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldCn + "必须是数字，收到：" + v);
        }
        if (d.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldCn + "不能为负数，收到：" + v);
        }
        return d;
    }
}
