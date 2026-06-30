package com.erp.system.controller;

import com.erp.common.Result;
import com.erp.system.entity.SysMenu;
import com.erp.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理 Controller
 * 路由: /api/system/menu
 */
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @GetMapping("/list")
    public Result<List<SysMenu>> list() {
        return Result.success(sysMenuService.listAll());
    }

    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        return Result.success(sysMenuService.getTree());
    }

    @GetMapping("/{id}")
    public Result<SysMenu> detail(@PathVariable Long id) {
        return Result.success(sysMenuService.getDetail(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody SysMenu menu) {
        sysMenuService.saveMenu(menu);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menu.setMenuId(id);
        sysMenuService.updateMenu(menu);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.deleteMenu(id);
        return Result.success();
    }
}
