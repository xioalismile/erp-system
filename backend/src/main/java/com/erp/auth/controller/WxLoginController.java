package com.erp.auth.controller;

import com.erp.auth.util.JwtUtil;
import com.erp.common.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 微信小程序登录控制器
 *
 * 提供三个接口：
 *   POST /api/auth/wx-login   - 微信登录（code换openid，命中已绑定的用户直接发token）
 *   POST /api/auth/wx-bind    - 绑定微信（当前登录用户绑定openid，下次免密登录）
 *   POST /api/auth/wx-unbind  - 解绑微信
 *
 * 部署前需要在 application.yml 配置：
 *   wx:
 *     miniapp:
 *       appid: ${WX_APPID:}
 *       secret: ${WX_SECRET:}
 *
 * 数据库需要执行：
 *   ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS wx_openid VARCHAR(64);
 *   CREATE INDEX IF NOT EXISTS idx_sys_user_wx_openid ON sys_user(wx_openid);
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class WxLoginController {

    private static final Logger log = LoggerFactory.getLogger(WxLoginController.class);
    private static final String WX_JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;

    @Value("${wx.miniapp.appid:}")
    private String wxAppId;

    @Value("${wx.miniapp.secret:}")
    private String wxAppSecret;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    /**
     * 微信登录：用code换openid，命中已绑定用户则直接发token
     * 不需要Authorization头（在InterceptorConfig中排除）
     */
    @PostMapping("/wx-login")
    public Result<Map<String, Object>> wxLogin(@RequestBody WxLoginRequest req) {
        if (wxAppId == null || wxAppId.isBlank() || wxAppSecret == null || wxAppSecret.isBlank()) {
            return Result.error("服务器未配置微信小程序appid/secret，请联系管理员");
        }
        if (req == null || req.getCode() == null || req.getCode().isBlank()) {
            return Result.error("缺少微信code");
        }

        // 调用微信接口换openid
        String openid = code2Openid(req.getCode());
        if (openid == null) {
            return Result.error("微信登录失败：无法获取openid");
        }

        // 用openid查sys_user
        List<Map<String, Object>> users;
        try {
            users = jdbcTemplate.queryForList(
                    "SELECT user_id, username, real_name, is_enabled FROM sys_user WHERE wx_openid = ? AND is_enabled = 1",
                    openid);
        } catch (Exception e) {
            log.error("查询wx_openid失败，可能sys_user表缺少wx_openid字段。请执行: ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS wx_openid VARCHAR(64);", e);
            return Result.error("服务器未启用微信登录，请联系管理员执行数据库升级");
        }

        if (users.isEmpty()) {
            // 未绑定，返回needBind标志，前端引导用户先账号登录再绑定
            Map<String, Object> data = new HashMap<>();
            data.put("needBind", true);
            return Result.success(data);
        }

        Map<String, Object> user = users.get(0);
        Long userId = ((Number) user.get("user_id")).longValue();
        String username = (String) user.get("username");

        String token = jwtUtil.generateToken(userId, username);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("username", username);
        result.put("realName", user.getOrDefault("real_name", username));
        return Result.success(result);
    }

    /**
     * 绑定微信：当前登录用户绑定openid
     * 需要Authorization头（已登录状态）
     */
    @PostMapping("/wx-bind")
    public Result<Map<String, Object>> bindWx(@RequestBody WxLoginRequest req, HttpServletRequest httpReq) {
        if (wxAppId == null || wxAppId.isBlank() || wxAppSecret == null || wxAppSecret.isBlank()) {
            return Result.error("服务器未配置微信小程序appid/secret");
        }
        if (req == null || req.getCode() == null || req.getCode().isBlank()) {
            return Result.error("缺少微信code");
        }

        Long userId = (Long) httpReq.getAttribute("currentUserId");
        if (userId == null) {
            return Result.error("未登录");
        }

        String openid = code2Openid(req.getCode());
        if (openid == null) {
            return Result.error("获取微信openid失败");
        }

        // 检查该openid是否已被其他用户绑定
        try {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                    "SELECT user_id, username FROM sys_user WHERE wx_openid = ? AND user_id <> ?", openid, userId);
            if (!existing.isEmpty()) {
                return Result.error("该微信已绑定其他账号(" + existing.get(0).get("username") + ")，请先解绑");
            }
        } catch (Exception e) {
            return Result.error("服务器未启用微信登录，请联系管理员执行数据库升级");
        }

        // 绑定
        try {
            jdbcTemplate.update("UPDATE sys_user SET wx_openid = ? WHERE user_id = ?", openid, userId);
        } catch (Exception e) {
            return Result.error("绑定失败：sys_user表可能缺少wx_openid字段");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("bound", true);
        data.put("openid", openid);
        return Result.success(data);
    }

    /**
     * 登记订阅消息授权：用户在小程序授权弹窗点"允许"后调用，给该用户剩余可推次数 +1。
     * （微信一次性订阅规则：一次授权只能推一条；用户勾选"总是保持以上选择"后每次可连续授权。）
     * 2026-11-20 按模板分额度：type=approval（默认）加审批额度 wx_subscribe_quota；
     * type=operation 加工序通知额度 wx_operation_quota——授权哪个模板就登记哪个额度。
     */
    @PostMapping("/wx-subscribe-record")
    public Result<Map<String, Object>> recordWxSubscribe(@RequestBody(required = false) Map<String, Object> body,
                                                         HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("currentUserId");
        if (userId == null) {
            return Result.error("未登录");
        }
        String type = body == null || body.get("type") == null ? "approval"
                : String.valueOf(body.get("type"));
        String quotaCol = "operation".equals(type) ? "wx_operation_quota" : "wx_subscribe_quota";
        try {
            jdbcTemplate.update(
                    "UPDATE sys_user SET " + quotaCol + " = COALESCE(" + quotaCol + ", 0) + 1 WHERE user_id = ?",
                    userId);
            Number quota = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(" + quotaCol + ", 0) FROM sys_user WHERE user_id = ?",
                    Integer.class, userId);
            Map<String, Object> data = new HashMap<>();
            data.put("recorded", true);
            data.put("type", type);
            data.put("quota", quota == null ? 0 : quota.intValue());
            return Result.success(data);
        } catch (Exception e) {
            log.error("登记微信订阅授权失败（sys_user 可能缺少额度列，重启后自愈）", e);
            return Result.error("服务器未启用微信订阅提醒，请联系管理员执行数据库升级");
        }
    }

    /**
     * 解绑微信：清除当前用户的openid
     */
    @PostMapping("/wx-unbind")
    public Result<Void> unbindWx(HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("currentUserId");
        if (userId == null) {
            return Result.error("未登录");
        }
        try {
            jdbcTemplate.update("UPDATE sys_user SET wx_openid = NULL WHERE user_id = ?", userId);
        } catch (Exception e) {
            return Result.error("解绑失败");
        }
        return Result.success();
    }

    /**
     * 调用微信 jscode2session 接口换 openid
     */
    private String code2Openid(String code) {
        String url = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                WX_JSCODE2SESSION_URL, wxAppId, wxAppSecret, code);

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("调用微信接口失败: HTTP {}", response.code());
                return null;
            }
            String body = response.body() != null ? response.body().string() : "";
            JsonNode node = MAPPER.readTree(body);
            String openid = node.path("openid").asText(null);
            if (openid == null || openid.isBlank()) {
                String errcode = node.path("errcode").asText("");
                String errmsg = node.path("errmsg").asText("");
                log.error("微信登录失败: errcode={}, errmsg={}", errcode, errmsg);
                return null;
            }
            return openid;
        } catch (Exception e) {
            log.error("调用微信接口异常", e);
            return null;
        }
    }

    @Data
    public static class WxLoginRequest {
        private String code;
    }
}
