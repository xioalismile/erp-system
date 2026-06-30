package com.erp.ai.dto;

import lombok.Data;
import java.util.Map;

/**
 * 统一AI请求 - 所有AI渠道的请求都适配为这个结构
 */
@Data
public class UnifiedRequest {
    /** 全链路追踪ID */
    private String traceId;
    /** 渠道: WEB / QWEN / DEEPSEEK / DINGTALK / WECOM */
    private String channel;
    /** 渠道用户ID */
    private String channelUserId;
    /** ERP用户ID(经身份映射后) */
    private Long erpUserId;
    /** 会话ID(多轮对话) */
    private String sessionId;
    /** 用户原话/输入文本 */
    private String userInput;
    /** 输入类型: TEXT / VOICE */
    private String inputType;
    /** 渠道上下文 */
    private Map<String, Object> context;
}
