package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.imports.ImportPermissionResolver;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.export.service.ExcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询可导入实体清单与某实体的标准模板列。
 *
 * <p>用途：AI 在导入任何数据前，必须先拿模板列（列名必须与系统模板一致），才能校验用户文件/结构化行，
 * 避免"猜列名"。列来源与界面"下载模板"完全同源（{@link ExcelService#getImportColumns}）。
 * 只读，不消耗写开关；权限走 {@link ImportPermissionResolver}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportTemplateListTool implements AiTool {

    private final ExcelService excelService;
    private final ImportPermissionResolver permissionResolver;

    @Override
    public String getName() {
        return "import_template_list";
    }

    @Override
    public String getDescription() {
        return "【只读】查询可导入实体清单及标准模板列。"
                + "传入 entityName 返回该实体的模板列（label=列名、required=是否必填、order=列序），"
                + "列名必须与系统模板完全一致；不传 entityName 返回当前账号有权限导入的实体清单（含中文名）。"
                + "任何导入前都必须先调用本工具拿列定义，不要自行编造列名。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> entityName = new LinkedHashMap<>();
        entityName.put("type", "string");
        entityName.put("description", "可选。实体名（如 customer/supplier/material/saleOrder/purchaseOrder）；不传则返回全部有权限的可导入实体");
        props.put("entityName", entityName);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of());
        return params;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            String entityName = SettingSupport.str(params == null ? null : params.get("entityName"));
            if (entityName != null) {
                if (!excelService.getImportableEntities().contains(entityName)) {
                    return fail("不支持导入的实体: " + entityName);
                }
                String deny = permissionResolver.check(entityName, scope);
                if (deny != null) return fail(deny);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("entityName", entityName);
                result.put("entityLabel", permissionResolver.entityLabel(entityName));
                result.put("columns", excelService.getImportColumns(entityName));
                return result;
            }

            String deny = permissionResolver.checkAny(scope);
            if (deny != null) return fail(deny);
            List<String> permitted = permissionResolver.permittedEntities(scope);
            permitted.sort(String::compareTo);
            List<Map<String, Object>> entities = new ArrayList<>();
            for (String name : permitted) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("entityName", name);
                item.put("entityLabel", permissionResolver.entityLabel(name));
                entities.add(item);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("entityCount", entities.size());
            result.put("entities", entities);
            return result;
        } catch (Exception e) {
            log.error("[AI导入模板] 执行异常", e);
            return fail("查询失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", message);
        return m;
    }
}
