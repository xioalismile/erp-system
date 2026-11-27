package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.erp.config.BusinessException;
import com.erp.device.entity.DeviceChannel;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.mapper.DeviceChannelMapper;
import com.erp.device.mapper.DeviceInfoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** 设备接入通道配置（阶段 2 只做配置与状态展示，实际连接在阶段 3） */
@Service
@RequiredArgsConstructor
public class DeviceChannelService extends ServiceImpl<DeviceChannelMapper, DeviceChannel> {

    private static final Set<String> TYPES = Set.of("HTTP", "MQTT", "TCP", "WEBSOCKET", "POLL");

    private final DeviceInfoMapper deviceInfoMapper;
    private final ObjectMapper objectMapper;

    public List<DeviceChannel> listByDevice(Long deviceId) {
        List<DeviceChannel> list = list(new QueryWrapper<DeviceChannel>()
                .eq("device_id", deviceId).orderByAsc("id"));
        DeviceInfo device = deviceInfoMapper.selectById(deviceId);
        for (DeviceChannel c : list) {
            c.setDeviceCode(device == null ? null : device.getDeviceCode());
            c.setDeviceName(device == null ? null : device.getDeviceName());
        }
        return list;
    }

    /** 同一设备同一通道类型只保留一条：存在则更新，不存在则新增 */
    public DeviceChannel saveOrUpdateChannel(DeviceChannel c) {
        validate(c);
        DeviceChannel old = getOne(new QueryWrapper<DeviceChannel>()
                .eq("device_id", c.getDeviceId()).eq("channel_type", c.getChannelType()).last("limit 1"));
        if (old == null) {
            save(c);
            return getById(c.getId());
        }
        old.setChannelName(c.getChannelName());
        old.setConfigJson(c.getConfigJson());
        old.setHeartbeatInterval(c.getHeartbeatInterval());
        old.setOfflineThreshold(c.getOfflineThreshold());
        old.setEnabled(c.getEnabled());
        updateById(old);
        return getById(old.getId());
    }

    public DeviceChannel updateChannel(Long id, DeviceChannel c) {
        DeviceChannel old = getById(id);
        if (old == null) throw new BusinessException("接入通道不存在");
        c.setId(id);
        c.setDeviceId(old.getDeviceId());
        c.setChannelType(old.getChannelType());
        validate(c);
        old.setChannelName(c.getChannelName());
        old.setConfigJson(c.getConfigJson());
        old.setHeartbeatInterval(c.getHeartbeatInterval());
        old.setOfflineThreshold(c.getOfflineThreshold());
        old.setEnabled(c.getEnabled());
        updateById(old);
        return getById(id);
    }

    public void toggle(Long id, Integer enabled) {
        DeviceChannel old = getById(id);
        if (old == null) throw new BusinessException("接入通道不存在");
        old.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        updateById(old);
    }

    public void deleteChannel(Long id) {
        DeviceChannel old = getById(id);
        if (old == null) return;
        if (old.getEnabled() != null && old.getEnabled() == 1) {
            throw new BusinessException("通道启用中，请先关闭再删除");
        }
        removeById(id);
    }

    private void validate(DeviceChannel c) {
        if (c == null) throw new BusinessException("通道信息不能为空");
        if (c.getDeviceId() == null || deviceInfoMapper.selectById(c.getDeviceId()) == null) {
            throw new BusinessException("设备不存在，请重新选择");
        }
        if (c.getChannelType() == null || !TYPES.contains(c.getChannelType())) {
            throw new BusinessException("通道类型只能是：HTTP / MQTT / TCP / WebSocket / 云端轮询");
        }
        if (c.getHeartbeatInterval() == null || c.getHeartbeatInterval() < 5 || c.getHeartbeatInterval() > 3600) {
            throw new BusinessException("心跳间隔需在 5~3600 秒之间");
        }
        if (c.getOfflineThreshold() == null || c.getOfflineThreshold() < 1 || c.getOfflineThreshold() > 100) {
            throw new BusinessException("离线判定周期需在 1~100 之间");
        }
        if (c.getEnabled() == null) c.setEnabled(0);
        if (c.getConfigJson() != null && !c.getConfigJson().isBlank()) {
            try {
                objectMapper.readTree(c.getConfigJson());
            } catch (Exception e) {
                throw new BusinessException("连接参数不是合法 JSON，请检查格式");
            }
        }
        if (c.getChannelName() == null || c.getChannelName().isBlank()) {
            c.setChannelName(c.getChannelType() + " 通道");
        }
    }
}
