package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.purchase.entity.PurchaseInbound;
import com.erp.purchase.entity.PurchaseInboundItem;
import com.erp.purchase.mapper.PurchaseInboundItemMapper;
import com.erp.purchase.service.PurchaseInboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购入库单明细行适配器（2026-09-28 通用插件适配：采购模块第 4 种单据）。
 *
 * <p>保存链路 = {@code PurchaseInboundService.saveInboundWithItems(inbound, items)} ——
 * 与采购入库单编辑页点"保存"完全同一条路：重算金额/入库合计、跑设置中心各项校验
 * （单据号查重、锁单据日期、KGW 审批阈值）、拦"已生效不可改"。入库单涉及库存，走原 Service 尤为重要。
 *
 * <p><b>可改字段只有 2 个</b>：单价/数量。采购入库单明细**没有税率、折扣率列**
 * （已核实实体注解 purchase_receipt_item，仅有 quantity/unit_price/amount），故不开放这两项。
 *
 * <p>明细主键 = {@code receiptItemId}（列 receipt_item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseInboundItemHandler implements BillItemHandler {

    private final PurchaseInboundService purchaseInboundService;
    private final PurchaseInboundItemMapper purchaseInboundItemMapper;

    @Override
    public String billType() {
        return "PURCHASE_INBOUND";
    }

    @Override
    public String itemLabel() {
        return "采购入库单明细";
    }

    @Override
    public String itemIdField() {
        return "receiptItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("unitPrice", "quantity");
    }

    @Override
    public Object loadBill(Long billId) {
        return purchaseInboundService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return purchaseInboundItemMapper.selectList(new QueryWrapper<PurchaseInboundItem>()
                .eq("receipt_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof PurchaseInbound b ? b.getReceiptNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        PurchaseInbound b = (PurchaseInbound) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", b.getReceiptNo());
        m.put("供应商", b.getSupplierName());
        m.put("状态", b.getStatus());
        m.put("合计金额", b.getTotalAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof PurchaseInboundItem it ? it.getReceiptItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof PurchaseInboundItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        PurchaseInboundItem it = (PurchaseInboundItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getReceiptItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getModel());
        m.put("数量", it.getQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("金额", it.getAmount());
        m.put("已退货数量", it.getReturnedQuantity());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        PurchaseInboundItem it = (PurchaseInboundItem) item;
        if (patch.containsKey("unitPrice")) {
            it.setUnitPrice(toDecimal(patch.get("unitPrice"), "单价"));
        }
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        purchaseInboundService.saveInboundWithItems((PurchaseInbound) bill, (List<PurchaseInboundItem>) items);
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
