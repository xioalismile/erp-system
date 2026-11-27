package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.imports.ImportPermissionResolver;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.ImportExecSupport;
import com.erp.ai.tool.support.InMemoryMultipartFile;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.export.dto.ImportResultDTO;
import com.erp.export.service.ExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 写工具：高频实体「行级」导入。
 *
 * <p>入参是 AI 已按模板列整理好的结构化行（不是文件）。流程：
 * 先取模板列序 → 把行映射成二维数组 → {@link ExcelService#buildStandardExcel} 造标准 Excel →
 * 复用与 {@code import_file} 相同的分派链路导入。
 *
 * <p>铁律：缺必填列 / 列名不在模板中一律**拒绝导入**并返回需修正项（不猜）；物料走物料两段式链路
 * （默认跳过已存在，覆盖需用户明确指定）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportRowsTool implements AiTool {

    /** 本期行级导入白名单（写死，AI 不得扩展） */
    private static final Set<String> ROW_IMPORT_ENTITIES =
            Set.of("customer", "supplier", "saleOrder", "purchaseOrder", "material");

    private static final String POLICY_SKIP = "SKIP";
    private static final String POLICY_OVERWRITE = "OVERWRITE";

    private final ExcelService excelService;
    private final ImportExecSupport execSupport;
    private final ImportPermissionResolver permissionResolver;

    @Override
    public String getName() {
        return "import_rows";
    }

    @Override
    public String getDescription() {
        return "【写操作，用户确认后执行】按系统标准模板的「结构化行」导入数据（本期支持 customer/supplier/saleOrder/purchaseOrder/material）。"
                + "入参 rows 是每项为 {模板列名: 值} 的对象数组，列名必须来自 import_template_list 返回的模板列。"
                + "流程：先 import_template_list 拿列序 → 把数据展示给用户确认 → 用户确认后调用本工具。"
                + "缺必填列或出现模板中没有的列名会被拒绝并返回需修正项（不猜测）。单据类多行明细时，表头字段只需首行填，续行留空。"
                + "物料导入：默认跳过已存在（SKIP）；如需覆盖必须在用户明确要求后传 onExistingConflict=OVERWRITE，不得代选。"
                + "customer 可传 poolType（0=私海/1=公海），不传则按系统默认。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> rowProps = new LinkedHashMap<>();
        rowProps.put("type", "object");
        rowProps.put("description", "每行一个对象：键=模板列名（来自 import_template_list），值=该列内容");
        Map<String, Object> rows = new LinkedHashMap<>();
        rows.put("type", "array");
        rows.put("description", "结构化数据行");
        rows.put("items", rowProps);

        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> entityName = new LinkedHashMap<>();
        entityName.put("type", "string");
        entityName.put("description", "目标实体名");
        entityName.put("enum", List.of("customer", "supplier", "saleOrder", "purchaseOrder", "material"));
        props.put("entityName", entityName);
        props.put("rows", rows);
        Map<String, Object> poolType = new LinkedHashMap<>();
        poolType.put("type", "integer");
        poolType.put("description", "可选，仅 customer 有效：0=私海（归属当前用户）/1=公海；不传则按系统默认");
        props.put("poolType", poolType);
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("type", "string");
        policy.put("description", "可选，仅物料有效：已存在物料处理策略 SKIP=跳过（默认）/ OVERWRITE=覆盖；须由用户明确选择");
        policy.put("enum", List.of(POLICY_SKIP, POLICY_OVERWRITE));
        props.put("onExistingConflict", policy);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of("entityName", "rows"));
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        String entityName = SettingSupport.str(params == null ? null : params.get("entityName"));
        if (entityName == null || !ROW_IMPORT_ENTITIES.contains(entityName)) {
            return fail("本期行级导入仅支持 customer/supplier/saleOrder/purchaseOrder/material，不支持: " + entityName);
        }
        String deny = permissionResolver.check(entityName, scope);
        if (deny != null) return fail(deny);

        Object rowsObj = params.get("rows");
        if (!(rowsObj instanceof List<?> rows) || rows.isEmpty()) {
            return fail("没有可导入的数据行（rows 为空）");
        }

        List<Map<String, Object>> columns = excelService.getImportColumns(entityName);
        List<String> labels = new ArrayList<>(columns.size());
        Set<String> labelSet = new LinkedHashSet<>();
        List<String> requiredLabels = new ArrayList<>();
        for (Map<String, Object> col : columns) {
            String label = String.valueOf(col.get("label"));
            labels.add(label);
            labelSet.add(label);
            if (Boolean.TRUE.equals(col.get("required"))) {
                requiredLabels.add(label);
            }
        }

        List<List<String>> dataRows = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 1;
            Object o = rows.get(i);
            if (!(o instanceof Map<?, ?> r)) {
                problems.add("第" + rowNo + "行不是对象（应为 {列名: 值}）");
                continue;
            }
            for (Object k : r.keySet()) {
                String key = k == null ? "" : String.valueOf(k);
                if (!labelSet.contains(key)) {
                    problems.add("第" + rowNo + "行 列名「" + key + "」不在模板中");
                }
            }
            List<String> line = new ArrayList<>(labels.size());
            for (String label : labels) {
                line.add(cell(r.get(label)));
            }
            for (String req : requiredLabels) {
                if (cell(r.get(req)).isEmpty()) {
                    problems.add("第" + rowNo + "行 缺少必填列「" + req + "」");
                }
            }
            dataRows.add(line);
        }
        if (!problems.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("needUserInput", true);
            result.put("message", "以下内容需要修正后再导入（不猜测、不自动修正）：");
            result.put("problems", problems);
            return result;
        }

        try {
            byte[] excel = excelService.buildStandardExcel(entityName, dataRows);
            MultipartFile mf = new InMemoryMultipartFile(entityName + "_ai_rows.xlsx", excel);

            ImportResultDTO imp;
            String label = permissionResolver.entityLabel(entityName);
            if ("material".equals(entityName)) {
                String rawPolicy = SettingSupport.str(params.get("onExistingConflict"));
                String policy = rawPolicy == null ? POLICY_SKIP : rawPolicy.toUpperCase(Locale.ROOT);
                if (!POLICY_SKIP.equals(policy) && !POLICY_OVERWRITE.equals(policy)) {
                    return fail("物料的 onExistingConflict 取值非法，仅支持 SKIP/OVERWRITE");
                }
                imp = excelService.confirmMaterialImport(mf, policy);
            } else {
                // poolType 仅 customer 有效（0 私海/1 公海）；其它实体传 null 走既有通用导入链路
                Integer poolType = "customer".equals(entityName)
                        ? SettingSupport.integer(params.get("poolType")) : null;
                imp = execSupport.dispatch(entityName, mf, poolType, scope == null ? null : scope.getUserId());
            }
            Map<String, Object> result = ImportExecSupport.summarize(imp, label);
            log.info("[AI导入] 行级导入 用户{} 实体{} 行数{} 成功{} 失败{}",
                    scope == null ? null : scope.getUsername(), entityName, rows.size(),
                    imp.getSuccessCount(), imp.getFailCount());
            return result;
        } catch (Exception e) {
            log.error("[AI导入] 行级导入失败 entity={}", entityName, e);
            return fail("行级导入失败: " + e.getMessage());
        }
    }

    private String cell(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }
}
