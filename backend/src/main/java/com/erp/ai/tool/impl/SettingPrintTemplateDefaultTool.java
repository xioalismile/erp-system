package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.print.template.entity.PrintTemplate;
import com.erp.print.template.service.PrintTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：把某打印模板设为单据默认模板（设置中心 → 打印模板设置），走强制确认。
 * 同单据类型下原默认模板会被自动取消默认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingPrintTemplateDefaultTool implements AiTool {

    private final PrintTemplateService printTemplateService;

    @Override
    public String getName() {
        return "setting_print_template_default";
    }

    @Override
    public String getDescription() {
        return "把某个打印模板设为其单据类型的默认模板（执行前会弹确认卡片）。"
                + "设置后同单据类型下原默认模板会自动取消默认。"
                + "必填参数：id(模板ID，先通过 setting_print_template_list 确认)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "integer", "description", "模板ID，必填"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("id"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Long id = SettingSupport.longVal(params == null ? null : params.get("id"));
            if (id == null) {
                return Map.of("success", false, "message", "参数缺失：需要 id");
            }
            PrintTemplate template = printTemplateService.getById(id);
            if (template == null) {
                return Map.of("success", false, "message", "打印模板不存在: " + id);
            }
            printTemplateService.setDefault(id);
            log.info("[AI设置] 用户{}将打印模板设为默认 id={} name={} docType={}",
                    scope != null ? scope.getUsername() : "?", id, template.getTemplateName(), template.getDocType());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已将模板「" + template.getTemplateName() + "」设为【"
                    + template.getDocType() + "】的默认打印模板。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 设置默认打印模板失败", e);
            return Map.of("success", false, "message", "设置默认打印模板失败: " + e.getMessage());
        }
    }
}
