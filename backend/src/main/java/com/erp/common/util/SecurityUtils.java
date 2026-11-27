package com.erp.common.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 安全上下文工具类 - 从 HttpServletRequest 中获取当前登录用户信息。
 * 数据来源：{@link com.erp.config.AuthInterceptor} 在 preHandle 中注入的属性。
 *
 * 角色编码对照（与 sys_role.role_code 一致）：
 *   ADMIN / MANAGER / SALES / PURCHASE / WAREHOUSE / PRODUCTION / FINANCE / VIEWER
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Long getCurrentUserId(HttpServletRequest request) {
        Object v = request.getAttribute("currentUserId");
        if (v == null) return null;
        if (v instanceof Long) return (Long) v;
        return Long.valueOf(v.toString());
    }

    public static String getCurrentUsername(HttpServletRequest request) {
        Object v = request.getAttribute("currentUsername");
        return v == null ? null : v.toString();
    }

    /**
     * 当前用户真实姓名（sys_user.real_name，由 AuthInterceptor 注入）。
     * 取不到时返回 null，调用方自行回落登录账号 —— 操作日志要展示"是谁改的"，
     * 展示账号（如 lyc123）用户看不懂。
     */
    public static String getCurrentRealName(HttpServletRequest request) {
        Object v = request.getAttribute("currentRealName");
        return v == null ? null : v.toString();
    }

    /** 当前用户对应的员工ID（sys_user.employee_id），用于销售/采购等按员工过滤的场景 */
    public static Long getCurrentEmployeeId(HttpServletRequest request) {
        Object v = request.getAttribute("currentEmployeeId");
        if (v == null) return null;
        if (v instanceof Long) return (Long) v;
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    /** 当前用户的角色编码列表（来自 sys_user_role -> sys_role.role_code） */
    @SuppressWarnings("unchecked")
    public static List<String> getCurrentRoles(HttpServletRequest request) {
        Object v = request.getAttribute("currentRoles");
        if (v == null) return Collections.emptyList();
        if (v instanceof List) return (List<String>) v;
        return Collections.emptyList();
    }

    /** 是否包含任一指定角色 */
    public static boolean hasAnyRole(HttpServletRequest request, String... roleCodes) {
        if (roleCodes == null || roleCodes.length == 0) return false;
        List<String> roles = getCurrentRoles(request);
        if (roles.isEmpty()) return false;
        return Arrays.stream(roleCodes).anyMatch(roles::contains);
    }

    /** 当前用户的权限码列表（来自 sys_role_permission -> sys_menu.permission_code） */
    @SuppressWarnings("unchecked")
    public static List<String> getCurrentPermissions(HttpServletRequest request) {
        Object v = request.getAttribute("currentPermissions");
        if (v == null) return Collections.emptyList();
        if (v instanceof List) return (List<String>) v;
        return Collections.emptyList();
    }

    /** 是否拥有指定权限码（含通配符 '*' 全放行）。按权限码判断，不按角色名硬编码。 */
    public static boolean hasPermission(HttpServletRequest request, String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) return false;
        List<String> perms = getCurrentPermissions(request);
        if (perms.isEmpty()) return false;
        if (perms.contains("*")) return true;
        return perms.contains(permissionCode);
    }

    /**
     * 当前用户的菜单/子功能路径权限（来自 sys_role_permission.menu_id 中 '/' 开头者，
     * 如 {@code /system/settings}、{@code /system/settings?item=custom-field}）。
     * 与「能打开某个菜单页面」同口径，供设置中心这类"按菜单路径授权"的场景判定。
     */
    @SuppressWarnings("unchecked")
    public static List<String> getCurrentMenuPaths(HttpServletRequest request) {
        Object v = request.getAttribute("currentMenuPaths");
        if (v == null) return Collections.emptyList();
        if (v instanceof List) return (List<String>) v;
        return Collections.emptyList();
    }

    /** 是否拥有指定菜单路径（含子项：{@code /system/settings} 可命中 {@code /system/settings?item=xxx}） */
    public static boolean hasMenuPath(HttpServletRequest request, String menuPath) {
        if (menuPath == null || menuPath.isBlank()) return false;
        List<String> paths = getCurrentMenuPaths(request);
        if (paths.isEmpty()) return false;
        for (String p : paths) {
            if (p == null) continue;
            if (p.equals(menuPath) || p.startsWith(menuPath + "?") || p.equals("*")) return true;
        }
        return false;
    }

    /**
     * 是否为多租户"版本管理员"角色码。
     *
     * 背景（2026-09-20 定位到的真实缺陷）：租户创建时用 TenantService.assignAdminRole 分配的是
     * **版本角色** —— STANDARD→ADMIN_STANDARD、CRM→ADMIN_CRM、其余→ADMIN_ULTIMATE；
     * 紧接着 trimTenantRoles 又把通用 ADMIN/MANAGER 角色删掉（注释自述"与版本管理员语义重复"）。
     * 而所有管理员鉴权原先只认 ADMIN/SUPER_ADMIN/MANAGER → 租户管理员在自己租户里被判为"非管理员"，
     * 表现为：建用户/改用户/重置密码/AI配置/角色字段权限/自定义报表 等接口一律 403。
     * 用平台超管 admin 测试时因其持有 SUPER_ADMIN 而永远发现不了这个缺陷。
     */
    public static boolean isVersionAdminRoleCode(String roleCode) {
        return roleCode != null && roleCode.startsWith("ADMIN_");
    }

    /** 当前用户是否持有任一"版本管理员"角色（ADMIN_STANDARD / ADMIN_CRM / ADMIN_ULTIMATE …） */
    public static boolean hasVersionAdminRole(HttpServletRequest request) {
        for (String r : getCurrentRoles(request)) {
            if (isVersionAdminRoleCode(r)) return true;
        }
        return false;
    }

    /** 是否为管理员（ADMIN / MANAGER，或租户的版本管理员角色） */
    public static boolean isAdmin(HttpServletRequest request) {
        return hasAnyRole(request, "ADMIN", "MANAGER") || hasVersionAdminRole(request);
    }

    /** 是否为销售员（仅 SALES，非管理岗） */
    public static boolean isSalesOnly(HttpServletRequest request) {
        List<String> roles = getCurrentRoles(request);
        return roles.contains("SALES") && !roles.contains("ADMIN") && !roles.contains("MANAGER");
    }

    /** 是否为采购员（仅 PURCHASE，非管理岗） */
    public static boolean isPurchaseOnly(HttpServletRequest request) {
        List<String> roles = getCurrentRoles(request);
        return roles.contains("PURCHASE") && !roles.contains("ADMIN") && !roles.contains("MANAGER");
    }

    /** 是否仅看自己的数据：非管理岗的销售员/采购员 */
    public static boolean shouldFilterBySelf(HttpServletRequest request) {
        return isSalesOnly(request) || isPurchaseOnly(request);
    }

    /**
     * 获取当前用户的部门ID（sys_user.dept_id）
     */
    public static Long getCurrentDeptId(HttpServletRequest request) {
        Object v = request.getAttribute("currentDeptId");
        if (v == null) return null;
        if (v instanceof Long) return (Long) v;
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    /**
     * 判断是否为销售主管（SALES_MANAGER）
     */
    public static boolean isSalesManager(HttpServletRequest request) {
        List<String> roles = getCurrentRoles(request);
        return roles.contains("SALES_MANAGER") && !roles.contains("ADMIN") && !roles.contains("MANAGER");
    }

    /**
     * 判断是否为采购主管（PURCHASE_MANAGER）
     */
    public static boolean isPurchaseManager(HttpServletRequest request) {
        List<String> roles = getCurrentRoles(request);
        return roles.contains("PURCHASE_MANAGER") && !roles.contains("ADMIN") && !roles.contains("MANAGER");
    }

    /**
     * 数据范围过滤：销售员只能看自己的数据
     * 返回 true 表示需要按用户ID过滤
     */
    public static boolean shouldFilterByDept(HttpServletRequest request) {
        // MANAGER 或 ADMIN 看全部，不过滤
        if (isAdmin(request)) return false;
        // SALES_MANAGER 或 PURCHASE_MANAGER 按部门过滤
        if (isSalesManager(request) || isPurchaseManager(request)) return true;
        // 普通销售员/采购员 按自己过滤
        return isSalesOnly(request) || isPurchaseOnly(request);
    }

    // ==================== 扩展角色判断 ====================

    /** 是否为仓库员（仅 WAREHOUSE，非管理岗） */
    public static boolean isWarehouseOnly(HttpServletRequest request) {
        List<String> roles = getCurrentRoles(request);
        return roles.contains("WAREHOUSE") && !roles.contains("ADMIN") && !roles.contains("MANAGER");
    }

    /** 是否为仓库主管（WAREHOUSE_MANAGER） */
    public static boolean isWarehouseManager(HttpServletRequest request) {
        List<String> roles = getCurrentRoles(request);
        return roles.contains("WAREHOUSE_MANAGER") && !roles.contains("ADMIN") && !roles.contains("MANAGER");
    }

    /** 是否为生产员（仅 PRODUCTION，非管理岗） */
    public static boolean isProductionOnly(HttpServletRequest request) {
        List<String> roles = getCurrentRoles(request);
        return roles.contains("PRODUCTION") && !roles.contains("ADMIN") && !roles.contains("MANAGER");
    }

    /** 是否为生产主管（PRODUCTION_MANAGER） */
    public static boolean isProductionManager(HttpServletRequest request) {
        List<String> roles = getCurrentRoles(request);
        return roles.contains("PRODUCTION_MANAGER") && !roles.contains("ADMIN") && !roles.contains("MANAGER");
    }

    /** 是否为财务员（仅 FINANCE，非管理岗） */
    public static boolean isFinanceOnly(HttpServletRequest request) {
        List<String> roles = getCurrentRoles(request);
        return roles.contains("FINANCE") && !roles.contains("ADMIN") && !roles.contains("MANAGER");
    }

    /** 是否为财务主管（FINANCE_MANAGER） */
    public static boolean isFinanceManager(HttpServletRequest request) {
        List<String> roles = getCurrentRoles(request);
        return roles.contains("FINANCE_MANAGER") && !roles.contains("ADMIN") && !roles.contains("MANAGER");
    }

    /**
     * 判断当前用户是否为普通员工（非管理岗、非admin）。
     * 普通员工只能看到自己创建的数据。
     */
    public static boolean isRegularEmployee(HttpServletRequest request) {
        return isSalesOnly(request) || isPurchaseOnly(request)
                || isWarehouseOnly(request) || isProductionOnly(request)
                || isFinanceOnly(request);
    }

    /**
     * 获取数据权限过滤的创建人字段值。
     * 普通员工返回其用户名，管理岗返回 null（不过滤）。
     * 用于按 created_by 字段过滤数据。
     *
     * @return 需要过滤时返回当前用户名，不需要过滤时返回 null
     */
    public static String getCreatedByFilter(HttpServletRequest request) {
        if (isAdmin(request)) return null;
        // 主管级看本部门全部，暂不过滤（后续可扩展按部门过滤）
        if (isSalesManager(request) || isPurchaseManager(request)
                || isWarehouseManager(request) || isProductionManager(request)
                || isFinanceManager(request)) {
            return null;
        }
        // 普通员工只能看自己创建的数据
        if (isRegularEmployee(request)) {
            return getCurrentUsername(request);
        }
        return null;
    }

    /**
     * 获取数据权限过滤的员工ID。
     * 销售/采购普通员工返回其 employeeId，其他角色返回 null。
     *
     * @return 需要过滤时返回员工ID，不需要过滤时返回 null
     */
    public static Long getEmployeeIdFilter(HttpServletRequest request) {
        if (isAdmin(request)) return null;
        if (isSalesManager(request) || isPurchaseManager(request)) return null;
        if (isSalesOnly(request) || isPurchaseOnly(request)) {
            return getCurrentEmployeeId(request);
        }
        return null;
    }
}
