package com.erp.device.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.device.entity.DeviceChannel;
import com.erp.device.entity.DeviceInfo;
import com.erp.device.entity.DevicePoint;
import com.erp.device.mapper.DeviceChannelMapper;
import com.erp.device.mapper.DeviceInfoMapper;
import com.erp.device.mapper.DevicePointMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.PushbackInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 设备接入引擎（阶段 3）：
 * - MQTT：按设备通道配置连接外部 Broker 并订阅主题，收到的信封报文交给接入服务验签解析；
 * - TCP：按端口监听，支持「4 字节长度 + JSON 信封」与「单行 JSON 信封」两种帧；
 * - 轮询：Modbus TCP / OPC UA，服务端按间隔读取点位并写入实时值。
 *
 * 启动后立即扫描一次，之后每 60 秒对账：新增通道自动启动，删除/停用自动停止。
 */
@Slf4j
@Component
public class DeviceAccessEngine {

    private final DeviceTenantScope tenantScope;
    private final DeviceChannelMapper channelMapper;
    private final DeviceInfoMapper deviceInfoMapper;
    private final DevicePointMapper pointMapper;
    private final DeviceIngestService ingestService;
    private final DeviceModbusClient modbusClient;
    private final DeviceOpcUaClient opcUaClient;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate platformJdbcTemplate;

    /**
     * 显式构造器：@Qualifier 必须落在构造参数上。
     * 用 Lombok 的 @RequiredArgsConstructor 时，字段上的 @Qualifier 不会自动复制到构造参数，
     * 容器里存在 jdbcTemplate / businessJdbcTemplate / platformJdbcTemplate 三个 Bean，会启动失败。
     */
    public DeviceAccessEngine(DeviceTenantScope tenantScope,
                              DeviceChannelMapper channelMapper,
                              DeviceInfoMapper deviceInfoMapper,
                              DevicePointMapper pointMapper,
                              DeviceIngestService ingestService,
                              DeviceModbusClient modbusClient,
                              DeviceOpcUaClient opcUaClient,
                              ObjectMapper objectMapper,
                              @Qualifier("platformJdbcTemplate") JdbcTemplate platformJdbcTemplate) {
        this.tenantScope = tenantScope;
        this.channelMapper = channelMapper;
        this.deviceInfoMapper = deviceInfoMapper;
        this.pointMapper = pointMapper;
        this.ingestService = ingestService;
        this.modbusClient = modbusClient;
        this.opcUaClient = opcUaClient;
        this.objectMapper = objectMapper;
        this.platformJdbcTemplate = platformJdbcTemplate;
    }

    private final Map<String, MqttWorker> mqttWorkers = new ConcurrentHashMap<>();
    private final Map<Integer, TcpWorker> tcpWorkers = new ConcurrentHashMap<>();
    private final Map<String, PollWorker> pollWorkers = new ConcurrentHashMap<>();
    private final AtomicBoolean reconciling = new AtomicBoolean(false);
    private volatile boolean started = false;

    /**
     * 设备采集总开关（2026-11-12）：默认开启，保持全量包原有行为。
     * 客户包 / 没有设备模块的环境置 false 后，不再启动通道扫描与 60 秒轮询对账，
     * 避免"设备表不存在"的持续报错（121 实测每天 3300+ 条）。
     */
    @Value("${erp.device.collector.enabled:true}")
    private boolean collectorEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!collectorEnabled) {
            log.info("[设备接入] 采集服务已关闭（erp.device.collector.enabled=false），不启动设备通道扫描");
            return;
        }
        if (started) return;
        started = true;
        log.info("[设备接入] 引擎启动，开始扫描接入通道");
        reconcileSafely();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scheduledReconcile() {
        if (!collectorEnabled || !started) return;
        reconcileSafely();
    }

    @PreDestroy
    public void stopAll() {
        started = false;
        mqttWorkers.values().forEach(MqttWorker::stop);
        tcpWorkers.values().forEach(TcpWorker::stop);
        pollWorkers.values().forEach(PollWorker::stop);
        mqttWorkers.clear();
        tcpWorkers.clear();
        pollWorkers.clear();
        log.info("[设备接入] 引擎已停止");
    }

    private void reconcileSafely() {
        if (!reconciling.compareAndSet(false, true)) return;
        try {
            reconcile();
        } catch (Exception e) {
            log.error("[设备接入] 通道对账失败", e);
        } finally {
            reconciling.set(false);
        }
    }

    /** 手动触发一次对账（配置变更后立即生效，不等 60 秒） */
    public void reconcileNow() {
        reconcileSafely();
    }

    /** 引擎运行状态（调试台/运维页展示） */
    public Map<String, Object> status() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("started", started);
        data.put("mqtt", mqttWorkers.size());
        data.put("tcp", tcpWorkers.size());
        data.put("poll", pollWorkers.size());
        data.put("tcpPorts", new java.util.ArrayList<>(tcpWorkers.keySet()));
        return data;
    }

    private void reconcile() {
        Set<String> wantMqtt = new HashSet<>();
        Set<Integer> wantTcp = new HashSet<>();
        Map<String, String> wantPoll = new HashMap<>();

        List<Long> tenantIds = platformJdbcTemplate.queryForList(
                "SELECT tenant_id FROM sys_tenant WHERE status = 'active'", Long.class);
        for (Long tenantId : tenantIds) {
            tenantScope.runInTenant(tenantId, () -> {
                collect(tenantId, wantMqtt, wantTcp, wantPoll);
                return null;
            });
        }

        mqttWorkers.entrySet().removeIf(e -> {
            if (wantMqtt.contains(e.getKey())) return false;
            e.getValue().stop();
            log.info("[设备接入] MQTT 已停止 {}", e.getKey());
            return true;
        });
        tcpWorkers.entrySet().removeIf(e -> {
            if (wantTcp.contains(e.getKey())) return false;
            e.getValue().stop();
            log.info("[设备接入] TCP 已停止 端口 {}", e.getKey());
            return true;
        });
        pollWorkers.entrySet().removeIf(e -> {
            if (wantPoll.containsKey(e.getKey())) return false;
            e.getValue().stop();
            log.info("[设备接入] 轮询已停止 {}", e.getKey());
            return true;
        });
        for (Map.Entry<String, String> e : wantPoll.entrySet()) {
            PollWorker existing = pollWorkers.get(e.getKey());
            if (existing != null && existing.signature.equals(e.getValue())) continue;
            if (existing != null) existing.stop();
            String[] parts = e.getKey().split(":", 2);
            Long tenantId = Long.parseLong(parts[0]);
            Long deviceId = Long.parseLong(parts[1]);
            tenantScope.runInTenant(tenantId, () -> {
                DeviceChannel channel = channelMapper.selectOne(new QueryWrapper<DeviceChannel>()
                        .eq("device_id", deviceId).eq("channel_type", "POLL").eq("enabled", 1).last("limit 1"));
                if (channel == null) return null;
                JsonNode cfg = readJson(channel.getConfigJson());
                String protocol = cfg.path("protocol").asText("MODBUS_TCP");
                PollWorker worker = new PollWorker(tenantId, deviceId, protocol, cfg, e.getValue());
                pollWorkers.put(e.getKey(), worker);
                worker.start();
                log.info("[设备接入] 轮询已启动 租户{} 设备{} 协议{}", tenantId, deviceId, protocol);
                return null;
            });
        }
    }

    private void collect(Long tenantId, Set<String> wantMqtt, Set<Integer> wantTcp, Map<String, String> wantPoll) {
        List<DeviceChannel> channels;
        try {
            channels = channelMapper.selectList(new QueryWrapper<DeviceChannel>().eq("enabled", 1));
        } catch (Exception e) {
            log.warn("[设备接入] 读取租户 {} 通道失败: {}", tenantId, e.getMessage());
            return;
        }
        for (DeviceChannel ch : channels) {
            DeviceInfo device = deviceInfoMapper.selectById(ch.getDeviceId());
            if (device == null || !"启用".equals(device.getStatus())) continue;
            JsonNode cfg = readJson(ch.getConfigJson());
            switch (ch.getChannelType()) {
                case "MQTT" -> {
                    String host = cfg.path("host").asText(cfg.path("broker").asText("")).trim();
                    int port = cfg.path("port").asInt(1883);
                    String topic = cfg.path("topic").asText("").trim();
                    if (host.isBlank() || topic.isBlank()) continue;
                    String key = host + ":" + port + "|" + topic;
                    wantMqtt.add(key);
                    mqttWorkers.computeIfAbsent(key, k -> {
                        MqttWorker w = new MqttWorker(host, port, topic, cfg);
                        w.start();
                        return w;
                    });
                }
                case "TCP" -> {
                    int port = cfg.path("port").asInt(9001);
                    if (port <= 0 || port > 65535) continue;
                    wantTcp.add(port);
                    tcpWorkers.computeIfAbsent(port, p -> {
                        TcpWorker w = new TcpWorker(p);
                        w.start();
                        return w;
                    });
                }
                case "POLL" -> {
                    String protocol = cfg.path("protocol").asText("MODBUS_TCP");
                    if (!"MODBUS_TCP".equals(protocol) && !"OPC_UA".equals(protocol)) continue;
                    String key = tenantId + ":" + device.getId();
                    wantPoll.put(key, pollSignature(ch, cfg));
                }
                default -> {
                    // HTTP / WEBSOCKET 不常驻连接
                }
            }
        }
    }

    private String pollSignature(DeviceChannel ch, JsonNode cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append(ch.getId()).append('|').append(ch.getConfigJson()).append('|');
        List<DevicePoint> points = pointMapper.selectList(new QueryWrapper<DevicePoint>()
                .eq("device_id", ch.getDeviceId()).eq("enabled", 1));
        for (DevicePoint p : points) {
            sb.append(p.getId()).append(':').append(p.getPointKey()).append(':')
                    .append(p.getAddress()).append(':').append(p.getDataType()).append(';');
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    // ==================== MQTT 工作线程 ====================

    private class MqttWorker {
        final String host;
        final int port;
        final String topic;
        final JsonNode cfg;
        volatile MqttClient client;
        volatile boolean running = true;

        MqttWorker(String host, int port, String topic, JsonNode cfg) {
            this.host = host;
            this.port = port;
            this.topic = topic;
            this.cfg = cfg;
        }

        void start() {
            Thread t = new Thread(this::connect, "device-mqtt-" + host);
            t.setDaemon(true);
            t.start();
        }

        void connect() {
            if (!running) return;
            try {
                String clientId = "erp-dev-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                MqttClient c = new MqttClient("tcp://" + host + ":" + port, clientId, new MemoryPersistence());
                MqttConnectOptions o = new MqttConnectOptions();
                o.setAutomaticReconnect(true);
                o.setCleanSession(true);
                o.setConnectionTimeout(10);
                o.setKeepAliveInterval(30);
                String user = cfg.path("username").asText("");
                if (!user.isBlank()) {
                    o.setUserName(user);
                    o.setPassword(cfg.path("password").asText("").toCharArray());
                }
                c.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        log.info("[设备接入] MQTT 已连接 {}:{}（重连={}），订阅 {}", host, port, reconnect, topic);
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        log.warn("[设备接入] MQTT 连接断开 {}:{} - {}", host, port,
                                cause == null ? "unknown" : cause.getMessage());
                    }

                    @Override
                    public void messageArrived(String t, MqttMessage message) {
                        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                        try {
                            DeviceIngestService.IngestResult r =
                                    ingestService.ingestEnvelope(payload, "MQTT", null, "mqtt");
                            if (!r.ok()) log.warn("[设备接入] MQTT 报文被拒: {}", r.message());
                        } catch (Exception e) {
                            log.warn("[设备接入] MQTT 报文处理失败: {}", e.getMessage());
                        }
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        // 只订阅，不发消息
                    }
                });
                c.connect(o);
                c.subscribe(topic, 1);
                this.client = c;
            } catch (Exception e) {
                log.error("[设备接入] MQTT 连接失败 {}:{} - {}", host, port, e.getMessage());
            }
        }

        void stop() {
            running = false;
            try {
                if (client != null) {
                    if (client.isConnected()) client.disconnect();
                    client.close();
                }
            } catch (Exception ignore) {
                // 停止失败不影响对账
            }
        }
    }

    // ==================== TCP 工作线程 ====================

    private class TcpWorker {
        final int port;
        volatile ServerSocket server;
        volatile boolean running = true;
        final ExecutorService pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "device-tcp-handler");
            t.setDaemon(true);
            return t;
        });

        TcpWorker(int port) {
            this.port = port;
        }

        void start() {
            Thread t = new Thread(this::acceptLoop, "device-tcp-" + port);
            t.setDaemon(true);
            t.start();
        }

        void acceptLoop() {
            try (ServerSocket ss = new ServerSocket(port)) {
                this.server = ss;
                log.info("[设备接入] TCP 已监听 {}", port);
                while (running) {
                    Socket socket = ss.accept();
                    pool.submit(() -> handle(socket));
                }
            } catch (Exception e) {
                if (running) log.error("[设备接入] TCP 监听 {} 失败: {}", port, e.getMessage());
            }
        }

        void handle(Socket socket) {
            String ip = socket.getRemoteSocketAddress() == null ? "" : socket.getRemoteSocketAddress().toString();
            try (Socket s = socket) {
                s.setSoTimeout(60_000);
                PushbackInputStream pin = new PushbackInputStream(s.getInputStream(), 1);
                int first = pin.read();
                if (first < 0) return;
                pin.unread(first);
                String json;
                if (first == '{') {
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = pin.read()) != -1) {
                        if (c == '\n') break;
                        if (c != '\r') sb.append((char) c);
                        if (sb.length() > 1_048_576) break;
                    }
                    json = sb.toString();
                } else {
                    DataInputStream in = new DataInputStream(pin);
                    int len = in.readInt();
                    if (len <= 0 || len > 1_048_576) return;
                    byte[] buf = new byte[len];
                    in.readFully(buf);
                    json = new String(buf, StandardCharsets.UTF_8);
                }
                if (json.isBlank()) return;
                DeviceIngestService.IngestResult r = ingestService.ingestEnvelope(json, "TCP", null, ip);
                if (!r.ok()) log.warn("[设备接入] TCP 报文被拒（{}）: {}", ip, r.message());
            } catch (Exception e) {
                log.warn("[设备接入] TCP 连接处理失败（{}）: {}", ip, e.getMessage());
            }
        }

        void stop() {
            running = false;
            try {
                if (server != null) server.close();
            } catch (Exception ignore) {
                // ignore
            }
            pool.shutdownNow();
        }
    }

    // ==================== 轮询工作线程 ====================

    private class PollWorker {
        final Long tenantId;
        final Long deviceId;
        final String protocol;
        final JsonNode cfg;
        final String signature;
        final int intervalSeconds;
        final ScheduledExecutorService exec;

        PollWorker(Long tenantId, Long deviceId, String protocol, JsonNode cfg, String signature) {
            this.tenantId = tenantId;
            this.deviceId = deviceId;
            this.protocol = protocol;
            this.cfg = cfg;
            this.signature = signature;
            this.intervalSeconds = Math.max(5, cfg.path("interval").asInt(30));
            this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "device-poll-" + deviceId);
                t.setDaemon(true);
                return t;
            });
        }

        void start() {
            exec.scheduleWithFixedDelay(this::run, 1, intervalSeconds, TimeUnit.SECONDS);
        }

        void run() {
            try {
                tenantScope.runInTenant(tenantId, () -> {
                    DeviceInfo device = deviceInfoMapper.selectById(deviceId);
                    if (device == null || !"启用".equals(device.getStatus())) return null;
                    List<DevicePoint> points = pointMapper.selectList(new QueryWrapper<DevicePoint>()
                            .eq("device_id", deviceId).eq("enabled", 1));
                    if (points.isEmpty()) return null;
                    Map<String, Object> values;
                    if ("OPC_UA".equals(protocol)) {
                        values = opcUaClient.readAll(cfg.path("endpoint").asText(""), points,
                                cfg.path("timeoutMs").asInt(5000));
                    } else {
                        values = modbusClient.readAll(cfg.path("host").asText(""),
                                cfg.path("port").asInt(502), cfg.path("unitId").asInt(1), points,
                                cfg.path("timeoutMs").asInt(3000));
                    }
                    if (values.isEmpty()) return null;
                    String raw;
                    try {
                        raw = objectMapper.writeValueAsString(Map.of("protocol", protocol, "points", values));
                    } catch (Exception e) {
                        raw = "{\"protocol\":\"" + protocol + "\"}";
                    }
                    ingestService.ingestPolled(device, values, raw, "POLL");
                    return null;
                });
            } catch (Exception e) {
                log.warn("[设备接入] 轮询设备 {} 失败: {}", deviceId, e.getMessage());
            }
        }

        void stop() {
            exec.shutdownNow();
        }
    }
}
