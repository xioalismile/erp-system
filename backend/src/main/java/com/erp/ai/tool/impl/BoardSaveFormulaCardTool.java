package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.board.custom.core.BoardDataAggregator;
import com.erp.board.custom.service.CustomBoardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：把多来源公式卡保存到指定看板（2026-11-07）。
 * 只写当前登录人的看板配置；执行前会由 AI 确认卡二次确认，权限由 ToolRegistry 统一把关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardSaveFormulaCardTool implements AiTool {

    private static final List<Integer> SIZES = List.of(3, 4, 6, 8, 12);

    private final BoardDataAggregator aggregator;
    private final CustomBoardService customBoardService;
    private final BoardAiSupport support;

    @Override
    public String getName() {
        return "board_save_formula_card";
    }

    @Override
    public String getDescription() {
        return "把多来源公式卡保存到看板当前登录人的配置里。只在用户明确说「保存到看板/放到看板」时调用。"
                + "pageKey=finance/sale/inventory/operation；blockTitle 填已有区块标题（如「毛利率」「销售额」）"
                + "则替换该区块的取数，不填则新增一张卡片；items 与 formula 与 query_board_formula 相同。"
                + "保存前会先试算，结果为空或有错误时不保存。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("type", "string");
        page.put("description", "看板：finance / sale / inventory / operation");
        props.put("pageKey", page);
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "string");
        block.put("description", "已有区块标题，如「毛利率」；不传则新增自定义卡");
        props.put("blockTitle", block);
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("type", "string");
        title.put("description", "卡片标题，如「净利率」");
        props.put("title", title);
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "array");
        items.put("description", "取数项数组，与 query_board_formula 相同");
        props.put("items", items);
        Map<String, Object> formula = new LinkedHashMap<>();
        formula.put("type", "string");
        formula.put("description", "计算公式，变量名必须与 items.name 一致");
        props.put("formula", formula);
        Map<String, Object> size = new LinkedHashMap<>();
        size.put("type", "integer");
        size.put("description", "卡片宽度 3/4/6/8/12，默认 6");
        props.put("size", size);
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("type", "string");
        period.put("description", "试算用时间维度，默认 year");
        props.put("period", period);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("required", List.of("pageKey", "items", "formula"));
        params.put("properties", props);
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return support.fail("缺少登录上下文，请在系统小楷中使用");
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        if (scope == null || scope.getUsername() == null) return support.fail("无法识别当前登录人");
        String pageKey = params == null || params.get("pageKey") == null
                ? "" : String.valueOf(params.get("pageKey")).trim();
        if (!support.validPage(pageKey)) return support.fail("不支持的看板：" + pageKey);
        String deny = support.denyPage(scope, pageKey);
        if (deny != null) return support.fail(deny);
        try {
            List<Map<String, Object>> items = support.itemsOf(params.get("items"));
            if (items.isEmpty()) return support.fail("请先提供取数项");
            List<String> tokens = support.formulaTokens(String.valueOf(params.get("formula")));
            if (tokens.isEmpty()) return support.fail("请提供计算公式");
            String title = params.get("title") == null ? "" : String.valueOf(params.get("title")).trim();
            String blockTitle = params.get("blockTitle") == null ? "" : String.valueOf(params.get("blockTitle")).trim();
            int size = intOf(params.get("size"), 6);
            if (!SIZES.contains(size)) size = 6;
            String period = params.get("period") == null ? "year" : String.valueOf(params.get("period")).trim();

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", "mc" + System.currentTimeMillis());
            card.put("type", "metric");
            card.put("mode", "multi");
            card.put("title", title.isEmpty() ? (blockTitle.isEmpty() ? "看板公式卡" : blockTitle) : title);
            card.put("decimals", 2);
            card.put("size", size);
            card.put("color", "#2563eb");
            card.put("icon", "target");
            card.put("items", items);
            card.put("formula", tokens);

            // 保存前先试算：取数失败或公式错误不落库
            Map<String, Object> preview = aggregator.cardData(card, period, support.scope(scope));
            if (preview.get("error") != null) return support.fail("试算失败，未保存：" + preview.get("error"));
            if (preview.get("formulaError") != null) return support.fail("试算失败，未保存：" + preview.get("formulaError"));

            Map<String, Object> layout = customBoardService.pageLayout(scope.getUsername(), pageKey);
            List<Map<String, Object>> cards = new ArrayList<>();
            if (layout.get("cards") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, Object> one = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : m.entrySet()) one.put(String.valueOf(e.getKey()), e.getValue());
                        cards.add(one);
                    }
                }
            }

            Map<String, Object> target = null;
            for (Map<String, Object> c : cards) {
                Object bid = c.get("id");
                Object btitle = c.get("title");
                boolean builtin = Boolean.TRUE.equals(c.get("builtin"));
                if (builtin && ((blockTitle != null && !blockTitle.isEmpty())
                        && (blockTitle.equals(String.valueOf(btitle)) || blockTitle.equals(String.valueOf(bid))))) {
                    target = c;
                    break;
                }
            }
            String savedTo;
            if (target != null) {
                target.put("override", true);
                target.put("card", card);
                if (!title.isEmpty()) target.put("title", title);
                savedTo = "区块 " + target.get("title");
            } else {
                // 自定义卡的落库格式必须与前端「看板设置」一致：卡片字段平铺在顶层（带 type），
                // 不能包成 {builtin:false, card:{...}} —— 否则页面读取时会被判为无 type 而丢弃，
                // 表现为"保存成功但看板看不到卡"。
                Map<String, Object> entry = new LinkedHashMap<>(card);
                entry.put("order", cards.size());
                entry.put("hidden", false);
                cards.add(entry);
                savedTo = "新增卡片 " + card.get("title");
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("cards", cards);
            customBoardService.savePageLayout(scope.getUsername(), pageKey, body);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("pageKey", pageKey);
            out.put("pageName", support.boardName(pageKey));
            out.put("savedTo", savedTo);
            out.put("value", preview.get("value"));
            out.put("formula", preview.get("formula"));
            out.put("message", "已保存到" + support.boardName(pageKey) + "的「" + savedTo + "」，刷新看板即可看到。");
            return out;
        } catch (Exception e) {
            log.warn("[小楷-保存看板公式卡] 失败 page={}: {}", pageKey, e.getMessage());
            return support.fail("保存失败：" + (e.getMessage() == null ? "请稍后重试" : e.getMessage()));
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
