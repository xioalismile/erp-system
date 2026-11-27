package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.ai.service.AiCrudAgentService;
import com.erp.sale.entity.SaleContract;
import com.erp.sale.entity.SaleContractItem;
import com.erp.sale.mapper.SaleContractItemMapper;
import com.erp.sale.service.SaleContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售合同明细行适配器（2026-09-18 通用插件适配：销售模块第 3 种单据）。
 *
 * <p>保存链路 = {@code SaleContractService.saveContractWithItems(contract, items)} ——
 * 与合同编辑页点"保存"完全同一条路：重算金额/税额/合计、跑设置中心各项校验、
 * 拦"已生效/已完结不可改"。
 *
 * <p><b>与其它销售单据的差异（已核实源码）</b>：
 * <ol>
 *   <li>明细主键字段是 {@code itemId}（列 {@code item_id}），不是 xxxItemId；</li>
 *   <li>明细**没有 lineNo 列**，行序字段是 {@code sortNo}（列 {@code sort_no}）→
 *       {@link #lineNoField()} 必须返回 sortNo，否则定位行会失败；</li>
 *   <li>明细的 {@code unit} 是**真实列**（其它销售单据是 exist=false 展示字段）。</li>
 * </ol>
 *
 * <p>可改字段只有 4 个（{@link #editableFields()}）：单价/数量/税率/折扣率。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleContractItemHandler implements BillItemHandler {

    private final SaleContractService saleContractService;
    private final SaleContractItemMapper saleContractItemMapper;
    private final AiCrudAgentService aiCrudAgentService;

    @Override
    public String billType() {
        return "SALE_CONTRACT";
    }

    @Override
    public String itemLabel() {
        return "销售合同明细";
    }

    @Override
    public String itemIdField() {
        return "itemId";
    }

    /** 合同明细没有 lineNo 列，行序字段是 sort_no（已核实实体注解） */
    @Override
    public String lineNoField() {
        return "sortNo";
    }

    @Override
    public List<String> editableFields() {
        return List.of("unitPrice", "quantity", "taxRate", "discountRate");
    }

    @Override
    public Object loadBill(Long billId) {
        return saleContractService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return saleContractItemMapper.selectList(new QueryWrapper<SaleContractItem>()
                .eq("contract_id", billId)
                .orderByAsc("sort_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof SaleContract c ? c.getContractNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        SaleContract c = (SaleContract) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", c.getContractNo());
        m.put("客户", customerName(bill));
        m.put("状态", c.getStatus());
        m.put("合计金额", c.getTotalAmount());
        m.put("税额", c.getTaxAmount());
        m.put("价税合计", c.getNetAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof SaleContractItem it ? it.getItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof SaleContractItem it ? it.getSortNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        SaleContractItem it = (SaleContractItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getSortNo() != null ? it.getSortNo() : fallbackLineNo);
        m.put("明细行ID", it.getItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("单位", it.getUnit());
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
        SaleContractItem it = (SaleContractItem) item;
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
        saleContractService.saveContractWithItems((SaleContract) bill, (List<SaleContractItem>) items);
    }

    /**
     * 客户名：优先取合同实体上真实存在的 customerShortName 列，
     * 取不到再走「外键 → 主档名」回查（与其它单据同口径，单一适配点）。
     */
    private String customerName(Object bill) {
        try {
            if (bill instanceof SaleContract c && c.getCustomerShortName() != null
                    && !c.getCustomerShortName().isBlank()) {
                return c.getCustomerShortName();
            }
            return aiCrudAgentService.foreignDisplayName(bill);
        } catch (Exception e) {
            log.warn("[明细行编辑] 取客户名失败（不影响主流程）: {}", e.getMessage());
            return null;
        }
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
