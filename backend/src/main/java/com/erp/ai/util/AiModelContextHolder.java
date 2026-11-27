package com.erp.ai.util;

import com.erp.ai.gateway.AiChannelAdapter;

/**
 * AI请求级"用户所选模型"上下文（ThreadLocal）。
 * <p>
 * 一次Agent请求（ReAct多轮 + 工具内嵌的视觉调用）在同一线程串行执行：
 * ReActAgentService 解析出"用户在小助手里选的渠道/服务商/模型"对应适配器后 set()，
 * 以图识图 / 图片下单 / 图片识别BOM 等工具据此调用**同一个模型**，
 * 不再各自去取"后台默认通道"（历史行为：图片识别固定走默认渠道，与用户所选模型无关）。
 * <p>
 * set() 之后必须在 finally 中 clear()，防线程池复用导致串账。
 * 未 set() 时 get() 返回 null，调用方回退默认通道（保证无界面的后台调用行为不变）。
 */
public final class AiModelContextHolder {

    private static final ThreadLocal<AiChannelAdapter> HOLDER = new ThreadLocal<>();

    private AiModelContextHolder() {
    }

    public static void set(AiChannelAdapter adapter) {
        if (adapter == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(adapter);
        }
    }

    public static AiChannelAdapter get() {
        return HOLDER.get();
    }

    /** 当前所选模型名（用于失败提示），无上下文或渠道未暴露模型名时返回 null */
    public static String getModelName() {
        AiChannelAdapter a = HOLDER.get();
        if (a == null) {
            return null;
        }
        String name = a.getModelName();
        return (name == null || name.isBlank()) ? null : name;
    }

    /** 当前所选渠道标识（用于失败提示），无上下文返回 null */
    public static String getChannel() {
        AiChannelAdapter a = HOLDER.get();
        return a == null ? null : a.getChannel();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
