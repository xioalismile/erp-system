package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.common.PageResult;
import com.erp.system.entity.SysRole;
import com.erp.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读工具：查询角色列表（设置中心 → 角色权限设置）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingRoleListTool implements AiTool {

    private final SysRoleService sysRoleService;

    @Override
    public String getName() {
        return "setting_role_list";
    }

    @Override
    public String getDescription() {
        return "查询角色列表（分页，只读）。适用于：查看系统有哪些角色、角色编码/数据范围/是否内置。"
                + "参数：keyword(可选，按角色编码或名称过滤)、page(默认1)、size(默认20)。"
                + "新增/修改角色请用 setting_role_save，删除请用 setting_role_delete。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("keyword", Map.of("type", "string", "description", "可选，按角色编码或角色名称模糊过滤"));
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
            Integer page = SettingSupport.integer(params == null ? null : params.get("page"));
            Integer size = SettingSupport.integer(params == null ? null : params.get("size"));
            int current = page == null || page < 1 ? 1 : page;
            int pageSize = size == null || size < 1 ? 20 : size;

            PageResult<SysRole> pr = sysRoleService.pageQuery(current, pageSize, keyword, null, null);
            List<Map<String, Object>> items = new ArrayList<>();
            if (pr != null && pr.getRecords() != null) {
                for (SysRole r : pr.getRecords()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("roleId", r.getRoleId());
                    item.put("roleCode", r.getRoleCode());
                    item.put("roleName", r.getRoleName());
                    item.put("roleType", r.getRoleType());
                    item.put("dataScope", r.getDataScope());
                    item.put("dataScopeValue", r.getDataScopeValue());
                    item.put("isSystem", r.getIsSystem());
                    item.put("sortOrder", r.getSortOrder());
                    item.put("status", r.getStatus());
                    item.put("remark", r.getRemark());
                    items.add(item);
                }
            }
            long total = pr == null ? 0 : pr.getTotal();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("total", total);
            result.put("items", items);
            result.put("message", "共查到 " + total + " 个角色（第 " + current + " 页）。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 查询角色列表失败", e);
            return Map.of("success", false, "message", "查询角色列表失败: " + e.getMessage());
        }
    }
}
