package com.erp.system.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.system.entity.SysOperateLog;
import com.erp.system.service.SysOperateLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志 Controller
 * 路由: /api/system/log
 */
@RestController
@RequestMapping("/api/system/log")
@RequiredArgsConstructor
public class SysOperateLogController {

    private final SysOperateLogService sysOperateLogService;

    @GetMapping("/page")
    public Result<PageResult<SysOperateLog>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String operateType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(sysOperateLogService.pageQuery(current, size, keyword, operateType, startDate, endDate));
    }
}
