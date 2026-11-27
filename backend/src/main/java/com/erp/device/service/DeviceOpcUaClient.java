package com.erp.device.service;

import com.erp.device.entity.DevicePoint;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OPC UA 客户端（阶段 3）：
 * - 仅支持 SecurityPolicy None + 匿名（用户名密码留给后续版本）；
 * - 点位地址即 NodeId，例如 ns=2;s=Temperature、ns=2;i=1001；
 * - 一次连接批量读取全部点位，读完即断开（轮询场景足够，避免长期占用连接）。
 */
@Slf4j
@Component
public class DeviceOpcUaClient {

    public Map<String, Object> readAll(String endpoint, List<DevicePoint> points, int timeoutMs) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (points == null || points.isEmpty() || endpoint == null || endpoint.isBlank()) return values;
        log.info("[OPC UA] 开始读取 {}，点位数 {}", endpoint, points.size());

        List<DevicePoint> usable = new ArrayList<>();
        List<NodeId> nodeIds = new ArrayList<>();
        for (DevicePoint p : points) {
            String addr = p.getAddress() == null ? "" : p.getAddress().trim();
            if (addr.isEmpty()) continue;
            try {
                NodeId nodeId = NodeId.parse(addr);
                nodeIds.add(nodeId);
                usable.add(p);
            } catch (Exception e) {
                log.warn("[OPC UA] 点位 {} 的 NodeId 不合法: {}", p.getPointKey(), addr);
            }
        }
        if (nodeIds.isEmpty()) return values;

        OpcUaClient client = null;
        try {
            client = OpcUaClient.create(endpoint,
                    endpoints -> endpoints.stream()
                            .filter(e -> org.eclipse.milo.opcua.stack.core.security.SecurityPolicy.None
                                    .getUri().equals(e.getSecurityPolicyUri()))
                            .findFirst()
                            .or(() -> endpoints.stream().findFirst()),
                    configBuilder -> configBuilder
                            .setApplicationName(LocalizedText.english("erp-device"))
                            .setApplicationUri("urn:erp:device")
                            .setIdentityProvider(new AnonymousProvider())
                            .setRequestTimeout(org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger.valueOf(timeoutMs))
                            .build());
            client.connect().get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            List<DataValue> results = client.readValues(0.0, TimestampsToReturn.Both, nodeIds)
                    .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            log.info("[OPC UA] 读取完成 {}，返回 {} 个值", endpoint, results == null ? 0 : results.size());
            for (int i = 0; i < results.size() && i < usable.size(); i++) {
                DataValue dv = results.get(i);
                if (dv == null || dv.getStatusCode() == null || !dv.getStatusCode().isGood()) {
                    continue;
                }
                Object v = dv.getValue() == null ? null : dv.getValue().getValue();
                if (v != null) values.put(usable.get(i).getPointKey(), v);
            }
        } catch (Exception e) {
            log.warn("[OPC UA] 读取 {} 失败: {}", endpoint, e.getMessage());
        } finally {
            if (client != null) {
                try {
                    client.disconnect().get();
                } catch (Exception ignore) {
                    // 断开失败不影响本轮数据
                }
            }
        }
        return values;
    }

    /** 写单个 NodeId（参数下发） */
    public boolean writeValue(String endpoint, String nodeIdText, Object value, int timeoutMs) {
        OpcUaClient client = null;
        try {
            NodeId nodeId = NodeId.parse(nodeIdText);
            client = OpcUaClient.create(endpoint,
                    endpoints -> endpoints.stream()
                            .filter(e -> org.eclipse.milo.opcua.stack.core.security.SecurityPolicy.None
                                    .getUri().equals(e.getSecurityPolicyUri()))
                            .findFirst()
                            .or(() -> endpoints.stream().findFirst()),
                    configBuilder -> configBuilder
                            .setApplicationName(LocalizedText.english("erp-device"))
                            .setApplicationUri("urn:erp:device")
                            .setIdentityProvider(new AnonymousProvider())
                            .setRequestTimeout(org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger.valueOf(timeoutMs))
                            .build());
            client.connect().get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            client.writeValue(nodeId,
                            DataValue.valueOnly(new org.eclipse.milo.opcua.stack.core.types.builtin.Variant(value)))
                    .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            log.warn("[OPC UA] 写入 {} 失败: {}", nodeIdText, e.getMessage());
            return false;
        } finally {
            if (client != null) {
                try {
                    client.disconnect().get(2, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
    }
}
