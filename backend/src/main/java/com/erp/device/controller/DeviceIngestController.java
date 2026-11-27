package com.erp.device.controller;

import com.erp.common.Result;
import com.erp.device.entity.DeviceRawMessage;
import com.erp.device.service.DeviceCommandService;
import com.erp.device.service.DeviceCredentialService;
import com.erp.device.service.DeviceIngestService;
import com.erp.device.service.DeviceTenantScope;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备数据上报与指令拉取入口（公开，不走登录 Token，由接入凭证 + 签名鉴权）。
 * 已在 InterceptorConfig 中放行 /api/device/ingest/**。
 */
@RestController
@RequestMapping("/api/device/ingest")
@RequiredArgsConstructor
public class DeviceIngestController {

    private final DeviceIngestService ingestService;
    private final DeviceCredentialService credentialService;
    private final DeviceTenantScope tenantScope;
    private final DeviceCommandService commandService;

    @PostMapping("/http")
    public Result<Map<String, Object>> http(
            @RequestHeader(value = "X-Device-Credential", required = false) String credentialNo,
            @RequestHeader(value = "X-Device-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Device-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Device-Sign", required = false) String sign,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {

        DeviceIngestService.IngestResult r =
                ingestService.ingestPublic(credentialNo, timestamp, nonce, sign, body, ip(request));
        if (!r.ok()) {
            return Result.error(401, r.message());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageKey", r.messageKey());
        data.put("duplicate", r.duplicate());
        data.put("simulated", r.simulated());
        data.put("parsed", r.parsed());
        data.put("unknownKeys", r.unknownKeys());
        data.put("ignoredKeys", r.ignoredKeys());
        return Result.success(data);
    }

    /** 设备拉取待执行指令（HTTP/TCP 设备的下发通道） */
    @PostMapping("/pull")
    public Result<List<Map<String, Object>>> pull(
            @RequestHeader(value = "X-Device-Credential", required = false) String credentialNo,
            @RequestHeader(value = "X-Device-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Device-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Device-Sign", required = false) String sign,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {

        Long tenantId = tenantScope.parseTenantId(credentialNo);
        if (tenantId == null) return Result.error(401, "凭证格式不正确");
        String bodyText = body == null ? "" : body;
        return tenantScope.runInTenant(tenantId, () -> {
            DeviceCredentialService.AuthResult auth =
                    credentialService.authenticate(credentialNo, timestamp, nonce, sign, bodyText, ip(request));
            if (!auth.ok()) return Result.error(401, auth.message());
            List<DeviceRawMessage> commands = commandService.pullCommands(auth.device().getId());
            List<Map<String, Object>> data = new ArrayList<>();
            for (DeviceRawMessage m : commands) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", m.getId());
                row.put("messageKey", m.getMessageKey());
                row.put("payload", m.getRawContent());
                row.put("receivedAt", m.getReceivedAt());
                data.add(row);
            }
            return Result.success(data);
        });
    }

    private String ip(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip;
    }
}
