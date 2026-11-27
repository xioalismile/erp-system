package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.customfield.entity.CustomField;
import com.erp.customfield.mapper.CustomFieldMapper;
import com.erp.customfield.service.CustomFieldService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：修改自定义字段定义（设置中心 → 自定义字段设置），走强制确认。
 * 只允许改可维护项；fieldKey 不可修改（值存储键）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingCustomFieldUpdateTool implements AiTool {

    private final CustomFieldService customFieldService;
    private final CustomFieldMapper customFieldMapper;
    /** 自建 ObjectMapper 必须注册 JavaTimeModule（历史坑：AI 链路 LocalDate 序列化崩溃） */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String getName() {
        return "setting_custom_field_update";
    }

    @Override
    public String getDescription() {
        return "修改某个已存在的自定义字段（如改名称、改必填、改是否列表显示、改是否可搜索、停用等）。执行前会弹确认卡片。"
                + "必填参数：fieldId（先用 setting_custom_field_list 查到）。其余参数只传需要修改的项，未传的保持不变。"
                + "字段标识(fieldKey)与所属实体(entityType)不可修改。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fieldId", Map.of("type", "integer", "description", "字段定义ID（必填）"));
        props.put("fieldLabel", Map.of("type", "string", "description", "新的字段名称"));
        props.put("isRequired", Map.of("type", "boolean", "description", "是否必填"));
        props.put("isListShow", Map.of("type", "boolean", "description", "是否在列表显示"));
        props.put("isSearch", Map.of("type", "boolean", "description", "是否可搜索"));
        props.put("isEnabled", Map.of("type", "boolean", "description", "是否启用（false=停用该字段）"));
        props.put("sectionName", Map.of("type", "string", "description", "表单分区标题"));
        props.put("sortOrder", Map.of("type", "integer", "description", "排序"));
        props.put("placeholder", Map.of("type", "string", "description", "输入提示"));
        props.put("remark", Map.of("type", "string", "description", "备注"));
        props.put("precisionVal", Map.of("type", "integer", "description", "number/formula 小数位"));
        props.put("fieldOptions", Map.of("type", "array",
                "description", "select 类型的新选项数组 [{label,value}]", "items", Map.of("type", "object")));
        props.put("formulaExpr", Map.of("type", "string", "description", "formula 类型的新公式表达式"));
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
            List<String> changed = new ArrayList<>();
            if (params.containsKey("fieldLabel") && SettingSupport.str(params.get("fieldLabel")) != null) {
                f.setFieldLabel(SettingSupport.str(params.get("fieldLabel")));
                changed.add("名称=" + f.getFieldLabel());
            }
            if (params.containsKey("isRequired")) {
                f.setIsRequired(SettingSupport.bool(params.get("isRequired"), false) ? 1 : 0);
                changed.add("必填=" + (f.getIsRequired() == 1 ? "是" : "否"));
            }
            if (params.containsKey("isListShow")) {
                f.setIsListShow(SettingSupport.bool(params.get("isListShow"), false) ? 1 : 0);
                changed.add("列表显示=" + (f.getIsListShow() == 1 ? "是" : "否"));
            }
            if (params.containsKey("isSearch")) {
                f.setIsSearch(SettingSupport.bool(params.get("isSearch"), false) ? 1 : 0);
                changed.add("可搜索=" + (f.getIsSearch() == 1 ? "是" : "否"));
            }
            if (params.containsKey("isEnabled")) {
                f.setIsEnabled(SettingSupport.bool(params.get("isEnabled"), true) ? 1 : 0);
                changed.add("启用=" + (f.getIsEnabled() == 1 ? "是" : "否"));
            }
            if (params.containsKey("sectionName") && SettingSupport.str(params.get("sectionName")) != null) {
                f.setSectionName(SettingSupport.str(params.get("sectionName")));
                changed.add("分区=" + f.getSectionName());
            }
            if (params.containsKey("sortOrder")) {
                Integer so = SettingSupport.integer(params.get("sortOrder"));
                if (so != null) {
                    f.setSortOrder(so);
                    changed.add("排序=" + so);
                }
            }
            if (params.containsKey("placeholder")) {
                f.setPlaceholder(SettingSupport.str(params.get("placeholder")));
                changed.add("提示=" + (f.getPlaceholder() == null ? "(空)" : f.getPlaceholder()));
            }
            if (params.containsKey("remark")) {
                f.setRemark(SettingSupport.str(params.get("remark")));
                changed.add("备注=" + (f.getRemark() == null ? "(空)" : f.getRemark()));
            }
            if (params.containsKey("precisionVal")) {
                Integer pv = SettingSupport.integer(params.get("precisionVal"));
                if (pv != null) {
                    f.setPrecisionVal(pv);
                    changed.add("小数位=" + pv);
                }
            }
            if (params.containsKey("fieldOptions")) {
                Object opts = params.get("fieldOptions");
                if (opts != null) {
                    f.setFieldOptions(mapper.writeValueAsString(opts));
                    changed.add("选项已更新");
                }
            }
            if (params.containsKey("formulaExpr") && SettingSupport.str(params.get("formulaExpr")) != null) {
                f.setFormulaExpr(SettingSupport.str(params.get("formulaExpr")));
                changed.add("公式已更新");
            }
            if (changed.isEmpty()) {
                return Map.of("success", false, "message", "没有需要修改的内容");
            }

            CustomField saved = customFieldService.saveField(f);
            log.info("[AI设置] 用户{}修改自定义字段 fieldId={} key={} 变更={}",
                    scope != null ? scope.getUsername() : "?", fieldId, f.getFieldKey(), changed);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已修改字段「" + saved.getFieldLabel() + "」（" + String.join("，", changed) + "）");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 修改自定义字段失败", e);
            return Map.of("success", false, "message", "修改自定义字段失败: " + e.getMessage());
        }
    }
}
