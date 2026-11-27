package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.sale.entity.SaleExchange;
import com.erp.sale.entity.SaleExchangeItem;
import com.erp.sale.mapper.SaleExchangeItemMapper;
import com.erp.sale.service.SaleExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售换货单明细行适配器（2026-11-22 立项二期补登）。
 *
 * <p>保存链路 = {@code SaleExchangeService.saveExchangeWithItems(bill, items)} ——
 * 与换货单编辑页保存同一条路（重算金额/税额/合计、拦"非草稿不可改"）。
 *
 * <p>每行同时带原货品快照（只读）与替换货品；可改字段只有替换货品的 4 个：
 * 单价/数量/税率/折扣率。原货品快照与来源链、待退回数量等一律由业务回写，禁止 AI 直改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleExchangeItemHandler implements BillItemHandler {

    private final SaleExchangeService saleExchangeService;
    private final SaleExchangeItemMapper saleExchangeItemMapper;

    @Override
    public String billType() {
        return "SALE_EXCHANGE";
    }

    @Override
    public String itemLabel() {
        return "销售换货单明细";
    }

    @Override
    public String itemIdField() {
        return "exchangeItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("unitPrice", "quantity", "taxRate", "discountRate");
    }

    @Override
    public Object loadBill(Long billId) {
        return saleExchangeService.getDetail(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return saleExchangeItemMapper.selectList(new QueryWrapper<SaleExchangeItem>()
                .eq("exchange_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SaleExchange o ? o.getExchangeNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SaleExchange o = (SaleExchange) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", o.getExchangeNo());
        m.put("客户", o.getCustomerName());
        m.put("状态", o.getStatus());
        m.put("来源订单", o.getSourceBillNo());
        m.put("合计金额", o.getTotalAmount());
        m.put("价税合计", o.getNetAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SaleExchangeItem it ? it.getExchangeItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof SaleExchangeItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SaleExchangeItem it = (SaleExchangeItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getExchangeItemId());
        m.put("原货品编码", it.getOriginMaterialCode());
        m.put("原货品名称", it.getOriginMaterialName());
        m.put("替换货品编码", it.getMaterialCode());
        m.put("替换货品名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("单位", it.getUnit());
        m.put("数量", it.getQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("税率", it.getTaxRate());
        m.put("金额", it.getAmount());
        m.put("税额", it.getTaxAmount());
        m.put("净额", it.getNetAmount());
        m.put("待退回数量", it.getPendingReturnQuantity());
        m.put("路线", "A".equals(it.getRouteType()) ? "先补发后退货" : ("B".equals(it.getRouteType()) ? "先退货后补发" : null));
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        SaleExchangeItem it = (SaleExchangeItem) item;
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
        saleExchangeService.saveExchangeWithItems((SaleExchange) bill, (List<SaleExchangeItem>) items);
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
