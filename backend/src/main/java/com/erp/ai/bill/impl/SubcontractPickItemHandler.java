package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.subcontract.pick.entity.SubcontractPick;
import com.erp.subcontract.pick.entity.SubcontractPickItem;
import com.erp.subcontract.pick.mapper.SubcontractPickItemMapper;
import com.erp.subcontract.pick.service.SubcontractPickService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 委外发料单明细行适配器（2026-09-29 通用插件适配：委外模块第 2 种单据）。
 *
 * <p>保存链路 = {@code SubcontractPickService.savePickWithItems(pick, items)} ——
 * 与委外发料单编辑页点"保存"完全同一条路：重算表头发料总数量，拦"已生效/已出库不可改"。
 *
 * <p><b>可改字段只有 1 个</b>：数量。该明细实体没有单价、金额、税率列（已核实 subcontract_pick_item），
 * 数量是唯一对表头发料总数量有影响的业务列。
 *
 * <p>明细主键 = {@code id}（列 id）；该表无行序字段（行序按主键升序）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubcontractPickItemHandler implements BillItemHandler {

    private final SubcontractPickService subcontractPickService;
    private final SubcontractPickItemMapper subcontractPickItemMapper;

    @Override
    public String billType() {
        return "SUBCONTRACT_DELIVERY";
    }

    @Override
    public String itemLabel() {
        return "委外发料单明细";
    }

    @Override
    public String itemIdField() {
        return "id";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity");
    }

    @Override
    public Object loadBill(Long billId) {
        return subcontractPickService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return subcontractPickItemMapper.selectList(new QueryWrapper<SubcontractPickItem>()
                .eq("pick_id", billId)
                .orderByAsc("id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SubcontractPick p ? p.getPickNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SubcontractPick p = (SubcontractPick) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", p.getPickNo());
        m.put("供应商", p.getSupplierName());
        m.put("状态", p.getStatus());
        m.put("合计数量", p.getTotalQuantity());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SubcontractPickItem it ? it.getId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SubcontractPickItem it = (SubcontractPickItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", fallbackLineNo);
        m.put("明细行ID", it.getId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("单位", it.getUnit());
        m.put("数量", it.getQuantity());
        m.put("仓库", it.getWarehouseName());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        SubcontractPickItem it = (SubcontractPickItem) item;
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        subcontractPickService.savePickWithItems((SubcontractPick) bill, (List<SubcontractPickItem>) items);
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
