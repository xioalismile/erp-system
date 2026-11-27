package com.erp.device.controller;

import com.erp.common.Result;
import com.erp.device.entity.DeviceChannel;
import com.erp.device.service.DeviceChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 设备接入通道 */
@RestController
@RequestMapping("/api/device/channel")
@RequiredArgsConstructor
public class DeviceChannelController {

    private final DeviceChannelService channelService;

    @GetMapping("/{deviceId}")
    public Result<List<DeviceChannel>> list(@PathVariable Long deviceId) {
        return Result.success(channelService.listByDevice(deviceId));
    }

    @PostMapping
    public Result<DeviceChannel> save(@RequestBody DeviceChannel channel) {
        return Result.success(channelService.saveOrUpdateChannel(channel));
    }

    @PutMapping("/{id}")
    public Result<DeviceChannel> update(@PathVariable Long id, @RequestBody DeviceChannel channel) {
        return Result.success(channelService.updateChannel(id, channel));
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam Integer enabled) {
        channelService.toggle(id, enabled);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        channelService.deleteChannel(id);
        return Result.success();
    }
}
