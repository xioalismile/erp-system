package com.erp.ai.tool.impl;

import com.erp.ai.tool.AiTool;
import com.erp.print.template.service.PrintTemplateFieldCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询打印模板的「可用字段」。
 *
 * <p>动机（用户 2026-10-10）：用户上传报价单模板截图/Word 要求"照着建打印模板"时，模型即使读到了内容，
 * 也不知道本系统某单据类型到底有哪些字段可用（字段词汇原先只写在前端页面里），建出来的模板取不到数。
 * 本工具把该词汇（唯一来源在后端）暴露给模型，配合 setting_print_template_save 使用。
 *
 * <p>同时把**模板内容格式**讲清楚：HTML + {@code {{字段key}}} 变量 + {@code {{#items}}...{{/items}}}
 * 明细行循环（与前端设计器、后端渲染器同一口径），避免模型生成不能渲染的内容。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrintTemplateFieldsTool implements AiTool {

    private final PrintTemplateFieldCatalog fieldCatalog;

    @Override
    public String getName() {
        return "print_template_fields";
    }

    @Override
    public String getDescription() {
        return "【只读】查询打印模板的可用字段与内容格式。"
                + "传 docType（单据类型，如 SALE_QUOTE 销售报价单）→ 返回该单据类型可用的表头字段 / 明细字段 / 表尾字段（含 key 与中文名），"
                + "以及模板内容该怎么写；不传 docType → 返回所有可用的单据类型清单。"
                + "用途：用户要\"新建/照着截图或文档建打印模板\"时，**先调本工具拿到可用字段**，再调 setting_print_template_save 落库；"
                + "严禁使用本工具未返回的字段名（乱填会导致打印时取不到值）。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("docType", Map.of("type", "string",
                "description", "单据类型编码（如 SALE_QUOTE 销售报价单 / SALE_ORDER 销售订单 / WORK_ORDER 生产工单）；留空则返回所有单据类型清单"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        String docType = params == null || params.get("docType") == null ? "" : String.valueOf(params.get("docType")).trim();
        if (docType.isBlank()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", true);
            m.put("docTypes", fieldCatalog.docTypes());
            m.put("commonItemFields", fieldCatalog.commonItemFields());
            m.put("message", "以上是系统已登记的全部单据类型。请选定一个后，用本工具带 docType 再查一次可用字段。");
            return m;
        }

        Map<String, Object> fields = fieldCatalog.forDocType(docType);
        if (fields == null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", false);
            m.put("message", "系统没有为单据类型「" + docType + "」登记可用字段。可用类型：" + fieldCatalog.docTypes());
            return m;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("docType", docType);
        result.put("header", fields.get("header"));
        result.put("items", fields.get("items"));
        result.put("footer", fields.get("footer"));
        result.put("commonItemFields", fieldCatalog.commonItemFields());
        result.put("contentFormat", "模板内容 templateContent 是 HTML：① 普通字段直接写 {{字段key}}；"
                + "② 明细行必须用 {{#items}}<tr>…每格写 {{明细字段key}}…</tr>{{/items}} 包住那**一行表格**"
                + "（系统按 items 逐行循环）；③ 表头/表尾可用 {{#header}}…{{/header}}、{{#footer}}…{{/footer}} 条件块包裹；"
                + "④ 只能用上面返回过的 key，不要自造字段名；⑤ 表格请用 <table>，表头行就是普通行。");
        result.put("hint", "请按用户看到的截图/文档挑出对应字段，用返回的 key 组织 templateContent，"
                + "并把模板名称、单据类型、纸张与方向一起核对后展示给用户确认；确认后再调 setting_print_template_save 落库。"
                + "用户提到的列在可用字段里找不到时，如实说明\"系统没有这个字段\"，不要编造字段名。");
        return result;
    }
}
