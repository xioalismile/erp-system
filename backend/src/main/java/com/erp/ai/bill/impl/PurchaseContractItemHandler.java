package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.purchase.entity.PurchaseContract;
import com.erp.purchase.entity.PurchaseContractItem;
import com.erp.purchase.mapper.PurchaseContractItemMapper;
import com.erp.purchase.service.PurchaseContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购合同明细行适配器（2026-09-28 通用插件适配：采购模块第 3 种单据）。
 *
 * <p>保存链路 = {@code PurchaseContractService.saveContractWithItems(contract, items)} ——
 * 与采购合同编辑页点"保存"完全同一条路：重算金额/税额/净额/合同合计、跑设置中心各项校验、
 * 拦"已生效不可改"。
 *
 * <p>与销售合同的差异（已核实采购合同实体注解）：采购合同明细**有 line_no 列**，
 * 行序字段就是 {@code lineNo}；明细主键是 {@code contractItemId}（列 contract_item_id）。
 *
 * <p>可改字段只有 4 个（{@link #editableFields()}）：单价/数量/税率/折扣率。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseContractItemHandler implements BillItemHandler {

    private final PurchaseContractService purchaseContractService;
    private final PurchaseContractItemMapper purchaseContractItemMapper;

    @Override
    public String billType() {
        return "PURCHASE_CONTRACT";
    }

    @Override
    public String itemLabel() {
        return "采购合同明细";
    }

    @Override
    public String itemIdField() {
        return "contractItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("unitPrice", "quantity", "taxRate", "discountRate");
    }

    @Override
    public Object loadBill(Long billId) {
        return purchaseContractService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return purchaseContractItemMapper.selectList(new QueryWrapper<PurchaseContractItem>()
                .eq("contract_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof PurchaseContract c ? c.getContractNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        PurchaseContract c = (PurchaseContract) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", c.getContractNo());
        m.put("供应商", c.getSupplierShortName());
        m.put("状态", c.getStatus());
        m.put("合计金额", c.getTotalAmount());
        m.put("税额", c.getTaxAmount());
        m.put("价税合计", c.getNetAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof PurchaseContractItem it ? it.getContractItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof PurchaseContractItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        PurchaseContractItem it = (PurchaseContractItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getContractItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("数量", it.getQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("折扣率", it.getDiscountRate());
        m.put("税率", it.getTaxRate());
        m.put("金额", it.getAmount());
        m.put("税额", it.getTaxAmount());
        m.put("净额", it.getNetAmount());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        PurchaseContractItem it = (PurchaseContractItem) item;
        if (patch.containsKey("unitPrice")) {
            it.setUnitPrice(toDecimal(patch.get("unitPrice"), "单价"));
        }
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
        if (patch.containsKey("taxRate")) {
            it.setTaxRate(toDecimal(patch.get("taxRate"), "税率"));
        }
        if (patch.containsKey("discountRate")) {
            it.setDiscountRate(toDecimal(patch.get("discountRate"), "折扣率"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        purchaseContractService.saveContractWithItems((PurchaseContract) bill, (List<PurchaseContractItem>) items);
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
