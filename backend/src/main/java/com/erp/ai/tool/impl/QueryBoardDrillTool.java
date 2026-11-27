package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.board.custom.core.BoardDataAggregator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：看板明细下钻（2026-11-07）。
 * 用户问「是哪几张单 / 明细给我看」时，按某个取数项查单据清单。
 */
@Component
@RequiredArgsConstructor
public class QueryBoardDrillTool implements AiTool {

    private final BoardDataAggregator aggregator;
    private final BoardAiSupport support;

    @Override
    public String getName() {
        return "query_board_drill";
    }

    @Override
    public String getDescription() {
        return "看板明细下钻：按取数项查背后的单据清单（单据号、业务日期、状态、金额）。"
                + "用户问「是哪几张单」「明细给我看」「为什么是这个数」时调用。"
                + "items 与 query_board_formula 相同；itemIndex 指定看第几个取数项，默认 0；"
                + "period 默认 year；limit 默认 50、最多 100。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "array");
        items.put("description", "取数项数组，与 query_board_formula 相同");
        props.put("items", items);
        Map<String, Object> itemIndex = new LinkedHashMap<>();
        itemIndex.put("type", "integer");
        itemIndex.put("description", "看第几个取数项，从 0 开始，默认 0");
        props.put("itemIndex", itemIndex);
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("type", "string");
        period.put("description", "时间维度：day/week/month/quarter/year，默认 year");
        props.put("period", period);
        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("type", "integer");
        limit.put("description", "返回行数，默认 50，最多 100");
        props.put("limit", limit);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("required", List.of("items"));
        params.put("properties", props);
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return support.fail("缺少登录上下文，请在系统小楷中使用");
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        String deny = support.denyBoard(scope);
        if (deny != null) return support.fail(deny);
        try {
            List<Map<String, Object>> items = support.itemsOf(params == null ? null : params.get("items"));
            if (items.isEmpty()) return support.fail("请先提供取数项");
            int idx = intOf(params == null ? null : params.get("itemIndex"), 0);
            int limit = Math.min(Math.max(intOf(params == null ? null : params.get("limit"), 50), 1), 100);
            String period = params != null && params.get("period") != null
                    ? String.valueOf(params.get("period")).trim() : "year";
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("type", "metric");
            card.put("mode", "multi");
            card.put("items", items);
            Map<String, Object> result = aggregator.drill(card, period, null, limit, idx, support.scope(scope));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("period", period);
            out.put("itemIndex", idx);
            out.put("docType", result.get("docType"));
            out.put("docName", result.get("docName"));
            out.put("columns", result.get("columns"));
            out.put("rows", result.get("rows"));
            out.put("total", result.get("total"));
            out.put("truncated", result.get("truncated"));
            return out;
        } catch (Exception e) {
            return support.fail("明细查询失败：" + (e.getMessage() == null ? "请检查取数项" : e.getMessage()));
        }
    }

    private int intOf(Object o, int def) {
        if (o == null) return def;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return def;
        }
    }
}
