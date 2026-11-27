package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.produce.mrp.entity.MrpPlan;
import com.erp.produce.mrp.entity.MrpPlanItem;
import com.erp.produce.mrp.mapper.MrpPlanItemMapper;
import com.erp.produce.mrp.service.MrpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MRP 计划明细行适配器（2026-11-22 立项二期补登）——<b>只读</b>。
 *
 * <p>MRP 明细（毛需求/可用库存/净需求/建议）由系统按 BOM 与库存运算生成，
 * 不是人工编辑对象：改一行建议数量不会重跑供需平衡，改了也是假数据。
 * 因此本适配器只开放"查看"（bill_item_list），patch/save 一律拒绝并说明业务原因；
 * 调整需求请改运算条件（销售订单/工单）后重新运算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MrpPlanItemHandler implements BillItemHandler {

    private static final String READ_ONLY_REASON =
            "MRP 计划明细由系统按 BOM 与库存自动运算生成，不支持对话修改；请调整运算条件（销售订单/生产工单）后重新运算";

    private final MrpService mrpService;
    private final MrpPlanItemMapper mrpPlanItemMapper;

    @Override
    public String billType() {
        return "MRP_PLAN";
    }

    @Override
    public String itemLabel() {
        return "MRP计划明细";
    }

    @Override
    public String itemIdField() {
        return "id";
    }

    @Override
    public String lineNoField() {
        // 运算结果无行号字段：bill_item_list 的「行号」由下标兜底，bill_item_update 的行号定位也走下标兜底
        return null;
    }

    @Override
    public List<String> editableFields() {
        return List.of();
    }

    @Override
    public Object loadBill(Long billId) {
        return mrpService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return mrpPlanItemMapper.selectList(new QueryWrapper<MrpPlanItem>()
                .eq("plan_id", billId)
                .orderByAsc("id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof MrpPlan o ? o.getPlanNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        MrpPlan o = (MrpPlan) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("计划编号", o.getPlanNo());
        m.put("计划名称", o.getPlanName());
        m.put("计划状态", planStatusLabel(o.getPlanStatus()));
        m.put("净需求总量", o.getTotalNetDemand());
        m.put("采购建议总量", o.getTotalPurchaseSuggestion());
        m.put("生产建议总量", o.getTotalProduceSuggestion());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof MrpPlanItem it ? it.getId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        MrpPlanItem it = (MrpPlanItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", fallbackLineNo);
        m.put("明细行ID", it.getId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("单位", it.getUnit());
        m.put("毛需求", it.getGrossDemand());
        m.put("可用库存", it.getAvailableStock());
        m.put("安全库存", it.getSafetyStock());
        m.put("净需求", it.getNetDemand());
        m.put("计划数量", it.getPlanQuantity());
        m.put("建议类型", suggestTypeLabel(it.getSuggestType()));
        m.put("建议日期", it.getSuggestDate());
        m.put("销售订单号", it.getSaleOrderNo());
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

    /** 计划状态：0 草稿 / 1 已运算 / 2 已生效 / 3 已执行 / 7 已作废（口径来自 MrpService 接口注释） */
    private String planStatusLabel(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "已运算";
            case 2 -> "已生效";
            case 3 -> "已执行";
            case 7 -> "已作废";
            default -> String.valueOf(status);
        };
    }

    /** 建议类型：PURCHASE=采购 / PRODUCE=生产 / SUBCONTRACT=委外（口径来自 generate* 方法注释） */
    private String suggestTypeLabel(String t) {
        if (t == null) {
            return null;
        }
        return switch (t) {
            case "PURCHASE" -> "采购建议";
            case "PRODUCE" -> "生产建议";
            case "SUBCONTRACT" -> "委外建议";
            default -> t;
        };
    }
}
