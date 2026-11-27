package com.erp.ai.util;

/**
 * 思考强度（light/high/max）的请求级 ThreadLocal（2026-11-22 照参考设计三档）。
 *
 * 用途：适配器在构建模型请求时据此放大单轮输出上限（light/high=各模型默认；max=在默认基础上翻倍，
 * 受各模型实际上限约束）。与 AiModelContextHolder 同模式 —— 必须在请求结束的 finally 里无条件
 * clear()，防线程池线程复用串账。
 */
public final class AiThoughtContextHolder {

    private static final ThreadLocal<String> LEVEL = new ThreadLocal<>();

    private AiThoughtContextHolder() {}

    public static void set(String level) {
        LEVEL.set(level);
    }

    public static String get() {
        return LEVEL.get();
    }

    public static void clear() {
        LEVEL.remove();
    }
}
