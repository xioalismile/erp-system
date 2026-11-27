package com.erp.device.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.device.entity.DeviceReportPending;
import com.erp.device.service.DeviceReportPendingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 待确认报工单 */
@RestController
@RequestMapping("/api/device/report-pending")
@RequiredArgsConstructor
public class DeviceReportPendingController {

    private final DeviceReportPendingService pendingService;

    @GetMapping("/page")
    public Result<PageResult<DeviceReportPending>> page(@RequestParam(defaultValue = "1") int current,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) Long deviceId,
                                                        @RequestParam(required = false) String status) {
        return Result.success(pendingService.pageQuery(current, size, deviceId, status));
    }

    @PostMapping("/{id}/confirm")
    public Result<DeviceReportPending> confirm(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(pendingService.confirm(id, SecurityUtils.getCurrentUsername(request)));
    }

    @PostMapping("/{id}/reject")
    public Result<DeviceReportPending> reject(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                              HttpServletRequest request) {
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return Result.success(pendingService.reject(id, reason, SecurityUtils.getCurrentUsername(request)));
    }
}
