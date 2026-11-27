package com.erp.production.controller;

import com.erp.common.Result;
import com.erp.production.service.WorkOrderShareService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 生产工单进度分享（免登录公开读取，2026-11-25）。
 *
 * <p>微信用户点开分享链接后由此接口取数：只凭令牌访问，校验过期与停止状态，
 * 只返回生产进度字段（不含客户、价格、成本、附件）。接口在鉴权/License 中按
 * {@code /api/public/**} 放行，租户由令牌携带并由服务端切换数据源。</p>
 */
@RestController
@RequestMapping("/api/public/work-order-share")
@RequiredArgsConstructor
public class PublicWorkOrderShareController {

    private final WorkOrderShareService shareService;

    @GetMapping
    public Result<Map<String, Object>> view(@RequestParam String token, HttpServletRequest request) {
        return Result.success(shareService.publicView(token, request));
    }
}
