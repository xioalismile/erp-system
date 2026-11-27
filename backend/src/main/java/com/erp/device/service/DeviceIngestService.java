package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.device.entity.DeviceChannel;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.entity.DevicePoint;
import com.erp.device.entity.DeviceProtocolTemplate;
import com.erp.device.entity.DeviceRawMessage;
import com.erp.device.entity.DeviceRealtimeValue;
import com.erp.device.entity.DeviceTask;
import com.erp.device.mapper.DeviceChannelMapper;
import com.erp.device.mapper.DevicePointMapper;
import com.erp.device.mapper.DeviceProtocolTemplateMapper;
import com.erp.device.mapper.DeviceRawMessageMapper;
import com.erp.device.mapper.DeviceRealtimeValueMapper;
import com.erp.device.mapper.DeviceTaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 设备数据接入：验签 → 解析 → 写原始报文 → 写点位实时值。
 * 支持三种来源：
 * 1) HTTP 直传（JSON 报文，公开接口，签名鉴权）；
 * 2) MQTT / TCP 信封报文（envelope 内带凭证与签名，payload 按协议模板解析）；
 * 3) 云端轮询（Modbus / OPC UA，服务端主动读取，无需签名）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceIngestService {

    private static final Set<String> RESERVED = Set.of("messageKey", "deviceTime", "simulated", "points");
    private static final DateTimeFormatter[] TIME_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    private final DeviceCredentialService credentialService;
    private final DevicePointService pointService;
    private final DeviceTenantScope tenantScope;
    private final DeviceRawMessageMapper rawMessageMapper;
    private final DeviceRealtimeValueMapper realtimeValueMapper;
    private final DeviceChannelMapper channelMapper;
    private final DevicePointMapper pointMapper;
    private final DeviceProtocolTemplateMapper templateMapper;
    private final DeviceFrameParser frameParser;
    private final DeviceAssociationService associationService;
    private final DeviceReportPendingService pendingService;
    private final DeviceTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    /** 公开 HTTP 上报（凭证 + 时间戳 + nonce + 签名） */
    public IngestResult ingestPublic(String credentialNo, String timestamp, String nonce, String sign,
                                     String rawBody, String ip) {
        Long tenantId = tenantScope.parseTenantId(credentialNo);
        if (tenantId == null) {
            return IngestResult.fail("凭证格式不正确，凭证号应为 DEVC-{租户}-{随机串}");
        }
        return tenantScope.runInTenant(tenantId, () -> {
            DeviceCredentialService.AuthResult auth =
                    credentialService.authenticate(credentialNo, timestamp, nonce, sign, rawBody, ip);
            if (!auth.ok()) return IngestResult.fail(auth.message());
            return processJson(auth.device(), rawBody, false, "HTTP");
        });
    }

    /**
     * MQTT / TCP 信封报文：
     * {"credentialNo":"...","timestamp":"...","nonce":"...","sign":"...","encoding":"JSON|BASE64","payload":"..."}
     * 签名对象 = payload 字段原文（BASE64 时对 base64 字符串签名）。
     */
    public IngestResult ingestEnvelope(String envelopeJson, String channelType, Long templateId, String ip) {
        JsonNode env;
        try {
            env = objectMapper.readTree(envelopeJson);
        } catch (Exception e) {
            return IngestResult.fail("信封报文不是合法 JSON");
        }
        String credentialNo = text(env, "credentialNo");
        String timestamp = text(env, "timestamp");
        String nonce = text(env, "nonce");
        String sign = text(env, "sign");
        String payloadRaw = text(env, "payload");
        String encoding = text(env, "encoding");
        if (payloadRaw == null) return IngestResult.fail("信封缺少 payload");
        final byte[] payloadBytes;
        if ("BASE64".equalsIgnoreCase(encoding)) {
            try {
                payloadBytes = Base64.getDecoder().decode(payloadRaw);
            } catch (Exception e) {
                return IngestResult.fail("payload 不是合法 Base64");
            }
        } else {
            payloadBytes = payloadRaw.getBytes(StandardCharsets.UTF_8);
        }
        Long tenantId = tenantScope.parseTenantId(credentialNo);
        if (tenantId == null) return IngestResult.fail("凭证格式不正确");

        // 签名对象 = payload 字段原文（BASE64 时对 base64 字符串签名）
        final String signText = payloadRaw;
        return tenantScope.runInTenant(tenantId, () -> {
            DeviceCredentialService.AuthResult auth =
                    credentialService.authenticate(credentialNo, timestamp, nonce, sign, signText, ip);
            if (!auth.ok()) return IngestResult.fail(auth.message());
            Map<String, Object> values;
            try {
                Long tid = templateId;
                if (tid == null) {
                    // MQTT/TCP 设备不知道模板 ID：取该设备点位绑定的第一个模板
                    DevicePoint tplPoint = pointMapper.selectOne(new QueryWrapper<DevicePoint>()
                            .eq("device_id", auth.device().getId())
                            .isNotNull("template_id").last("limit 1"));
                    if (tplPoint != null) tid = tplPoint.getTemplateId();
                }
                DeviceProtocolTemplate template = tid == null ? null : templateMapper.selectById(tid);
                values = frameParser.parse(payloadBytes, template);
            } catch (Exception e) {
                saveRaw(auth.device().getId(), channelType, null, envelopeJson, null, "失败", e.getMessage(), 0);
                return IngestResult.fail(e.getMessage());
            }
            String messageKey = text(env, "messageKey");
            if (messageKey == null || messageKey.isBlank()) {
                messageKey = "ENV-" + (nonce == null ? UUID.randomUUID().toString().replace("-", "") : nonce);
            }
            return processValues(auth.device(), values, envelopeJson, messageKey,
                    parseTime(text(env, "deviceTime")), false, channelType);
        });
    }

    /** 云端轮询写入：服务端已读到点位值，无需签名 */
    public IngestResult ingestPolled(DeviceInfo device, Map<String, Object> values, String rawJson, String channelType) {
        String messageKey = "POLL-" + device.getId() + "-" + (System.currentTimeMillis() / 1000);
        return processValues(device, values, rawJson, messageKey, LocalDateTime.now(), false, channelType);
    }

    /** 调试台模拟上报：已登录用户，不验签，不写实时值，不参与生产联动 */
    public IngestResult simulate(DeviceInfo device, String rawBody) {
        return processJson(device, rawBody, true, "HTTP");
    }

    // ==================== 处理链 ====================

    private IngestResult processJson(DeviceInfo device, String rawBody, boolean simulated, String channelType) {
        if (rawBody == null || rawBody.isBlank()) {
            return IngestResult.fail("报文内容为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            saveRaw(device.getId(), channelType, null, rawBody, null, "失败",
                    "报文不是合法 JSON：" + e.getMessage(), simulated ? 1 : 0);
            return IngestResult.fail("报文不是合法 JSON");
        }
        String messageKey = text(root, "messageKey");
        Map<String, Object> values = new LinkedHashMap<>();
        JsonNode pointsNode = root.has("points") && root.get("points").isObject() ? root.get("points") : null;
        if (pointsNode != null) {
            pointsNode.fields().forEachRemaining(e -> values.put(e.getKey(), toValue(e.getValue())));
        } else {
            root.fields().forEachRemaining(e -> {
                if (!RESERVED.contains(e.getKey())) values.put(e.getKey(), toValue(e.getValue()));
            });
        }
        return processValues(device, values, rawBody, messageKey,
                parseTime(text(root, "deviceTime")), simulated, channelType);
    }

    private IngestResult processValues(DeviceInfo device, Map<String, Object> values, String rawContent,
                                       String messageKey, LocalDateTime valueTime,
                                       boolean simulated, String channelType) {
        String finalKey = (messageKey == null || messageKey.isBlank())
                ? "AUTO-" + sha256Hex(rawContent).substring(0, 24)
                : messageKey.trim();

        DeviceRawMessage existing = rawMessageMapper.selectOne(new QueryWrapper<DeviceRawMessage>()
                .eq("device_id", device.getId()).eq("message_key", finalKey).last("limit 1"));
        if (existing != null) {
            return new IngestResult(true, "重复消息，已按幂等忽略", device.getId(), finalKey,
                    true, simulated, readParsed(existing.getParsedJson()), List.of(), List.of());
        }

        Map<String, DevicePoint> points = new LinkedHashMap<>();
        for (DevicePoint p : pointService.listByDevice(device.getId())) {
            points.put(p.getPointKey(), p);
        }

        Map<String, Object> parsed = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        int updated = 0;

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            DevicePoint point = points.get(key);
            if (point == null) {
                unknown.add(key);
                parsed.put(key, "未配置点位");
                continue;
            }
            if (point.getEnabled() != null && point.getEnabled() == 0) {
                ignored.add(key);
                parsed.put(key, "点位已停用");
                continue;
            }
            Converted converted = convert(point, val);
            parsed.put(key, converted.display);
            if (!simulated && applyRealtime(device.getId(), point, converted, valueTime)) {
                updated++;
            }
        }

        String parsedJson;
        try {
            parsedJson = objectMapper.writeValueAsString(parsed);
        } catch (Exception e) {
            parsedJson = "{}";
        }
        String status = unknown.isEmpty() ? "成功" : (parsed.isEmpty() ? "失败" : "部分成功");
        String errorMsg = unknown.isEmpty() ? null : ("未配置点位：" + String.join(",", unknown));
        Long rawMessageId = saveRaw(device.getId(), channelType, finalKey, rawContent, parsedJson, status, errorMsg,
                simulated ? 1 : 0);

        if (!simulated) {
            touchChannel(device.getId(), channelType);
            // 阶段 4：数据入库后自动认任务；认不到且有产量时进未关联池
            associationService.onData(device, values, points, rawMessageId);
            // 阶段 5：已有执行中任务时，按阈值生成待确认报工
            DeviceTask running = taskMapper.findRunning(device.getId());
            if (running != null) {
                pendingService.generateIfNeeded(device, running, values, points);
            }
        }
        log.info("[设备接入] 设备 {} 来源 {} 消息 {} 点位 {} 个，更新 {} 个，未配置 {} 个，模拟={}",
                device.getDeviceCode(), channelType, finalKey, parsed.size(), updated, unknown.size(), simulated);
        return new IngestResult(true, "接收成功", device.getId(), finalKey, false, simulated,
                parsed, unknown, ignored);
    }

    private boolean applyRealtime(Long deviceId, DevicePoint point, Converted converted, LocalDateTime valueTime) {
        DeviceRealtimeValue old = realtimeValueMapper.selectOne(new QueryWrapper<DeviceRealtimeValue>()
                .eq("device_id", deviceId).eq("point_id", point.getId()).last("limit 1"));
        if (old != null && converted.num != null && old.getValueNum() != null
                && point.getDeadZone() != null && point.getDeadZone().compareTo(BigDecimal.ZERO) > 0
                && converted.num.subtract(old.getValueNum()).abs().compareTo(point.getDeadZone()) < 0) {
            return false;
        }
        if (old == null) {
            old = new DeviceRealtimeValue();
            old.setDeviceId(deviceId);
            old.setPointId(point.getId());
            old.setPointKey(point.getPointKey());
            old.setValueText(converted.text);
            old.setValueNum(converted.num);
            old.setValueTime(valueTime);
            old.setQuality("GOOD");
            realtimeValueMapper.insert(old);
        } else {
            old.setValueText(converted.text);
            old.setValueNum(converted.num);
            old.setValueTime(valueTime);
            old.setQuality("GOOD");
            realtimeValueMapper.updateById(old);
        }
        return true;
    }

    private Converted convert(DevicePoint point, Object val) {
        Converted c = new Converted();
        c.text = val == null ? null : String.valueOf(val);
        String type = point.getDataType() == null ? "DECIMAL" : point.getDataType();
        if ("TEXT".equals(type)) {
            c.display = c.text;
            return c;
        }
        try {
            BigDecimal num;
            if ("BOOLEAN".equals(type)) {
                boolean truthy = Boolean.TRUE.equals(val)
                        || "1".equals(c.text) || "true".equalsIgnoreCase(c.text);
                num = truthy ? BigDecimal.ONE : BigDecimal.ZERO;
            } else {
                num = new BigDecimal(c.text == null ? "0" : c.text.trim());
            }
            BigDecimal factor = point.getScaleFactor() == null ? BigDecimal.ONE : point.getScaleFactor();
            BigDecimal offset = point.getScaleOffset() == null ? BigDecimal.ZERO : point.getScaleOffset();
            c.num = num.multiply(factor).add(offset);
            c.display = c.num.stripTrailingZeros().toPlainString();
            return c;
        } catch (Exception e) {
            c.num = null;
            c.display = c.text;
            return c;
        }
    }

    private Long saveRaw(Long deviceId, String channelType, String messageKey, String raw,
                         String parsedJson, String status, String errorMsg, int simulated) {
        DeviceRawMessage m = new DeviceRawMessage();
        m.setDeviceId(deviceId);
        m.setChannelType(channelType);
        m.setDirection("IN");
        m.setMessageKey(messageKey);
        m.setRawContent(raw);
        m.setParsedJson(parsedJson);
        m.setParseStatus(status);
        m.setErrorMsg(errorMsg);
        m.setReceivedAt(LocalDateTime.now());
        m.setSimulated(simulated);
        rawMessageMapper.insert(m);
        return m.getId();
    }

    private void touchChannel(Long deviceId, String channelType) {
        DeviceChannel channel = channelMapper.selectOne(new QueryWrapper<DeviceChannel>()
                .eq("device_id", deviceId).eq("channel_type", channelType).last("limit 1"));
        if (channel != null) {
            channel.setLastConnectedTime(LocalDateTime.now());
            channelMapper.updateById(channel);
        }
    }

    private LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) return LocalDateTime.now();
        for (DateTimeFormatter f : TIME_FORMATS) {
            try {
                return LocalDateTime.parse(text.trim(), f);
            } catch (Exception ignore) {
                // try next
            }
        }
        return LocalDateTime.now();
    }

    private String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private Object toValue(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isBoolean()) return n.asBoolean();
        if (n.isNumber()) return n.numberValue();
        return n.asText();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readParsed(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private static class Converted {
        String text;
        BigDecimal num;
        String display;
    }

    /** 接入结果 */
    public record IngestResult(boolean ok, String message, Long deviceId, String messageKey,
                               boolean duplicate, boolean simulated, Map<String, Object> parsed,
                               List<String> unknownKeys, List<String> ignoredKeys) {
        static IngestResult fail(String message) {
            return new IngestResult(false, message, null, null, false, false, Map.of(),
                    List.of(), List.of());
        }
    }
}
