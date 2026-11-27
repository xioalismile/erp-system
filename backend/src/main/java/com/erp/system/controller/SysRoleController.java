package com.erp.system.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.system.entity.SysRole;
import com.erp.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理 Controller
 * 路由: /api/system/role
 */
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping("/page")
    public Result<PageResult<SysRole>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(sysRoleService.pageQuery(current, size, keyword, sortBy, sortOrder));
    }

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(sysRoleService.listAll());
    }

    @GetMapping("/{id}")
    public Result<SysRole> detail(@PathVariable Long id) {
        return Result.success(sysRoleService.getDetail(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody SysRole role) {
        sysRoleService.saveRole(role);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysRole role) {
        role.setRoleId(id);
        sysRoleService.updateRole(role);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.deleteRole(id);
        return Result.success();
    }

    @PostMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Object body) {
        java.util.List<String> menuIds = new java.util.ArrayList<>();
        if (body instanceof java.util.List) {
            java.util.List<?> raw = (java.util.List<?>) body;
            for (Object o : raw) {
                menuIds.add(o == null ? "" : o.toString());
            }
        } else if (body instanceof java.util.Map) {
            Object perms = ((java.util.Map<?, ?>) body).get("menuIds");
            if (perms instanceof java.util.List) {
                java.util.List<?> raw = (java.util.List<?>) perms;
                for (Object o : raw) {
                    menuIds.add(o == null ? "" : o.toString());
                }
            }
        }
        sysRoleService.assignPermissions(id, menuIds);
        return Result.success();
    }

    @GetMapping("/{id}/button-permissions")
    public Result<List<String>> getButtonPermissions(@PathVariable Long id) {
        return Result.success(sysRoleService.getButtonPermissions(id));
    }

    @PostMapping("/{id}/button-permissions")
    public Result<Void> assignButtonPermissions(@PathVariable Long id, @RequestBody Object body) {
        java.util.List<String> buttonCodes = new java.util.ArrayList<>();
        if (body instanceof java.util.List) {
            java.util.List<?> raw = (java.util.List<?>) body;
            for (Object o : raw) {
                if (o != null) buttonCodes.add(o.toString());
            }
        } else if (body instanceof java.util.Map) {
            Object codes = ((java.util.Map<?, ?>) body).get("buttonCodes");
            if (codes instanceof java.util.List) {
                java.util.List<?> raw = (java.util.List<?>) codes;
                for (Object o : raw) {
                    if (o != null) buttonCodes.add(o.toString());
                }
            }
        }
        sysRoleService.assignButtonPermissions(id, buttonCodes);
        return Result.success();
    }
}
