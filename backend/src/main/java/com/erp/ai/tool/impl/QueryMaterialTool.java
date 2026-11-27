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
public class QueryMaterialTool implements AiTool {

    private final AiCrudAgentService aiCrudAgentService;

    @Override
    public String getName() {
        return "query_material";
    }

    @Override
    public String getDescription() {
        return "查询物料/商品信息，支持按物料名称、编码、规格型号模糊搜索。用于了解物料详情、查找物料ID等。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "搜索关键词，支持物料名称、编码、规格型号的模糊匹配。如果不填则返回所有物料列表。");
        props.put("keyword", keyword);

        Map<String, Object> materialType = new LinkedHashMap<>();
        materialType.put("type", "string");
        materialType.put("description", "物料类型过滤，如：成品、半成品、原材料、包材等");
        props.put("materialType", materialType);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of());
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return aiCrudAgentService.query("QUERY_BASEDATA_MATERIAL", params);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, com.erp.ai.dto.AiDataScope scope) {
        return aiCrudAgentService.query("QUERY_BASEDATA_MATERIAL", params, scope);
    }
}
