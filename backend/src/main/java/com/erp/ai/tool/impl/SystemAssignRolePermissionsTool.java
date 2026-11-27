package com.erp.ai.tool.impl;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.tool.AiTool;
import com.erp.system.entity.SysMenu;
import com.erp.system.entity.SysRole;
import com.erp.system.service.SysMenuService;
import com.erp.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 超管工具：给角色追加菜单/功能权限（合并语义，只开启不关闭）。
 * 仅 ADMIN/MANAGER 可用；写操作，走强制确认流程。
 * 注意：本工具不做权限移除（移除需到设置中心界面操作，防止口误锁死系统）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemAssignRolePermissionsTool implements AiTool {

    private final SysRoleService sysRoleService;
    private final SysMenuService sysMenuService;

    @Override
    public String getName() {
        return "system_assign_role_permissions";
    }

    @Override
    public String getDescription() {
        return "给指定角色追加开启菜单/功能权限（合并到已有权限，不关闭已有权限）。仅系统管理员可用。"
                + "适用于『给销售角色开报表权限』『给仓库员加生产菜单』等场景。"
                + "menuNames 用菜单名称，如：销售订单、客户管理、自定义报表。"
                + "不支持移除权限——如需移除请告知用户前往设置中心操作。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("roleName", Map.of("type", "string", "description", "目标角色名称（如：销售员、仓库主管），必填"));
        props.put("menuNames", Map.of(
                "type", "array",
                "description", "要开启的菜单/功能名称列表，必填",
                "items", Map.of("type", "string")));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("roleName", "menuNames"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, AiDataScope scope) {
        if (scope == null || !scope.isAdmin()) {
            return Map.of("success", false, "message", "权限拒绝：该工具仅系统管理员可用");
        }
        String roleName = params.get("roleName") == null ? null : params.get("roleName").toString().trim();
        Object menuNamesRaw = params.get("menuNames");
        if (roleName == null || roleName.isBlank() || menuNamesRaw == null) {
            return Map.of("success", false, "message", "参数缺失：需要 roleName 和 menuNames");
        }
        List<String> menuNames = new ArrayList<>();
        if (menuNamesRaw instanceof List<?> l) {
            for (Object o : l) {
                if (o != null && !o.toString().isBlank()) menuNames.add(o.toString().trim());
            }
        }
        if (menuNames.isEmpty()) {
            return Map.of("success", false, "message", "参数缺失：menuNames 不能为空");
        }
        try {
            // 1. 定位角色
            SysRole role = sysRoleService.listAll().stream()
                    .filter(r -> roleName.equals(r.getRoleName()) || roleName.equalsIgnoreCase(r.getRoleCode()))
                    .findFirst().orElse(null);
            if (role == null) {
                String available = sysRoleService.listAll().stream()
                        .map(SysRole::getRoleName).collect(Collectors.joining("、"));
                return Map.of("success", false, "message", "未找到角色: " + roleName + "。可用角色: " + available);
            }

            // 2. 菜单名 → 菜单路径（assignPermissions 接收菜单path如 /sale/orders）
            List<SysMenu> allMenus = sysMenuService.listAll();
            LinkedHashSet<String> newMenuPaths = new LinkedHashSet<>();
            List<String> notFound = new ArrayList<>();
            for (String mn : menuNames) {
                SysMenu hit = allMenus.stream()
                        .filter(m -> mn.equals(m.getMenuName()))
                        .findFirst().orElse(null);
                if (hit != null && hit.getRoutePath() != null && !hit.getRoutePath().isBlank()) {
                    newMenuPaths.add(hit.getRoutePath());
                } else {
                    notFound.add(mn);
                }
            }
            if (!newMenuPaths.isEmpty()) {
                // 3. 合并语义：读角色现权限（getDetail返回menuIds即path列表），并集后分配
                SysRole detail = sysRoleService.getDetail(role.getRoleId());
                List<String> currentMenuIds = detail != null && detail.getMenuIds() != null
                        ? detail.getMenuIds() : new ArrayList<>();
                LinkedHashSet<String> merged = new LinkedHashSet<>(currentMenuIds);
                List<String> addedPaths = newMenuPaths.stream()
                        .filter(p -> !merged.contains(p)).collect(Collectors.toList());
                if (addedPaths.isEmpty()) {
                    return Map.of("success", true, "message",
                            "角色 " + role.getRoleName() + " 已拥有权限: " + String.join("、", menuNames) + "，无需重复开启");
                }
                merged.addAll(addedPaths);
                sysRoleService.assignPermissions(role.getRoleId(), new ArrayList<>(merged));

                String added = addedPaths.stream()
                        .map(p -> allMenus.stream().filter(m -> p.equals(m.getRoutePath()))
                                .findFirst().map(SysMenu::getMenuName).orElse(p))
                        .collect(Collectors.joining("、"));
                log.info("[AI权限] 管理员{}为角色{}追加菜单权限: {}", scope.getUsername(), role.getRoleName(), added);
                String notFoundTip = notFound.isEmpty() ? "" : "（未找到菜单: " + String.join("、", notFound) + "）";
                return Map.of("success", true, "message",
                        "已为角色 " + role.getRoleName() + " 开启权限: " + added + notFoundTip);
            }
            String availableMenus = allMenus.stream()
                    .map(SysMenu::getMenuName).limit(50).collect(Collectors.joining("、"));
            return Map.of("success", false, "message",
                    "未找到菜单: " + String.join("、", notFound) + "。部分可用菜单: " + availableMenus);
        } catch (Exception e) {
            log.error("[AI权限] 分配菜单权限失败", e);
            return Map.of("success", false, "message", "分配权限失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> params, Long userId) {
        return Map.of("success", false, "message", "该工具必须携带用户身份上下文执行");
    }
}
