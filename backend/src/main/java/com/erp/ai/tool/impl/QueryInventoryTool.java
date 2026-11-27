package com.erp.ai.tool.impl;

import com.erp.ai.service.AiInventoryService;
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
public class QueryInventoryTool implements AiTool {

    private final AiInventoryService aiInventoryService;

    @Override
    public String getName() {
        return "query_inventory";
    }

    @Override
    public String getDescription() {
        return "查询实时库存（物料、仓库、数量）。**必须带上物料编码或物料名称**：查一个/多个物料时把编码用逗号一次传入"
                + "（如 \"G10401,D0001\"）；不带任何物料条件时只会返回按物料编码排序的前 50 条（相当于全量列表，属于答非所问）。"
                + "返回字段：inventoryId / materialCode / materialName / stockName（仓库）/ quantity / safetyStock / warning。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> materialCode = new LinkedHashMap<>();
        materialCode.put("type", "string");
        materialCode.put("description", "物料编码，可多个（用逗号/顿号分隔），按编码精确查询库存");
        props.put("materialCode", materialCode);

        Map<String, Object> materialName = new LinkedHashMap<>();
        materialName.put("type", "string");
        materialName.put("description", "物料名称关键词，可多个（用逗号/顿号分隔），按名称模糊查询库存");
        props.put("materialName", materialName);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return aiInventoryService.queryInventory(params);
    }
}
