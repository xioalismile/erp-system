package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.common.PageResult;
import com.erp.system.entity.SysI18n;
import com.erp.system.service.I18nService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询多语言语言包译文（设置中心 → 语言管理）。
 * 缺词回退中文由前端 t() 保证，本工具只读展示某语言下已有译文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingI18nSearchTool implements AiTool {

    private final I18nService i18nService;

    @Override
    public String getName() {
        return "setting_i18n_search";
    }

    @Override
    public String getDescription() {
        return "查询多语言语言包译文（分页，只读）。适用于：查看某语言下词条是否已翻译、排查缺失译文。"
                + "参数：keyword(可选，匹配翻译键或译文)、module(可选，分组：common/login/layout/menu/page/dict/print)、"
                + "lang(可选，默认 en，如 zh-CN/en)、page(默认1)、size(默认20，最大500)。"
                + "修改译文请使用 setting_i18n_save。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "可选，按翻译键或译文模糊过滤"));
        props.put("module", Map.of("type", "string",
                "description", "可选，分组：common/login/layout/menu/page/dict/print"));
        props.put("lang", Map.of("type", "string", "description", "目标语言，可选，默认 en，如 zh-CN/en"));
        props.put("page", Map.of("type", "integer", "description", "页码，默认1"));
        props.put("size", Map.of("type", "integer", "description", "每页条数，默认20，最大500"));
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
            String module = SettingSupport.str(params == null ? null : params.get("module"));
            String lang = SettingSupport.str(params == null ? null : params.get("lang"));
            Integer page = SettingSupport.integer(params == null ? null : params.get("page"));
            Integer size = SettingSupport.integer(params == null ? null : params.get("size"));
            int current = page == null || page < 1 ? 1 : page;
            int pageSize = size == null || size < 1 ? 20 : Math.min(size, 500);

            PageResult<SysI18n> pr = i18nService.pageItems(current, pageSize, keyword, module, lang);
            List<Map<String, Object>> items = new ArrayList<>();
            if (pr != null && pr.getRecords() != null) {
                for (SysI18n r : pr.getRecords()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", r.getId());
                    item.put("i18nKey", r.getI18nKey());
                    item.put("lang", r.getLang());
                    item.put("text", r.getText());
                    item.put("module", r.getModule());
                    item.put("zhText", r.getZhText());
                    item.put("updatedBy", r.getUpdatedBy());
                    item.put("updatedAt", r.getUpdatedAt());
                    items.add(item);
                }
            }
            long total = pr == null ? 0 : pr.getTotal();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("total", total);
            result.put("items", items);
            result.put("message", "共查到 " + total + " 条译文（第 " + current + " 页）。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询多语言译文失败", e);
            return Map.of("success", false, "message", "查询多语言译文失败: " + e.getMessage());
        }
    }
}
