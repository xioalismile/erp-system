package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.customfield.entity.CustomField;
import com.erp.customfield.mapper.CustomFieldMapper;
import com.erp.customfield.service.CustomFieldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：删除自定义字段定义（设置中心 → 自定义字段设置），走强制确认。
 * 警告：会同时删除该字段已录入的全部值，不可恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingCustomFieldDeleteTool implements AiTool {

    private final CustomFieldService customFieldService;
    private final CustomFieldMapper customFieldMapper;

    @Override
    public String getName() {
        return "setting_custom_field_delete";
    }

    @Override
    public String getDescription() {
        return "删除一个自定义字段定义（⚠ 会同时删除该字段已录入的全部值，不可恢复）。执行前会弹确认卡片。"
                + "必填参数：fieldId（先用 setting_custom_field_list 查到）。"
                + "执行前请向用户复述要删除的字段名称与所属实体，确认无误后再调用。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fieldId", Map.of("type", "integer", "description", "要删除的字段定义ID（必填）"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("fieldId"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Long fieldId = SettingSupport.longVal(params == null ? null : params.get("fieldId"));
            if (fieldId == null) {
                return Map.of("success", false, "message", "参数缺失：需要 fieldId");
            }
            CustomField f = customFieldMapper.selectById(fieldId);
            if (f == null) {
                return Map.of("success", false, "message", "字段不存在: fieldId=" + fieldId);
            }
            String label = f.getFieldLabel();
            String entityLabel = SettingSupport.entityLabel(f.getEntityType());
            boolean ok = customFieldService.deleteField(fieldId);
            log.info("[AI设置] 用户{}删除自定义字段 fieldId={} key={} label={}",
                    scope != null ? scope.getUsername() : "?", fieldId, f.getFieldKey(), label);
            if (!ok) {
                return Map.of("success", false, "message", "删除失败: " + label);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已删除【" + entityLabel + "】的自定义字段「" + label
                    + "」（标识 " + f.getFieldKey() + "），该字段已录入的值一并清除，不可恢复。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 删除自定义字段失败", e);
            return Map.of("success", false, "message", "删除自定义字段失败: " + e.getMessage());
        }
    }
}
