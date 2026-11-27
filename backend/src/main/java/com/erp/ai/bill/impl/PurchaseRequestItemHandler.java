package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.purchase.entity.PurchaseRequest;
import com.erp.purchase.entity.PurchaseRequestItem;
import com.erp.purchase.mapper.PurchaseRequestItemMapper;
import com.erp.purchase.service.PurchaseRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购申请单明细行适配器（2026-09-28 通用插件适配：采购模块第 1 种单据）。
 *
 * <p>保存链路 = {@code PurchaseRequestService.saveRequestWithItems(request, items)} ——
 * 与采购申请单编辑页点"保存"完全同一条路：重算申请总数量、拦"待审批/已生效/已关闭不可改"。
 *
 * <p><b>可改字段只有 1 个</b>：数量。采购申请单明细**没有单价、税率、折扣率、金额列**
 * （已核实实体注解 purchase_request_item：只有 quantity/approved_qty 等），
 * 该实体数量 Java 字段名为 {@code applyQty}（数据库列 quantity），适配层对外统一叫 quantity。
 *
 * <p>明细主键 = {@code requestItemId}（列 request_item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseRequestItemHandler implements BillItemHandler {

    private final PurchaseRequestService purchaseRequestService;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;

    @Override
    public String billType() {
        return "PURCHASE_REQUEST";
    }

    @Override
    public String itemLabel() {
        return "采购申请单明细";
    }

    @Override
    public String itemIdField() {
        return "requestItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity");
    }

    @Override
    public Object loadBill(Long billId) {
        return purchaseRequestService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return purchaseRequestItemMapper.selectList(new QueryWrapper<PurchaseRequestItem>()
                .eq("request_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof PurchaseRequest r ? r.getRequestNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        PurchaseRequest r = (PurchaseRequest) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", r.getRequestNo());
        m.put("申请部门", r.getApplyDeptName());
        m.put("申请人", r.getApplierName());
        m.put("状态", r.getStatus());
        m.put("总数量", r.getTotalQty());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof PurchaseRequestItem it ? it.getRequestItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof PurchaseRequestItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        PurchaseRequestItem it = (PurchaseRequestItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getRequestItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getModel());
        m.put("数量", it.getApplyQty());
        m.put("审批数量", it.getApprovedQty());
        m.put("建议供应商", it.getSuggestedSupplierName());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        PurchaseRequestItem it = (PurchaseRequestItem) item;
        if (patch.containsKey("quantity")) {
            it.setApplyQty(toDecimal(patch.get("quantity"), "数量"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        purchaseRequestService.saveRequestWithItems((PurchaseRequest) bill, (List<PurchaseRequestItem>) items);
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
