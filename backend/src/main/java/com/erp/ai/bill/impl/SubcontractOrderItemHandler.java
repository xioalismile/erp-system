package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.subcontract.order.entity.SubcontractOrder;
import com.erp.subcontract.order.entity.SubcontractOrderItem;
import com.erp.subcontract.order.mapper.SubcontractOrderItemMapper;
import com.erp.subcontract.order.service.SubcontractOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 委外加工单明细行适配器（2026-09-29 通用插件适配：委外模块第 1 种单据）。
 *
 * <p>保存链路 = {@code SubcontractOrderService.saveOrderWithItems(order, items)} ——
 * 与委外加工单编辑页点"保存"完全同一条路：重算明细金额（单价×数量）、税额（金额×税率%）、
 * 净额（金额+税额）与表头委外数量/加工费总额/税额/净额/本位币金额，拦"已生效等不可改"。
 *
 * <p><b>可改字段 3 个</b>（按明细实体真实列逐个核实）：数量、单价、税率。
 * 三者对表头金额均有可见影响：数量→表头委外数量+明细金额；单价→明细金额+表头加工费总额；税率→明细税额+表头税额。
 * 金额/税额/净额/本位币金额为保存链路重算的派生列，**禁止 AI 直改**；实发数量(finishedQty)不影响表头金额，不开放。
 *
 * <p>明细主键 = {@code id}（列 id）；该表无行序字段（行序按主键升序）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubcontractOrderItemHandler implements BillItemHandler {

    private final SubcontractOrderService subcontractOrderService;
    private final SubcontractOrderItemMapper subcontractOrderItemMapper;

    @Override
    public String billType() {
        return "SUBCONTRACT_ORDER";
    }

    @Override
    public String itemLabel() {
        return "委外加工单明细";
    }

    @Override
    public String itemIdField() {
        return "id";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "unitPrice", "taxRate");
    }

    @Override
    public Object loadBill(Long billId) {
        return subcontractOrderService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return subcontractOrderItemMapper.selectList(new QueryWrapper<SubcontractOrderItem>()
                .eq("order_id", billId)
                .orderByAsc("id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SubcontractOrder o ? o.getOrderNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SubcontractOrder o = (SubcontractOrder) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", o.getOrderNo());
        m.put("供应商", o.getSupplierName());
        m.put("状态", o.getStatus());
        m.put("合计数量", o.getTotalQuantity());
        m.put("合计金额", o.getTotalAmount());
        m.put("税额", o.getTaxAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SubcontractOrderItem it ? it.getId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SubcontractOrderItem it = (SubcontractOrderItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", fallbackLineNo);
        m.put("明细行ID", it.getId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("单位", it.getUnit());
        m.put("数量", it.getQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("金额", it.getAmount());
        m.put("税率", it.getTaxRate());
        m.put("税额", it.getTaxAmount());
        m.put("净额", it.getNetAmount());
        m.put("实发数量", it.getFinishedQty());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        SubcontractOrderItem it = (SubcontractOrderItem) item;
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
        if (patch.containsKey("unitPrice")) {
            it.setUnitPrice(toDecimal(patch.get("unitPrice"), "单价"));
        }
        if (patch.containsKey("taxRate")) {
            it.setTaxRate(toDecimal(patch.get("taxRate"), "税率"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        subcontractOrderService.saveOrderWithItems((SubcontractOrder) bill, (List<SubcontractOrderItem>) items);
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
