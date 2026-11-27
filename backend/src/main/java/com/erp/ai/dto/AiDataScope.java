package com.erp.ai.dto;

import com.erp.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 数据权限上下文 - 由服务端从认证上下文(HttpServletRequest)构建，绝不接受前端传入。
 * AI 工具执行查询时必须按此范围过滤数据，保证"AI 能看到的 = 界面能看到的"。
 *
 * <p>例外说明：{@link #displayPrefs} 是**纯展示口径偏好**（用户在「小助手 → ⚙️ 设置」里选的
 * 单据标识方式等），可由前端随请求传入。它<b>只影响文案措辞</b>，
 * 绝不参与身份识别、权限判定、数据过滤 —— 上述安全字段仍然一律由服务端从认证上下文构建。
 *
 * 角色编码与 SecurityUtils 保持一致（sys_role.role_code）。
 */
@Data
public class AiDataScope {

    private Long userId;
    private String username;
    private Long employeeId;
    private Long deptId;
    private List<String> roles;

    /**
     * 当前用户的权限码集合（来源 AuthInterceptor 注入的 request attribute，与界面 v-permission 同源）。
     * 用于设置中心类工具的权限判定，避免再查一次库。
     */
    private List<String> permissions;

    /**
     * 当前用户的菜单/子功能路径授权（来源 AuthInterceptor 注入的 currentMenuPaths，
     * 形如 {@code /system/settings}、{@code /system/settings?item=custom-field}）。
     *
     * <p>用途（2026-09-18 新增）：设置中心类工具的权限判定 —— 界面侧对设置中心是"按菜单路径授权"
     * （sys_role_permission.menu_id 存的是 route_path），而权限码 system:settings:view 会随菜单
     * 重建而丢失，历史上曾因此让所有非管理员账号的 41 个 setting_* 工具静默消失。
     * 这里以"与界面同源"的菜单路径做判定依据，权限码只作兼容保留。
     */
    private List<String> menuPaths;

    /**
     * 展示口径偏好（仅文案措辞，无任何权限语义）：
     * {@code billLabelStyle} = code（单号，默认）/ name（客户名）/ both（单号+客户名）。
     * 写操作确认执行时从服务端暂存的待确认记录里还原，不依赖前端二次传值。
     */
    private Map<String, String> displayPrefs;

    /** 从 HTTP 请求构建（AuthInterceptor 已注入属性） */
    public static AiDataScope from(HttpServletRequest request) {
        AiDataScope scope = new AiDataScope();
        scope.setUserId(SecurityUtils.getCurrentUserId(request));
        scope.setUsername(SecurityUtils.getCurrentUsername(request));
        scope.setEmployeeId(SecurityUtils.getCurrentEmployeeId(request));
        scope.setDeptId(SecurityUtils.getCurrentDeptId(request));
        List<String> roles = SecurityUtils.getCurrentRoles(request);
        scope.setRoles(roles != null ? roles : Collections.emptyList());
        List<String> perms = SecurityUtils.getCurrentPermissions(request);
        scope.setPermissions(perms != null ? perms : Collections.emptyList());
        List<String> menuPaths = SecurityUtils.getCurrentMenuPaths(request);
        scope.setMenuPaths(menuPaths != null ? menuPaths : Collections.emptyList());
        return scope;
    }

    /**
     * 是否拥有指定菜单路径权限（子项也算：{@code /system/settings} 可命中
     * {@code /system/settings?item=custom-field}）。与"界面能打开该菜单"同口径。
     */
    public boolean hasMenuPath(String menuPath) {
        if (menuPath == null || menuPath.isBlank()) return false;
        if (menuPaths == null || menuPaths.isEmpty()) return false;
        for (String p : menuPaths) {
            if (p == null) continue;
            if (p.equals("*") || p.equals(menuPath) || p.startsWith(menuPath + "?")) return true;
        }
        return false;
    }

    /** 是否拥有指定权限码（含 '*' 通配全放行）。与 SecurityUtils.hasPermission 同口径。 */
    public boolean hasPermission(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) return false;
        if (permissions == null || permissions.isEmpty()) return false;
        if (permissions.contains("*")) return true;
        return permissions.contains(permissionCode);
    }

    /** 是否拥有任一以指定前缀开头的权限码（如 "sale:" 表示销售模块任一权限）。 */
    public boolean hasPermissionPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return false;
        if (permissions == null || permissions.isEmpty()) return false;
        if (permissions.contains("*")) return true;
        return permissions.stream().anyMatch(p -> p != null && p.startsWith(prefix));
    }

    public boolean isAdmin() {
        // SUPER_ADMIN=平台超管+业务全权限（与系统口径一致，见project_memory 2026-08-28）
        return roles.contains("ADMIN") || roles.contains("MANAGER") || roles.contains("SUPER_ADMIN");
    }

    /** 仅销售员（非管理岗） */
    public boolean isSalesOnly() {
        return roles.contains("SALES") && !isAdmin();
    }

    /** 仅采购员（非管理岗） */
    public boolean isPurchaseOnly() {
        return roles.contains("PURCHASE") && !isAdmin();
    }

    /** 普通员工（任一非管理岗业务角色） */
    public boolean isRegularEmployee() {
        return isSalesOnly() || isPurchaseOnly()
                || (roles.contains("WAREHOUSE") && !isAdmin())
                || (roles.contains("PRODUCTION") && !isAdmin())
                || (roles.contains("FINANCE") && !isAdmin());
    }

    /** 销售/采购普通员工需要按本人过滤业务单据 */
    public Long getEmployeeIdFilter() {
        if (isAdmin()) return null;
        if (isSalesOnly() || isPurchaseOnly()) return employeeId;
        return null;
    }

    /**
     * 普通员工按创建人过滤（返回用户名）。
     * created_by 字段在不同链路可能存用户名或用户ID字符串，过滤时两者都匹配。
     */
    public String getCreatedByFilter() {
        if (isAdmin()) return null;
        if (isRegularEmployee()) return username;
        return null;
    }
}
