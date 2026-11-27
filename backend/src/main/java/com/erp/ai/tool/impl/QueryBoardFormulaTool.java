package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.board.custom.core.BoardDataAggregator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：多来源公式试算（2026-11-07）。
 * 用户说「净利率 = 毛利 − 销售费用 − 采购费用 − 税金」这类跨单据公式时，先按取数项取数再按公式计算，不落库。
 */
@Component
@RequiredArgsConstructor
public class QueryBoardFormulaTool implements AiTool {

    private final BoardDataAggregator aggregator;
    private final BoardAiSupport support;

    @Override
    public String getName() {
        return "query_board_formula";
    }

    @Override
    public String getDescription() {
        return "多来源公式试算：传多个取数项和一个公式，当场算出结果，不保存。"
                + "取数项 items 为数组，每项 {name 取数项名称, source 数据源key, timeField 时间字段, agg 聚合方式, "
                + "field 数值字段, joins 关联单据key数组, filters 筛选条件数组}；"
                + "name 是公式里的变量名，卡片内不能重复；字段必须先通过 query_board_sources 查证。"
                + "formula 支持 + - * /、× ÷ −、括号和数字常量，例如「毛利 - 销售费用 - 采购费用 - 税金」"
                + "或「(营收 - 成本) / 营收 * 100」。period 默认 year。"
                + "返回每个取数项的值、命中行数、最终结果；分母为 0 或变量无值时返回中文原因，禁止自行补 0。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "array");
        items.put("description", "取数项数组，每项含 name/source/timeField/agg/field/joins/filters");
        props.put("items", items);
        Map<String, Object> formula = new LinkedHashMap<>();
        formula.put("type", "string");
        formula.put("description", "计算公式，变量名必须与 items.name 一致，如「毛利 - 销售费用 - 采购费用 - 税金」");
        props.put("formula", formula);
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("type", "string");
        period.put("description", "时间维度：day/week/month/quarter/year，默认 year");
        props.put("period", period);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("required", List.of("items", "formula"));
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
            if (items.size() > 8) return support.fail("一张卡片最多 8 个取数项");
            List<String> tokens = support.formulaTokens(params == null ? null : String.valueOf(params.get("formula")));
            if (tokens.isEmpty()) return support.fail("请提供计算公式");
            String period = params != null && params.get("period") != null
                    ? String.valueOf(params.get("period")).trim() : "year";

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("type", "metric");
            card.put("mode", "multi");
            card.put("items", items);
            card.put("formula", tokens);
            Map<String, Object> result = aggregator.cardData(card, period, support.scope(scope));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", result.get("error") == null);
            out.put("period", period);
            out.put("value", result.get("value"));
            out.put("formula", result.get("formula"));
            out.put("items", result.get("items"));
            if (result.get("criteria") != null) out.put("criteria", result.get("criteria"));
            if (result.get("error") != null) out.put("message", result.get("error"));
            if (result.get("formulaError") != null) out.put("message", result.get("formulaError"));
            if (result.get("emptyHint") != null) out.put("message", result.get("emptyHint"));
            out.put("hint", "结果未保存；如需放到看板，请用户确认后调用 board_save_formula_card。");
            return out;
        } catch (Exception e) {
            return support.fail("公式试算失败：" + (e.getMessage() == null ? "请检查取数项与公式" : e.getMessage()));
        }
    }
}
