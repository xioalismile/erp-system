package com.erp.system.controller;

import com.erp.common.Result;
import com.erp.system.entity.SysMenu;
import com.erp.system.service.SysMenuService;
import jakarta.servlet.http.HttpServletRequest;
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
    public Result<List<SysMenu>> tree(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        // 检查是否admin
        boolean isAdmin = false;
        Object isAdminAttr = request.getAttribute("isAdmin");
        if (isAdminAttr != null) {
            isAdmin = Boolean.TRUE.equals(isAdminAttr) || "1".equals(isAdminAttr.toString());
        }
        if (!isAdmin) {
            // 检查角色
            Object roles = request.getAttribute("currentRoles");
            if (roles instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> roleList = (List<String>) roles;
                isAdmin = roleList.contains("ADMIN");
            }
        }
        return Result.success(sysMenuService.getTreeByUser(userId, isAdmin));
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
