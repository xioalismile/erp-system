package com.erp.ai.tool.impl;

import com.erp.ai.service.AiCrudAgentService;
import com.erp.ai.tool.AiTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuerySaleQuoteTool implements AiTool {

    private final AiCrudAgentService aiCrudAgentService;

    @Override
    public String getName() {
        return "query_sale_quote";
    }

    @Override
    public String getDescription() {
        return "查询销售报价单，支持按报价单号、客户名称模糊搜索。返回报价单号、客户名称、金额、状态等信息。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "搜索关键词，支持报价单号、客户名称的模糊匹配。如果不填则返回所有列表。");
        props.put("keyword", keyword);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of());
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return aiCrudAgentService.query("QUERY_SALE_QUOTE", params);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, com.erp.ai.dto.AiDataScope scope) {
        return aiCrudAgentService.query("QUERY_SALE_QUOTE", params, scope);
    }
}
