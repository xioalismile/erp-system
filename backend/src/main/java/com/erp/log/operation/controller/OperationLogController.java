package com.erp.log.operation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.log.operation.entity.OperationLog;
import com.erp.log.operation.service.OperationLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operation-log")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public Result<PageResult<OperationLog>> pageQuery(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        Page<OperationLog> page = operationLogService.pageQuery(current, size, keyword, module,
                operationType, userId, startTime, endTime, sortBy, sortOrder);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), current, size));
    }

    @GetMapping("/{id}")
    public Result<OperationLog> getDetail(@PathVariable Long id) {
        OperationLog log = operationLogService.getDetail(id);
        if (log == null) {
            return Result.error("日志不存在");
        }
        return Result.success(log);
    }

    @GetMapping("/stats/module")
    public Result<List<Map<String, Object>>> getModuleStats(@RequestParam(defaultValue = "7") int days) {
        return Result.success(operationLogService.getModuleStats(days));
    }

    @GetMapping("/stats/operation-type")
    public Result<List<Map<String, Object>>> getOperationTypeStats(@RequestParam(defaultValue = "7") int days) {
        return Result.success(operationLogService.getOperationTypeStats(days));
    }
}
