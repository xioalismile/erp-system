package com.erp.production.controller;

import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.config.service.SysConfigService;
import com.erp.production.entity.WorkOrderShare;
import com.erp.production.service.WorkOrderShareService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产工单进度分享（登录态）：生成 / 查询 / 停止（2026-11-25）。
 *
 * <p>手机端「工序进度」使用。公开读取走 {@link PublicWorkOrderShareController}，
 * 链接域名优先取系统参数 {@code qrcode.base_url}，未配置按当前访问域名自动生成。</p>
 */
@RestController
@RequestMapping("/api/production/work-order-share")
@RequiredArgsConstructor
public class WorkOrderShareController {

    private static final String CFG_BASE_URL = "qrcode.base_url";
    /** 手机网页公开进度页（与 pages.json 注册路径一致） */
    private static final String SHARE_PAGE = "/mobile/#/pages/share/work-order-progress?token=";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WorkOrderShareService shareService;
    private final SysConfigService sysConfigService;

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> body,
                                              HttpServletRequest request) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Long orderId = longVal(b.get("orderId"));
        if (orderId == null) return Result.error("请选择工单");
        WorkOrderShare share = shareService.create(
                orderId,
                strVal(b.get("title")),
                strVal(b.get("remark")),
                intVal(b.get("days")),
                boolVal(b.get("showAssignee")),
                boolVal(b.get("showPlanDate")),
                boolVal(b.get("showMaterialCode")),
                SecurityUtils.getCurrentUserId(request),
                firstNonBlank(SecurityUtils.getCurrentRealName(request), SecurityUtils.getCurrentUsername(request)));
        return Result.success(toView(share, request));
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam Long orderId, HttpServletRequest request) {
        List<WorkOrderShare> rows = shareService.listByOrder(orderId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (WorkOrderShare s : rows) out.add(toView(s, request));
        return Result.success(out);
    }

    @PostMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        shareService.revoke(id);
        return Result.success();
    }

    // -------------------- 输出 --------------------

    private Map<String, Object> toView(WorkOrderShare s, HttpServletRequest request) {
        boolean expired = s.getExpireAt() != null && s.getExpireAt().isBefore(LocalDateTime.now());
        boolean active = s.getStatus() != null && s.getStatus() == 1 && !expired;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("orderId", s.getOrderId());
        m.put("orderNo", s.getOrderNo());
        m.put("token", s.getToken());
        m.put("title", s.getTitle());
        m.put("remark", s.getRemark());
        m.put("expireAt", s.getExpireAt() == null ? null : s.getExpireAt().format(DTF));
        m.put("status", active ? "有效" : (expired ? "已过期" : "已停止"));
        m.put("active", active);
        m.put("showAssignee", on(s.getShowAssignee()));
        m.put("showPlanDate", on(s.getShowPlanDate()));
        m.put("showMaterialCode", on(s.getShowMaterialCode()));
        m.put("viewCount", s.getViewCount() == null ? 0 : s.getViewCount());
        m.put("createdByName", s.getCreatedByName());
        m.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().format(DTF));
        m.put("url", baseUrl(request) + SHARE_PAGE + s.getToken());
        return m;
    }

    /** 域名：系统参数优先，未配置按当前请求自动生成（支持反向代理头） */
    private String baseUrl(HttpServletRequest request) {
        String configured = sysConfigService.getValue(CFG_BASE_URL, "");
        if (configured != null && !configured.isBlank()) {
            return trimSlash(configured.trim());
        }
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) scheme = request.getScheme();
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            int port = request.getServerPort();
            host = request.getServerName() + (port > 0 && port != 80 && port != 443 ? ":" + port : "");
        }
        return scheme + "://" + host;
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static boolean on(Integer v) {
        return v == null || v == 1;
    }

    private static String strVal(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intVal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean boolVal(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "是".equals(s);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
