package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.device.entity.DeviceAccessCredential;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.entity.DeviceRawMessage;
import com.erp.device.mapper.DeviceRawMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 接入调试台：报文流、点位实时值、模拟上报 */
@Service
@RequiredArgsConstructor
public class DeviceDebugService {

    private final DeviceInfoService deviceInfoService;
    private final DeviceCredentialService credentialService;
    private final DeviceChannelService channelService;
    private final DevicePointService pointService;
    private final DeviceIngestService ingestService;
    private final DeviceRawMessageMapper rawMessageMapper;

    public Map<String, Object> overview(Long deviceId) {
        DeviceInfo device = deviceInfoService.getDetail(deviceId);
        DeviceAccessCredential credential = credentialService.current(deviceId);
        List<DeviceRawMessage> recent = messages(deviceId, 1);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("device", device);
        data.put("credentialStatus", credential == null ? "未生成" : credential.getStatus());
        data.put("credentialMasked", credential == null ? null : credential.getSecretMasked());
        data.put("credentialNo", credential == null ? null : credential.getCredentialNo());
        data.put("channels", channelService.listByDevice(deviceId));
        data.put("pointCount", pointService.listByDevice(deviceId).size());
        data.put("lastMessageTime", recent.isEmpty() ? null : recent.get(0).getReceivedAt());
        return data;
    }

    public List<DeviceRawMessage> messages(Long deviceId, Integer limit) {
        int size = limit == null || limit < 1 ? 50 : Math.min(limit, 200);
        List<DeviceRawMessage> list = rawMessageMapper.selectList(new QueryWrapper<DeviceRawMessage>()
                .eq("device_id", deviceId).orderByDesc("id").last("limit " + size));
        // 倒序查询后按时间正序展示更符合“报文流”直觉
        List<DeviceRawMessage> ordered = new ArrayList<>(list);
        java.util.Collections.reverse(ordered);
        return ordered;
    }

    public List<Map<String, Object>> values(Long deviceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        pointService.listByDevice(deviceId).forEach(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pointId", p.getId());
            row.put("pointKey", p.getPointKey());
            row.put("pointName", p.getPointName());
            row.put("purpose", p.getPurpose());
            row.put("unit", p.getUnit());
            row.put("dataType", p.getDataType());
            row.put("lastValue", p.getLastValue());
            row.put("lastValueTime", p.getLastValueTime());
            row.put("enabled", p.getEnabled());
            rows.add(row);
        });
        return rows;
    }

    public DeviceIngestService.IngestResult simulate(DeviceInfo device, String body) {
        return ingestService.simulate(device, body);
    }
}
