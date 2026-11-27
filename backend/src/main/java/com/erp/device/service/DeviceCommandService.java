package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.config.BusinessException;
import com.erp.device.entity.DeviceChannel;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.entity.DevicePoint;
import com.erp.device.entity.DeviceRawMessage;
import com.erp.device.entity.DeviceTask;
import com.erp.device.mapper.DeviceChannelMapper;
import com.erp.device.mapper.DeviceInfoMapper;
import com.erp.device.mapper.DevicePointMapper;
import com.erp.device.mapper.DeviceRawMessageMapper;
import com.erp.device.mapper.DeviceTaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 双向下发（阶段 5）：
 * - 任务下发：MQTT 设备直接发到指令主题；HTTP/TCP 设备进指令队列，由设备拉取；
 * - 参数下发：Modbus / OPC UA 轮询设备直接写；MQTT 发指令主题；HTTP/TCP 进队列；
 * - 所有下发都写 OUT 报文留痕（成功 / 失败 / 待拉取）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCommandService {

    private final DeviceInfoMapper deviceInfoMapper;
    private final DeviceChannelMapper channelMapper;
    private final DevicePointMapper pointMapper;
    private final DeviceRawMessageMapper rawMessageMapper;
    private final DeviceTaskMapper taskMapper;
    private final DeviceModbusClient modbusClient;
    private final DeviceOpcUaClient opcUaClient;
    private final ObjectMapper objectMapper;

    /** 任务下发：返回是否已送达（排队也算成功，设备拉取即送达） */
    public Map<String, Object> dispatchTask(Long taskId) {
        DeviceTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("设备任务不存在");
        if (!"待下发".equals(task.getTaskStatus()) && !"下发失败".equals(task.getTaskStatus())) {
            throw new BusinessException("只有待下发或下发失败的任务可以下发");
        }
        DeviceInfo device = deviceInfoMapper.selectById(task.getDeviceId());
        if (device == null) throw new BusinessException("设备不存在");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "TASK");
        payload.put("taskNo", task.getTaskNo());
        payload.put("orderNo", task.getOrderNo());
        payload.put("operationName", task.getOperationName());
        payload.put("dispatchedAt", LocalDateTime.now().toString());

        SendResult result = send(device, payload, "TASK");
        task.setTaskStatus(result.ok ? "已下发" : "下发失败");
        if (task.getStartTime() == null) task.setStartTime(LocalDateTime.now());
        taskMapper.updateById(task);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", result.ok);
        data.put("channel", result.channel);
        data.put("message", result.message);
        return data;
    }

    /** 参数下发 */
    public Map<String, Object> writeParams(Long deviceId, List<Map<String, Object>> params) {
        DeviceInfo device = deviceInfoMapper.selectById(deviceId);
        if (device == null) throw new BusinessException("设备不存在");
        if (params == null || params.isEmpty()) throw new BusinessException("请至少配置一个参数");

        Map<String, DevicePoint> points = new LinkedHashMap<>();
        for (DevicePoint p : pointMapper.selectList(new QueryWrapper<DevicePoint>()
                .eq("device_id", deviceId).eq("writable", 1))) {
            points.put(p.getPointKey(), p);
        }
        if (points.isEmpty()) throw new BusinessException("该设备没有可写点位，请先在接入管理里把点位设为可写");

        Map<String, Object> values = new LinkedHashMap<>();
        for (Map<String, Object> row : params) {
            String key = row.get("pointKey") == null ? null : String.valueOf(row.get("pointKey"));
            if (key == null || !points.containsKey(key)) {
                throw new BusinessException("参数点位[" + key + "]不存在或未设为可写");
            }
            values.put(key, row.get("value"));
        }

        DeviceChannel mqtt = enabledChannel(deviceId, "MQTT");
        DeviceChannel poll = enabledChannel(deviceId, "POLL");
        int okCount = 0;
        int failCount = 0;
        String channel;

        if (mqtt != null) {
            channel = "MQTT";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "PARAMS");
            payload.put("params", values);
            SendResult r = publishMqtt(mqtt, payload);
            okCount = r.ok ? values.size() : 0;
            failCount = r.ok ? 0 : values.size();
            logOut(deviceId, "MQTT", payload, r.ok, r.message);
        } else if (poll != null) {
            JsonNode cfg = readJson(poll.getConfigJson());
            String protocol = cfg.path("protocol").asText("MODBUS_TCP");
            channel = "POLL";
            for (Map.Entry<String, Object> e : values.entrySet()) {
                DevicePoint p = points.get(e.getKey());
                boolean ok;
                if ("OPC_UA".equals(protocol)) {
                    ok = opcUaClient.writeValue(cfg.path("endpoint").asText(""),
                            p.getAddress(), e.getValue(), cfg.path("timeoutMs").asInt(5000));
                } else {
                    String addr = p.getAddress() == null ? "" : p.getAddress().trim();
                    if (addr.toUpperCase().startsWith("COIL:")) {
                        ok = false; // 阶段 5 只下发保持寄存器
                    } else {
                        String reg = addr.toUpperCase().startsWith("HR:") ? addr.substring(3).trim() : addr;
                        try {
                            ok = modbusClient.writeRegister(cfg.path("host").asText(""),
                                    cfg.path("port").asInt(502), cfg.path("unitId").asInt(1),
                                    Integer.parseInt(reg), new BigDecimal(String.valueOf(e.getValue())).intValue(),
                                    cfg.path("timeoutMs").asInt(3000));
                        } catch (Exception ex) {
                            ok = false;
                        }
                    }
                }
                if (ok) okCount++; else failCount++;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "PARAMS");
            payload.put("protocol", protocol);
            payload.put("params", values);
            logOut(deviceId, "POLL", payload, failCount == 0, failCount == 0 ? "写入成功" : (okCount + " 个成功，" + failCount + " 个失败"));
        } else {
            channel = "QUEUE";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "PARAMS");
            payload.put("params", values);
            enqueue(deviceId, payload);
            okCount = values.size();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channel", channel);
        data.put("okCount", okCount);
        data.put("failCount", failCount);
        data.put("message", failCount == 0 ? "下发成功" : "部分参数下发失败");
        return data;
    }

    public List<DevicePoint> writablePoints(Long deviceId) {
        return pointMapper.selectList(new QueryWrapper<DevicePoint>()
                .eq("device_id", deviceId).eq("writable", 1).eq("enabled", 1).orderByAsc("id"));
    }

    /** 设备拉取待执行指令（HTTP/TCP 设备用），拉取后标记已拉取 */
    public List<DeviceRawMessage> pullCommands(Long deviceId) {
        List<DeviceRawMessage> list = rawMessageMapper.selectList(new QueryWrapper<DeviceRawMessage>()
                .eq("device_id", deviceId).eq("direction", "OUT").eq("parse_status", "待拉取")
                .orderByAsc("id").last("limit 20"));
        for (DeviceRawMessage m : list) {
            m.setParseStatus("已拉取");
            rawMessageMapper.updateById(m);
        }
        return list;
    }

    // ==================== 内部 ====================

    private SendResult send(DeviceInfo device, Map<String, Object> payload, String type) {
        DeviceChannel mqtt = enabledChannel(device.getId(), "MQTT");
        if (mqtt != null) {
            SendResult r = publishMqtt(mqtt, payload);
            logOut(device.getId(), "MQTT", payload, r.ok, r.message);
            return r;
        }
        enqueue(device.getId(), payload);
        return new SendResult(true, "QUEUE", "已进入指令队列，等待设备拉取");
    }

    private DeviceChannel enabledChannel(Long deviceId, String type) {
        return channelMapper.selectOne(new QueryWrapper<DeviceChannel>()
                .eq("device_id", deviceId).eq("channel_type", type).eq("enabled", 1).last("limit 1"));
    }

    private SendResult publishMqtt(DeviceChannel channel, Map<String, Object> payload) {
        JsonNode cfg = readJson(channel.getConfigJson());
        String host = cfg.path("host").asText(cfg.path("broker").asText("")).trim();
        int port = cfg.path("port").asInt(1883);
        String topic = cfg.path("commandTopic").asText("");
        if (topic.isBlank()) topic = cfg.path("topic").asText("device/data") + "/cmd";
        if (host.isBlank()) return new SendResult(false, "MQTT", "MQTT 通道未配置 Broker 地址");
        MqttClient client = null;
        try {
            client = new MqttClient("tcp://" + host + ":" + port,
                    "erp-cmd-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                    new MemoryPersistence());
            MqttConnectOptions o = new MqttConnectOptions();
            o.setCleanSession(true);
            o.setConnectionTimeout(5);
            String user = cfg.path("username").asText("");
            if (!user.isBlank()) {
                o.setUserName(user);
                o.setPassword(cfg.path("password").asText("").toCharArray());
            }
            client.connect(o);
            byte[] body = objectMapper.writeValueAsBytes(payload);
            MqttMessage msg = new MqttMessage(body);
            msg.setQos(1);
            client.publish(topic, msg);
            return new SendResult(true, "MQTT", "已发送到 " + topic);
        } catch (Exception e) {
            return new SendResult(false, "MQTT", "MQTT 下发失败：" + e.getMessage());
        } finally {
            if (client != null) {
                try {
                    if (client.isConnected()) client.disconnect();
                    client.close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
    }

    private void enqueue(Long deviceId, Map<String, Object> payload) {
        try {
            logOut(deviceId, "QUEUE", payload, true, "待设备拉取", "待拉取");
        } catch (Exception e) {
            log.warn("[设备下发] 指令入队失败: {}", e.getMessage());
        }
    }

    private void logOut(Long deviceId, String channelType, Map<String, Object> payload,
                        boolean ok, String message) {
        logOut(deviceId, channelType, payload, ok, message, ok ? "成功" : "失败");
    }

    private void logOut(Long deviceId, String channelType, Map<String, Object> payload,
                        boolean ok, String message, String status) {
        try {
            DeviceRawMessage m = new DeviceRawMessage();
            m.setDeviceId(deviceId);
            m.setChannelType(channelType);
            m.setDirection("OUT");
            m.setMessageKey("CMD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6));
            m.setRawContent(objectMapper.writeValueAsString(payload));
            m.setParsedJson(message);
            m.setParseStatus(status);
            m.setErrorMsg(ok ? null : message);
            m.setReceivedAt(LocalDateTime.now());
            m.setSimulated(0);
            rawMessageMapper.insert(m);
        } catch (Exception e) {
            log.warn("[设备下发] 留痕失败: {}", e.getMessage());
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private record SendResult(boolean ok, String channel, String message) {
    }
}
