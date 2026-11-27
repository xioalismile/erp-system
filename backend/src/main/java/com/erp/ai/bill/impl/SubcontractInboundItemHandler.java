package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.subcontract.inbound.entity.SubcontractInbound;
import com.erp.subcontract.inbound.entity.SubcontractInboundItem;
import com.erp.subcontract.inbound.mapper.SubcontractInboundItemMapper;
import com.erp.subcontract.inbound.service.SubcontractInboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 委外入库单明细行适配器（2026-09-29 通用插件适配：委外模块第 3 种单据）。
 *
 * <p>保存链路 = {@code SubcontractInboundService.saveInboundWithItems(inbound, items)} ——
 * 与委外入库单编辑页点"保存"完全同一条路：重算表头入库总数量与加工费合计，拦"已生效不可改"。
 *
 * <p><b>可改字段 2 个</b>（按入库单编辑页真实可录入列逐个核实）：数量、加工费。
 * 两者对表头均有可见影响：数量→入库总数量；加工费→表头加工费合计。金额派生列**禁止 AI 直改**。
 *
 * <p>明细主键 = {@code id}（列 id）；该表无行序字段（行序按主键升序）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubcontractInboundItemHandler implements BillItemHandler {

    private final SubcontractInboundService subcontractInboundService;
    private final SubcontractInboundItemMapper subcontractInboundItemMapper;

    @Override
    public String billType() {
        return "SUBCONTRACT_RECEIPT";
    }

    @Override
    public String itemLabel() {
        return "委外入库单明细";
    }

    @Override
    public String itemIdField() {
        return "id";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "processingFee");
    }

    @Override
    public Object loadBill(Long billId) {
        return subcontractInboundService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return subcontractInboundItemMapper.selectList(new QueryWrapper<SubcontractInboundItem>()
                .eq("inbound_id", billId)
                .orderByAsc("id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SubcontractInbound b ? b.getInboundNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SubcontractInbound b = (SubcontractInbound) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", b.getInboundNo());
        m.put("供应商", b.getSupplierName());
        m.put("状态", b.getStatus());
        m.put("合计数量", b.getTotalQuantity());
        m.put("合计金额", b.getProcessingFee());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SubcontractInboundItem it ? it.getId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SubcontractInboundItem it = (SubcontractInboundItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", fallbackLineNo);
        m.put("明细行ID", it.getId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("单位", it.getUnit());
        m.put("数量", it.getQuantity());
        m.put("加工费", it.getProcessingFee());
        m.put("仓库", it.getWarehouseName());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        SubcontractInboundItem it = (SubcontractInboundItem) item;
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
        if (patch.containsKey("processingFee")) {
            it.setProcessingFee(toDecimal(patch.get("processingFee"), "加工费"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        subcontractInboundService.saveInboundWithItems((SubcontractInbound) bill, (List<SubcontractInboundItem>) items);
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
