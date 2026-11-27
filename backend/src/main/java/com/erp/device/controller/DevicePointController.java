package com.erp.device.controller;

import com.erp.common.Result;
import com.erp.device.entity.DevicePoint;
import com.erp.device.service.DevicePointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 设备数据点 */
@RestController
@RequestMapping("/api/device/point")
@RequiredArgsConstructor
public class DevicePointController {

    private final DevicePointService pointService;

    /** 列表（含最近值） */
    @GetMapping("/{deviceId}")
    public Result<List<DevicePoint>> list(@PathVariable Long deviceId) {
        return Result.success(pointService.listByDevice(deviceId));
    }

    @PostMapping
    public Result<DevicePoint> create(@RequestBody DevicePoint point) {
        return Result.success(pointService.create(point));
    }

    @PutMapping("/{id}")
    public Result<DevicePoint> update(@PathVariable Long id, @RequestBody DevicePoint point) {
        return Result.success(pointService.update(id, point));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        pointService.delete(id);
        return Result.success();
    }
}
