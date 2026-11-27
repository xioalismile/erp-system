package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.production.entity.WorkOrder;
import com.erp.production.entity.WorkOrderMaterial;
import com.erp.production.mapper.WorkOrderMaterialMapper;
import com.erp.production.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产工单用料明细行适配器（2026-09-29 通用插件适配：生产模块第 1 种单据）。
 *
 * <p>保存链路 = {@code WorkOrderService.saveOrderWithItems(order, materials)} ——
 * 与生产工单编辑页点"保存"完全同一条路：整单按 order_id 先删旧明细再重插（成品明细/用料明细同链）。
 *
 * <p><b>可改字段只有 1 个</b>：需领数量（{@code requiredQuantity}）。
 * 工单用料明细的单位成本/总成本由"生产领料单审核"归集回写，已领/已退/已耗/报废数量由发料退料执行回写，
 * BOM 数量由 BOM 展开写入 —— 以上均非用户录入口径，一律不开放；该表没有单价/税率/折扣率/金额列。
 *
 * <p><b>整单携带口径（关键，勿省）</b>：
 * <ol>
 *   <li>{@link #loadBill} 必须取整单详情（带成品明细 products）。若不带成品明细，保存链路会把
 *       "未携带成品明细"当成空 → 按表头成品自动生成 1 行并覆盖原有成品明细（多成品工单被压成 1 行）；</li>
 *   <li>同时把"工序清单"置空再提交 —— 明细落库时成品行主键会重建，工序的成品归属由保存链路内部重挂，
 *       外部再提交一份带旧主键的工序清单会把工序挂到失效主键上。置空后保存链路跳过工序同步，既有工序不动。</li>
 * </ol>
 *
 * <p>明细主键 = {@code orderItemId}（列 order_item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderMaterialItemHandler implements BillItemHandler {

    private final WorkOrderService workOrderService;
    private final WorkOrderMaterialMapper workOrderMaterialMapper;

    @Override
    public String billType() {
        return "PRODUCTION_ORDER";
    }

    @Override
    public String itemLabel() {
        return "生产工单用料明细";
    }

    @Override
    public String itemIdField() {
        return "orderItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("requiredQuantity");
    }

    @Override
    public Object loadBill(Long billId) {
        // 取整单详情（必须带成品明细），并把工序清单置空（理由见类注释）
        WorkOrder order = workOrderService.getDetail(billId);
        order.setOperations(null);
        return order;
    }

    @Override
    public List<?> loadItems(Long billId) {
        return workOrderMaterialMapper.selectList(new QueryWrapper<WorkOrderMaterial>()
                .eq("order_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof WorkOrder o ? o.getOrderNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        WorkOrder o = (WorkOrder) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", o.getOrderNo());
        m.put("状态", o.getStatus());
        m.put("成品", o.getMaterialName() != null ? o.getMaterialName() : o.getMaterialCode());
        m.put("生产数量", o.getQuantity());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof WorkOrderMaterial it ? it.getOrderItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof WorkOrderMaterial it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        WorkOrderMaterial it = (WorkOrderMaterial) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getOrderItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("BOM数量", it.getBomQuantity());
        m.put("需领数量", it.getRequiredQuantity());
        m.put("已领数量", it.getIssuedQuantity());
        m.put("已退数量", it.getReturnedQuantity());
        m.put("已耗数量", it.getConsumedQuantity());
        m.put("报废数量", it.getScrapQuantity());
        m.put("供应类型", it.getSupplyType());
        m.put("工序号", it.getProcessNo());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        WorkOrderMaterial it = (WorkOrderMaterial) item;
        if (patch.containsKey("requiredQuantity")) {
            it.setRequiredQuantity(toDecimal(patch.get("requiredQuantity"), "需领数量"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        workOrderService.saveOrderWithItems((WorkOrder) bill, (List<WorkOrderMaterial>) items);
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
