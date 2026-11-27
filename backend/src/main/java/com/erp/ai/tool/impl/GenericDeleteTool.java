package com.erp.ai.tool.impl;

import com.erp.ai.service.AiCrudAgentService;
import com.erp.ai.tool.AiTool;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GenericDeleteTool implements AiTool {

    private final String toolName;
    private final String description;
    private final String intent;
    private final String entityName;
    private final String primaryKeyField;
    private final AiCrudAgentService aiCrudAgentService;

    public GenericDeleteTool(String toolName, String description, String intent,
                            String entityName, String primaryKeyField,
                            AiCrudAgentService aiCrudAgentService) {
        this.toolName = toolName;
        this.description = description;
        this.intent = intent;
        this.entityName = entityName;
        this.primaryKeyField = primaryKeyField;
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

        Map<String, Object> idParam = new LinkedHashMap<>();
        idParam.put("type", "integer");
        idParam.put("description", "要删除的" + entityName + "的ID（" + primaryKeyField + "）");
        props.put(primaryKeyField, idParam);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of(primaryKeyField));
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return aiCrudAgentService.delete(intent, params, userId);
    }
}
