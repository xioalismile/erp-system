package com.erp.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.Result;
import com.erp.permission.dto.FieldMetaDTO;
import com.erp.permission.dto.PermissionDTO;
import com.erp.permission.entity.SysRoleFieldPermission;
import com.erp.permission.service.FieldMetaService;
import com.erp.permission.service.SysRoleFieldPermissionService;
import com.erp.system.entity.SysUserRole;
import com.erp.system.mapper.SysUserRoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字段权限 Controller
 * 路径前缀: /api/system/field-permission
 * 复用 com.erp.permission.service 下的 Service 实现。
 */
@RestController
@RequestMapping("/api/system/field-permission")
@RequiredArgsConstructor
public class FieldPermissionController {

    private final SysRoleFieldPermissionService roleFieldPermissionService;
    private final FieldMetaService fieldMetaService;
    private final SysUserRoleMapper sysUserRoleMapper;

    /**
     * 查询指定角色 + 页面的字段权限列表
     */
    @GetMapping("/role/{roleId}/page/{pageKey}")
    public Result<List<SysRoleFieldPermission>> getByRoleAndPage(@PathVariable Long roleId,
                                                                 @PathVariable String pageKey) {
        return Result.success(roleFieldPermissionService.getByRoleAndPage(roleId, pageKey));
    }

    /**
     * 保存指定角色 + 页面的字段权限列表
     * 前端使用 POST 提交权限列表
     */
    @PostMapping("/role/{roleId}/page/{pageKey}")
    public Result<Void> saveByRoleAndPage(@PathVariable Long roleId,
                                          @PathVariable String pageKey,
                                          @RequestBody List<SysRoleFieldPermission> list) {
        roleFieldPermissionService.savePermissions(roleId, pageKey, list);
        return Result.success();
    }

    /**
     * 查询当前登录用户在指定页面的字段权限 Map
     * key: fieldKey, value: PermissionDTO
     */
    @GetMapping("/current/page/{pageKey}")
    public Result<Map<String, PermissionDTO>> getCurrentUserPagePermissions(@PathVariable String pageKey,
                                                                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (userId == null) {
            return Result.error(401, "用户未登录");
        }
        List<Long> roleIds = getUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Result.success(Collections.emptyMap());
        }
        return Result.success(roleFieldPermissionService.getFieldPermissionMap(roleIds, pageKey));
    }

    /**
     * 复制字段权限
     * 前端以 JSON body 形式提交 { sourceRoleId, targetRoleId }
     */
    @PostMapping("/copy")
    public Result<Void> copy(@RequestBody Map<String, Object> body) {
        Object src = body.get("sourceRoleId");
        Object tgt = body.get("targetRoleId");
        if (src == null || tgt == null) {
            return Result.error("sourceRoleId 与 targetRoleId 不能为空");
        }
        Long sourceRoleId = Long.valueOf(src.toString());
        Long targetRoleId = Long.valueOf(tgt.toString());
        roleFieldPermissionService.copyPermissions(sourceRoleId, targetRoleId);
        return Result.success();
    }

    /**
     * 查询指定页面的字段元数据列表
     */
    @GetMapping("/meta/page/{pageKey}")
    public Result<List<FieldMetaDTO>> getFieldMetaList(@PathVariable String pageKey) {
        return Result.success(fieldMetaService.getFieldsByPage(pageKey));
    }

    private List<Long> getUserRoleIds(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new QueryWrapper<SysUserRole>().eq("user_id", userId));
        return userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
    }
}
