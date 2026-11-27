package com.erp.ai.tool.impl;

import com.erp.ai.service.AiCrudAgentService;
import com.erp.ai.tool.AiTool;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GenericCreateTool implements AiTool {

    private final String toolName;
    private final String description;
    private final String intent;
    private final String entityName;
    /** 该实体是否为"带明细的单据"（实体里有 items 列表）→ 必须一起给明细，否则系统拒绝保存空单 */
    private final boolean requiresItems;
    private final AiCrudAgentService aiCrudAgentService;

    public GenericCreateTool(String toolName, String description, String intent,
                             String entityName, boolean requiresItems, AiCrudAgentService aiCrudAgentService) {
        this.toolName = toolName;
        this.description = description;
        this.intent = intent;
        this.entityName = entityName;
        this.requiresItems = requiresItems;
        this.aiCrudAgentService = aiCrudAgentService;
    }

    @Override
    public String getName() {
        return toolName;
    }

    @Override
    public String getDescription() {
        if (!requiresItems) return description;
        // 带明细的单据：明确要求"一次调用里同时给表头 + 明细"。
        // 历史问题（2026-10-21 用户实测）：只给了客户，明细没给，接口照样返回"创建成功"，
        // 用户打开单据发现内容为空（没有物料/数量/金额）。
        return "新增一张" + entityName + "（含明细）。必须**同时**提供 data（表头，如客户 customerName、单据日期）"
                + "和 items（明细行数组，每行至少给物料编码或名称 + 数量；有单价就一并给）。"
                + "系统会按明细自动生成单号并重算金额；**没有明细的单据不会保存**。"
                + "客户/物料先用对应 query_ 工具查准名称或编码，禁止编造。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "object");
        data.put("description", "要创建的" + entityName + "数据对象，包含各字段的键值对。例如创建客户时传 {customerName: '张三', contactPhone: '13800000000'}");
        props.put("data", data);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("data"));

        if (requiresItems) {
            Map<String, Object> itemProps = new LinkedHashMap<>();
            itemProps.put("materialCode", Map.of("type", "string", "description", "物料编码（与 materialName 至少给一个）"));
            itemProps.put("materialName", Map.of("type", "string", "description", "物料名称（与 materialCode 至少给一个）"));
            itemProps.put("quantity", Map.of("type", "number", "description", "数量，必须大于 0"));
            itemProps.put("unitPrice", Map.of("type", "number", "description", "单价，用户提到价格时必填"));
            itemProps.put("taxRate", Map.of("type", "number", "description", "税率(%)，可选，不给则按物料档案税率"));
            itemProps.put("discountRate", Map.of("type", "number", "description", "折扣率(%)，可选"));

            Map<String, Object> items = new LinkedHashMap<>();
            items.put("type", "array");
            items.put("description", "明细行（至少一行）。每行必须有物料 + 数量，系统按它重算金额");
            items.put("items", Map.of("type", "object", "properties", itemProps));
            props.put("items", items);
            params.put("required", List.of("data", "items"));
        }
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return aiCrudAgentService.create(intent, params, userId);
    }
}
