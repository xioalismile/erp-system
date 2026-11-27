package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.inventory.entity.OtherReceipt;
import com.erp.inventory.entity.OtherReceiptItem;
import com.erp.inventory.mapper.OtherReceiptItemMapper;
import com.erp.inventory.service.OtherReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 其他入库单明细行适配器（2026-09-29 通用插件适配：库存模块第 3 种单据）。
 *
 * <p>保存链路 = {@code OtherReceiptService.saveReceiptWithItems(receipt, items)} ——
 * 与其他入库单编辑页点"保存"完全同一条路：重算明细金额与入库总额、回填核算成本、
 * 拦"已生效/已关闭/已取消不可改"。
 *
 * <p><b>可改字段只有 2 个</b>：数量/单价。入库明细的金额 = 数量 × 单价（保存链路
 * {@code calculateItemAmount} 只依据这两列），平均成本由核算引擎回填；
 * 金额/税额/净额/折扣金额等派生列一律不开放。
 *
 * <p>明细主键 = {@code receiptItemId}（列 receipt_item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtherReceiptItemHandler implements BillItemHandler {

    private final OtherReceiptService otherReceiptService;
    private final OtherReceiptItemMapper otherReceiptItemMapper;

    @Override
    public String billType() {
        return "OTHER_RECEIPT";
    }

    @Override
    public String itemLabel() {
        return "其他入库单明细";
    }

    @Override
    public String itemIdField() {
        return "receiptItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "unitPrice");
    }

    @Override
    public Object loadBill(Long billId) {
        return otherReceiptService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return otherReceiptItemMapper.selectList(new QueryWrapper<OtherReceiptItem>()
                .eq("receipt_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof OtherReceipt r ? r.getReceiptNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        OtherReceipt r = (OtherReceipt) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", r.getReceiptNo());
        m.put("状态", r.getStatus());
        m.put("入库类型", r.getReceiptType());
        m.put("合计金额", r.getTotalAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof OtherReceiptItem it ? it.getReceiptItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof OtherReceiptItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        OtherReceiptItem it = (OtherReceiptItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getReceiptItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("数量", it.getQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("金额", it.getAmount());
        m.put("平均成本", it.getAvgCost());
        m.put("批号", it.getBatchNo());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        OtherReceiptItem it = (OtherReceiptItem) item;
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
        if (patch.containsKey("unitPrice")) {
            it.setUnitPrice(toDecimal(patch.get("unitPrice"), "单价"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        otherReceiptService.saveReceiptWithItems((OtherReceipt) bill, (List<OtherReceiptItem>) items);
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
