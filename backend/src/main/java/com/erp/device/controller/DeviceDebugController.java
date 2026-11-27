package com.erp.device.controller;

import com.erp.common.Result;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.entity.DeviceRawMessage;
import com.erp.device.service.DeviceDebugService;
import com.erp.device.service.DeviceInfoService;
import com.erp.device.service.DeviceIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 接入调试台 */
@RestController
@RequestMapping("/api/device/debug")
@RequiredArgsConstructor
public class DeviceDebugController {

    private final DeviceDebugService debugService;
    private final DeviceInfoService deviceInfoService;

    @GetMapping("/{deviceId}/overview")
    public Result<Map<String, Object>> overview(@PathVariable Long deviceId) {
        return Result.success(debugService.overview(deviceId));
    }

    @GetMapping("/{deviceId}/messages")
    public Result<List<DeviceRawMessage>> messages(@PathVariable Long deviceId,
                                                   @RequestParam(required = false) Integer limit) {
        return Result.success(debugService.messages(deviceId, limit));
    }

    @GetMapping("/{deviceId}/values")
    public Result<List<Map<String, Object>>> values(@PathVariable Long deviceId) {
        return Result.success(debugService.values(deviceId));
    }

    /** 模拟上报：走完整解析链路，但写入的报文标记为模拟，不写实时值、不参与生产联动 */
    @PostMapping("/{deviceId}/simulate")
    public Result<Map<String, Object>> simulate(@PathVariable Long deviceId,
                                                @RequestBody String body) {
        DeviceInfo device = deviceInfoService.getDetail(deviceId);
        DeviceIngestService.IngestResult r = debugService.simulate(device, body);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", r.ok());
        data.put("message", r.message());
        data.put("messageKey", r.messageKey());
        data.put("parsed", r.parsed());
        data.put("unknownKeys", r.unknownKeys());
        data.put("ignoredKeys", r.ignoredKeys());
        if (!r.ok()) return Result.error(400, r.message());
        return Result.success(data);
    }
}
