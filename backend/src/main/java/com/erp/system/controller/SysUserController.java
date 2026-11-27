package com.erp.system.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.system.entity.SysUser;
import com.erp.system.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 用户管理 Controller
 * 路由: /api/system/user
 */
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /**
     * 写操作鉴权（安全审计 C-1）：原实现对 /api/system/user 的写接口无任何服务端校验，
     * 任意已登录用户可直接重置他人密码 / 自助授予管理员角色 → 完全接管系统。
     * 角色与权限来源：AuthInterceptor 写入的 currentRoles/currentPermissions（sys_user_role 真实数据）。
     */
    private static HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    /**
     * 管理员口径：ADMIN/SUPER_ADMIN 角色 或 通配权限 * 或 租户版本管理员（ADMIN_ULTIMATE 等）。
     * 与前端 EmployeeEdit.vue 的 canResetPassword 判断逐字一致（不改动既有功能边界）。
     *
     * 2026-09-20 修复：原先只认 ADMIN/SUPER_ADMIN，导致多租户下"租户管理员"（版本角色
     * ADMIN_STANDARD/ADMIN_CRM/ADMIN_ULTIMATE）被判为无权限 → 建用户/改用户/删用户一律 403。
     */
    private static boolean isAdmin() {
        HttpServletRequest req = currentRequest();
        return SecurityUtils.hasAnyRole(req, "ADMIN", "SUPER_ADMIN")
                || SecurityUtils.hasVersionAdminRole(req)
                || SecurityUtils.hasPermission(req, "*");
    }

    /** 设置中心口径（2026-10-13 与按钮权限拦截器对齐）：管理员 或 显式勾选「用户管理:保存」按钮码 */
    private static boolean canWriteUser() {
        return isAdmin() || SecurityUtils.hasPermission(currentRequest(), "system:user:save");
    }

    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(sysUserService.pageQuery(current, size, keyword, status, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<SysUser> detail(@PathVariable Long id) {
        return Result.success(sysUserService.getDetail(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody SysUser user) {
        if (!canWriteUser()) return Result.error(403, "无权限管理用户：请在【设置中心 → 角色权限设置 → 按钮权限】为该角色勾选「用户管理」的「保存」权限");
        sysUserService.saveUser(user);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        if (!canWriteUser()) return Result.error(403, "无权限管理用户：请在【设置中心 → 角色权限设置 → 按钮权限】为该角色勾选「用户管理」的「保存」权限");
        user.setUserId(id);
        sysUserService.updateUser(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!isAdmin() && !SecurityUtils.hasPermission(currentRequest(), "system:user:delete")) {
            return Result.error(403, "无权限删除用户：请在【设置中心 → 角色权限设置 → 按钮权限】为该角色勾选「用户管理」的「删除」权限");
        }
        sysUserService.deleteUser(id);
        return Result.success();
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        // 密码重置属提权动作：管理员 或 显式勾选「用户管理:重置密码」按钮码（2026-10-13 起可配置）
        if (!isAdmin() && !SecurityUtils.hasPermission(currentRequest(), "system:user:resetPassword")) {
            return Result.error(403, "无权重置密码：请在【设置中心 → 角色权限设置 → 按钮权限】为该角色勾选「用户管理」的「重置密码」权限");
        }
        sysUserService.resetPassword(id);
        return Result.success();
    }

    @PostMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        // 角色分配属提权动作：管理员 或 显式勾选「用户管理:分配角色」按钮码（2026-10-13 起可配置）
        if (!isAdmin() && !SecurityUtils.hasPermission(currentRequest(), "system:user:assignRole")) {
            return Result.error(403, "无权分配角色：请在【设置中心 → 角色权限设置 → 按钮权限】为该角色勾选「用户管理」的「分配角色」权限");
        }
        sysUserService.assignRoles(id, roleIds);
        return Result.success();
    }
}
