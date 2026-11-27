package com.erp.device.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.device.entity.DeviceTask;
import com.erp.device.service.DeviceTaskService;
import com.erp.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 设备任务：设备与工单工序的关联 */
@RestController
@RequestMapping("/api/device/task")
@RequiredArgsConstructor
public class DeviceTaskController {

    private final DeviceTaskService taskService;

    @GetMapping("/page")
    public Result<PageResult<DeviceTask>> page(@RequestParam(defaultValue = "1") int current,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) Long deviceId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String orderNo,
                                               @RequestParam(required = false) String sourceType) {
        return Result.success(taskService.pageQuery(current, size, deviceId, status, orderNo, sourceType));
    }

    @GetMapping("/{id}")
    public Result<DeviceTask> detail(@PathVariable Long id) {
        return Result.success(taskService.getDetail(id));
    }

    /** 可关联的工单工序下拉 */
    @GetMapping("/operation-options")
    public Result<List<Map<String, Object>>> operationOptions(@RequestParam(required = false) String keyword) {
        return Result.success(taskService.operationOptions(keyword));
    }

    /** 人工建立关联 */
    @PostMapping
    public Result<DeviceTask> create(@RequestBody Map<String, Object> body) {
        Long deviceId = body.get("deviceId") == null ? null : Long.valueOf(String.valueOf(body.get("deviceId")));
        Long operationRowId = body.get("operationRowId") == null ? null : Long.valueOf(String.valueOf(body.get("operationRowId")));
        return Result.success(taskService.createManual(deviceId, operationRowId));
    }

    @PostMapping("/{id}/dispatch")
    public Result<Void> dispatch(@PathVariable Long id) {
        taskService.dispatch(id);
        return Result.success();
    }

    @PostMapping("/{id}/rebind")
    public Result<Void> rebind(@PathVariable Long id, @RequestParam Long operationRowId) {
        taskService.rebind(id, operationRowId);
        return Result.success();
    }

    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id, @RequestParam(required = false) String reason) {
        taskService.release(id, reason);
        return Result.success();
    }
}
