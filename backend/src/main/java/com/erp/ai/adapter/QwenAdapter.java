package com.erp.ai.adapter;

import com.erp.ai.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通义千问适配器
 * 阿里云百炼平台, 兼容OpenAI接口, 有免费额度
 * 申请地址: https://bailian.console.aliyun.com/
 * API Key从AiConfigService动态读取（用户在前端配置，加密存储）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QwenAdapter extends AbstractOpenAiCompatibleAdapter {

    private final AiConfigService aiConfigService;

    @Override
    public String getChannel() { return "QWEN"; }

    @Override
    public boolean isEnabled() {
        String apiKey = getApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    protected String getApiKey() {
        return aiConfigService.getChannelApiKey("QWEN");
    }

    @Override
    protected String getBaseUrl() {
        return aiConfigService.getChannelConfig("QWEN", "base-url",
                "https://dashscope.aliyuncs.com/compatible-mode/v1");
    }

    @Override
    protected String getModel() {
        return aiConfigService.getChannelConfig("QWEN", "model", "qwen-plus");
    }

    @Override
    protected String getVisionModel() {
        return aiConfigService.getChannelConfig("QWEN", "vision-model", "qwen-vl-plus");
    }

    /**
     * 联网搜索：设置中心「对话能力-联网搜索」开启后，附加通义千问 enable_search 参数
     * （阿里云百炼兼容模式支持；DeepSeek 等其它渠道不受影响）。
     */
    @Override
    protected Map<String, Object> extraBodyParams() {
        try {
            if (aiConfigService != null && aiConfigService.isWebSearchEnabled()) {
                return Map.of("enable_search", true);
            }
        } catch (Exception ignore) {
        }
        return null;
    }
}
