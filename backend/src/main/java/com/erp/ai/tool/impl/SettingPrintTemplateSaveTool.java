package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.print.template.dto.PrintTemplateDTO;
import com.erp.print.template.service.PrintTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：新增/修改打印模板（设置中心 → 打印模板设置），走强制确认。
 * id 为空走 saveTemplate（新增），否则走 updateTemplate（修改，只覆盖传入字段）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingPrintTemplateSaveTool implements AiTool {

    private final PrintTemplateService printTemplateService;

    @Override
    public String getName() {
        return "setting_print_template_save";
    }

    @Override
    public String getDescription() {
        return "新增或修改打印模板（执行前会弹确认卡片）。不传 id 为新增，传 id 为修改（只改传入的字段）。"
                + "新增必填：templateName(模板名称)、docType(单据类型)。"
                + "可选：id、templateCode(模板编码)、templateType、templateContent(模板内容HTML)、templateStyle、"
                + "isDefault(0否/1是默认模板)、pageSize(如A4)、pageOrientation(portrait/landscape)、"
                + "marginTop/marginBottom/marginLeft/marginRight(页边距)、headerContent、footerContent、"
                + "watermark、watermarkEnabled(0/1)、status(1启用/0停用)、sortOrder、remark。"
                + "改前建议先用 setting_print_template_list 核对 id。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", Map.of("type", "integer", "description", "模板ID，修改时必传；新增不传"));
        props.put("templateCode", Map.of("type", "string", "description", "模板编码，可空"));
        props.put("templateName", Map.of("type", "string", "description", "模板名称，新增必填"));
        props.put("docType", Map.of("type", "string", "description", "单据类型，新增必填，如 SALE_ORDER"));
        props.put("templateType", Map.of("type", "string", "description", "模板类型，可空"));
        props.put("templateContent", Map.of("type", "string", "description", "模板内容（HTML），可空"));
        props.put("templateStyle", Map.of("type", "string", "description", "模板样式，可空"));
        props.put("isDefault", Map.of("type", "integer", "description", "是否默认模板：1是/0否"));
        props.put("pageSize", Map.of("type", "string", "description", "纸张大小，如 A4/A5"));
        props.put("pageOrientation", Map.of("type", "string", "description", "纸张方向：portrait纵向/landscape横向"));
        props.put("marginTop", Map.of("type", "integer", "description", "上边距"));
        props.put("marginBottom", Map.of("type", "integer", "description", "下边距"));
        props.put("marginLeft", Map.of("type", "integer", "description", "左边距"));
        props.put("marginRight", Map.of("type", "integer", "description", "右边距"));
        props.put("headerContent", Map.of("type", "string", "description", "页眉内容"));
        props.put("footerContent", Map.of("type", "string", "description", "页脚内容"));
        props.put("watermark", Map.of("type", "string", "description", "水印文字"));
        props.put("watermarkEnabled", Map.of("type", "integer", "description", "是否启用水印：1是/0否"));
        props.put("status", Map.of("type", "integer", "description", "状态：1启用/0停用"));
        props.put("sortOrder", Map.of("type", "integer", "description", "排序"));
        props.put("remark", Map.of("type", "string", "description", "备注"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Map<String, Object> p = params == null ? Map.of() : params;
            Long id = SettingSupport.longVal(p.get("id"));
            String templateName = SettingSupport.str(p.get("templateName"));
            String docType = SettingSupport.str(p.get("docType"));

            if (id == null) {
                if (templateName == null || docType == null) {
                    return Map.of("success", false, "message", "新增打印模板需要 templateName、docType");
                }
            }

            PrintTemplateDTO dto = new PrintTemplateDTO();
            dto.setId(id);
            if (p.containsKey("templateCode")) dto.setTemplateCode(SettingSupport.str(p.get("templateCode")));
            if (p.containsKey("templateName")) dto.setTemplateName(templateName);
            if (p.containsKey("docType")) dto.setDocType(docType);
            if (p.containsKey("templateType")) dto.setTemplateType(SettingSupport.str(p.get("templateType")));
            if (p.containsKey("templateContent")) dto.setTemplateContent(SettingSupport.str(p.get("templateContent")));
            if (p.containsKey("templateStyle")) dto.setTemplateStyle(SettingSupport.str(p.get("templateStyle")));
            if (p.containsKey("pageSize")) dto.setPageSize(SettingSupport.str(p.get("pageSize")));
            if (p.containsKey("pageOrientation")) dto.setPageOrientation(SettingSupport.str(p.get("pageOrientation")));
            if (p.containsKey("headerContent")) dto.setHeaderContent(SettingSupport.str(p.get("headerContent")));
            if (p.containsKey("footerContent")) dto.setFooterContent(SettingSupport.str(p.get("footerContent")));
            if (p.containsKey("watermark")) dto.setWatermark(SettingSupport.str(p.get("watermark")));
            if (p.containsKey("remark")) dto.setRemark(SettingSupport.str(p.get("remark")));
            if (p.containsKey("isDefault")) dto.setIsDefault(SettingSupport.integer(p.get("isDefault")));
            if (p.containsKey("status")) dto.setStatus(SettingSupport.integer(p.get("status")));
            if (p.containsKey("sortOrder")) dto.setSortOrder(SettingSupport.integer(p.get("sortOrder")));
            if (p.containsKey("watermarkEnabled")) dto.setWatermarkEnabled(SettingSupport.integer(p.get("watermarkEnabled")));
            if (p.containsKey("marginTop")) dto.setMarginTop(SettingSupport.integer(p.get("marginTop")));
            if (p.containsKey("marginBottom")) dto.setMarginBottom(SettingSupport.integer(p.get("marginBottom")));
            if (p.containsKey("marginLeft")) dto.setMarginLeft(SettingSupport.integer(p.get("marginLeft")));
            if (p.containsKey("marginRight")) dto.setMarginRight(SettingSupport.integer(p.get("marginRight")));

            boolean isNew = id == null;
            if (isNew) {
                printTemplateService.saveTemplate(dto);
            } else {
                printTemplateService.updateTemplate(dto);
            }
            log.info("[AI设置] 用户{} {}打印模板 name={} docType={} id={}",
                    scope != null ? scope.getUsername() : "?", isNew ? "新增" : "修改",
                    dto.getTemplateName(), dto.getDocType(), dto.getId());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", (isNew ? "已新增" : "已修改") + "打印模板「"
                    + (dto.getTemplateName() != null ? dto.getTemplateName() : dto.getId()) + "」。");
            if (dto.getId() != null) result.put("id", dto.getId());
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 保存打印模板失败", e);
            return Map.of("success", false, "message", "保存打印模板失败: " + e.getMessage());
        }
    }
}
