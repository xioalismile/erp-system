package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.finance.prepayment.purchase.entity.PurchasePrePayment;
import com.erp.finance.prepayment.purchase.entity.PurchasePrePaymentItem;
import com.erp.finance.prepayment.purchase.mapper.PurchasePrePaymentItemMapper;
import com.erp.finance.prepayment.purchase.service.PurchasePrePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购预付款单明细行（付款明细）适配器（2026-09-29 通用插件适配：财务模块第 4 种单据）。
 *
 * <p>保存链路 = {@code PurchasePrePaymentService.savePrePayment(prePayment)} —— 与采购预付款单编辑页点"保存"完全同一条路：
 * 更新主表（重算剩余金额/本位币金额）+ 先删后插付款明细，拦"只有草稿可改"。
 *
 * <p><b>只读/只改「付款明细」</b>：该单另有一类「核销明细」（source_type 非空，用于冲抵订单/应付），
 * 整单保存的删除只作用于 source_type 为空的付款明细，核销明细不受影响；本适配器读明细时也按同一口径过滤。
 *
 * <p><b>可改字段 2 个</b>（按编辑页明细行真实可录入列核实）：付款金额、退回金额。已核金额/本位币金额等派生列不开放。
 *
 * <p>明细主键 = {@code id}（列 id）；该表无行序字段（行序按主键升序）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchasePrePaymentItemHandler implements BillItemHandler {

    private final PurchasePrePaymentService purchasePrePaymentService;
    private final PurchasePrePaymentItemMapper purchasePrePaymentItemMapper;

    @Override
    public String billType() {
        return "PURCHASE_PRE_PAYMENT";
    }

    @Override
    public String itemLabel() {
        return "采购预付款单付款明细";
    }

    @Override
    public String itemIdField() {
        return "id";
    }

    @Override
    public List<String> editableFields() {
        return List.of("amount", "returnAmount");
    }

    @Override
    public Object loadBill(Long billId) {
        return purchasePrePaymentService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return purchasePrePaymentItemMapper.selectList(new QueryWrapper<PurchasePrePaymentItem>()
                .eq("pre_payment_id", billId)
                // 只回显付款明细（source_type 为空），排除核销明细
                .and(w -> w.isNull("source_type").or().eq("source_type", ""))
                .orderByAsc("id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof PurchasePrePayment p ? p.getPaymentNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        PurchasePrePayment p = (PurchasePrePayment) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", p.getPaymentNo());
        m.put("供应商", p.getSupplierName());
        m.put("状态", p.getStatus());
        m.put("合计金额", p.getAmount());
        m.put("剩余金额", p.getRemainAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof PurchasePrePaymentItem it ? it.getId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        PurchasePrePaymentItem it = (PurchasePrePaymentItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", fallbackLineNo);
        m.put("明细行ID", it.getId());
        m.put("账户", it.getAccountName());
        m.put("银行账号", it.getAccountNo());
        m.put("金额", it.getAmount());
        m.put("退回金额", it.getReturnAmount());
        m.put("已核金额", it.getUsedAmount());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        PurchasePrePaymentItem it = (PurchasePrePaymentItem) item;
        if (patch.containsKey("amount")) {
            it.setAmount(toDecimal(patch.get("amount"), "金额"));
        }
        if (patch.containsKey("returnAmount")) {
            it.setReturnAmount(toDecimal(patch.get("returnAmount"), "退回金额"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        PurchasePrePayment p = (PurchasePrePayment) bill;
        p.setItems((List<PurchasePrePaymentItem>) items);
        purchasePrePaymentService.savePrePayment(p);
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
