package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.subcontract.supplement.entity.SubcontractSupplement;
import com.erp.subcontract.supplement.entity.SubcontractSupplementItem;
import com.erp.subcontract.supplement.mapper.SubcontractSupplementItemMapper;
import com.erp.subcontract.supplement.service.SubcontractSupplementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 委外补料单明细行适配器（2026-09-29 通用插件适配：委外模块第 5 种单据）。
 *
 * <p>保存链路 = {@code SubcontractSupplementService.saveSupplementWithItems(supplement, items)} ——
 * 与委外补料单编辑页点"保存"完全同一条路：重算表头补料总数量，拦"已生效/已出库不可改"。
 *
 * <p><b>可改字段只有 1 个</b>：数量。该明细实体没有单价、金额、税率列（已核实 subcontract_supplement_item），
 * 数量是唯一对表头补料总数量有影响的业务列。
 *
 * <p>明细主键 = {@code id}（列 id）；该表无行序字段（行序按主键升序）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubcontractSupplementItemHandler implements BillItemHandler {

    private final SubcontractSupplementService subcontractSupplementService;
    private final SubcontractSupplementItemMapper subcontractSupplementItemMapper;

    @Override
    public String billType() {
        return "SUBCONTRACT_SUPPLEMENT";
    }

    @Override
    public String itemLabel() {
        return "委外补料单明细";
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
        return subcontractSupplementService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return subcontractSupplementItemMapper.selectList(new QueryWrapper<SubcontractSupplementItem>()
                .eq("supplement_id", billId)
                .orderByAsc("id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SubcontractSupplement s ? s.getSupplementNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SubcontractSupplement s = (SubcontractSupplement) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", s.getSupplementNo());
        m.put("供应商", s.getSupplierName());
        m.put("状态", s.getStatus());
        m.put("合计数量", s.getTotalQuantity());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SubcontractSupplementItem it ? it.getId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SubcontractSupplementItem it = (SubcontractSupplementItem) item;
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
        SubcontractSupplementItem it = (SubcontractSupplementItem) item;
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        subcontractSupplementService.saveSupplementWithItems((SubcontractSupplement) bill, (List<SubcontractSupplementItem>) items);
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
