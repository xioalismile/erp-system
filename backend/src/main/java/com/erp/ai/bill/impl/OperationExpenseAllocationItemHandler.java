package com.erp.ai.bill.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.ai.bill.BillItemHandler;
import com.erp.finance.entity.OperationExpense;
import com.erp.finance.entity.OperationExpenseAllocation;
import com.erp.finance.mapper.OperationExpenseAllocationMapper;
import com.erp.finance.service.OperationExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 费用分摊单明细行（分摊明细）适配器（2026-09-29 通用插件适配：财务模块第 5 种单据）。
 *
 * <p>保存链路 = {@code OperationExpenseService.updateEntity(expense)} —— 与费用分摊编辑页点"保存"完全同一条路：
 * 更新主表（同步费用明细行 JSON）+ 先删后插分摊明细（operation_expense_allocation），拦"只有草稿可改"。
 *
 * <p><b>必须整单带回分摊明细</b>：整单保存会"先删分摊明细再按传入集合重插"，若不带回明细会删掉且不重插（数据丢失）。
 * 故 {@link #loadBill} 取整单详情（含分摊明细）。
 *
 * <p><b>可改字段只有 1 个</b>：分摊金额。分摊基数是按分摊方式计算比例/金额的界面辅助列（后端不据此重算），
 * 分摊比例与金额为联动派生值，故均不开放，避免"改了没反应"。
 *
 * <p>明细主键 = {@code allocationId}（列 allocation_id）；该表无行序字段（行序按主键升序）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationExpenseAllocationItemHandler implements BillItemHandler {

    private final OperationExpenseService operationExpenseService;
    private final OperationExpenseAllocationMapper operationExpenseAllocationMapper;

    @Override
    public String billType() {
        return "OPERATION_EXPENSE";
    }

    @Override
    public String itemLabel() {
        return "费用分摊单分摊明细";
    }

    @Override
    public String itemIdField() {
        return "allocationId";
    }

    @Override
    public List<String> editableFields() {
        return List.of("amount");
    }

    @Override
    public Object loadBill(Long billId) {
        Long cnt = operationExpenseService.count(new QueryWrapper<OperationExpense>().eq("expense_id", billId));
        if (cnt == null || cnt == 0) {
            return null;
        }
        // 取整单详情（含分摊明细 + 费用明细行 JSON），保证整单保存不丢明细
        return operationExpenseService.getById(billId);
    }

    @Override
    public List<?> loadItems(Long billId) {
        return operationExpenseAllocationMapper.selectList(new QueryWrapper<OperationExpenseAllocation>()
                .eq("expense_id", billId)
                .orderByAsc("allocation_id"));
    }

    @Override
    public String billNo(Object bill) {
        return bill instanceof OperationExpense e ? e.getExpenseNo() : null;
    }

    @Override
    public Map<String, Object> billSummary(Object bill) {
        OperationExpense e = (OperationExpense) bill;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("单号", e.getExpenseNo());
        m.put("费用类型", e.getExpenseType());
        m.put("状态", e.getStatus());
        m.put("合计金额", e.getAmount());
        return m;
    }

    @Override
    public Long itemId(Object item) {
        return item instanceof OperationExpenseAllocation it ? it.getAllocationId() : null;
    }

    @Override
    public Integer lineNo(Object item) {
        return null;
    }

    @Override
    public Map<String, Object> toItemMap(Object item, int fallbackLineNo) {
        OperationExpenseAllocation it = (OperationExpenseAllocation) item;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("行号", fallbackLineNo);
        m.put("明细行ID", it.getAllocationId());
        m.put("分摊对象类型", it.getOrderType());
        m.put("分摊对象", it.getOrderNo());
        m.put("费用类型", it.getExpenseType());
        m.put("分摊基数", it.getAllocationBase());
        m.put("分摊比例", it.getAllocationRatio());
        m.put("金额", it.getAmount());
        return m;
    }

    @Override
    public void applyPatch(Object item, Map<String, Object> patch) {
        OperationExpenseAllocation it = (OperationExpenseAllocation) item;
        if (patch.containsKey("amount")) {
            it.setAmount(toDecimal(patch.get("amount"), "金额"));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void save(Object bill, List<?> items) {
        OperationExpense e = (OperationExpense) bill;
        e.setAllocations((List<OperationExpenseAllocation>) items);
        operationExpenseService.updateEntity(e);
    }

    /** 数值解析：允许 0，不允许负数/空/非数字 */
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
