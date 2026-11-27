package com.erp.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.system.entity.SysRole;
import com.erp.system.entity.SysUser;
import com.erp.system.service.SysRoleService;
import com.erp.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 超管工具：给员工分配角色（合并语义，只追加不清空已有角色）。
 * 仅 ADMIN/MANAGER 可用；写操作，走强制确认流程。
 * 注意：本工具不做角色移除（移除需到设置中心界面操作，防止口误锁死账号）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemAssignUserRolesTool implements AiTool {

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;

    @Override
    public String getName() {
        return "system_assign_user_roles";
    }

    @Override
    public String getDescription() {
        return "给指定员工追加分配角色（合并到已有角色，不删除已有角色）。仅系统管理员可用。"
                + "适用于『给张三开销售主管权限』『给员工某某加仓库员角色』等场景。"
                + "不支持移除角色——如需移除角色请告知用户前往设置中心操作。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("username", Map.of("type", "string",
                "description", "目标员工的账号(username)或真实姓名(realName)，必填"));
        props.put("roleNames", Map.of(
                "type", "array",
                "description", "要追加的角色名称列表（如：销售主管、仓库员），必填",
                "items", Map.of("type", "string")));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("username", "roleNames"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        // 执行级超管校验（暴露级校验之外的最后一道防线）
        if (scope == null || !scope.isAdmin()) {
            return Map.of("success", false, "message", "权限拒绝：该工具仅系统管理员可用");
        }
        String username = str(params.get("username"));
        Object roleNamesRaw = params.get("roleNames");
        if (username == null || username.isBlank() || roleNamesRaw == null) {
            return Map.of("success", false, "message", "参数缺失：需要 username 和 roleNames");
        }
        List<String> roleNames = toStringList(roleNamesRaw);
        if (roleNames.isEmpty()) {
            return Map.of("success", false, "message", "参数缺失：roleNames 不能为空");
        }
        try {
            // 1. 定位目标用户（按账号或真实姓名）
            SysUser user = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .or().eq(SysUser::getRealName, username)
                    .last("LIMIT 1"));
            if (user == null) {
                return Map.of("success", false, "message", "未找到用户: " + username + "，请确认账号或姓名");
            }

            // 2. 定位角色（按角色名或编码精确匹配）
            List<SysRole> allRoles = sysRoleService.listAll();
            List<SysRole> matched = new ArrayList<>();
            List<String> notFound = new ArrayList<>();
            for (String rn : roleNames) {
                SysRole hit = allRoles.stream()
                        .filter(r -> rn.equals(r.getRoleName()) || rn.equalsIgnoreCase(r.getRoleCode()))
                        .findFirst().orElse(null);
                if (hit != null) matched.add(hit);
                else notFound.add(rn);
            }
            if (!notFound.isEmpty()) {
                String available = allRoles.stream().map(SysRole::getRoleName).collect(Collectors.joining("、"));
                return Map.of("success", false, "message",
                        "未找到角色: " + String.join("、", notFound) + "。可用角色: " + available);
            }

            // 3. 合并语义：读现角色，并集后分配（不删除已有角色）
            SysUser detail = sysUserService.getDetail(user.getUserId());
            List<Long> currentRoleIds = detail != null && detail.getRoleIds() != null
                    ? detail.getRoleIds() : new ArrayList<>();
            List<Long> newRoleIds = matched.stream().map(SysRole::getRoleId)
                    .filter(id -> !currentRoleIds.contains(id)).collect(Collectors.toList());
            if (newRoleIds.isEmpty()) {
                return Map.of("success", true, "message",
                        "用户 " + user.getUsername() + " 已拥有角色 " + String.join("、", roleNames) + "，无需重复分配");
            }
            currentRoleIds.addAll(newRoleIds);
            sysUserService.assignRoles(user.getUserId(), currentRoleIds);

            String added = matched.stream().map(SysRole::getRoleName).collect(Collectors.joining("、"));
            log.info("[AI权限] 管理员{}为用户{}追加角色: {}", scope.getUsername(), user.getUsername(), added);
            return Map.of("success", true, "message",
                    "已为用户 " + user.getUsername() + "（" + user.getRealName() + "）追加角色: " + added);
        } catch (Exception e) {
            log.error("[AI权限] 分配角色失败", e);
            return Map.of("success", false, "message", "分配角色失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        // 旧签名无角色上下文，拒绝执行（必须走带scope的入口）
        return Map.of("success", false, "message", "该工具必须携带用户身份上下文执行");
    }

    private String str(Object o) {
        return o == null ? null : o.toString().trim();
    }

    private List<String> toStringList(Object raw) {
        List<String> list = new ArrayList<>();
        if (raw instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) list.add(o.toString().trim());
            }
        } else if (raw != null) {
            for (String s : raw.toString().split("[,，、]")) {
                if (!s.isBlank()) list.add(s.trim());
            }
        }
        return list;
    }
}
