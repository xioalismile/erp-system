package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.inventory.entity.StockCheck;
import com.erp.inventory.entity.StockCheckItem;
import com.erp.inventory.mapper.StockCheckItemMapper;
import com.erp.inventory.service.StockCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存盘点单明细行适配器（2026-09-29 通用插件适配：库存模块第 2 种单据）。
 *
 * <p>保存链路 = {@code StockCheckService.saveCheckWithItems(check, items)} ——
 * 与库存盘点单编辑页点"保存"完全同一条路：重算盘点金额/盘点总额/盘盈盘亏、回填核算成本、
 * 拦"已生效/已关闭/已取消不可改"。
 *
 * <p><b>可改字段只有 2 个</b>：盘点数量（对外统一叫 quantity）、单价。盘点明细的
 * 账面数量/账面金额来自库存、盘盈/盘亏数量与金额、盘点结果由保存链路
 * {@code calculateItemAmount} 自动重算（依据 盘点数量 × 单价），一律不开放。
 *
 * <p>明细主键 = {@code checkItemId}（列 check_item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockCheckItemHandler implements BillItemHandler {

    private final StockCheckService stockCheckService;
    private final StockCheckItemMapper stockCheckItemMapper;

    @Override
    public String billType() {
        return "STOCK_CHECK";
    }

    @Override
    public String itemLabel() {
        return "库存盘点单明细";
    }

    @Override
    public String itemIdField() {
        return "checkItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "unitPrice");
    }

    @Override
    public Object loadBill(Long billId) {
        return stockCheckService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return stockCheckItemMapper.selectList(new QueryWrapper<StockCheckItem>()
                .eq("check_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof StockCheck c ? c.getCheckNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        StockCheck c = (StockCheck) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", c.getCheckNo());
        m.put("状态", c.getStatus());
        m.put("库存类型", c.getStockType());
        m.put("合计金额", c.getCheckAmount());
        m.put("盘盈盘亏金额", c.getProfitLossAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof StockCheckItem it ? it.getCheckItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof StockCheckItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        StockCheckItem it = (StockCheckItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getCheckItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("账面数量", it.getBookQuantity());
        m.put("数量", it.getCheckQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("金额", it.getCheckAmount());
        m.put("盘盈数量", it.getProfitQuantity());
        m.put("盘亏数量", it.getLossQuantity());
        m.put("盘点结果", it.getCheckResult());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        StockCheckItem it = (StockCheckItem) item;
        if (patch.containsKey("quantity")) {
            it.setCheckQuantity(toDecimal(patch.get("quantity"), "盘点数量"));
        }
        if (patch.containsKey("unitPrice")) {
            it.setUnitPrice(toDecimal(patch.get("unitPrice"), "单价"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        stockCheckService.saveCheckWithItems((StockCheck) bill, (List<StockCheckItem>) items);
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
