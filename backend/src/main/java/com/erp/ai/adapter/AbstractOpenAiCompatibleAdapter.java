package com.erp.ai.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.ai.dto.UnifiedRequest;
import com.erp.ai.dto.UnifiedResponse;
import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.config.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通义千问适配器 - 通过阿里云百炼API调用
 * 兼容OpenAI接口格式: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
 * 有免费额度, 适合低成本起步
 */
@Slf4j
public abstract class AbstractOpenAiCompatibleAdapter implements AiChannelAdapter {

    protected final ObjectMapper mapper = new ObjectMapper();
    private OkHttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 子类提供: API Key
     */
    protected abstract String getApiKey();

    /**
     * 子类提供: Base URL (如 https://dashscope.aliyuncs.com/compatible-mode/v1)
     */
    protected abstract String getBaseUrl();

    /**
     * 子类提供: 模型名 (如 qwen-plus, deepseek-chat)
     */
    protected abstract String getModel();

    @Override
    public UnifiedResponse process(UnifiedRequest request) {
        // 基类提供默认实现，子类可覆盖
        throw new BusinessException("process method should be implemented by AiGatewayService");
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        if (!isEnabled()) {
            throw new BusinessException("AI渠道 " + getChannel() + " 未启用");
        }
        String url = getBaseUrl() + "/chat/completions";
        try {
            Map<String, Object> body = Map.of(
                    "model", getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 2000
            );
            String json = mapper.writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "unknown";
                    log.error("AI调用失败: {} - {}", response.code(), err);
                    throw new BusinessException("AI服务调用失败: " + response.code());
                }
                String respJson = response.body().string();
                Map<?, ?> resp = mapper.readValue(respJson, Map.class);
                List<?> choices = (List<?>) resp.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                    Map<?, ?> message = (Map<?, ?>) choice.get("message");
                    return (String) message.get("content");
                }
                throw new BusinessException("AI返回内容为空");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI调用异常", e);
            throw new BusinessException("AI服务异常: " + e.getMessage());
        }
    }
}
