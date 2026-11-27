package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.ai.service.AiCrudAgentService;
import com.erp.sale.entity.SaleInvoice;
import com.erp.sale.entity.SaleInvoiceItem;
import com.erp.sale.mapper.SaleInvoiceItemMapper;
import com.erp.sale.service.SaleInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售发票明细行适配器（2026-09-18 通用插件适配：销售模块第 6 种单据）。
 *
 * <p>保存链路 = {@code SaleInvoiceService.saveInvoiceWithItems(saleInvoice, items)} ——
 * 与发票编辑页点"保存"完全同一条路：重算金额/税额/合计、跑设置中心各项校验、
 * 拦"已生效/已完结不可改"。
 *
 * <p>可改字段只有 4 个（{@link #editableFields()}）：单价/数量/税率/折扣率。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleInvoiceItemHandler implements BillItemHandler {

    private final SaleInvoiceService saleInvoiceService;
    private final SaleInvoiceItemMapper saleInvoiceItemMapper;
    private final AiCrudAgentService aiCrudAgentService;

    @Override
    public String billType() {
        return "SALE_INVOICE";
    }

    @Override
    public String itemLabel() {
        return "销售发票明细";
    }

    @Override
    public String itemIdField() {
        return "invoiceItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("unitPrice", "quantity", "taxRate", "discountRate");
    }

    @Override
    public Object loadBill(Long billId) {
        return saleInvoiceService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return saleInvoiceItemMapper.selectList(new QueryWrapper<SaleInvoiceItem>()
                .eq("invoice_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SaleInvoice i ? i.getInvoiceNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SaleInvoice i = (SaleInvoice) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", i.getInvoiceNo());
        m.put("客户", customerName(bill));
        m.put("状态", i.getStatus());
        m.put("合计金额", i.getTotalAmount());
        m.put("税额", i.getTaxAmount());
        m.put("价税合计", i.getNetAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SaleInvoiceItem it ? it.getInvoiceItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof SaleInvoiceItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SaleInvoiceItem it = (SaleInvoiceItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getInvoiceItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("单位", it.getUnit());
        m.put("数量", it.getQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("折扣率", it.getDiscountRate());
        m.put("税率", it.getTaxRate());
        m.put("金额", it.getAmount());
        m.put("税额", it.getTaxAmount());
        m.put("净额", it.getNetAmount());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        SaleInvoiceItem it = (SaleInvoiceItem) item;
        if (patch.containsKey("unitPrice")) {
            it.setUnitPrice(toDecimal(patch.get("unitPrice"), "单价"));
        }
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
        if (patch.containsKey("taxRate")) {
            it.setTaxRate(toDecimal(patch.get("taxRate"), "税率"));
        }
        if (patch.containsKey("discountRate")) {
            it.setDiscountRate(toDecimal(patch.get("discountRate"), "折扣率"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        saleInvoiceService.saveInvoiceWithItems((SaleInvoice) bill, (List<SaleInvoiceItem>) items);
    }

    /**
     * 客户名：发票实体的 customerName 是 @TableField(exist=false) 展示字段（反射恒为 null），
     * 必须走既有的「外键 → 主档名」回查（单一适配点，避免重复写死表和列）。
     */
    private String customerName(Object bill) {
        try {
            return aiCrudAgentService.foreignDisplayName(bill);
        } catch (Exception e) {
            log.warn("[明细行编辑] 取客户名失败（不影响主流程）: {}", e.getMessage());
            return null;
        }
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
