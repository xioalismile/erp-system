package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.inventory.entity.OtherIssue;
import com.erp.inventory.entity.OtherIssueItem;
import com.erp.inventory.mapper.OtherIssueItemMapper;
import com.erp.inventory.service.OtherIssueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 其他出库单明细行适配器（2026-09-29 通用插件适配：库存模块第 4 种单据）。
 *
 * <p>保存链路 = {@code OtherIssueService.saveIssueWithItems(issue, items)} ——
 * 与其他出库单编辑页点"保存"完全同一条路：重算明细金额与出库总额、回填核算成本、
 * 拦"已生效/已关闭/已取消不可改"。
 *
 * <p><b>可改字段只有 2 个</b>：数量/单价。出库明细的金额 = 数量 × 单价（保存链路
 * {@code calculateItemAmount} 只依据这两列），平均成本由核算引擎回填；
 * 金额/税额/净额/折扣金额等派生列一律不开放。
 *
 * <p>明细主键 = {@code issueItemId}（列 issue_item_id）；行序字段 = {@code lineNo}（列 line_no）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtherIssueItemHandler implements BillItemHandler {

    private final OtherIssueService otherIssueService;
    private final OtherIssueItemMapper otherIssueItemMapper;

    @Override
    public String billType() {
        return "OTHER_ISSUE";
    }

    @Override
    public String itemLabel() {
        return "其他出库单明细";
    }

    @Override
    public String itemIdField() {
        return "issueItemId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("quantity", "unitPrice");
    }

    @Override
    public Object loadBill(Long billId) {
        return otherIssueService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return otherIssueItemMapper.selectList(new QueryWrapper<OtherIssueItem>()
                .eq("issue_id", billId)
                .orderByAsc("line_no"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof OtherIssue i ? i.getIssueNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        OtherIssue i = (OtherIssue) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", i.getIssueNo());
        m.put("状态", i.getStatus());
        m.put("出库类型", i.getIssueType());
        m.put("合计金额", i.getTotalAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof OtherIssueItem it ? it.getIssueItemId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return item instanceof OtherIssueItem it ? it.getLineNo() : null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        OtherIssueItem it = (OtherIssueItem) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", it.getLineNo() != null ? it.getLineNo() : fallbackLineNo);
        m.put("明细行ID", it.getIssueItemId());
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
        OtherIssueItem it = (OtherIssueItem) item;
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
        otherIssueService.saveIssueWithItems((OtherIssue) bill, (List<OtherIssueItem>) items);
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
