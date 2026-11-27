package com.erp.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStep {

    public enum StepType {
        THOUGHT,
        ACTION,
        OBSERVATION,
        FINAL_ANSWER,
        ERROR,
        /** 写操作待确认：携带 confirmToken + 操作摘要，前端渲染确认/取消按钮卡片 */
        CONFIRM_NEEDED,
        /** 本轮用量汇总：耗时 + token 消耗（2026-10-13 用户要求展示，渲染在答复末尾） */
        USAGE,
        /** 思考增量：流式模式下模型推理（reasoning_content）的一小段实时文本（2026-10-14，
         *  用户反馈"思考不应该一整个都默认思考中，没有分步骤的吗"——模型长思考期间界面只有一个动画，
         *  且 90 秒无数据会被前端看门狗误判为服务无响应。增量不进历史、不落库，仅实时展示） */
        REASONING_DELTA
    }

    /** THOUGHT 专用：true = 本轮还在模型思考中（前端显示动画+已收到的推理增量）；模型返回后由正式思考步骤替换 */
    private boolean streaming;

    private StepType type;
    private String content;
    private String toolName;
    private Map<String, Object> toolParams;
    private Map<String, Object> toolResult;
    /** CONFIRM_NEEDED 专用：确认令牌（一次性，5分钟有效） */
    private String confirmToken;
    /** THOUGHT 专用：模型真实推理内容（reasoning_content）；null = 模型本轮未返回思考（前端降级显示步骤说明） */
    private String reasoning;
    /** USAGE 专用：本轮总耗时（毫秒，从收到用户输入到答复完成） */
    private Long elapsedMs;
    /** USAGE 专用：token 用量 [promptTokens, completionTokens, totalTokens, calls, cacheHitTokens] */
    private long[] usage;
    /** USAGE 专用：本轮扣除的积分（2026-10-24 积分制） */
    private java.math.BigDecimal credits;
    /** USAGE 专用：扣减后的积分余额 */
    private java.math.BigDecimal creditBalance;
    /** USAGE 专用：扣减来源 USER=员工个人积分 / TENANT=公司总积分 */
    private String creditOwnerType;
    /** USAGE 专用：本次请求开始时的上下文估算 tokens（系统提示 + 历史 + 用户输入 + 工具清单） */
    private Integer contextTokens;
    /** USAGE 专用：上下文上限 tokens（设置中心「上下文上限」，默认 128000，最大 1000000） */
    private Integer contextLimit;
    private long timestamp;

    public static AgentStep thought(String content) {
        return AgentStep.builder()
                .type(StepType.THOUGHT)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /** 轮次开始的"思考中"占位步骤（streaming=true）：模型思考期间界面就有内容，看门狗也不会误判超时 */
    public static AgentStep thinkingPlaceholder(int round) {
        return AgentStep.builder()
                .type(StepType.THOUGHT)
                .content("第 " + round + " 轮：正在思考…")
                .streaming(true)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /** 流式推理增量（一小段实时思考文本，前端追加到当前"思考中"步骤） */
    public static AgentStep reasoningDelta(String text) {
        return AgentStep.builder()
                .type(StepType.REASONING_DELTA)
                .content(text)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /** 带模型真实推理内容的思考步骤（reasoning 为空时等价于 thought(content)） */
    public static AgentStep thoughtWithReasoning(String content, String reasoning) {
        return AgentStep.builder()
                .type(StepType.THOUGHT)
                .content(content)
                .reasoning(reasoning)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /** 本轮用量汇总步骤（答复末尾渲染：耗时 + token 用量 + 积分消耗） */
    public static AgentStep usageStep(long elapsedMs, long[] usage) {
        return usageStep(elapsedMs, usage, (java.math.BigDecimal) null, (java.math.BigDecimal) null, null);
    }

    /** 本轮用量汇总步骤（含积分扣除与余额） */
    public static AgentStep usageStep(long elapsedMs, long[] usage,
                                      java.math.BigDecimal credits, java.math.BigDecimal creditBalance,
                                      String creditOwnerType) {
        return usageStep(elapsedMs, usage, credits, creditBalance, creditOwnerType, null, null);
    }

    /** 本轮用量汇总步骤（含积分与上下文使用率） */
    public static AgentStep usageStep(long elapsedMs, long[] usage,
                                      java.math.BigDecimal credits, java.math.BigDecimal creditBalance,
                                      String creditOwnerType, Integer contextTokens, Integer contextLimit) {
        return AgentStep.builder()
                .type(StepType.USAGE)
                .elapsedMs(elapsedMs)
                .usage(usage)
                .credits(credits)
                .creditBalance(creditBalance)
                .creditOwnerType(creditOwnerType)
                .contextTokens(contextTokens)
                .contextLimit(contextLimit)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentStep action(String toolName, Map<String, Object> toolParams) {
        return AgentStep.builder()
                .type(StepType.ACTION)
                .toolName(toolName)
                .toolParams(toolParams)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentStep observation(String toolName, Map<String, Object> toolResult) {
        return AgentStep.builder()
                .type(StepType.OBSERVATION)
                .toolName(toolName)
                .toolResult(toolResult)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentStep finalAnswer(String content) {
        return AgentStep.builder()
                .type(StepType.FINAL_ANSWER)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentStep error(String content) {
        return AgentStep.builder()
                .type(StepType.ERROR)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentStep confirmNeeded(String toolName, Map<String, Object> toolParams,
                                          String confirmToken, String summary) {
        return AgentStep.builder()
                .type(StepType.CONFIRM_NEEDED)
                .toolName(toolName)
                .toolParams(toolParams)
                .confirmToken(confirmToken)
                .content(summary)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
