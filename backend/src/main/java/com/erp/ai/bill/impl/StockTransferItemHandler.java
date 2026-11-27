package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.inventory.entity.StockTransfer;
import com.erp.inventory.entity.StockTransferItem;
import com.erp.inventory.mapper.StockTransferItemMapper;
import com.erp.inventory.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存调拨单明细行适配器（2026-09-29 通用插件适配：库存模块第 1 种单据）。
 *
 * <p>保存链路 = {@code StockTransferService.saveTransferWithItems(transfer, items)} ——
 * 与库存调拨单编辑页点"保存"完全同一条路：重算明细金额与调拨总额、回填核算成本、
 * 拦"已生效/已关闭/已取消不可改"。
 *
 * <p><b>可改字段只有 2 个</b>：数量/单价。调拨明细的金额 = 数量 × 单价（保存链路
 * {@code calculateItemAmount} 只依据这两列），平均成本由核算引擎回填；
 * 金额/税额/净额/折扣金额等派生列一律不开放。
 *
 * <p>明细主键 = {@code transferItemId}（列 transfer_item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockTransferItemHandler implements BillItemHandler {

    private final StockTransferService stockTransferService;
    private final StockTransferItemMapper stockTransferItemMapper;

    @Override
    public String billType() {
        return "STOCK_TRANSFER";
    }

    @Override
    public String itemLabel() {
        return "库存调拨单明细";
    }

    @Override
    public String itemIdField() {
        return "transferItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "unitPrice");
    }

    @Override
    public Object loadBill(Long billId) {
        return stockTransferService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return stockTransferItemMapper.selectList(new QueryWrapper<StockTransferItem>()
                .eq("transfer_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof StockTransfer t ? t.getTransferNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        StockTransfer t = (StockTransfer) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", t.getTransferNo());
        m.put("状态", t.getStatus());
        m.put("调拨类型", t.getTransferType());
        m.put("合计金额", t.getTotalAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof StockTransferItem it ? it.getTransferItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof StockTransferItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        StockTransferItem it = (StockTransferItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getTransferItemId());
        m.put("物料编码", it.getMaterialCode());
        m.put("物料名称", it.getMaterialName());
        m.put("规格型号", it.getSpecification());
        m.put("数量", it.getQuantity());
        m.put("单价", it.getUnitPrice());
        m.put("金额", it.getAmount());
        m.put("平均成本", it.getAvgCost());
        m.put("批号", it.getBatchNo());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        StockTransferItem it = (StockTransferItem) item;
        if (patch.containsKey("quantity")) {
            it.setQuantity(toDecimal(patch.get("quantity"), "数量"));
        }
        if (patch.containsKey("unitPrice")) {
            it.setUnitPrice(toDecimal(patch.get("unitPrice"), "单价"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        stockTransferService.saveTransferWithItems((StockTransfer) bill, (List<StockTransferItem>) items);
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
