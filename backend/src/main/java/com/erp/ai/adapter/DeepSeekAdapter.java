package com.erp.ai.adapter;

import com.erp.ai.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DeepSeek适配器
 * 价格极低(约0.001元/千token), 效果优秀
 * 申请地址: https://platform.deepseek.com/
 * API Key从AiConfigService动态读取（用户在前端配置，加密存储）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekAdapter extends AbstractOpenAiCompatibleAdapter {

    private final AiConfigService aiConfigService;

    @Override
    public String getChannel() { return "DEEPSEEK"; }

    @Override
    public boolean isEnabled() {
        String apiKey = getApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    protected String getApiKey() {
        return aiConfigService.getChannelApiKey("DEEPSEEK");
    }

    @Override
    protected String getBaseUrl() {
        return aiConfigService.getChannelConfig("DEEPSEEK", "base-url",
                "https://api.deepseek.com");
    }

    @Override
    protected String getModel() {
        return aiConfigService.getChannelConfig("DEEPSEEK", "model", "deepseek-flash");
    }
}
