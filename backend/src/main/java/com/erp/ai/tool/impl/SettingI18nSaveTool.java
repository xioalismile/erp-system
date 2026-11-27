package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.system.entity.SysI18n;
import com.erp.system.service.I18nService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：批量保存多语言译文（设置中心 → 语言管理），走强制确认。
 * saveTranslations 按 key+lang 幂等 upsert，只提交需要改的行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingI18nSaveTool implements AiTool {

    private final I18nService i18nService;
    /** 自建 ObjectMapper 必须注册 JavaTimeModule（历史坑：AI 链路 LocalDate 序列化崩溃） */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String getName() {
        return "setting_i18n_save";
    }

    @Override
    public String getDescription() {
        return "批量保存多语言译文（设置中心 → 语言管理，执行前会弹确认卡片）。按 翻译键+语言 幂等新增/更新，只提交需要改的行。"
                + "必填参数：rows(数组)，每项形如 {i18nKey:'common.save', lang:'en', text:'Save', module:'common'}。"
                + "lang 可为 zh-CN(原文)/en 等；module 可选，默认 common。单次最多 200 条，译文长度不超过 500 字。"
                + "保存前建议先用 setting_i18n_search 确认翻译键。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> rowProps = new LinkedHashMap<>();
        rowProps.put("type", "object");
        Map<String, Object> rowFields = new LinkedHashMap<>();
        rowFields.put("i18nKey", Map.of("type", "string", "description", "翻译键，如 common.save"));
        rowFields.put("lang", Map.of("type", "string", "description", "语言，如 zh-CN/en"));
        rowFields.put("text", Map.of("type", "string", "description", "译文；空字符串表示缺词"));
        rowFields.put("module", Map.of("type", "string", "description", "分组，可选，默认 common"));
        rowProps.put("properties", rowFields);
        rowProps.put("required", List.of("i18nKey", "lang", "text"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("rows", Map.of("type", "array", "description",
                "译文行数组，每项 {i18nKey, lang, text, module}", "items", rowProps));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("rows"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Object rowsObj = params == null ? null : params.get("rows");
            if (!(rowsObj instanceof List<?> rawRows) || rawRows.isEmpty()) {
                return Map.of("success", false, "message", "参数缺失：rows 必须是非空数组");
            }
            List<SysI18n> rows = new ArrayList<>();
            int idx = 0;
            for (Object o : rawRows) {
                idx++;
                SysI18n row;
                if (o instanceof SysI18n s) {
                    row = s;
                } else if (o instanceof Map) {
                    row = mapper.convertValue(o, SysI18n.class);
                } else {
                    return Map.of("success", false, "message", "第 " + idx + " 项译文格式不正确");
                }
                if (SettingSupport.str(row.getI18nKey()) == null || SettingSupport.str(row.getLang()) == null) {
                    return Map.of("success", false, "message", "第 " + idx + " 项译文缺少 i18nKey 或 lang");
                }
                rows.add(row);
            }

            int affected = i18nService.saveTranslations(rows);
            log.info("[AI设置] 用户{}批量保存多语言译文 {} 条，实际写入 {} 条",
                    scope != null ? scope.getUsername() : "?", rows.size(), affected);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("affected", affected);
            result.put("message", "已保存 " + affected + " 条译文。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 保存多语言译文失败", e);
            return Map.of("success", false, "message", "保存多语言译文失败: " + e.getMessage());
        }
    }
}
