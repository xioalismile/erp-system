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
public class QueryCustomerTool implements AiTool {

    private final AiCrudAgentService aiCrudAgentService;

    @Override
    public String getName() {
        return "query_customer";
    }

    @Override
    public String getDescription() {
        return "查询客户档案信息，支持按客户名称、编码、联系人、电话模糊搜索。用于了解客户详情、查找客户ID等。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "搜索关键词，支持客户名称、编码、联系人、电话的模糊匹配。如果不填则返回所有客户列表。");
        props.put("keyword", keyword);

        Map<String, Object> customerLevel = new LinkedHashMap<>();
        customerLevel.put("type", "string");
        customerLevel.put("description", "客户等级过滤，如：VIP客户、普通客户等");
        props.put("customerLevel", customerLevel);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("type", "integer");
        page.put("description", "页码，从1开始，默认1");
        props.put("page", page);

        Map<String, Object> size = new LinkedHashMap<>();
        size.put("type", "integer");
        size.put("description", "每页条数，默认100，最大500");
        props.put("size", size);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of());
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return aiCrudAgentService.query("QUERY_BASEDATA_CUSTOMER", params);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, com.erp.ai.dto.AiDataScope scope) {
        return aiCrudAgentService.query("QUERY_BASEDATA_CUSTOMER", params, scope);
    }
}
