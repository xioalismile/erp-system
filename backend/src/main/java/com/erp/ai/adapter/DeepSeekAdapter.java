package com.erp.ai.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DeepSeek适配器
 * 价格极低(约0.001元/千token), 效果优秀
 * 申请地址: https://platform.deepseek.com/
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "erp.ai.deepseek.enabled", havingValue = "true")
public class DeepSeekAdapter extends AbstractOpenAiCompatibleAdapter {

    @Value("${erp.ai.deepseek.api-key}")
    private String apiKey;

    @Value("${erp.ai.deepseek.base-url}")
    private String baseUrl;

    @Value("${erp.ai.deepseek.model}")
    private String model;

    @Override
    public String getChannel() { return "DEEPSEEK"; }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    protected String getApiKey() { return apiKey; }

    @Override
    protected String getBaseUrl() { return baseUrl; }

    @Override
    protected String getModel() { return model; }
}
