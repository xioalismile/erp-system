package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.sale.entity.SaleRepair;
import com.erp.sale.entity.SaleRepairItem;
import com.erp.sale.entity.SaleRepairPart;
import com.erp.sale.mapper.SaleRepairItemMapper;
import com.erp.sale.mapper.SaleRepairPartMapper;
import com.erp.sale.service.SaleRepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 售后维修单明细行适配器（2026-11-22 立项二期补登）。
 *
 * <p>两类明细合并展示：客户产品行（{@code SaleRepairItem}，可改）+ 配件领用行
 * （{@code SaleRepairPart}，<b>只读</b>——配件行的明细行ID恒为 null，AI 无法选中改它）。
 *
 * <p>保存链路 = {@code SaleRepairService.saveRepairWithItems(bill, items, parts)} ——
 * 与维修单页面保存同一条路（保修判定、拦"非草稿不可改"）。配件行原样传回（保持不变）。
 *
 * <p>可改字段（仅客户产品行）：数量/工时。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleRepairItemHandler implements BillItemHandler {

    private final SaleRepairService saleRepairService;
    private final SaleRepairItemMapper saleRepairItemMapper;
    private final SaleRepairPartMapper saleRepairPartMapper;

    @Override
    public String billType() {
        return "SALE_REPAIR";
    }

    @Override
    public String itemLabel() {
        return "售后维修单明细";
    }

    @Override
    public String itemIdField() {
        return "repairItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "laborHours");
    }

    @Override
    public Object loadBill(Long billId) {
        return saleRepairService.getDetail(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        List<Object> all = new ArrayList<>();
        all.addAll(saleRepairItemMapper.selectList(new QueryWrapper<SaleRepairItem>()
                .eq("repair_id", billId)
                .orderByAsc("line_no")));
        // 配件领用行并入展示（只读：明细行ID 为 null，AI 无法选中修改）
        all.addAll(saleRepairPartMapper.selectList(new QueryWrapper<SaleRepairPart>()
                .eq("repair_id", billId)
                .orderByAsc("line_no")));
        return all;
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SaleRepair o ? o.getRepairNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SaleRepair o = (SaleRepair) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", o.getRepairNo());
        m.put("客户", o.getCustomerName());
        m.put("状态", o.getStatus());
        m.put("维修费", o.getRepairFee());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        // 配件行恒为 null：不可被 AI 选中修改（只读展示）
        return item instanceof SaleRepairItem it ? it.getRepairItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof SaleRepairItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (item instanceof SaleRepairItem it) {
            m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
            m.put("明细行ID", it.getRepairItemId());
            m.put("行类型", "客户产品");
            m.put("物料编码", it.getMaterialCode());
            m.put("物料名称", it.getMaterialName());
            m.put("规格型号", it.getSpecification());
            m.put("单位", it.getUnit());
            m.put("数量", it.getQuantity());
            m.put("序列号", it.getSerialNo());
            m.put("批号", it.getBatchNo());
            m.put("保修状态", it.getWarrantyStatus());
            m.put("工时", it.getLaborHours());
            m.put("故障描述", it.getFaultDescription());
        } else if (item instanceof SaleRepairPart p) {
            m.put("行号", p.getLineNo() != null ? p.getLineNo() : fallbackLineNo);
            m.put("明细行ID", null);
            m.put("行类型", "配件领用");
            m.put("物料编码", p.getMaterialCode());
            m.put("物料名称", p.getMaterialName());
            m.put("规格型号", p.getSpecification());
            m.put("单位", p.getUnit());
            m.put("数量", p.getQuantity());
            m.put("批号", p.getBatchNo());
            m.put("金额", p.getAmount());
        }
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        if (!(item instanceof SaleRepairItem it)) {
            throw new IllegalArgumentException("配件领用行不支持对话修改，请在售后维修单页面维护");
        }
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
        if (patch.containsKey("laborHours")) {
            it.setLaborHours(toDecimal(patch.get("laborHours"), "工时"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        List<SaleRepairItem> rows = new ArrayList<>();
        List<SaleRepairPart> parts = new ArrayList<>();
        for (Object o : items) {
            if (o instanceof SaleRepairItem it) {
                rows.add(it);
            } else if (o instanceof SaleRepairPart p) {
                parts.add(p);
            }
        }
        saleRepairService.saveRepairWithItems((SaleRepair) bill, rows, parts);
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
