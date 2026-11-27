package com.erp.ai.adapter;

import com.erp.ai.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 本地大模型适配器（Ollama）
 * 数据全程不出内网，OpenAI兼容接口（http://127.0.0.1:11434/v1）
 * 使用前需安装Ollama并拉取模型：ollama pull qwen3:8b
 * base-url/model 均可在设置中心动态配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaAdapter extends AbstractOpenAiCompatibleAdapter {

    private final AiConfigService aiConfigService;

    @Override
    public String getChannel() { return "OLLAMA"; }

    @Override
    public boolean isEnabled() {
        // 本地模型无需API Key，始终启用（Ollama服务未启动时调用会返回网络错误）
        return true;
    }

    @Override
    protected String getApiKey() {
        // Ollama不校验Authorization，占位值即可；支持用户在设置中心自定义
        String key = aiConfigService.getChannelApiKey("OLLAMA");
        return (key == null || key.isBlank()) ? "ollama-local" : key;
    }

    @Override
    protected String getBaseUrl() {
        return aiConfigService.getChannelConfig("OLLAMA", "base-url", "http://127.0.0.1:11434/v1");
    }

    @Override
    protected String getModel() {
        // 默认qwen2.5:7b（无思考模式，响应快，支持工具调用）；qwen3:8b更聪明但默认开启深度思考，CPU下响应极慢
        return aiConfigService.getChannelConfig("OLLAMA", "model", "qwen2.5:7b");
    }

    @Override
    protected String getVisionModel() {
        // 视觉模型需单独拉取：ollama pull qwen2.5vl:7b
        return aiConfigService.getChannelConfig("OLLAMA", "vision-model", "qwen2.5vl:7b");
    }
}
