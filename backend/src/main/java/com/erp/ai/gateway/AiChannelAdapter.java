package com.erp.ai.gateway;

import com.erp.ai.dto.UnifiedRequest;
import com.erp.ai.dto.UnifiedResponse;

/**
 * AI渠道适配器接口 - 所有AI渠道(通义/DeepSeek/钉钉/企微)实现此接口
 */
public interface AiChannelAdapter {

    /**
     * 渠道标识
     */
    String getChannel();

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
     * 处理统一请求(包含意图识别+参数提取+业务执行)
     */
    UnifiedResponse process(UnifiedRequest request);
}
