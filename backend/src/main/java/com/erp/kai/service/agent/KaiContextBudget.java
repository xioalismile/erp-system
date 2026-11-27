package com.erp.kai.service.agent;

import java.util.Locale;

/**
 * 上下文预算（2026-10-20）。
 *
 * 此前历史注入写死 40 条消息 —— 模型有多少窗口都用不上，长会话必"失忆"。
 * 这里按**模型真实窗口**换算可注入的历史字符量：
 * 窗口 60% 留给历史对话，其余留给系统提示、工具定义与模型输出。
 *
 * 中文按 1 文字≈1.6 字符/token 保守估算（英文更高，取保守值不会超窗）。
 */
public final class KaiContextBudget {

    private KaiContextBudget() {}

    /** 模型上下文窗口（token）。不同服务商差异很大，这里按型号识别。 */
    public static int windowOf(String modelName) {
        String m = modelName == null ? "" : modelName.toLowerCase(Locale.ROOT);
        if (m.contains("gemini")) return 1_000_000;
        if (m.contains("claude")) return 200_000;
        // GPT-4.1 / GPT-5 系列为 1M 窗口
        if (m.contains("gpt-4.1") || m.contains("gpt-5")) return 1_000_000;
        if (m.contains("gpt-4o") || m.contains("gpt-4")) return 128_000;
        if (m.contains("deepseek")) return 128_000;
        if (m.contains("qwen") || m.contains("glm") || m.contains("kimi")
                || m.contains("moonshot") || m.contains("llama") || m.contains("mistral")) {
            return 128_000;
        }
        return 128_000;
    }

    /** 可注入历史的字符预算。 */
    public static int historyChars(String modelName) {
        int window = windowOf(modelName);
        return (int) (window * 0.6 * 1.6);
    }

    /**
     * 可注入历史的字符预算（2026-11-22 三档窗口覆盖版）。
     * overrideTokens = 用户选的上下文档位（512000 / 1000000）；与模型真实窗口取较小值 ——
     * 档位超出模型窗口时按模型实际能力生效，不会把超出模型窗口的请求发出去。
     */
    public static int historyChars(String modelName, Integer overrideTokens) {
        int window = windowOf(modelName);
        if (overrideTokens != null && overrideTokens > 0) {
            window = Math.min(window, overrideTokens);
        }
        return (int) (window * 0.6 * 1.6);
    }

    /** 历史消息条数上限（防极端会话）。 */
    public static int messageCap(String modelName) {
        int window = windowOf(modelName);
        return Math.min(2000, Math.max(40, window / 400));
    }
}
