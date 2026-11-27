package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.sale.entity.SaleOrder;
import com.erp.sale.entity.SaleOrderItem;
import com.erp.sale.mapper.SaleOrderItemMapper;
import com.erp.sale.service.SaleOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售订单明细行适配器（首批接入，2026-09-18）。
 *
 * <p>保存链路 = {@code SaleOrderService.saveOrderWithItems(order, items)} ——
 * 与销售订单编辑页点"保存"完全同一条路：重算金额/税额/合计、跑设置中心各项校验
 * （含"允许单价为0"）、拦"已生效/已完结不可改"。
 *
 * <p>可改字段只有 4 个（{@link #editableFields()}）：单价/数量/税率/折扣率；
 * 金额、税额、净额等派生列一律由上面那条保存链路重算，**禁止 AI 直改**。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleOrderItemHandler implements BillItemHandler {

    private final SaleOrderService saleOrderService;
    private final SaleOrderItemMapper saleOrderItemMapper;

    @Override
    public String billType() {
        return "SALE_ORDER";
    }

    @Override
    public String itemLabel() {
        return "销售订单明细";
    }

    @Override
    public String itemIdField() {
        return "orderItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("unitPrice", "quantity", "taxRate", "discountRate");
    }

    @Override
    public Object loadBill(Long billId) {
        return saleOrderService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return saleOrderItemMapper.selectList(new QueryWrapper<SaleOrderItem>()
                .eq("order_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SaleOrder o ? o.getOrderNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SaleOrder o = (SaleOrder) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", o.getOrderNo());
        m.put("客户", o.getCustomerShortName() != null ? o.getCustomerShortName() : o.getCustomerName());
        m.put("状态", o.getStatus());
        m.put("合计金额", o.getTotalAmount());
        m.put("税额", o.getTaxAmount());
        m.put("价税合计", o.getNetAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SaleOrderItem it ? it.getOrderItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof SaleOrderItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SaleOrderItem it = (SaleOrderItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getOrderItemId());
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
        m.put("已出库数量", it.getDeliveredQuantity());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        SaleOrderItem it = (SaleOrderItem) item;
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
    public void save(Object bill, List<?> items) {
        @SuppressWarnings("unchecked")
        List<SaleOrderItem> list = (List<SaleOrderItem>) items;
        saleOrderService.saveOrderWithItems((SaleOrder) bill, list);
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
