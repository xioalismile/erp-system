package com.erp.ai.tool.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.print.template.entity.PrintTemplate;
import com.erp.print.template.service.PrintTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询打印模板列表（设置中心 → 打印模板设置）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingPrintTemplateListTool implements AiTool {

    private final PrintTemplateService printTemplateService;

    @Override
    public String getName() {
        return "setting_print_template_list";
    }

    @Override
    public String getDescription() {
        return "查询打印模板列表（分页，只读）。适用于：查看某单据类型有哪些打印模板、模板是否默认/启用。"
                + "参数：keyword(可选，按模板编码或名称过滤)、docType(可选，单据类型)、status(可选，1启用/0停用)、"
                + "page(默认1)、size(默认20)。修改请用 setting_print_template_save。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "可选，按模板编码或名称模糊过滤"));
        props.put("docType", Map.of("type", "string", "description", "可选，单据类型，如 SALE_ORDER"));
        props.put("status", Map.of("type", "integer", "description", "可选，状态：1启用 / 0停用"));
        props.put("page", Map.of("type", "integer", "description", "页码，默认1"));
        props.put("size", Map.of("type", "integer", "description", "每页条数，默认20"));
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
            String keyword = SettingSupport.str(params == null ? null : params.get("keyword"));
            String docType = SettingSupport.str(params == null ? null : params.get("docType"));
            Integer status = SettingSupport.integer(params == null ? null : params.get("status"));
            Integer page = SettingSupport.integer(params == null ? null : params.get("page"));
            Integer size = SettingSupport.integer(params == null ? null : params.get("size"));
            int current = page == null || page < 1 ? 1 : page;
            int pageSize = size == null || size < 1 ? 20 : size;

            Page<PrintTemplate> pageResult = printTemplateService.pageQuery(
                    current, pageSize, keyword, docType, status, null, null);
            List<Map<String, Object>> items = new ArrayList<>();
            if (pageResult != null && pageResult.getRecords() != null) {
                for (PrintTemplate t : pageResult.getRecords()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", t.getId());
                    item.put("templateCode", t.getTemplateCode());
                    item.put("templateName", t.getTemplateName());
                    item.put("docType", t.getDocType());
                    item.put("templateType", t.getTemplateType());
                    item.put("isDefault", t.getIsDefault());
                    item.put("pageSize", t.getPageSize());
                    item.put("pageOrientation", t.getPageOrientation());
                    item.put("status", t.getStatus());
                    item.put("sortOrder", t.getSortOrder());
                    item.put("remark", t.getRemark());
                    items.add(item);
                }
            }
            long total = pageResult == null ? 0 : pageResult.getTotal();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("total", total);
            result.put("items", items);
            result.put("message", "共查到 " + total + " 个打印模板（第 " + current + " 页）。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询打印模板失败", e);
            return Map.of("success", false, "message", "查询打印模板失败: " + e.getMessage());
        }
    }
}
