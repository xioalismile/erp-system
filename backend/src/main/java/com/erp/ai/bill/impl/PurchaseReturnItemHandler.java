package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.purchase.entity.PurchaseReturn;
import com.erp.purchase.entity.PurchaseReturnItem;
import com.erp.purchase.mapper.PurchaseReturnItemMapper;
import com.erp.purchase.service.PurchaseReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购退货单明细行适配器（2026-09-28 通用插件适配：采购模块第 5 种单据）。
 *
 * <p>保存链路 = {@code PurchaseReturnService.saveReturnWithItems(returnBill, items)} ——
 * 与采购退货单编辑页点"保存"完全同一条路：重算金额/退货合计、跑设置中心各项校验
 * （单据号查重、锁单据日期）、拦"已生效不可改"。退货单涉及库存回冲，走原 Service 尤为重要。
 *
 * <p><b>可改字段只有 2 个</b>：单价/数量。采购退货单明细**没有税率、折扣率列**
 * （已核实实体注解 purchase_return_item，仅有 quantity/unit_price/amount），故不开放这两项。
 *
 * <p>明细主键 = {@code returnItemId}（列 return_item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseReturnItemHandler implements BillItemHandler {

    private final PurchaseReturnService purchaseReturnService;
    private final PurchaseReturnItemMapper purchaseReturnItemMapper;

    @Override
    public String billType() {
        return "PURCHASE_RETURN";
    }

    @Override
    public String itemLabel() {
        return "采购退货单明细";
    }

    @Override
    public String itemIdField() {
        return "returnItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("unitPrice", "quantity");
    }

    @Override
    public Object loadBill(Long billId) {
        return purchaseReturnService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return purchaseReturnItemMapper.selectList(new QueryWrapper<PurchaseReturnItem>()
                .eq("return_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof PurchaseReturn r ? r.getReturnNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        PurchaseReturn r = (PurchaseReturn) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", r.getReturnNo());
        m.put("供应商", r.getSupplierName());
        m.put("状态", r.getStatus());
        m.put("合计金额", r.getTotalAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof PurchaseReturnItem it ? it.getReturnItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof PurchaseReturnItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        PurchaseReturnItem it = (PurchaseReturnItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getReturnItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getModel());
        m.put("数量", it.getQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("金额", it.getAmount());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        PurchaseReturnItem it = (PurchaseReturnItem) item;
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
        purchaseReturnService.saveReturnWithItems((PurchaseReturn) bill, (List<PurchaseReturnItem>) items);
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
