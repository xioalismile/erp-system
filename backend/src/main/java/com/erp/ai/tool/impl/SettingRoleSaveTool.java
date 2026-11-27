package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.support.SettingSupport;
import com.erp.system.entity.SysRole;
import com.erp.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写工具：新增/修改角色（设置中心 → 角色权限设置），走强制确认。
 * id 为空走 saveRole（新增），否则走 updateRole（只覆盖传入字段）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingRoleSaveTool implements AiTool {

    private final SysRoleService sysRoleService;

    @Override
    public String getName() {
        return "setting_role_save";
    }

    @Override
    public String getDescription() {
        return "新增或修改角色（执行前会弹确认卡片）。不传 roleId 为新增，传 roleId 为修改（只改传入字段）。"
                + "新增必填：roleName(角色名称)。可选：roleId、roleCode(角色编码，留空由系统生成，不要编造)、"
                + "dataScope(数据范围，如 全部数据/本部门/本部门及以下/仅本人)、dataScopeValue、"
                + "sortOrder、remark、status(启用/停用)。"
                + "注意：本工具只维护角色基本信息，不分配菜单/按钮权限；请到 设置中心 → 角色权限设置 界面分配。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("roleId", Map.of("type", "integer", "description", "角色ID，修改时必传；新增不传"));
        props.put("roleName", Map.of("type", "string", "description", "角色名称，新增必填"));
        props.put("roleCode", Map.of("type", "string", "description", "角色编码，可空（留空由系统生成，不要编造）"));
        props.put("dataScope", Map.of("type", "string", "description", "数据范围，如 全部数据/本部门/本部门及以下/仅本人"));
        props.put("dataScopeValue", Map.of("type", "string", "description", "数据范围取值（自定义部门时用）"));
        props.put("sortOrder", Map.of("type", "integer", "description", "排序"));
        props.put("remark", Map.of("type", "string", "description", "备注"));
        props.put("status", Map.of("type", "string", "description", "状态，如 启用/停用"));
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
            Long roleId = SettingSupport.longVal(p.get("roleId"));
            String roleName = SettingSupport.str(p.get("roleName"));
            if (roleId == null && roleName == null) {
                return Map.of("success", false, "message", "新增角色需要 roleName");
            }

            SysRole role = new SysRole();
            role.setRoleId(roleId);
            if (p.containsKey("roleName")) role.setRoleName(roleName);
            if (p.containsKey("roleCode")) role.setRoleCode(SettingSupport.str(p.get("roleCode")));
            if (p.containsKey("dataScope")) role.setDataScope(SettingSupport.str(p.get("dataScope")));
            if (p.containsKey("dataScopeValue")) role.setDataScopeValue(SettingSupport.str(p.get("dataScopeValue")));
            if (p.containsKey("remark")) role.setRemark(SettingSupport.str(p.get("remark")));
            if (p.containsKey("status")) role.setStatus(SettingSupport.str(p.get("status")));
            if (p.containsKey("sortOrder")) role.setSortOrder(SettingSupport.integer(p.get("sortOrder")));

            boolean isNew = roleId == null;
            if (isNew) {
                sysRoleService.saveRole(role);
            } else {
                sysRoleService.updateRole(role);
            }
            log.info("[AI设置] 用户{} {}角色 name={} id={}",
                    scope != null ? scope.getUsername() : "?", isNew ? "新增" : "修改",
                    role.getRoleName(), role.getRoleId());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", (isNew ? "已新增" : "已修改") + "角色「"
                    + (role.getRoleName() != null ? role.getRoleName() : role.getRoleId()) + "」。");
            if (role.getRoleId() != null) result.put("roleId", role.getRoleId());
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 保存角色失败", e);
            return Map.of("success", false, "message", "保存角色失败: " + e.getMessage());
        }
    }
}
