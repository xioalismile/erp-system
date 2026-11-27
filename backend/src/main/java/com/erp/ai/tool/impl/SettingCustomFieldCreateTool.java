package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.customfield.entity.CustomField;
import com.erp.customfield.service.CustomFieldService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：新增自定义字段定义（设置中心 → 自定义字段设置），走强制确认。
 * fieldKey 由后端自动生成（custom_N），不接受 AI 传值。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingCustomFieldCreateTool implements AiTool {

    private final CustomFieldService customFieldService;
    /** 自建 ObjectMapper 必须注册 JavaTimeModule（历史坑：AI 链路 LocalDate 序列化崩溃） */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String getName() {
        return "setting_custom_field_create";
    }

    @Override
    public String getDescription() {
        return "给某个实体新增一个自定义字段（如『给物料档案加一个字段』）。执行前会弹确认卡片。"
                + "必填参数：entityType(实体，如 material)、fieldLabel(字段名称)、fieldType(字段类型)。"
                + "fieldType 取值：text 单行文本 / textarea 多行文本 / number 数字 / select 下拉选择 / "
                + "date 日期 / datetime 日期时间 / switch 开关 / formula 公式计算。"
                + "select 类型必须提供 fieldOptions（[{label,value}]）；formula 类型必须提供 formulaExpr（如 {{custom_1}}*0.13）。"
                + "isRequired=true 表示必填；isListShow=true 表示在列表显示该列；isSearch=true 表示支持按该字段搜索。"
                + "字段标识(fieldKey)由系统自动生成，无需也无法指定。新增前建议先用 setting_custom_field_list 查重。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("entityType", Map.of("type", "string", "description", "实体类型，如 material/customer/sale_order"));
        props.put("fieldLabel", Map.of("type", "string", "description", "字段名称，如『小助手创建』"));
        props.put("fieldType", Map.of("type", "string",
                "description", "字段类型：text/textarea/number/select/date/datetime/switch/formula"));
        props.put("isRequired", Map.of("type", "boolean", "description", "是否必填，默认 false"));
        props.put("isListShow", Map.of("type", "boolean", "description", "是否在列表显示，默认 false"));
        props.put("isSearch", Map.of("type", "boolean", "description", "是否可搜索，默认 false"));
        props.put("fieldOptions", Map.of("type", "array",
                "description", "select 类型必填，选项数组，如 [{label:'选项1',value:'option1'}]",
                "items", Map.of("type", "object")));
        props.put("precisionVal", Map.of("type", "integer", "description", "number/formula 类型小数位，默认 2"));
        props.put("formulaExpr", Map.of("type", "string", "description", "formula 类型必填，公式表达式"));
        props.put("sectionName", Map.of("type", "string", "description", "表单分区标题，默认『自定义信息』"));
        props.put("sortOrder", Map.of("type", "integer", "description", "排序，默认 0"));
        props.put("placeholder", Map.of("type", "string", "description", "输入提示，可空"));
        props.put("remark", Map.of("type", "string", "description", "备注，可空"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("entityType", "fieldLabel", "fieldType"));
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
            String fieldLabel = SettingSupport.str(params == null ? null : params.get("fieldLabel"));
            String fieldType = SettingSupport.str(params == null ? null : params.get("fieldType"));

            if (entityType == null || fieldLabel == null || fieldType == null) {
                return Map.of("success", false, "message", "参数缺失：需要 entityType、fieldLabel、fieldType");
            }
            if (!SettingSupport.CUSTOM_FIELD_ENTITY_TYPES.contains(entityType)) {
                return Map.of("success", false, "message", "不支持的实体类型: " + entityType);
            }
            if (!SettingSupport.CUSTOM_FIELD_TYPES.contains(fieldType)) {
                return Map.of("success", false, "message", "不支持的字段类型: " + fieldType);
            }

            CustomField f = new CustomField();
            f.setEntityType(entityType);
            f.setFieldLabel(fieldLabel);
            f.setFieldType(fieldType);
            f.setIsRequired(SettingSupport.bool(params.get("isRequired"), false) ? 1 : 0);
            f.setIsListShow(SettingSupport.bool(params.get("isListShow"), false) ? 1 : 0);
            f.setIsSearch(SettingSupport.bool(params.get("isSearch"), false) ? 1 : 0);
            f.setIsEnabled(1);
            f.setSectionName(SettingSupport.str(params.get("sectionName")) == null
                    ? "自定义信息" : SettingSupport.str(params.get("sectionName")));
            Integer sortOrder = SettingSupport.integer(params.get("sortOrder"));
            f.setSortOrder(sortOrder == null ? 0 : sortOrder);
            f.setPlaceholder(SettingSupport.str(params.get("placeholder")));
            f.setRemark(SettingSupport.str(params.get("remark")));
            if ("number".equals(fieldType) || "formula".equals(fieldType)) {
                Integer precision = SettingSupport.integer(params.get("precisionVal"));
                f.setPrecisionVal(precision == null ? 2 : precision);
            }
            if ("select".equals(fieldType)) {
                Object opts = params.get("fieldOptions");
                if (opts == null) {
                    return Map.of("success", false, "message", "下拉选择类型必须提供 fieldOptions 选项");
                }
                f.setFieldOptions(mapper.writeValueAsString(opts));
            }
            if ("formula".equals(fieldType)) {
                String expr = SettingSupport.str(params.get("formulaExpr"));
                if (expr == null) {
                    return Map.of("success", false, "message", "公式类型必须提供 formulaExpr 公式表达式");
                }
                f.setFormulaExpr(expr);
            }

            CustomField saved = customFieldService.saveField(f);
            log.info("[AI设置] 用户{}新增自定义字段 entity={} label={} type={} key={}",
                    scope != null ? scope.getUsername() : "?", entityType, fieldLabel, fieldType, saved.getFieldKey());

            String entityLabel = SettingSupport.entityLabel(entityType);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("fieldId", saved.getFieldId());
            result.put("fieldKey", saved.getFieldKey());
            result.put("message", "已为【" + entityLabel + "】新增自定义字段「" + fieldLabel + "」（标识 "
                    + saved.getFieldKey() + "，类型 " + SettingSupport.fieldTypeLabel(fieldType)
                    + (f.getIsRequired() == 1 ? "，必填" : "")
                    + (f.getIsListShow() == 1 ? "，列表显示" : "")
                    + (f.getIsSearch() == 1 ? "，可搜索" : "") + "）。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 新增自定义字段失败", e);
            return Map.of("success", false, "message", "新增自定义字段失败: " + e.getMessage());
        }
    }
}
