package com.erp.system.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.system.entity.SysRole;
import com.erp.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @RequestParam(required = false) String keyword) {
        return Result.success(sysRoleService.pageQuery(current, size, keyword));
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
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        sysRoleService.assignPermissions(id, menuIds);
        return Result.success();
    }
}
