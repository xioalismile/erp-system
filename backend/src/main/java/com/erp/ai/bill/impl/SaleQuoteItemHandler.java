package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.ai.service.AiCrudAgentService;
import com.erp.sale.entity.SaleQuote;
import com.erp.sale.entity.SaleQuoteItem;
import com.erp.sale.mapper.SaleQuoteItemMapper;
import com.erp.sale.service.SaleQuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售报价单明细行适配器（2026-09-18 通用插件适配：销售模块第 2 种单据）。
 *
 * <p>保存链路 = {@code SaleQuoteService.saveQuoteWithItems(quote, items)} ——
 * 与报价单编辑页点"保存"完全同一条路：重算金额/税额/合计、跑设置中心各项校验、
 * 拦"已生效/已完结不可改"。
 *
 * <p>可改字段只有 4 个（{@link #editableFields()}）：单价/数量/税率/折扣率；
 * 金额、税额、净额等派生列一律由保存链路重算，**禁止 AI 直改**。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleQuoteItemHandler implements BillItemHandler {

    private final SaleQuoteService saleQuoteService;
    private final SaleQuoteItemMapper saleQuoteItemMapper;
    private final AiCrudAgentService aiCrudAgentService;

    @Override
    public String billType() {
        return "SALE_QUOTE";
    }

    @Override
    public String itemLabel() {
        return "销售报价单明细";
    }

    @Override
    public String itemIdField() {
        return "quoteItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("unitPrice", "quantity", "taxRate", "discountRate");
    }

    @Override
    public Object loadBill(Long billId) {
        return saleQuoteService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return saleQuoteItemMapper.selectList(new QueryWrapper<SaleQuoteItem>()
                .eq("quote_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SaleQuote q ? q.getQuoteNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SaleQuote q = (SaleQuote) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", q.getQuoteNo());
        m.put("客户", customerName(bill));
        m.put("状态", q.getStatus());
        m.put("合计金额", q.getTotalAmount());
        m.put("税额", q.getTaxAmount());
        m.put("价税合计", q.getNetAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SaleQuoteItem it ? it.getQuoteItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof SaleQuoteItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SaleQuoteItem it = (SaleQuoteItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getQuoteItemId());
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
        SaleQuoteItem it = (SaleQuoteItem) item;
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
        saleQuoteService.saveQuoteWithItems((SaleQuote) bill, (List<SaleQuoteItem>) items);
    }

    /**
     * 客户名：报价单实体的 customerName 是 @TableField(exist=false) 展示字段（反射恒为 null），
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
