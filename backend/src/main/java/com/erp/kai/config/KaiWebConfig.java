package com.erp.kai.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 小楷独立版 Web 配置（2026-10-12 从主工程 InterceptorConfig 收回）。
 *
 * <p>背景：客户包（mvn -P customer）编译期排除 com/erp/kai/**，主工程 InterceptorConfig
 * 一旦 import 本包任何类都会隐式编译导致排除失败。故本拦截器注册收归 kai 包自持：
 * 全量包 = 拦截器生效；客户包 = 本类随整包消失，/api/kai-standalone/** 自然 404。</p>
 */
@Configuration
@RequiredArgsConstructor
public class KaiWebConfig implements WebMvcConfigurer {

    private final KaiAuthInterceptor kaiAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 小楷独立版：独立用户体系、独立 token，专管自己的路径（AuthInterceptor 已放行该前缀）
        registry.addInterceptor(kaiAuthInterceptor)
                .addPathPatterns("/api/kai-standalone/**")
                .order(2);
    }
}
