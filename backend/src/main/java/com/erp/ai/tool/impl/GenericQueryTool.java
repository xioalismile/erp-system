package com.erp.ai.tool.impl;

import com.erp.ai.service.AiCrudAgentService;
import com.erp.ai.tool.AiTool;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GenericQueryTool implements AiTool {

    private final String toolName;
    private final String description;
    private final String intent;
    private final List<String> searchFields;
    private final List<String> displayFields;
    /** 允许按条件筛选的字段白名单（空 = 只支持关键词模糊搜） */
    private final List<String> filterFields;
    private final String entityName;
    private final AiCrudAgentService aiCrudAgentService;

    public GenericQueryTool(String toolName, String description, String intent,
                            String entityName, List<String> searchFields, List<String> displayFields,
                            List<String> filterFields,
                            AiCrudAgentService aiCrudAgentService) {
        this.toolName = toolName;
        this.description = description;
        this.intent = intent;
        this.entityName = entityName;
        this.searchFields = searchFields;
        this.displayFields = displayFields;
        this.filterFields = filterFields;
        this.aiCrudAgentService = aiCrudAgentService;
    }

    @Override
    public String getName() {
        return toolName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        if (searchFields != null && !searchFields.isEmpty()) {
            Map<String, Object> keyword = new LinkedHashMap<>();
            keyword.put("type", "string");
            String searchDesc = String.join("、", searchFields);
            keyword.put("description", "搜索关键词，支持" + searchDesc + "的模糊匹配。如果不填则返回所有列表。");
            props.put("keyword", keyword);
        }

        if (filterFields != null && !filterFields.isEmpty()) {
            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put("type", "array");
            filters.put("description", "按条件精确筛选（可选，可与 keyword 同用）。每项形如 {\"field\":\"status\",\"op\":\"eq\",\"value\":\"草稿\"}；"
                    + "可用字段：" + String.join("、", filterFields)
                    + "；op 可用 eq/ne/gt/ge/lt/le/like/between（between 的 value 传 [起始,结束]）。"
                    + "例：[{\"field\":\"status\",\"op\":\"eq\",\"value\":\"草稿\"},"
                    + "{\"field\":\"orderDate\",\"op\":\"between\",\"value\":[\"2026-07-01\",\"2026-07-31\"]}]。"
                    + "用户说「按状态/日期区间/金额区间/某个客户筛选」时用这个参数，不要把这些条件拼进 keyword。");
            Map<String, Object> items = new LinkedHashMap<>();
            items.put("type", "object");
            items.put("description", "field=字段名，op=比较方式，value=值");
            filters.put("items", items);
            props.put("filters", filters);
        }
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
        return aiCrudAgentService.query(intent, params);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, com.erp.ai.dto.AiDataScope scope) {
        return aiCrudAgentService.query(intent, params, scope);
    }
}
