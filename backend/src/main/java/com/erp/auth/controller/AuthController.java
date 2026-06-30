package com.erp.auth.controller;

import com.erp.auth.util.JwtUtil;
import com.erp.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        // 兼容PG(user_id, is_enabled)和H2(id, deleted)两种主键名
        Map<String, Object> user;
        try {
            user = jdbcTemplate.queryForMap(
                    "SELECT user_id, username, real_name, is_admin FROM sys_user WHERE username = ? AND password = ? AND is_enabled = 1",
                    req.getUsername(), req.getPassword());
        } catch (Exception e) {
            user = jdbcTemplate.queryForMap(
                    "SELECT id AS user_id, username, real_name, 0 AS is_admin FROM sys_user WHERE username = ? AND password = ? AND deleted = 0",
                    req.getUsername(), req.getPassword());
        }

        Long userId = ((Number) user.get("user_id")).longValue();
        String username = (String) user.get("username");

        String token = jwtUtil.generateToken(userId, username);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("username", username);
        result.put("realName", user.getOrDefault("real_name", username));
        return Result.success(result);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    /**
     * 获取当前登录用户信息(含权限列表)
     * 前端store调用此接口获取permissions和roles
     */
    @GetMapping("/userinfo")
    public Result<Map<String, Object>> userInfo(HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("currentUserId");
        String username = (String) req.getAttribute("currentUsername");

        Map<String, Object> info = new HashMap<>();
        info.put("userId", userId);
        info.put("username", username);

        // 查询真实姓名和管理员标识
        try {
            Map<String, Object> user = jdbcTemplate.queryForMap(
                    "SELECT real_name, is_admin FROM sys_user WHERE user_id = ?", userId);
            info.put("realName", user.get("real_name"));
            boolean isAdmin = user.get("is_admin") != null && ((Number) user.get("is_admin")).intValue() == 1;
            info.put("roles", isAdmin ? List.of("admin") : List.of("user"));
            // 超级管理员返回通配权限
            if (isAdmin) {
                info.put("permissions", List.of("*"));
                return Result.success(info);
            }
        } catch (Exception ignored) {
            // H2或字段不存在时降级
        }

        // 普通用户：通过角色→权限查询菜单权限标识 (PG表 sys_menu 字段为 permission_code)
        List<String> permissions = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT m.permission_code FROM sys_user_role ur " +
                    "JOIN sys_role_permission rp ON ur.role_id = rp.role_id " +
                    "JOIN sys_menu m ON rp.menu_id = m.menu_id " +
                    "WHERE ur.user_id = ? AND m.permission_code IS NOT NULL", userId);
            for (Map<String, Object> row : rows) {
                Object p = row.get("permission_code");
                if (p != null && !p.toString().isBlank()) permissions.add(p.toString());
            }
        } catch (Exception ignored) {
            // 权限表未就绪时返回空列表
        }
        info.put("permissions", permissions);
        return Result.success(info);
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
