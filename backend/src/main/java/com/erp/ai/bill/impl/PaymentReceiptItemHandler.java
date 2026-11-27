package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.finance.entity.BillWriteoffDetail;
import com.erp.finance.entity.PaymentReceipt;
import com.erp.finance.entity.PaymentReceiptItem;
import com.erp.finance.mapper.BillWriteoffDetailMapper;
import com.erp.finance.mapper.PaymentReceiptItemMapper;
import com.erp.finance.mapper.PaymentReceiptMapper;
import com.erp.finance.service.FinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收款单明细行（收款明细）适配器（2026-09-29 通用插件适配：财务模块第 1 种单据）。
 *
 * <p>保存链路 = {@code FinanceService.createReceipt(receipt)} —— 与收款单编辑页点"保存"完全同一条路：
 * 更新主表 + 先删后插收款明细 + 同步核销来源明细（bill_writeoff_detail），拦"非草稿不可改 / 已结案不可改"。
 *
 * <p><b>必须整单带回核销明细</b>：整单保存会"先删核销明细再按传入集合重插"，若不带回核销明细，
 * 草稿上已录的核销来源会被删掉且不重插（数据丢失）。故 {@link #loadBill} 同时装载收款明细与核销明细。
 *
 * <p><b>可改字段只有 1 个</b>：金额（收款明细行的实收金额，编辑页"金额"列可直接录入，参与实收现金合计）。
 * 账户/银行账号/备注等非金额列不开放（AI 改账户会引入选错账户的风险）。
 *
 * <p>明细主键 = {@code itemId}（列 item_id）；行序字段 = {@code sortNo}（列 sort_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReceiptItemHandler implements BillItemHandler {

    private final FinanceService financeService;
    private final PaymentReceiptMapper paymentReceiptMapper;
    private final PaymentReceiptItemMapper paymentReceiptItemMapper;
    private final BillWriteoffDetailMapper billWriteoffDetailMapper;

    @Override
    public String billType() {
        return "PAYMENT_RECEIPT";
    }

    @Override
    public String itemLabel() {
        return "收款单收款明细";
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
        PaymentReceipt r = paymentReceiptMapper.selectById(billId);
        if (r == null) {
            return null;
        }
        // 必须带回核销明细，否则整单保存会把已录核销来源删掉且不重插
        r.setWriteOffItems(billWriteoffDetailMapper.selectList(new QueryWrapper<BillWriteoffDetail>()
                .eq("bill_type", "收款单").eq("bill_id", billId).orderByAsc("writeoff_id")));
        return r;
    }

    @Override
    public List<?> loadItems(Long billId) {
        return paymentReceiptItemMapper.selectList(new QueryWrapper<PaymentReceiptItem>()
                .eq("receipt_id", billId)
                .orderByAsc("sort_no", "item_id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof PaymentReceipt r ? r.getReceiptNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        PaymentReceipt r = (PaymentReceipt) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", r.getReceiptNo());
        m.put("客户", r.getCustomerName());
        m.put("状态", r.getStatus());
        m.put("收款金额", r.getAmount());
        m.put("合计金额", r.getAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof PaymentReceiptItem it ? it.getItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof PaymentReceiptItem it ? it.getSortNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        PaymentReceiptItem it = (PaymentReceiptItem) item;
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
        PaymentReceiptItem it = (PaymentReceiptItem) item;
        if (patch.containsKey("amount")) {
            it.setAmount(toDecimal(patch.get("amount"), "金额"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        PaymentReceipt r = (PaymentReceipt) bill;
        r.setItems((List<PaymentReceiptItem>) items);
        financeService.createReceipt(r);
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
