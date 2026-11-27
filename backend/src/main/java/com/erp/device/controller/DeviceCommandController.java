package com.erp.device.controller;

import com.erp.common.Result;
import com.erp.device.entity.DevicePoint;
import com.erp.device.service.DeviceCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 双向下发：任务下发 / 参数下发 / 可写点位 */
@RestController
@RequestMapping("/api/device/command")
@RequiredArgsConstructor
public class DeviceCommandController {

    private final DeviceCommandService commandService;

    @PostMapping("/task/{taskId}/dispatch")
    public Result<Map<String, Object>> dispatchTask(@PathVariable Long taskId) {
        return Result.success(commandService.dispatchTask(taskId));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/device/{deviceId}/params")
    public Result<Map<String, Object>> writeParams(@PathVariable Long deviceId,
                                                   @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> params = (List<Map<String, Object>>) body.get("params");
        return Result.success(commandService.writeParams(deviceId, params));
    }

    @GetMapping("/device/{deviceId}/writable-points")
    public Result<List<DevicePoint>> writablePoints(@PathVariable Long deviceId) {
        return Result.success(commandService.writablePoints(deviceId));
    }
}
