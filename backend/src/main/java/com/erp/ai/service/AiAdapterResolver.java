package com.erp.ai.service;

import com.erp.ai.adapter.CustomProviderAdapter;
import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.config.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 适配器解析器：把（渠道 / 自定义服务商 / 用户所选模型）统一解析为可用的 {@link AiChannelAdapter}。
 * <p>
 * - 内置渠道（QWEN / DEEPSEEK / OLLAMA）：返回对应的 Spring Bean，行为与改造前完全一致；
 * - 自定义服务商（channel=CUSTOM 或带 providerId）：按 sys_config 的 ai.providers 配置动态构造适配器。
 */
@Slf4j
@Service
public class AiAdapterResolver {

    @Resource
    private List<AiChannelAdapter> adapters;

    @Resource
    private AiConfigService aiConfigService;

    /**
     * @param channel    内置渠道标识或 CUSTOM
     * @param providerId 自定义服务商ID（内置渠道传 null）
     * @param model      用户选择的模型名（可空，空则用服务商第一个模型/渠道默认模型）
     */
    public AiChannelAdapter resolve(String channel, String providerId, String model) {
        // 1) 自定义服务商优先
        boolean custom = "CUSTOM".equalsIgnoreCase(channel) || (providerId != null && !providerId.isBlank());
        if (custom) {
            Map<String, Object> p = aiConfigService.getCustomProvider(providerId);
            if (p == null) {
                // 只传了 CUSTOM 没带 providerId：退回第一家已启用的服务商
                p = firstEnabledProvider();
            }
            if (p == null) {
                throw new BusinessException("未找到可用的自定义服务商，请先在 AI 助手中配置");
            }
            AiChannelAdapter a = build(p, model);
            if (a.isEnabled()) {
                return a;
            }
            throw new BusinessException("服务商「" + p.get("name") + "」配置不完整（缺少调用地址/API Key/模型）");
        }

        // 2) 内置渠道
        if (channel != null && !channel.isBlank()) {
            for (AiChannelAdapter a : adapters) {
                if (a.getChannel().equalsIgnoreCase(channel) && a.isEnabled()) {
                    return a;
                }
            }
        }

        // 3) 兜底：第一个已启用的内置渠道
        for (AiChannelAdapter a : adapters) {
            if (a.isEnabled()) {
                return a;
            }
        }
        throw new BusinessException("没有可用的AI渠道，请先在AI助手中配置模型服务商");
    }

    private Map<String, Object> firstEnabledProvider() {
        for (Map<String, Object> p : aiConfigService.listCustomProvidersRaw()) {
            if (isEnabledFlag(p.get("enabled"))) {
                return p;
            }
        }
        return null;
    }

    private boolean isEnabledFlag(Object v) {
        return v == null || Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    private AiChannelAdapter build(Map<String, Object> p, String model) {
        List<String> models = new ArrayList<>();
        Object mv = p.get("models");
        if (mv instanceof List<?> l) {
            for (Object o : l) {
                String s = o == null ? "" : String.valueOf(o).trim();
                if (!s.isBlank()) {
                    models.add(s);
                }
            }
        }
        String chosen = (model != null && !model.isBlank())
                ? model
                : (models.isEmpty() ? "" : models.get(0));
        CustomProviderAdapter adapter = new CustomProviderAdapter(
                p.get("id") == null ? null : String.valueOf(p.get("id")),
                p.get("name") == null ? null : String.valueOf(p.get("name")),
                p.get("baseUrl") == null ? null : String.valueOf(p.get("baseUrl")),
                p.get("apiKey") == null ? null : String.valueOf(p.get("apiKey")),
                chosen,
                p.get("visionModel") == null ? null : String.valueOf(p.get("visionModel")),
                "true".equalsIgnoreCase(String.valueOf(p.get("webSearch"))));
        adapter.setConfigService(aiConfigService);
        return adapter;
    }
}
