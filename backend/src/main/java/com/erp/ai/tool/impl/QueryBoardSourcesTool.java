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
 * 只读工具：查看自定义看板可用的数据源、字段与关联单据（2026-11-07）。
 * 小楷拼“多来源公式”前必须先查本工具，字段名必须原样使用。
 */
@Component
@RequiredArgsConstructor
public class QueryBoardSourcesTool implements AiTool {

    private final BoardDataAggregator aggregator;
    private final BoardAiSupport support;

    @Override
    public String getName() {
        return "query_board_sources";
    }

    @Override
    public String getDescription() {
        return "查看自定义看板可用的数据源与字段。不传 source 时返回全部数据源清单；传 source 时返回该数据源的字段、"
                + "可关联单据（本单表头、基础资料、来源单据）及各自字段。配置多来源公式前必须先调用本工具，"
                + "返回的 key/field 必须原样使用，不要自己拼字段名。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "string");
        source.put("description", "数据源标识（即来源单据的表名 key）；不传则只返回数据源清单");
        props.put("source", source);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
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
        String source = params == null || params.get("source") == null ? null : String.valueOf(params.get("source")).trim();
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            if (source == null || source.isEmpty()) {
                List<Map<String, Object>> sources = aggregator.listSources();
                out.put("sources", sources);
                out.put("hint", "选定数据源后，再用 source 参数查询它的字段与可关联单据。");
                return out;
            }
            out.put("source", source);
            out.put("fields", aggregator.listFields(source));
            out.put("joins", aggregator.listJoins(source));
            out.put("hint", "字段引用写法：主表字段直接用 field；关联单字段用「joinKey.field」。");
            return out;
        } catch (Exception e) {
            return support.fail("数据源查询失败：" + (e.getMessage() == null ? "请稍后重试" : e.getMessage()));
        }
    }
}
