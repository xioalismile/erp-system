package com.erp.kai.config;

import com.erp.auth.util.JwtUtil;
import com.erp.kai.entity.KaiUser;
import com.erp.kai.mapper.KaiUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 小楷独立版 · 独立鉴权拦截器（FEAT-KAI-STANDALONE-001）。
 * 只拦 /api/kai-standalone/**（登录注册除外）；
 * 要求 token 带 scope=kai-standalone 且用户存在并启用，注入 kaiUserId/kaiUsername。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KaiAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final KaiUserMapper kaiUserMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        // 只有登录、注册、公开配置免鉴权；/auth/me 仍需登录态。
        // 支付宝异步通知与支付完成跳回由支付宝直接访问，不带小楷 token，也必须放行。
        if (path.equals("/api/kai-standalone/auth/login")
                || path.equals("/api/kai-standalone/auth/register")
                || path.equals("/api/kai-standalone/auth/public-config")
                || path.equals("/api/kai-standalone/auth/operator-status")
                || path.equals("/api/kai-standalone/auth/operator-init")
                || path.equals("/api/kai-standalone/pay/alipay/notify")
                || path.equals("/api/kai-standalone/pay/alipay/return")) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return reject(response, "请先登录小楷");
        }
        token = token.substring(7);
        try {
            String scope = jwtUtil.parseScope(token);
            if (!"kai-standalone".equals(scope)) {
                log.warn("非独立版 token 访问小楷独立版接口: path={}", path);
                return reject(response, "请使用小楷独立版账号登录");
            }
            Long userId = jwtUtil.parseUserId(token);
            KaiUser user = kaiUserMapper.selectById(userId);
            if (user == null || (user.getStatus() != null && user.getStatus() != 1)) {
                return reject(response, "账号不存在或已停用");
            }
            request.setAttribute("kaiUserId", userId);
            request.setAttribute("kaiUsername", user.getUsername());
            request.setAttribute("kaiDisplayName", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
            return true;
        } catch (Exception e) {
            log.warn("小楷独立版 token 解析失败: {}", e.getMessage());
            return reject(response, "登录已过期，请重新登录");
        }
    }

    private boolean reject(HttpServletResponse response, String message) {
        try {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
            response.getWriter().flush();
        } catch (Exception e) {
            log.warn("写独立版鉴权失败响应体异常: {}", e.getMessage());
        }
        return false;
    }
}
