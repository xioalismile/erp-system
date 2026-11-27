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
 * 写工具：复制打印模板（设置中心 → 打印模板设置），走强制确认。
 * 副本默认非默认模板。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingPrintTemplateCopyTool implements AiTool {

    private final PrintTemplateService printTemplateService;

    @Override
    public String getName() {
        return "setting_print_template_copy";
    }

    @Override
    public String getDescription() {
        return "复制一个打印模板，生成新模板（执行前会弹确认卡片）。副本默认不是默认模板。"
                + "必填参数：id(被复制的模板ID)。可选参数：newName(新模板名称，不传则用『原名+副本』)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "integer", "description", "被复制的模板ID，必填"));
        props.put("newName", Map.of("type", "string", "description", "新模板名称，可选"));
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
            String newName = SettingSupport.str(params == null ? null : params.get("newName"));
            if (id == null) {
                return Map.of("success", false, "message", "参数缺失：需要 id");
            }
            PrintTemplate source = printTemplateService.getById(id);
            if (source == null) {
                return Map.of("success", false, "message", "打印模板不存在: " + id);
            }
            Long newId = printTemplateService.copyTemplate(id, newName);
            if (newId == null) {
                return Map.of("success", false, "message", "复制打印模板失败: " + id);
            }
            log.info("[AI设置] 用户{}复制打印模板 源id={} 新id={} 新名称={}",
                    scope != null ? scope.getUsername() : "?", id, newId, newName);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("newId", newId);
            result.put("message", "已复制模板「" + source.getTemplateName() + "」为新模板（新ID " + newId + "）。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 复制打印模板失败", e);
            return Map.of("success", false, "message", "复制打印模板失败: " + e.getMessage());
        }
    }
}
