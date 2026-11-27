package com.erp.ai.gateway;

import com.erp.ai.dto.UnifiedRequest;
import com.erp.ai.dto.UnifiedResponse;

import java.util.List;
import java.util.Map;

/**
 * AI渠道适配器接口 - 所有AI渠道(通义/DeepSeek/钉钉/企微)实现此接口
 */
public interface AiChannelAdapter {

    /**
     * 渠道标识
     */
    String getChannel();

    /**
     * 当前使用的模型名（用于界面展示与失败提示；未定义时返回空串）
     */
    default String getModelName() {
        return "";
    }

    /**
     * 是否启用
     */
    boolean isEnabled();

    /**
     * 调用大模型, 返回AI原始回复文本
     * @param systemPrompt 系统提示词(含ERP业务上下文)
     * @param userMessage  用户消息
     * @return AI回复文本
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * 调用大模型(支持多轮对话), 返回AI原始回复文本
     * @param systemPrompt 系统提示词(含ERP业务上下文)
     * @param userMessage  当前用户消息
     * @param history      历史对话消息列表(每项含role和content)
     * @return AI回复文本
     */
    default String chat(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        return chat(systemPrompt, userMessage);
    }

    /**
     * 处理统一请求(包含意图识别+参数提取+业务执行)
     */
    UnifiedResponse process(UnifiedRequest request);

    /**
     * 视觉对话（支持图片）
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息文本
     * @param imageUrls 图片URL列表
     * @return AI回复文本
     */
    default String visionChat(String systemPrompt, String userMessage, List<String> imageUrls) {
        return chat(systemPrompt, userMessage);
    }

    /**
     * 函数调用（Function Calling）
     * @param systemPrompt 系统提示词
     * @param userMessage 当前用户消息
     * @param messages 历史消息列表
     * @param tools 可用工具列表（OpenAI兼容格式）
     * @return 包含content和tool_calls的结果
     */
    default Map<String, Object> chatWithTools(String systemPrompt, String userMessage,
                                            List<Map<String, Object>> messages,
                                            List<Map<String, Object>> tools) {
        return Map.of("content", chat(systemPrompt, userMessage, null));
    }

    /**
     * 函数调用（带模型推理实时回调）。支持流式的渠道在模型思考过程中把 reasoning_content
     * 增量逐段回调给调用方（小助手据此在界面实时展示"分步思考"，2026-10-14）；不支持流式的渠道
     * 默认退化为一次性返回（回调不触发），行为与旧版完全一致。
     */
    default Map<String, Object> chatWithTools(String systemPrompt, String userMessage,
                                            List<Map<String, Object>> messages,
                                            List<Map<String, Object>> tools,
                                            java.util.function.Consumer<String> reasoningDeltaCallback) {
        return chatWithTools(systemPrompt, userMessage, messages, tools);
    }

    /**
     * 调用大模型并要求其按 JSON 输出（结构化输出）。
     * 支持 response_format 声明的适配器会向模型声明 JSON 输出格式；默认实现等同普通对话
     * （提示词本身已要求 JSON，调用方自带解析兜底）。
     */
    default String chatJson(String systemPrompt, String userMessage) {
        return chatJson(systemPrompt, userMessage, null);
    }

    /**
     * 调用大模型并要求其按 JSON 输出（带历史对话）。
     */
    default String chatJson(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        return chat(systemPrompt, userMessage, history);
    }
}
