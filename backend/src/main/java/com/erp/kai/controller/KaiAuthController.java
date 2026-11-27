package com.erp.kai.controller;

import com.erp.common.Result;
import com.erp.kai.entity.KaiUser;
import com.erp.kai.service.KaiAuthService;
import com.erp.kai.service.KaiCreditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小楷独立版 · 注册 / 登录（FEAT-KAI-STANDALONE-001）。
 * 公开接口（无需 token）：/auth/register、/auth/login、/auth/public-config。
 */
@Slf4j
@RestController
@RequestMapping("/api/kai-standalone/auth")
@RequiredArgsConstructor
public class KaiAuthController {

    private final KaiAuthService kaiAuthService;
    private final KaiCreditService creditService;

    /** 公开配置：注册页展示赠送积分与换算口径。 */
    @GetMapping("/public-config")
    public Result<Map<String, Object>> publicConfig() {
        return Result.success(creditService.publicConfig());
    }

    /** 是否已初始化运营管理员（公开：登录页据此显示初始化入口）。 */
    @GetMapping("/operator-status")
    public Result<Map<String, Object>> operatorStatus() {
        return Result.success(Map.of("initialized", kaiAuthService.operatorInitialized()));
    }

    /** 首次初始化运营管理员（仅在未初始化时可用；客户注册不会产生运营权限）。 */
    @PostMapping("/operator-init")
    public Result<Map<String, Object>> initOperator(@RequestBody Map<String, String> body) {
        KaiUser user = kaiAuthService.initOperator(
                body.get("username"), body.get("password"), body.get("displayName"));
        return Result.success(profile(user));
    }

    /** 当前登录用户资料（含积分余额与管理员标记）。 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("kaiUserId");
        KaiUser user = kaiAuthService.byId(userId);
        if (user == null) return Result.error(401, "登录已过期，请重新登录");
        return Result.success(profile(user));
    }

    /** 注册（创建即登录，返回 token 与用户信息）。 */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        KaiUser user = kaiAuthService.register(
                body.get("username"), body.get("password"),
                body.get("displayName"), body.get("companyName"),
                body.get("phone"), body.get("email"));
        return Result.success(profile(user));
    }

    /** 登录：返回独立 token 与用户信息。 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        KaiUser user = kaiAuthService.login(body.get("username"), body.get("password"));
        return Result.success(profile(user));
    }

    private Map<String, Object> profile(KaiUser user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", kaiAuthService.issueToken(user));
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("displayName", user.getDisplayName());
        m.put("companyName", user.getCompanyName());
        m.put("phone", user.getPhone());
        m.put("email", user.getEmail());
        m.put("creditBalance", user.getCreditBalance() == null ? 0 : user.getCreditBalance());
        m.put("isAdmin", user.getIsAdmin() != null && user.getIsAdmin() == 1);
        m.put("tokensPerCredit", creditService.tokensPerCredit());
        m.put("lowBalance", creditService.getLong(KaiCreditService.K_LOW_BALANCE));
        return m;
    }
}
