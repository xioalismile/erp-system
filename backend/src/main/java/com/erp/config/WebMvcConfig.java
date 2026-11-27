package com.erp.config;

import com.erp.license.validator.LicenseInterceptor;
import com.erp.tenant.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${erp.upload.path:D:/erp-files}")
    private String uploadPath;

    private final LicenseInterceptor licenseInterceptor;
    private final TenantInterceptor tenantInterceptor;

    /**
     * CORS 白名单（安全审计 P2：原 allowedOriginPatterns("*")+allowCredentials(true) 过度宽松，
     * 允许任意来源携带凭据跨域请求）。
     * 默认仅放行**本机 / 内网**开发来源：localhost、127.0.0.1（任意端口）以及典型局域网段（192.168.*）。
     * **仍限本机与内网，禁止放开为公网 `*`**。
     * 生产通过环境变量 ERP_CORS_ALLOWED_ORIGINS 注入实际域名（逗号分隔）。服务器前端为 nginx 同源部署，不依赖 CORS。
     * 【2026-10-14 修复】原白名单只写死 `http://localhost:3000` 等具体端口，导致两类常见情况写请求被拒（403 "Invalid CORS request"）：
     *   ① 3000 被占用时开发服务顺延到 3001；
     *   ② 用户习惯用 `http://127.0.0.1:3000` 或本机 IP 打开（与 `localhost` 不是同一来源）。
     *   → 表现为「保存按钮权限等写操作静默失败、界面以为已保存」。改为按来源模式放行本机任意端口。
     */
    @Value("${erp.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*,http://192.168.*.*:*}")
    private String corsAllowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = java.util.Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (origins.length == 0) {
            origins = new String[]{"http://localhost:*", "http://127.0.0.1:*"};
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Erp-Warning")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** 映射到本地文件目录, 使上传的文件可通过URL访问
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 【2026-09-20 变更】租户数据源切换已从 Interceptor 下沉到 Servlet Filter（TenantContextFilter）。
        //
        // 原因：原先在此注册 tenantInterceptor.order(0)、在 InterceptorConfig 注册 authInterceptor.order(1)，
        // 期望"先切库、后鉴权"；但线上实测（8cloud）鉴权先执行：
        //   AuthInterceptor  20:01:58.595  当前租户库=null      → 角色查询落主库，取到别人的角色 SALES_REP
        //   TenantInterceptor 20:01:58.645  切换后库=erp_tenant_sc_wudong
        // → 租户管理员被判为普通用户（建/删用户 403），且切库前的一切查询都跑在主库上，隔离失效。
        // Servlet 规范保证 Filter 早于 DispatcherServlet（也就早于所有 Interceptor），
        // 因此改由 TenantContextFilter 建立租户上下文，不再依赖 order 的相对关系。
        //
        // registry.addInterceptor(tenantInterceptor)
        //         .addPathPatterns("/api/**")
        //         .excludePathPatterns(
        //                 "/api/auth/login",
        //                 "/api/auth/wx-login",
        //                 "/api/ai/public/**",
        //                 "/api/public/lead",
        //                 "/api/system/build-info"
        //         )
        //         .order(0);

        // License 拦截器：拦截所有 /api/** 请求，校验失败返回 403
        // 排除项由 LicenseInterceptor 内部判断（登录/状态查询允许通过）
        registry.addInterceptor(licenseInterceptor)
                .addPathPatterns("/api/**");
    }
}
