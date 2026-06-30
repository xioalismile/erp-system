package com.erp.config;

import com.erp.auth.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // OPTIONS预检放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        // 登录、AI回调等公开接口放行
        if (path.contains("/api/auth/login") || path.contains("/api/ai/public")) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Long userId = jwtUtil.parseUserId(token);
                request.setAttribute("currentUserId", userId);
                request.setAttribute("currentUsername", jwtUtil.parseUsername(token));
                return true;
            } catch (Exception e) {
                log.warn("JWT解析失败: {}", e.getMessage());
            }
        }
        response.setStatus(401);
        return false;
    }
}
