package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.freesample.entity.FreeSampleOrder;
import com.erp.freesample.entity.FreeSampleOrderItem;
import com.erp.freesample.mapper.FreeSampleOrderItemMapper;
import com.erp.freesample.mapper.FreeSampleOrderMapper;
import com.erp.freesample.service.FreeSampleOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 免费样品单明细行适配器（2026-09-29 通用插件适配：打样模块第 2 种单据）。
 *
 * <p>保存链路 = {@code FreeSampleOrderService.update(orderId, order)} —— 与免费样品单编辑页
 * 点"保存"完全同一条路：主表与明细一起落库（先删旧明细再插新明细）、重算汇总数量、
 * 金额强制为 0.00、拦"已生效/已发货/已完成不可改"。明细通过 {@code order.items} 载体随主表提交。
 *
 * <p><b>可改字段只有 1 个</b>：数量。免费样品明细**没有单价、金额、税率、折扣率列**
 * （已核实实体注解 free_sample_order_item：只有 quantity 一个数值列），且单据金额恒为 0，
 * 故只开放数量。
 *
 * <p>明细主键 = {@code itemId}（列 item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreeSampleOrderItemHandler implements BillItemHandler {

    private final FreeSampleOrderService freeSampleOrderService;
    private final FreeSampleOrderMapper freeSampleOrderMapper;
    private final FreeSampleOrderItemMapper freeSampleOrderItemMapper;

    @Override
    public String billType() {
        return "FREE_SAMPLE";
    }

    @Override
    public String itemLabel() {
        return "免费样品单明细";
    }

    @Override
    public String itemIdField() {
        return "itemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity");
    }

    @Override
    public Object loadBill(Long billId) {
        // 用 Mapper 直读主表：免费样品 Service 的 getById 在"单据不存在"时抛异常，
        // 这里遵守接口约定返回 null（由工具统一给出"没找到该单据"提示）。
        return freeSampleOrderMapper.selectById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return freeSampleOrderItemMapper.selectList(new QueryWrapper<FreeSampleOrderItem>()
                .eq("order_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof FreeSampleOrder o ? o.getOrderNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        FreeSampleOrder o = (FreeSampleOrder) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", o.getOrderNo());
        m.put("状态", o.getStatus());
        m.put("客户", o.getCustomerName());
        m.put("合计数量", o.getTotalQuantity());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof FreeSampleOrderItem it ? it.getItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof FreeSampleOrderItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        FreeSampleOrderItem it = (FreeSampleOrderItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getModel());
        m.put("数量", it.getQuantity());
        m.put("单位", it.getUnitName());
        m.put("颜色", it.getColor());
        m.put("备注", it.getRemark());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        FreeSampleOrderItem it = (FreeSampleOrderItem) item;
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        FreeSampleOrder order = (FreeSampleOrder) bill;
        // 明细通过主表载体提交：update(orderId, order) 会读 order.items 重算数量并重建明细
        order.setItems((List<FreeSampleOrderItem>) items);
        freeSampleOrderService.update(order.getOrderId(), order);
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
