package com.erp.ai.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 通义千问适配器
 * 阿里云百炼平台, 兼容OpenAI接口, 有免费额度
 * 申请地址: https://bailian.console.aliyun.com/
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "erp.ai.qwen.enabled", havingValue = "true")
public class QwenAdapter extends AbstractOpenAiCompatibleAdapter {

    @Value("${erp.ai.qwen.api-key}")
    private String apiKey;

    @Value("${erp.ai.qwen.base-url}")
    private String baseUrl;

    @Value("${erp.ai.qwen.model}")
    private String model;

    @Override
    public String getChannel() { return "QWEN"; }

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
