package com.erp.license.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * License 拦截器
 * <p>
 * 拦截所有 /api/** 请求：
 * - License 失效时返回 403 Forbidden
 * - 功能模块限制：检查 features 字段，未授权模块返回 403
 * - 用户数限制：检查 maxUsers，超过限制返回 403
 * - 排除登录接口 /api/auth/login（允许用户登录查看 License 失效提示）
 * - 排除 License 状态查询接口 /api/license/status
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LicenseInterceptor implements HandlerInterceptor {

    private final LicenseValidator licenseValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        if (uri.startsWith("/api/auth/login")
                || uri.startsWith("/api/license/status")
                || uri.startsWith("/api/public/lead")
                || uri.startsWith("/api/public/work-order-share")
                || uri.startsWith("/api/system/i18n/package")
                || uri.equals("/api/system/build-info")) {
            // 官网线索收集接口 + 构建信息探针：即便 License 失效/功能限制也允许访问
            // build-info 只返回版本号字符串，无敏感数据，部署闸门探针必需
            return true;
        }

        // 开发模式（erp.license.enabled=false）整体放行，与 LicenseValidator.init 行为一致。
        // 防御纵深：即使未来出现新的校验调用方漏查开关，这里也不会把开发环境打成 403。
        if (!licenseValidator.isLicenseEnabled()) {
            return true;
        }

        if (!licenseValidator.isLicenseValid()) {
            return handleInvalidLicense(response);
        }

        String feature = extractFeatureFromUri(uri);
        if (feature != null && !licenseValidator.isFeatureAllowed(feature)) {
            return handleFeatureNotAllowed(response, feature);
        }

        return true;
    }

    private String extractFeatureFromUri(String uri) {
        if (uri.contains("/api/sale/")) return "sale";
        if (uri.contains("/api/purchase/")) return "purchase";
        if (uri.contains("/api/inventory/") || uri.contains("/api/stock/")) return "inventory";
        if (uri.contains("/api/production/")) return "production";
        if (uri.contains("/api/finance/") || uri.contains("/api/voucher/")) return "finance";
        if (uri.contains("/api/ai/")) return "ai";
        if (uri.contains("/api/approval/")) return "approval";
        if (uri.contains("/api/subcontract/")) return "subcontract";
        return null;
    }

    private boolean handleInvalidLicense(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", 403);
        body.put("message", "License 已失效：" + licenseValidator.getFailureReason());
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }

    private boolean handleFeatureNotAllowed(HttpServletResponse response, String feature) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", 403);
        body.put("message", "功能模块未授权：" + feature + " 模块不在 License 授权范围内");
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
