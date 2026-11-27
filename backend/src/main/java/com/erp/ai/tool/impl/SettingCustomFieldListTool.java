package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.customfield.entity.CustomField;
import com.erp.customfield.service.CustomFieldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询某实体的自定义字段定义（设置中心 → 自定义字段设置）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingCustomFieldListTool implements AiTool {

    private final CustomFieldService customFieldService;

    @Override
    public String getName() {
        return "setting_custom_field_list";
    }

    @Override
    public String getDescription() {
        return "查询某个实体（如物料/客户/销售订单）已配置的自定义字段清单，含字段标识、名称、类型、是否必填、是否列表显示、是否可搜索。"
                + "新增或修改自定义字段前，应先调用本工具查重并拿到 fieldId。"
                + "entityType 取值：material(物料)、customer(客户)、supplier(供应商)、sale_order(销售订单) 等。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("entityType", Map.of("type", "string",
                "description", "实体类型，如 material / customer / supplier / sale_order / sale_order_item 等"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("entityType"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            String entityType = SettingSupport.str(params == null ? null : params.get("entityType"));
            if (entityType == null) {
                return Map.of("success", false, "message", "参数缺失：需要 entityType");
            }
            if (!SettingSupport.CUSTOM_FIELD_ENTITY_TYPES.contains(entityType)) {
                return Map.of("success", false, "message",
                        "不支持的实体类型: " + entityType + "。可用类型: "
                                + String.join("、", SettingSupport.CUSTOM_FIELD_ENTITY_TYPES));
            }
            List<CustomField> list = customFieldService.listByEntity(entityType);
            List<Map<String, Object>> items = new ArrayList<>();
            if (list != null) {
                for (CustomField f : list) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("fieldId", f.getFieldId());
                    item.put("fieldKey", f.getFieldKey());
                    item.put("fieldLabel", f.getFieldLabel());
                    item.put("fieldType", f.getFieldType());
                    item.put("fieldTypeLabel", SettingSupport.fieldTypeLabel(f.getFieldType()));
                    item.put("isRequired", f.getIsRequired() != null && f.getIsRequired() == 1);
                    item.put("isListShow", f.getIsListShow() != null && f.getIsListShow() == 1);
                    item.put("isSearch", f.getIsSearch() != null && f.getIsSearch() == 1);
                    item.put("isEnabled", f.getIsEnabled() != null && f.getIsEnabled() == 1);
                    item.put("sectionName", f.getSectionName());
                    item.put("sortOrder", f.getSortOrder());
                    item.put("fieldOptions", f.getFieldOptions());
                    items.add(item);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("entityType", entityType);
            result.put("entityLabel", SettingSupport.entityLabel(entityType));
            result.put("total", items.size());
            result.put("items", items);
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询自定义字段失败", e);
            return Map.of("success", false, "message", "查询自定义字段失败: " + e.getMessage());
        }
    }
}
