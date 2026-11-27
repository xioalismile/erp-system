package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.finance.prepayment.sale.entity.SalePreReceipt;
import com.erp.finance.prepayment.sale.entity.SalePreReceiptItem;
import com.erp.finance.prepayment.sale.mapper.SalePreReceiptItemMapper;
import com.erp.finance.prepayment.sale.service.SalePreReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售预收款单明细行（收款明细）适配器（2026-09-29 通用插件适配：财务模块第 3 种单据）。
 *
 * <p>保存链路 = {@code SalePreReceiptService.savePreReceipt(preReceipt)} —— 与销售预收款单编辑页点"保存"完全同一条路：
 * 更新主表（重算剩余金额/本位币金额）+ 先删后插收款明细，拦"只有草稿可改"。
 *
 * <p><b>只读/只改「收款明细」</b>：该单另有一类「核销明细」（source_type 非空，用于冲抵订单/应收），
 * 整单保存的删除只作用于 source_type 为空的收款明细，核销明细不受影响；本适配器读明细时也按同一口径过滤。
 *
 * <p><b>可改字段 2 个</b>（按编辑页明细行真实可录入列核实）：收款金额、退回金额。已核金额/本位币金额等派生列不开放。
 *
 * <p>明细主键 = {@code id}（列 id）；该表无行序字段（行序按主键升序）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalePreReceiptItemHandler implements BillItemHandler {

    private final SalePreReceiptService salePreReceiptService;
    private final SalePreReceiptItemMapper salePreReceiptItemMapper;

    @Override
    public String billType() {
        return "SALE_PRE_RECEIPT";
    }

    @Override
    public String itemLabel() {
        return "销售预收款单收款明细";
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
        return salePreReceiptService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return salePreReceiptItemMapper.selectList(new QueryWrapper<SalePreReceiptItem>()
                .eq("pre_receipt_id", billId)
                // 只回显收款明细（source_type 为空），排除核销明细
                .and(w -> w.isNull("source_type").or().eq("source_type", ""))
                .orderByAsc("id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SalePreReceipt p ? p.getReceiptNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SalePreReceipt p = (SalePreReceipt) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", p.getReceiptNo());
        m.put("客户", p.getCustomerName());
        m.put("状态", p.getStatus());
        m.put("合计金额", p.getAmount());
        m.put("剩余金额", p.getRemainAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SalePreReceiptItem it ? it.getId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SalePreReceiptItem it = (SalePreReceiptItem) item;
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
        SalePreReceiptItem it = (SalePreReceiptItem) item;
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
        SalePreReceipt p = (SalePreReceipt) bill;
        p.setItems((List<SalePreReceiptItem>) items);
        salePreReceiptService.savePreReceipt(p);
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
