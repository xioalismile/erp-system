package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.sample.entity.SampleOrder;
import com.erp.sample.entity.SampleOrderItem;
import com.erp.sample.mapper.SampleOrderItemMapper;
import com.erp.sample.service.SampleOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 打样单明细行适配器（2026-09-29 通用插件适配：打样模块第 1 种单据）。
 *
 * <p>保存链路 = {@code SampleOrderService.saveSampleWithItems(sample, items)} ——
 * 与打样单编辑页点"保存"完全同一条路：重算子件金额与样品成本、写默认值、
 * 拦"已生效/打样中/已完成/已确认/已转BOM 不可改"。
 *
 * <p><b>可改字段只有 2 个</b>：基本用量（对外统一叫 quantity）、单价。打样子件明细的
 * 金额 = 基本用量 × 单价（保存链路 {@code calculateItemAmount} 只依据这两列）；
 * 金额等派生列一律不开放（该表没有税率/折扣率列）。
 *
 * <p>明细主键 = {@code itemId}（列 item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleOrderItemHandler implements BillItemHandler {

    private final SampleOrderService sampleOrderService;
    private final SampleOrderItemMapper sampleOrderItemMapper;

    @Override
    public String billType() {
        return "SAMPLE_ORDER";
    }

    @Override
    public String itemLabel() {
        return "打样单明细";
    }

    @Override
    public String itemIdField() {
        return "itemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "unitPrice");
    }

    @Override
    public Object loadBill(Long billId) {
        return sampleOrderService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return sampleOrderItemMapper.selectList(new QueryWrapper<SampleOrderItem>()
                .eq("sample_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SampleOrder s ? s.getSampleNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SampleOrder s = (SampleOrder) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", s.getSampleNo());
        m.put("状态", s.getStatus());
        m.put("样品名称", s.getSampleName());
        m.put("合计金额", s.getCostAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SampleOrderItem it ? it.getItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof SampleOrderItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SampleOrderItem it = (SampleOrderItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getItemId());
        m.put("物料编码", it.getChildMaterialCode());
        m.put("物料名称", it.getChildMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("数量", it.getBaseQty());
        m.put("单价", it.getUnitPrice());
        m.put("金额", it.getAmount());
        m.put("损耗率", it.getScrapRate());
        m.put("用量类型", it.getQtyType());
        m.put("供应类型", it.getSupplyType());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        SampleOrderItem it = (SampleOrderItem) item;
        if (patch.containsKey("quantity")) {
            it.setBaseQty(toDecimal(patch.get("quantity"), "基本用量"));
        }
        if (patch.containsKey("unitPrice")) {
            it.setUnitPrice(toDecimal(patch.get("unitPrice"), "单价"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        sampleOrderService.saveSampleWithItems((SampleOrder) bill, (List<SampleOrderItem>) items);
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
