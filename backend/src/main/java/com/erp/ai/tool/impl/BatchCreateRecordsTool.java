package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.service.AiCrudAgentService;
import com.erp.ai.tool.AiTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 写工具：**批量创建（可跨表、一次确认卡写完全部）**。
 *
 * <p>背景（2026-10-21 用户实测）：
 * 「批量创建不要限制 50 的数量，而且这个创建在跨表的时候也可以创建，比如我要同事创建分类、物料、工序、单据等信息，
 *  一个确定卡就可以直接写入全部信息」。
 *
 * <p>能力：
 * <ul>
 *   <li>一次调用可以包含多条**不同类型**的记录（分类、物料、工艺路线含工序、客户、单据…），只弹一张确认卡；</li>
 *   <li>按 entries 的**提交顺序**逐条创建——有依赖时把上游放前面（分类→物料→工艺路线→单据），
 *       后面的条目可以直接用前面刚建记录的名称/编码引用（创建时按名称解析外键）；</li>
 *   <li>不限制条数；逐条执行、逐条返回成功/失败原因，单条失败不影响其它条；</li>
 *   <li>单据明细放 items，工艺路线的工序放 operations，与单个创建工具的参数口径一致。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchCreateRecordsTool implements AiTool {

    private final AiCrudAgentService aiCrudAgentService;

    @Override
    public String getName() {
        return "batch_create_records";
    }

    @Override
    public String getDescription() {
        Map<String, String> options = aiCrudAgentService.listEntityOptions();
        String optionText = options.entrySet().stream()
                .map(e -> e.getKey() + "（" + e.getValue() + "）")
                .collect(Collectors.joining("，"));
        return "批量创建（**可跨表**、一次确认卡写入全部）：一次可以创建多条不同类型的数据，"
                + "例如同时创建「计量单位 → 物料分类 → 物料 → BOM(含子件) → 工艺路线(含工序) → 销售订单」。"
                + "参数 entries 是数组，每项：{entity: 数据类型, data: 该记录字段, items: 明细行(单据/BOM用), operations: 工序行(工艺路线用)}。"
                + "**不限制条数**；按数组顺序逐条创建，有依赖时把上游放前面（先单位/分类再物料、先物料再 BOM/工艺路线/单据），"
                + "后面的条目可以直接写前面刚建记录的名称/编码，系统会自动解析成对应主键。"
                + "逐条返回成功/失败原因，单条失败不影响其它条。执行前只弹**一张**确认卡片。"
                + "单据必须带 items（每行物料 + 数量）；BOM 必须带 items（每行子件 + 用量，用量必须大于 0）；"
                + "工艺路线必须带 operations（每行 operationName）。"
                + "BOM 口径：data 里写母件（materialCode/materialName）、BOM分类(bomCategory)、版本号(bomVersion)、数量(quantity)；"
                + "BOM 编号由系统自动生成，不要传 bomCode；默认BOM/主BOM由系统判定，不要传。"
                + "例（编码一律由系统生成，**不要传**；分类/单位/物料都用名称引用，系统自动解析）："
                + "entries=[{entity:basedata_materialcategory,data:{categoryName:'化妆品'}},"
                + "{entity:basedata_material,data:{materialName:'保湿修护面霜',materialType:'成品',categoryName:'化妆品',unitName:'件'}},"
                + "{entity:basedata_material,data:{materialName:'膏体基料',materialType:'半成品',categoryName:'化妆品',unitName:'千克'}},"
                + "{entity:production_bom,data:{materialName:'保湿修护面霜',bomCategory:'标准',bomVersion:'V1.0',quantity:1},"
                + "items:[{materialName:'膏体基料',quantity:0.05}]},"
                + "{entity:production_routing,data:{routingName:'面霜工艺',materialName:'保湿修护面霜'},operations:[{operationName:'配料'}]},"
                + "{entity:sale_order,data:{customerName:'楷煜'},items:[{materialName:'保湿修护面霜',quantity:20,unitPrice:100}]}]。"
                + "entity 可选值：" + optionText;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> entryProps = new LinkedHashMap<>();
        entryProps.put("entity", Map.of(
                "type", "string",
                "description", "数据类型（实体键），如 basedata_unit / basedata_materialcategory / basedata_material / production_bom / production_routing / sale_order",
                "enum", new ArrayList<>(aiCrudAgentService.listEntityOptions().keySet())));
        entryProps.put("data", Map.of("type", "object", "description", "该记录的字段（与单个创建时的 data 写法一致）"));
        entryProps.put("items", Map.of("type", "array", "description", "明细行：单据=物料行（每行物料 + 数量）；BOM=子件行（每行子件 + 用量，用量必须大于 0）"));
        entryProps.put("operations", Map.of("type", "array", "description", "工序行（工艺路线用），每行至少 operationName"));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "object");
        entry.put("properties", entryProps);
        entry.put("required", List.of("entity", "data"));

        Map<String, Object> entries = new LinkedHashMap<>();
        entries.put("type", "array");
        entries.put("description", "要创建的记录数组，按依赖顺序排列（上游在前），不限制条数");
        entries.put("items", entry);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("entries", entries);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("entries"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return doExecute(params, userId);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        return doExecute(params, scope != null ? scope.getUserId() : null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doExecute(Map<String, Object> params, Long userId) {
        try {
            Object raw = params == null ? null : params.get("entries");
            if (!(raw instanceof List<?> entries) || entries.isEmpty()) {
                return fail("请提供要创建的内容（entries 不能为空）。");
            }

            int successCount = 0;
            int failCount = 0;
            StringBuilder detail = new StringBuilder();
            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                int line = i + 1;
                Object one = entries.get(i);
                if (!(one instanceof Map<?, ?> entry)) {
                    failCount++;
                    String msg = "数据格式不正确";
                    detail.append("\n· 第 ").append(line).append(" 条 失败：").append(msg);
                    results.add(resultRow(line, "未知类型", false, null, null, null, msg));
                    continue;
                }
                String entityKey = str(entry.get("entity"));
                String intent = aiCrudAgentService.resolveIntent(entityKey);
                String label = intent == null ? (entityKey.isEmpty() ? "未知类型" : entityKey)
                        : aiCrudAgentService.intentLabel(intent);
                if (intent == null) {
                    failCount++;
                    String msg = "不支持的数据类型，请写全名（如 basedata_material / sale_order）";
                    detail.append("\n· 第 ").append(line).append(" 条【").append(label).append("】失败：").append(msg);
                    results.add(resultRow(line, label, false, null, null, null, msg));
                    continue;
                }
                Object dataObj = entry.get("data");
                if (!(dataObj instanceof Map<?, ?> dataMap)) {
                    failCount++;
                    String msg = "缺少 data 字段";
                    detail.append("\n· 第 ").append(line).append(" 条【").append(label).append("】失败：").append(msg);
                    results.add(resultRow(line, label, false, null, null, null, msg));
                    continue;
                }
                Map<String, Object> createParams = new LinkedHashMap<>();
                createParams.put("data", dataMap);
                if (entry.get("items") instanceof List<?> items) createParams.put("items", items);
                if (entry.get("operations") instanceof List<?> ops) createParams.put("operations", ops);

                Map<String, Object> result;
                try {
                    result = aiCrudAgentService.create(intent, createParams, userId);
                } catch (Exception e) {
                    result = Map.of("success", false, "message", e.getMessage() == null ? "创建失败" : e.getMessage());
                }
                if (Boolean.TRUE.equals(result.get("success"))) {
                    successCount++;
                    String code = str(result.get("code"));
                    String name = str(result.get("name"));
                    String ref = (code + (code.isEmpty() || name.isEmpty() ? "" : " ") + name).trim();
                    detail.append("\n· 第 ").append(line).append(" 条【").append(label).append("】成功")
                            .append(ref.isEmpty() ? "" : "：" + ref);
                    results.add(resultRow(line, label, true, result.get("id"), code, name, "创建成功"));
                } else {
                    failCount++;
                    String msg = str(result.get("message")).isEmpty() ? "未知原因" : str(result.get("message"));
                    detail.append("\n· 第 ").append(line).append(" 条【").append(label).append("】失败：").append(msg);
                    results.add(resultRow(line, label, false, result.get("id"), result.get("code"), result.get("name"), msg));
                }
            }

            String message = "批量创建完成：成功 " + successCount + " 条，失败 " + failCount + " 条。" + detail;
            log.info("[AI批量创建] 用户{} 共{}条 成功{} 失败{}", userId, entries.size(), successCount, failCount);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", successCount > 0);
            result.put("partial", failCount > 0);
            result.put("total", successCount + failCount);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("results", results);
            result.put("message", message);
            // 有失败时给出「继续处理」指引：前端据此自动让小楷读现状、补齐缺失项后重试失败部分，
            // 不再出现"失败就停住、没有第二步"（2026-10-28 用户实测）。
            if (failCount > 0) {
                Map<String, Object> followUp = new LinkedHashMap<>();
                followUp.put("prompt", buildFollowUpPrompt(results));
                followUp.put("reason", "批量创建部分失败");
                result.put("followUp", followUp);
            }
            return result;
        } catch (Exception e) {
            log.error("[AI批量创建] 执行异常", e);
            return fail("执行失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /** 单条创建结果（结构化返回，供模型继续用生成的编码建 BOM / 工单 / 后续引用） */
    private static Map<String, Object> resultRow(int line, String entity, boolean success,
                                                 Object id, Object code, Object name, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("line", line);
        m.put("entity", entity == null ? "" : entity);
        m.put("success", success);
        m.put("id", id == null ? "" : id);
        m.put("code", code == null ? "" : code);
        m.put("name", name == null ? "" : name);
        m.put("message", message == null ? "" : message);
        return m;
    }

    /** 面向模型的继续处理指引：只补失败项，已成功的不要重建 */
    private static String buildFollowUpPrompt(List<Map<String, Object>> results) {
        StringBuilder failed = new StringBuilder();
        int n = 0;
        for (Map<String, Object> r : results) {
            if (Boolean.TRUE.equals(r.get("success"))) continue;
            if (n++ > 0) failed.append("；");
            failed.append("第").append(r.get("line")).append("条【").append(r.get("entity")).append("】")
                    .append(r.get("message"));
            if (n >= 12) { failed.append("；…"); break; }
        }
        return "刚才的批量创建有一部分没有成功，失败明细：" + failed + "。"
                + "请你现在继续把它做完，不要停在失败提示上："
                + "① 先读取系统现状（物料分类、计量单位、已有物料/单据），确认失败原因是缺分类、缺单位、"
                + "名称对不上还是编码冲突；"
                + "② 只补建失败的那几条，**已经成功的不要再建一遍**；"
                + "③ 名称对不上的，用系统里真实存在的名称；确实不存在时先建它，再建引用它的那条；"
                + "④ 仍然用一条批量创建（batch_create_records）或一条多步骤任务（task_plan_execute）提交，"
                + "只弹一张确认卡；"
                + "⑤ 用业务语言告诉用户：这次补了什么、为什么之前会失败。";
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Map<String, Object> fail(String message) {
        return Map.of("success", false, "message", message);
    }
}
