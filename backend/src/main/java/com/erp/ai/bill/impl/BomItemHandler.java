package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.masterdata.entity.Bom;
import com.erp.masterdata.entity.BomItem;
import com.erp.masterdata.mapper.BomItemMapper;
import com.erp.masterdata.service.BomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BOM 子件明细行适配器（2026-11-22 立项二期补登）。
 *
 * <p>保存链路 = {@code BomService.saveBomWithItems(bom, items)} —— 与 BOM 编辑页保存同一条路。
 *
 * <p>可改字段只有 2 个：用量/损耗率。子件编码/名称/供应类型/工序等主档关联字段
 * 不开放（BOM 的成本列由母件成本链路重算，禁止 AI 直改）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BomItemHandler implements BillItemHandler {

    private final BomService bomService;
    private final BomItemMapper bomItemMapper;

    @Override
    public String billType() {
        return "BOM";
    }

    @Override
    public String itemLabel() {
        return "BOM子件明细";
    }

    @Override
    public String itemIdField() {
        return "bomItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "scrapRate");
    }

    @Override
    public Object loadBill(Long billId) {
        return bomService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return bomItemMapper.selectList(new QueryWrapper<BomItem>()
                .eq("bom_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof Bom o ? o.getBomCode() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        Bom o = (Bom) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", o.getBomCode());
        m.put("母件编码", o.getMaterialCode());
        m.put("母件名称", o.getMaterialName());
        m.put("状态", o.getStatus());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof BomItem it ? it.getBomItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof BomItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        BomItem it = (BomItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getBomItemId());
        m.put("子件编码", it.getMaterialCode());
        m.put("子件名称", it.getMaterialName());
        m.put("规格型号", it.getModelNo());
        m.put("用量类型", it.getQtyType());
        m.put("数量", it.getQuantity());
        m.put("损耗率", it.getScrapRate());
        m.put("供应类型", it.getSupplyType());
        m.put("工序号", it.getProcessNo());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        BomItem it = (BomItem) item;
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "用量"));
        }
        if (patch.containsKey("scrapRate")) {
            it.setScrapRate(toDecimal(patch.get("scrapRate"), "损耗率"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        bomService.saveBomWithItems((Bom) bill, (List<BomItem>) items);
    }

    /** 数值解析：允许 0，不允许负数/空/非数字（成本类不在这里改，交给保存链路重算） */
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
