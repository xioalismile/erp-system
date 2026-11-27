package com.erp.device.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.common.util.SecurityUtils;
import com.erp.device.entity.DeviceTask;
import com.erp.device.entity.DeviceUnmatchedData;
import com.erp.device.service.DeviceUnmatchedService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 未关联数据池 */
@RestController
@RequestMapping("/api/device/unmatched")
@RequiredArgsConstructor
public class DeviceUnmatchedController {

    private final DeviceUnmatchedService unmatchedService;

    @GetMapping("/page")
    public Result<PageResult<DeviceUnmatchedData>> page(@RequestParam(defaultValue = "1") int current,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) Long deviceId,
                                                        @RequestParam(required = false) String status) {
        return Result.success(unmatchedService.pageQuery(current, size, deviceId, status));
    }

    /** 人工认领到指定工单工序 */
    @PostMapping("/{id}/claim")
    public Result<DeviceTask> claim(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                    HttpServletRequest request) {
        Long operationRowId = body.get("operationRowId") == null ? null : Long.valueOf(String.valueOf(body.get("operationRowId")));
        String operator = SecurityUtils.getCurrentUsername(request);
        return Result.success(unmatchedService.claim(id, operationRowId, operator));
    }
}
