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
 * 写工具：删除角色（设置中心 → 角色权限设置），走强制确认。
 * 后端会拒绝删除系统内置角色/已被用户引用的角色。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingRoleDeleteTool implements AiTool {

    private final SysRoleService sysRoleService;

    @Override
    public String getName() {
        return "setting_role_delete";
    }

    @Override
    public String getDescription() {
        return "删除一个角色（执行前会弹确认卡片）。系统内置角色、以及已被用户引用的角色会被后端拒绝删除。"
                + "必填参数：roleId(角色ID，先通过 setting_role_list 确认)。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("roleId", Map.of("type", "integer", "description", "角色ID，必填"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("roleId"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return execute(params, (AiDataScope) null);
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        try {
            Long roleId = SettingSupport.longVal(params == null ? null : params.get("roleId"));
            if (roleId == null) {
                return Map.of("success", false, "message", "参数缺失：需要 roleId");
            }
            SysRole role = sysRoleService.getById(roleId);
            if (role == null) {
                return Map.of("success", false, "message", "角色不存在: " + roleId);
            }
            sysRoleService.deleteRole(roleId);
            log.info("[AI设置] 用户{}删除角色 id={} name={}",
                    scope != null ? scope.getUsername() : "?", roleId, role.getRoleName());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已删除角色「" + role.getRoleName() + "」。");
            return result;
        } catch (Exception e) {
            log.error("[AI设置] 删除角色失败", e);
            return Map.of("success", false, "message", "删除角色失败: " + e.getMessage());
        }
    }
}
