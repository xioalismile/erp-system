package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.purchase.entity.PurchaseInvoice;
import com.erp.purchase.entity.PurchaseInvoiceItem;
import com.erp.purchase.mapper.PurchaseInvoiceItemMapper;
import com.erp.purchase.service.PurchaseInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购发票明细行适配器（2026-09-28 通用插件适配：采购模块第 6 种单据）。
 *
 * <p>保存链路 = {@code PurchaseInvoiceService.saveInvoiceWithItems(invoice, items)} ——
 * 与采购发票编辑页点"保存"完全同一条路：重算金额/税额/价税合计、跑设置中心各项校验、
 * 拦"已生效不可改"。
 *
 * <p><b>可改字段只有 3 个</b>：单价/数量/税率。采购发票明细**没有折扣率列**
 * （已核实实体注解 purchase_invoice_item：quantity/unit_price/amount/tax_rate/tax_amount），
 * 故不开放折扣率。
 *
 * <p>明细主键 = {@code invoiceItemId}（列 invoice_item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseInvoiceItemHandler implements BillItemHandler {

    private final PurchaseInvoiceService purchaseInvoiceService;
    private final PurchaseInvoiceItemMapper purchaseInvoiceItemMapper;

    @Override
    public String billType() {
        return "PURCHASE_INVOICE";
    }

    @Override
    public String itemLabel() {
        return "采购发票明细";
    }

    @Override
    public String itemIdField() {
        return "invoiceItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("unitPrice", "quantity", "taxRate");
    }

    @Override
    public Object loadBill(Long billId) {
        return purchaseInvoiceService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return purchaseInvoiceItemMapper.selectList(new QueryWrapper<PurchaseInvoiceItem>()
                .eq("invoice_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof PurchaseInvoice i ? i.getInvoiceNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        PurchaseInvoice i = (PurchaseInvoice) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", i.getInvoiceNo());
        m.put("供应商", i.getSupplierName());
        m.put("状态", i.getStatus());
        m.put("合计金额", i.getAmount());
        m.put("税额", i.getTaxAmount());
        m.put("价税合计", i.getTaxTotalAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof PurchaseInvoiceItem it ? it.getInvoiceItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof PurchaseInvoiceItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        PurchaseInvoiceItem it = (PurchaseInvoiceItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getInvoiceItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getModel());
        m.put("数量", it.getQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("税率", it.getTaxRate());
        m.put("金额", it.getAmount());
        m.put("税额", it.getTaxAmount());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        PurchaseInvoiceItem it = (PurchaseInvoiceItem) item;
        if (patch.containsKey("unitPrice")) {
            it.setUnitPrice(toDecimal(patch.get("unitPrice"), "单价"));
        }
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
        if (patch.containsKey("taxRate")) {
            it.setTaxRate(toDecimal(patch.get("taxRate"), "税率"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        purchaseInvoiceService.saveInvoiceWithItems((PurchaseInvoice) bill, (List<PurchaseInvoiceItem>) items);
    }

    /** 数值解析：允许 0，不允许负数/空/非数字（金额类不在这里改，交给保存链路重算） */
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
