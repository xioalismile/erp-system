package com.erp.system.controller;

import com.erp.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 构建信息接口（部署闸门专用）
 * <p>
 * 暴露 GET /api/system/build-info 返回构建版本号 + 构建时间。
 * update-agent.ps1 健康检查会调用此接口，与前端 index.html 的版本号比对，
 * 不一致则判定为前后端版本错配 → 自动回滚。
 * <p>
 * 安全规范：
 * - 已加入 3 处拦截器白名单（InterceptorConfig + AuthInterceptor + LicenseInterceptor），无需登录可访问。
 * - 只返回版本号字符串 + 构建时间，不暴露任何敏感信息（无路径/用户名/密码）。
 * - build-info.properties 由 Publish.ps1 构建前生成，找不到时降级为 "unknown"（开发态兼容）。
 * - 版本号格式：build-{yyyyMMddHHmmss}，仅用于版本一致性校验。
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@PropertySource(value = "classpath:build-info.properties", ignoreResourceNotFound = true)
@RequiredArgsConstructor
public class BuildInfoController {

    @Value("${build.version:unknown}")
    private String buildVersion;

    @Value("${build.time:unknown}")
    private String buildTime;

    @GetMapping("/build-info")
    public Result<Map<String, String>> buildInfo() {
        Map<String, String> data = new HashMap<>();
        data.put("version", buildVersion);
        data.put("time", buildTime);
        return Result.success(data);
    }
}
