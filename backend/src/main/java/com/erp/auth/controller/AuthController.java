package com.erp.auth.controller;

import com.erp.auth.util.JwtUtil;
import com.erp.common.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        // 兼容PG(user_id)和H2(id)两种主键名，兼容有无deleted字段
        Map<String, Object> user;
        try {
            user = jdbcTemplate.queryForMap(
                    "SELECT user_id, username, real_name FROM sys_user WHERE username = ? AND password = ? AND is_enabled = 1",
                    req.getUsername(), req.getPassword());
        } catch (Exception e) {
            user = jdbcTemplate.queryForMap(
                    "SELECT id AS user_id, username, real_name FROM sys_user WHERE username = ? AND password = ? AND deleted = 0",
                    req.getUsername(), req.getPassword());
        }

        String token = jwtUtil.generateToken(
                ((Number) user.get("user_id")).longValue(),
                (String) user.get("username"));

        return Result.success(Map.of(
                "token", token,
                "username", user.get("username"),
                "realName", user.getOrDefault("real_name", user.get("username"))
        ));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
