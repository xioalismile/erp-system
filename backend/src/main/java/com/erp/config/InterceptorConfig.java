package com.erp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class InterceptorConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ButtonPermissionInterceptor buttonPermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/wx-login",
                        "/api/ai/public/**",
                        "/api/ai/channel/**",
                        "/api/system/build-info",
                        "/api/system/i18n/package/**",
                        "/api/kai-standalone/**",
                        // 设备上报入口：设备不带登录 Token，由接入凭证 + 时间戳 + nonce + 签名鉴权
                        "/api/device/ingest/**",
                        "/uploads/**"
                )
                .order(1); // 在租户拦截器之后执行
        // 按钮权限接口级拦截（2026-10-12）：必须在 authInterceptor 之后（order 2），
        // 它依赖鉴权层写入的 currentPermissions/currentRoles 属性。
        // 自身只认"单据动作类"注册表，未登记路径一律放行，无需 exclude。
        registry.addInterceptor(buttonPermissionInterceptor)
                .addPathPatterns("/api/**")
                .order(2);
        // 小楷独立版拦截器由 com.erp.kai.config.KaiWebConfig 自注册（2026-10-12 客户包收敛）：
        // 客户包（mvn -P customer）编译期排除 com/erp/kai/** 后该配置类不存在，拦截器自动消失；
        // 主工程不得 import com.erp.kai.** 任何类，否则排除编译会失败（该失败本身即是防护）。
    }
}
