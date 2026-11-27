package com.erp.ai.util;

/**
 * AI调用token用量线程级收集器。
 * <p>
 * 一次Agent请求（ReAct多轮 + 工具内嵌的视觉调用）都在同一线程串行执行：
 * 适配器层每次成功调用AI后 add()，由发起方（ReActAgentService）begin()/end()圈定统计范围。
 * end() 无条件清理ThreadLocal，防线程池复用导致用量串账。
 * 未begin()时add()静默忽略（如连通性测试、已停用的旧网关链路）。
 */
public final class AiUsageCollector {

    /** [0]=promptTokens [1]=completionTokens [2]=totalTokens [3]=AI调用次数 [4]=缓存命中tokens */
    private static final ThreadLocal<long[]> HOLDER = new ThreadLocal<>();

    private AiUsageCollector() {
    }

    /** 开始统计本次请求线程内的AI用量（重复调用重置累计值） */
    public static void begin() {
        HOLDER.set(new long[5]);
    }

    /** 记录一次AI调用的usage（未begin()时静默忽略；无缓存命中口径时走此重载） */
    public static void add(long promptTokens, long completionTokens, long totalTokens) {
        add(promptTokens, completionTokens, totalTokens, 0);
    }

    /** 记录一次AI调用的usage（含缓存命中tokens；未begin()时静默忽略） */
    public static void add(long promptTokens, long completionTokens, long totalTokens, long cacheHitTokens) {
        long[] u = HOLDER.get();
        if (u == null) {
            return;
        }
        u[0] += Math.max(0, promptTokens);
        u[1] += Math.max(0, completionTokens);
        u[2] += Math.max(0, totalTokens);
        u[3]++;
        u[4] += Math.max(0, cacheHitTokens);
    }

    /**
     * 结束统计并返回汇总 [promptTokens, completionTokens, totalTokens, calls, cacheHitTokens]。
     * 必须在finally中调用以清理ThreadLocal；未begin()时返回null。
     */
    public static long[] end() {
        long[] u = HOLDER.get();
        HOLDER.remove();
        return u;
    }
}
