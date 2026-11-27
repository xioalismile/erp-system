package com.erp.tenant;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 租户上下文过滤器（多租户隔离的地基 · 第一道）
 *
 * ============ 为什么必须是 Filter（2026-09-20 线上实测定位）============
 * 原先由 TenantInterceptor 负责"按 JWT 的 tenantId 切换数据源"，注册在 WebMvcConfig 里声明 order(0)，
 * AuthInterceptor 在另一个配置类里声明 order(1)，期望"先切库、后鉴权"。线上实测却是鉴权先跑：
 *   AuthInterceptor   : 当前租户库=null      → 角色查询落主库，取到别人的角色 SALES_REP（越权判定错误）
 *   TenantInterceptor : 切换后库=erp_tenant_sc_wudong
 * → 租户管理员被判为普通用户（建/删用户 403），切库前的一切查询都跑在主库上，隔离形同虚设。
 *
 * 处置：把租户上下文的建立下沉到 Servlet Filter（规范保证早于 DispatcherServlet，也就早于所有 Interceptor），
 * 清理放在 finally；同时在 AuthInterceptor 里再调用一次解析器做**双保险**（见 TenantContextResolver），
 * 使得"鉴权发生时租户上下文一定正确"不再依赖任何执行顺序。
 *
 * ============ 客户到期阻断（2026-11-09 新增）============
 * 需求：客户到期日期到了必须停止服务，不能继续登录、也不能靠旧会话继续使用。
 * 解析器在切库时顺带判定到期并打请求标记，本过滤器读标记后直接返回 403（先于一切拦截器），
 * 这样"已登录的会话跨过到期时刻"同样立即失效。
 * 放行清单只保留"无法判断所属客户/运维必需"的入口：登录、退出、选公司、公司列表、公开接口、
 * License 状态、构建信息，以及平台管理（超管要给到期客户续费，否则到期后无人能续）。
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter implements Filter {

    private final TenantContextResolver tenantContextResolver;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        try {
            tenantContextResolver.resolve(httpRequest);
            if (Boolean.TRUE.equals(httpRequest.getAttribute(TenantContextResolver.TENANT_EXPIRED_ATTR))
                    && !isExpiryExempt(httpRequest.getRequestURI())) {
                writeExpired((HttpServletResponse) response);
                return;
            }
            chain.doFilter(request, response);
        } finally {
            // 必须在 finally 清理：否则线程池复用会让下一个请求带上别人的租户上下文（越权）
            TenantContextHolder.clear();
        }
    }

    /** 到期不阻断的入口（登录阶段还没有客户身份；平台管理必须保留续费通道） */
    private boolean isExpiryExempt(String path) {
        if (path == null) {
            return false;
        }
        return path.contains("/api/auth/login")
                || path.contains("/api/auth/wx-login")
                || path.contains("/api/auth/logout")
                || path.contains("/api/tenant/select")
                || path.contains("/api/tenant/my")
                || path.contains("/api/ai/public")
                || path.startsWith("/api/public/")
                || path.startsWith("/api/license")
                || path.startsWith("/api/sys-config/public")
                || path.startsWith("/api/system/build-info")
                || path.startsWith("/api/kai-standalone/")
                || path.startsWith("/api/admin/");
    }

    private void writeExpired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"该客户已到期，请联系服务商续费\",\"data\":null}");
    }
}
