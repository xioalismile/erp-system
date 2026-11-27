package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.finance.cost.calculation.entity.CostCalculation;
import com.erp.finance.cost.calculation.entity.CostCalculationItem;
import com.erp.finance.cost.calculation.mapper.CostCalculationItemMapper;
import com.erp.finance.cost.calculation.service.CostCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本核算明细行适配器（2026-11-22 立项二期补登）——<b>只读</b>。
 *
 * <p>成本明细（材料/人工/制造费用行）由"核算"动作按入库/领料流水归集生成，
 * 手工改一行的金额会让成本单与库存台账对不上——这是财务红线。
 * 因此只开放"查看"，patch/save 一律拒绝并说明业务原因；需要调整请重新核算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostCalculationItemHandler implements BillItemHandler {

    private static final String READ_ONLY_REASON =
            "成本核算明细由系统按出入库流水自动归集生成，不支持对话修改；如需调整请在成本核算页面重新核算";

    private final CostCalculationService costCalculationService;
    private final CostCalculationItemMapper costCalculationItemMapper;

    @Override
    public String billType() {
        return "COST_CALCULATION";
    }

    @Override
    public String itemLabel() {
        return "成本核算明细";
    }

    @Override
    public String itemIdField() {
        return "id";
    }

    @Override
    public String lineNoField() {
        // 归集结果无行号字段：行号由下标兜底
        return null;
    }

    @Override
    public List<String> editableFields() {
        return List.of();
    }

    @Override
    public Object loadBill(Long billId) {
        return costCalculationService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return costCalculationItemMapper.selectList(new QueryWrapper<CostCalculationItem>()
                .eq("calc_id", billId)
                .orderByAsc("id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof CostCalculation o ? o.getCalcNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        CostCalculation o = (CostCalculation) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", o.getCalcNo());
        m.put("物料编码", o.getMaterialCode());
        m.put("物料名称", o.getMaterialName());
        m.put("状态", o.getApprovalStatus());
        m.put("合计金额", o.getTotalCost());
        m.put("总成本", o.getTotalCost());
        m.put("单位成本", o.getUnitCost());
        m.put("直接材料", o.getDirectMaterialCost());
        m.put("直接人工", o.getDirectLaborCost());
        m.put("制造费用", o.getManufacturingOverhead());
        m.put("报废损失", o.getScrapLoss());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof CostCalculationItem it ? it.getId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        CostCalculationItem it = (CostCalculationItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", fallbackLineNo);
        m.put("明细行ID", it.getId());
        m.put("费用类型", it.getCostType());
        m.put("费用名称", it.getCostName());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("单位", it.getUnit());
        m.put("数量", it.getQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("金额", it.getAmount());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        throw new IllegalArgumentException(READ_ONLY_REASON);
    }

    @Override
    public void save(Object bill, List<?> items) {
        throw new IllegalArgumentException(READ_ONLY_REASON);
    }
}
