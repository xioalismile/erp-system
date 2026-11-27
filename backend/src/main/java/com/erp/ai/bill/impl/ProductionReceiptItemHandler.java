package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.production.entity.ProductionReceipt;
import com.erp.production.entity.ProductionReceiptItem;
import com.erp.production.mapper.ProductionReceiptItemMapper;
import com.erp.production.service.ProductionReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产入库单明细行适配器（2026-09-29 通用插件适配：生产模块第 2 种单据）。
 *
 * <p>保存链路 = {@code ProductionReceiptService.saveReceiptWithItems(receipt, items)} ——
 * 与生产入库单编辑页点"保存"完全同一条路：重算明细金额（数量×单价）与表头
 * 总金额/总数量/合格数量/报废数量，并按最新核算成本回填平均成本。
 *
 * <p><b>可改字段 4 个</b>（按入库单编辑页真实可录入列逐个核实）：数量、合格数量、不合格数量、单价。
 * 四者对表头均有可见影响：数量/单价 → 明细金额 + 表头总金额/总数量重算；
 * 合格数量/不合格数量 → 表头合格/不合格合计重算。金额与平均成本为派生列（保存链路重算/系统回填），**禁止 AI 直改**。
 *
 * <p>明细主键 = {@code receiptItemId}（列 receipt_item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionReceiptItemHandler implements BillItemHandler {

    private final ProductionReceiptService productionReceiptService;
    private final ProductionReceiptItemMapper productionReceiptItemMapper;

    @Override
    public String billType() {
        return "PRODUCTION_RECEIPT";
    }

    @Override
    public String itemLabel() {
        return "生产入库单明细";
    }

    @Override
    public String itemIdField() {
        return "receiptItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "qualifiedQuantity", "rejectedQuantity", "unitPrice");
    }

    @Override
    public Object loadBill(Long billId) {
        return productionReceiptService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return productionReceiptItemMapper.selectList(new QueryWrapper<ProductionReceiptItem>()
                .eq("receipt_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof ProductionReceipt r ? r.getReceiptNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        ProductionReceipt r = (ProductionReceipt) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", r.getReceiptNo());
        m.put("状态", r.getStatus());
        m.put("工单号", r.getWorkOrderNo());
        m.put("合计数量", r.getTotalQuantity());
        m.put("合计金额", r.getTotalAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof ProductionReceiptItem it ? it.getReceiptItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof ProductionReceiptItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        ProductionReceiptItem it = (ProductionReceiptItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getReceiptItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("数量", it.getQuantity());
        m.put("合格数量", it.getQualifiedQuantity());
        m.put("不合格数量", it.getRejectedQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("金额", it.getAmount());
        m.put("平均成本", it.getAvgCost());
        m.put("批次号", it.getBatchNo());
        m.put("生产日期", it.getProductionDate());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        ProductionReceiptItem it = (ProductionReceiptItem) item;
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
        if (patch.containsKey("qualifiedQuantity")) {
            it.setQualifiedQuantity(toDecimal(patch.get("qualifiedQuantity"), "合格数量"));
        }
        if (patch.containsKey("rejectedQuantity")) {
            it.setRejectedQuantity(toDecimal(patch.get("rejectedQuantity"), "不合格数量"));
        }
        if (patch.containsKey("unitPrice")) {
            it.setUnitPrice(toDecimal(patch.get("unitPrice"), "单价"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        productionReceiptService.saveReceiptWithItems((ProductionReceipt) bill, (List<ProductionReceiptItem>) items);
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
