package com.erp.kai.service.adapter;

import com.erp.kai.entity.KaiExtConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 小楷独立版 · 适配器工厂（FEAT-KAI-STANDALONE-001）。
 * 按连接 type 路由到具体适配器；未实现的类型给中文说明（引导改用通用 REST 或等下一期）。
 */
@Component
@RequiredArgsConstructor
public class KaiAdapterFactory {

    private final DemoErpAdapter demoErpAdapter;
    private final GenericRestAdapter genericRestAdapter;
    private final KaiYunForwardAdapter kaiYunForwardAdapter;

    public KaiExtAdapter of(KaiExtConnection conn) {
        String type = conn.getType() == null ? "DEMO" : conn.getType();
        return switch (type) {
            case "DEMO" -> demoErpAdapter;
            case "GENERIC_REST" -> genericRestAdapter;
            case "KAIYUN" -> kaiYunForwardAdapter;
            case "KINGDEE" -> throw new IllegalArgumentException(
                    "金蝶系专用适配器尚未上线，当前可先用「通用 REST 接口」类型按金蝶开放平台文档配置对接");
            case "YONYOU" -> throw new IllegalArgumentException(
                    "用友系专用适配器尚未上线，当前可先用「通用 REST 接口」类型按用友开放平台文档配置对接");
            default -> throw new IllegalArgumentException("暂不支持的系统类型：" + type);
        };
    }

    /** 类型 → 适配器实现（供工具描述与连接页校验用）。 */
    public Map<String, KaiExtAdapter> all() {
        return Map.of(
                "DEMO", demoErpAdapter,
                "GENERIC_REST", genericRestAdapter,
                "KAIYUN", kaiYunForwardAdapter);
    }
}
