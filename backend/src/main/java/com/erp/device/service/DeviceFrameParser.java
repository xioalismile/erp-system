package com.erp.device.service;

import com.erp.config.BusinessException;
import com.erp.device.entity.DeviceProtocolTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设备报文解析引擎（阶段 3）：
 * JSON / 定长（FIXED）/ 分隔符（DELIMITER）/ 正则（REGEX）。
 * 脚本（SCRIPT）留到后续阶段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceFrameParser {

    private static final Set<String> RESERVED = Set.of("messageKey", "deviceTime", "simulated", "points");

    private final ObjectMapper objectMapper;

    public Map<String, Object> parse(byte[] payload, DeviceProtocolTemplate template) {
        String format = template == null || template.getParseFormat() == null ? "JSON" : template.getParseFormat();
        try {
            return switch (format) {
                case "FIXED" -> parseFixed(payload, template);
                case "DELIMITER" -> parseDelimiter(new String(payload, StandardCharsets.UTF_8), template);
                case "REGEX" -> parseRegex(new String(payload, StandardCharsets.UTF_8), template);
                default -> parseJson(new String(payload, StandardCharsets.UTF_8));
            };
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("报文解析失败（" + format + "）：" + e.getMessage());
        }
    }

    private Map<String, Object> parseJson(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        Map<String, Object> values = new LinkedHashMap<>();
        JsonNode pointsNode = root.has("points") && root.get("points").isObject() ? root.get("points") : null;
        if (pointsNode != null) {
            pointsNode.fields().forEachRemaining(e -> values.put(e.getKey(), toValue(e.getValue())));
            return values;
        }
        root.fields().forEachRemaining(e -> {
            if (!RESERVED.contains(e.getKey())) values.put(e.getKey(), toValue(e.getValue()));
        });
        return values;
    }

    private Object toValue(JsonNode n) {
        if (n == null) return null;
        if (n.isBoolean()) return n.asBoolean();
        if (n.isNumber()) return n.numberValue();
        if (n.isNull()) return null;
        return n.asText();
    }

    /** 定长：{"charset":"UTF-8","fields":[{"key":"a","offset":0,"length":2,"type":"int"}]} */
    private Map<String, Object> parseFixed(byte[] payload, DeviceProtocolTemplate template) throws Exception {
        JsonNode cfg = objectMapper.readTree(template.getConfigJson() == null ? "{}" : template.getConfigJson());
        JsonNode fields = cfg.get("fields");
        if (fields == null || !fields.isArray() || fields.isEmpty()) {
            throw new BusinessException("定长解析未配置 fields（模板：" + template.getTemplateName() + "）");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (JsonNode f : fields) {
            String key = f.path("key").asText();
            int offset = f.path("offset").asInt(0);
            int length = f.path("length").asInt(2);
            String type = f.path("type").asText("int");
            if (key.isBlank()) continue;
            if (offset < 0 || length <= 0 || offset + length > payload.length) {
                throw new BusinessException("定长字段[" + key + "]超出报文长度（报文 " + payload.length + " 字节）");
            }
            byte[] slice = java.util.Arrays.copyOfRange(payload, offset, offset + length);
            values.put(key, decodeFixed(slice, type, !"LITTLE_ENDIAN".equalsIgnoreCase(template.getByteOrder())));
        }
        return values;
    }

    private Object decodeFixed(byte[] slice, String type, boolean bigEndian) {
        ByteBuffer buf = ByteBuffer.wrap(slice);
        buf.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return switch (type.toLowerCase()) {
            case "bool", "boolean" -> slice.length > 0 && slice[0] != 0;
            case "byte" -> slice.length > 0 ? (int) slice[0] : 0;
            case "short" -> buf.getShort() & 0xFFFF;
            case "int", "integer" -> buf.getInt();
            case "long" -> buf.getLong();
            case "float" -> BigDecimal.valueOf(buf.getFloat());
            case "double" -> BigDecimal.valueOf(buf.getDouble());
            default -> new String(slice, StandardCharsets.UTF_8).trim();
        };
    }

    /** 分隔符：{"delimiter":",","keys":["a","b"],"trim":true} */
    private Map<String, Object> parseDelimiter(String payload, DeviceProtocolTemplate template) throws Exception {
        JsonNode cfg = objectMapper.readTree(template.getConfigJson() == null ? "{}" : template.getConfigJson());
        String delimiter = cfg.path("delimiter").asText(",");
        boolean trim = cfg.path("trim").asBoolean(true);
        JsonNode keys = cfg.get("keys");
        if (keys == null || !keys.isArray() || keys.isEmpty()) {
            throw new BusinessException("分隔符解析未配置 keys（模板：" + template.getTemplateName() + "）");
        }
        String[] parts = payload.split(Pattern.quote(delimiter), -1);
        if (parts.length < keys.size()) {
            throw new BusinessException("分隔符字段数不足：报文 " + parts.length + " 段，模板要求 " + keys.size() + " 段");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            String v = trim ? parts[i].trim() : parts[i];
            values.put(keys.get(i).asText(), v);
        }
        return values;
    }

    /** 正则：{"regex":"...","groups":["a","b"]}；groups 对应正则第 1..n 个分组 */
    private Map<String, Object> parseRegex(String payload, DeviceProtocolTemplate template) throws Exception {
        JsonNode cfg = objectMapper.readTree(template.getConfigJson() == null ? "{}" : template.getConfigJson());
        String regex = cfg.path("regex").asText();
        if (regex.isBlank()) throw new BusinessException("正则解析未配置 regex");
        JsonNode groups = cfg.get("groups");
        if (groups == null || !groups.isArray() || groups.isEmpty()) {
            throw new BusinessException("正则解析未配置 groups");
        }
        Matcher m = Pattern.compile(regex).matcher(payload);
        if (!m.find()) throw new BusinessException("报文未匹配正则");
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            values.put(groups.get(i).asText(), m.group(i + 1));
        }
        return values;
    }

    /** 供引擎确认模板是否可用于非 JSON 报文 */
    public List<String> supportedFormats() {
        List<String> list = new ArrayList<>();
        list.add("JSON");
        list.add("FIXED");
        list.add("DELIMITER");
        list.add("REGEX");
        return list;
    }
}
