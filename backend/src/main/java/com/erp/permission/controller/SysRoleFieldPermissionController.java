package com.erp.permission.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.permission.dto.FieldMetaDTO;
import com.erp.permission.dto.PermissionDTO;
import com.erp.permission.entity.SysRoleFieldPermission;
import com.erp.permission.service.FieldMetaService;
import com.erp.permission.service.SysRoleFieldPermissionService;
import com.erp.report.custom.core.EntityMetadataRegistry;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysUserRoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/role-field-permission")
public class SysRoleFieldPermissionController {

    private final SysRoleFieldPermissionService roleFieldPermissionService;
    private final FieldMetaService fieldMetaService;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final EntityMetadataRegistry entityMetadataRegistry;

    public SysRoleFieldPermissionController(SysRoleFieldPermissionService roleFieldPermissionService,
                                            FieldMetaService fieldMetaService,
                                            SysUserRoleMapper sysUserRoleMapper,
                                            EntityMetadataRegistry entityMetadataRegistry) {
        this.roleFieldPermissionService = roleFieldPermissionService;
        this.fieldMetaService = fieldMetaService;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.entityMetadataRegistry = entityMetadataRegistry;
    }

    @GetMapping("/role/{roleId}/page/{pageKey}")
    public Result<List<SysRoleFieldPermission>> getByRoleAndPage(@PathVariable Long roleId,
                                                                 @PathVariable String pageKey) {
        List<SysRoleFieldPermission> list = roleFieldPermissionService.getByRoleAndPage(roleId, pageKey);
        return Result.success(list);
    }

    /** 保存角色字段权限（仅管理员可操作） */
    @PutMapping("/role/{roleId}/page/{pageKey}")
    public Result<Void> savePermissions(@PathVariable Long roleId,
                                        @PathVariable String pageKey,
                                        @RequestBody List<SysRoleFieldPermission> list,
                                        HttpServletRequest request) {
        if (!SecurityUtils.hasAnyRole(request, "ADMIN", "MANAGER", "SUPER_ADMIN")
                && !SecurityUtils.hasVersionAdminRole(request)) {
            return Result.error(403, "仅管理员可配置字段权限");
        }
        roleFieldPermissionService.savePermissions(roleId, pageKey, list);
        return Result.success();
    }

    @GetMapping("/current/page/{pageKey}")
    public Result<Map<String, PermissionDTO>> getCurrentUserPagePermissions(@PathVariable String pageKey,
                                                                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            return Result.error(401, "用户未登录");
        }
        // 【2026-09-28 口径变更】管理员不再"直通短路返回空"（历史行为导致：配置者在本账号上永远看不到效果
        // —— 没有星号、也不会被必填拦住，用户判定"功能没生效"）。
        // ① 全权管理员：按"该页面上任何角色配过的字段并集"生效，配置完刷新即见，口径=配置即生效；
        // ② 其它账号：按"自己所属角色"的配置生效（多角色 required 取交集，沿用既有策略）。
        Map<String, PermissionDTO> permissionMap;
        if (SecurityUtils.hasAnyRole(request, "ADMIN", "MANAGER", "SUPER_ADMIN")
                || SecurityUtils.hasVersionAdminRole(request)) {
            permissionMap = roleFieldPermissionService.getAllRolesFieldPermissionMap(pageKey);
        } else {
            List<Long> roleIds = getUserRoleIds(userId);
            permissionMap = roleFieldPermissionService.getFieldPermissionMap(roleIds, pageKey);
        }
        return Result.success(permissionMap);
    }

    /** 复制角色字段权限（仅管理员可操作） */
    @PostMapping("/copy")
    public Result<Void> copyPermissions(@RequestParam Long sourceRoleId,
                                        @RequestParam Long targetRoleId,
                                        HttpServletRequest request) {
        if (!SecurityUtils.hasAnyRole(request, "ADMIN", "MANAGER", "SUPER_ADMIN")
                && !SecurityUtils.hasVersionAdminRole(request)) {
            return Result.error(403, "仅管理员可配置字段权限");
        }
        roleFieldPermissionService.copyPermissions(sourceRoleId, targetRoleId);
        return Result.success();
    }

    @GetMapping("/fields/{pageKey}")
    public Result<List<FieldMetaDTO>> getFieldMetaList(@PathVariable String pageKey) {
        List<FieldMetaDTO> fields = fieldMetaService.getFieldsByPage(pageKey);
        return Result.success(fields);
    }

    /** 可配置字段权限的页面列表（来自报表元数据注册表：key=表名/billType小写，含中文名与所属模块） */
    @GetMapping("/pages")
    public Result<List<Map<String, Object>>> getAvailablePages() {
        return Result.success(entityMetadataRegistry.listDataSources());
    }

    /** 该角色已配置过字段权限的页面 key（设置中心字段权限左栏据此决定默认展开哪些模块） */
    @GetMapping("/role/{roleId}/configured-pages")
    public Result<java.util.Set<String>> getConfiguredPages(@PathVariable Long roleId) {
        return Result.success(roleFieldPermissionService.getConfiguredPages(roleId));
    }

    private List<Long> getUserRoleIds(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new QueryWrapper<SysUserRole>().eq("user_id", userId));
        return userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
    }
}
