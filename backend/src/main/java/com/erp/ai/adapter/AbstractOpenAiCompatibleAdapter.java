package com.erp.ai.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.ai.dto.UnifiedRequest;
import com.erp.ai.dto.UnifiedResponse;
import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.ai.service.AiConfigService;
import com.erp.ai.util.AiUsageCollector;
import com.erp.ai.util.SensitiveDataMasker;
import com.erp.config.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI兼容协议适配器基类
 * 兼容通义千问/DeepSeek/Ollama等所有OpenAI接口格式的渠道
 */
@Slf4j
public abstract class AbstractOpenAiCompatibleAdapter implements AiChannelAdapter {

    protected final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private volatile OkHttpClient httpClient;

    /**
     * 用于读取"AI请求敏感字段脱敏"开关（ai.policy.mask-sensitive）。
     * Spring Bean（内置渠道）由 @Autowired 注入；
     * 运行时动态构造的自定义服务商适配器由 AiAdapterResolver 显式 set。
     */
    @Autowired(required = false)
    protected AiConfigService aiConfigService;

    @PostConstruct
    public void init() {
        client(); // 预热：内置渠道 Bean 启动即建好连接池
    }

    /**
     * 懒加载 HTTP 客户端。
     * 自定义服务商适配器是运行时 new 出来的（不是 Spring Bean），
     * 不会走 @PostConstruct，因此必须懒加载，否则调用时 httpClient 为 null。
     */
    protected OkHttpClient client() {
        OkHttpClient c = httpClient;
        if (c == null) {
            synchronized (this) {
                c = httpClient;
                if (c == null) {
                    // 读超时可配置（环境变量 ERP_AI_READ_TIMEOUT_SECONDS），默认 300 秒（2026-10-14 起，
                    // 旧值 120 秒：DeepSeek 思考模式对"查单据+建单"类复杂请求推理常超 120 秒，非流式等待
                    // 会被 OkHttp 掐断报 SocketTimeout；流式下该超时是"两段数据之间"的间隔上限，300 秒足够宽裕）；
                    // 本地小参数模型（Ollama）吃大工具清单时首字延迟可能超过 120 秒，部署侧可调大。
                    int readTimeoutSec = 300;
                    String envVal = System.getenv("ERP_AI_READ_TIMEOUT_SECONDS");
                    if (envVal != null && !envVal.isBlank()) {
                        try { readTimeoutSec = Integer.parseInt(envVal.trim()); } catch (NumberFormatException ignore) { }
                    }
                    c = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
                            .build();
                    httpClient = c;
                }
            }
        }
        return c;
    }

    /**
     * 数据安全出口脱敏：所有发往大模型API的文本统一过一遍敏感数据打码
     * （手机号/身份证/银行卡/邮箱/固定电话），在适配器出口层统一拦截，
     * 覆盖 chat / chatWithTools / visionChat 全部外发链路。
     * 受设置中心"AI请求敏感字段脱敏"开关控制（默认开启）；
     * 本地存储（会话历史Redis、日志原文）不受影响，仅外发内容脱敏。
     *
     * <p>2026-11-17 口径（方案A）：只对"系统查出来再发给模型"的数据打码（工具结果、系统提示），
     * 用户自己输入的内容与模型自己的回复不打码——否则用户口述的电话/邮箱会以打码值回写进业务单据
     * （实测：用户说 13800138000，模型只看到 138****8000，建客户时落库的就是打码值）。
     * 打码值是单向的，系统没有还原能力，因此用户输入必须原样外发。
     */
    private String maskOutbound(String text) {
        if (text == null) {
            return text;
        }
        try {
            if (aiConfigService != null && !aiConfigService.isDataMaskEnabled()) {
                return text; // 设置中心已关闭脱敏开关
            }
        } catch (Exception ignore) {
            // 开关读取失败时保守处理：继续脱敏
        }
        return SensitiveDataMasker.maskAll(text);
    }

    /**
     * 按消息角色决定是否打码：工具结果与系统提示打码（数据库数据），
     * 用户输入与模型回复原样外发（避免打码值回写业务数据）。
     */
    private String maskOutboundByRole(String role, String text) {
        if ("user".equals(role) || "assistant".equals(role)) {
            return text;
        }
        return maskOutbound(text);
    }

    /**
     * 子类可覆盖：附加到请求体的额外参数（如联网搜索 enable_search）。
     * 返回 null 表示无附加参数。仅作用于普通对话与函数调用链路；
     * 图片识别链路不附加（视觉模型对联网参数的支持不一，避免误伤）。
     * 读取失败时忽略附加参数，不影响主流程。
     */
    protected Map<String, Object> extraBodyParams() {
        return null;
    }

    /** 把子类附加参数合并进请求体 */
    private void applyExtraParams(Map<String, Object> body) {
        try {
            Map<String, Object> extra = extraBodyParams();
            if (extra != null && !extra.isEmpty()) {
                body.putAll(extra);
            }
        } catch (Exception e) {
            log.warn("[AI适配器] 附加参数读取失败（忽略，不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 回答严谨度（temperature）：设置中心「对话能力」可调，默认 0.3（与历史写死值一致）。
     * 配置服务不可用时（运行时构造的自定义服务商未注入）回退默认值。
     */
    protected double chatTemperature() {
        try {
            if (aiConfigService != null) {
                return aiConfigService.getChatTemperature();
            }
        } catch (Exception ignore) {
        }
        return 0.3;
    }

    /**
     * 回答详细度（普通对话单次最大输出）：设置中心可调，默认 2000。
     * 注意：函数调用链路保持固定 4096（写工具参数需要更长输出），不走本方法。
     */
    protected int chatMaxTokens() {
        try {
            if (aiConfigService != null) {
                return aiConfigService.getChatMaxTokens();
            }
        } catch (Exception ignore) {
        }
        return 2000;
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
        return chat(systemPrompt, userMessage, null);
    }

    @Override
    public String chat(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        return doChat(systemPrompt, userMessage, history, false);
    }

    /**
     * 结构化输出：要求模型按 JSON 返回。
     * 请求体声明 response_format={"type":"json_object"}（OpenAI 兼容协议）；
     * 个别模型/服务商不支持该声明时会报错——此时回退普通对话再试一次，
     * 提示词本身已要求 JSON、调用方另有解析兜底，双重保险。
     */
    @Override
    public String chatJson(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        try {
            return doChat(systemPrompt, userMessage, history, true);
        } catch (BusinessException e) {
            log.warn("[AI适配器] JSON输出模式调用失败，回退普通对话: {}", e.getMessage());
            return doChat(systemPrompt, userMessage, history, false);
        }
    }

    private String doChat(String systemPrompt, String userMessage, List<Map<String, String>> history, boolean jsonMode) {
        if (!isEnabled()) {
            throw new BusinessException("AI渠道 " + getChannel() + " 未启用，请先配置API Key");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new BusinessException("用户输入不能为空");
        }
        String url = getBaseUrl() + "/chat/completions";
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", maskOutbound(systemPrompt != null ? systemPrompt : "你是一个有用的助手"));
            messages.add(sysMsg);

            if (history != null && !history.isEmpty()) {
                for (Map<String, String> h : history) {
                    if (h == null) continue;
                    String role = h.get("role");
                    String content = h.get("content");
                    if (role == null || content == null) continue;
                    Map<String, String> msg = new HashMap<>();
                    msg.put("role", role);
                    msg.put("content", maskOutboundByRole(role, content));
                    messages.add(msg);
                }
            }

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            // 用户自己输入的内容原样外发（打码单向不可还原，打码后会被回写进业务单据）
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", getModel());
            body.put("messages", messages);
            body.put("temperature", chatTemperature());
            body.put("max_tokens", chatMaxTokens());
            if (jsonMode) {
                // 结构化输出：OpenAI 兼容协议的 JSON 声明（不支持的服务商会报错，由 chatJson 回退兜底）
                body.put("response_format", Map.of("type", "json_object"));
            }
            applyExtraParams(body);
            String json = mapper.writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client().newCall(request).execute()) {
                if (response.body() == null) {
                    throw new BusinessException("AI服务返回空响应");
                }
                String respJson = response.body().string();
                if (!response.isSuccessful()) {
                    log.error("AI调用失败: {} - {}", response.code(), respJson);
                    String errorMsg = extractErrorFromResponse(respJson);
                    throw new BusinessException("AI服务调用失败(" + response.code() + "): " + errorMsg);
                }
                Map<?, ?> resp = mapper.readValue(respJson, Map.class);
                collectUsage(resp);
                List<?> choices = (List<?>) resp.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                    Map<?, ?> message = (Map<?, ?>) choice.get("message");
                    if (message != null && message.get("content") != null) {
                        return (String) message.get("content");
                    }
                }
                throw new BusinessException("AI返回内容为空");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI调用异常 - channel={}, model={}", getChannel(), getModel(), e);
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new BusinessException("AI服务异常: " + errMsg);
        }
    }

    /**
     * 从AI错误响应中提取可读的错误信息
     */
    private String extractErrorFromResponse(String respJson) {
        try {
            Map<?, ?> resp = mapper.readValue(respJson, Map.class);
            Object error = resp.get("error");
            if (error instanceof Map) {
                Map<?, ?> errMap = (Map<?, ?>) error;
                String msg = (String) errMap.get("message");
                if (msg != null) return msg;
                String type = (String) errMap.get("type");
                if (type != null) return type;
            } else if (error instanceof String) {
                return (String) error;
            }
            String msg = (String) resp.get("message");
            if (msg != null) return msg;
            return "请求失败";
        } catch (Exception e) {
            return respJson.length() > 200 ? respJson.substring(0, 200) : respJson;
        }
    }

    /**
     * 从OpenAI兼容响应中提取usage并计入线程级用量收集器。
     * 通义千问/DeepSeek/Ollama均按OpenAI协议返回 usage.prompt_tokens/completion_tokens/total_tokens；
     * 缓存命中取 DeepSeek 的 usage.prompt_cache_hit_tokens，取不到再看 OpenAI 通用口径
     * usage.prompt_tokens_details.cached_tokens（两者都没有则记 0）。
     * 渠道未返回usage或统计失败时静默跳过，不影响主流程。
     */
    private void collectUsage(Map<?, ?> resp) {
        try {
            Object usageObj = resp.get("usage");
            if (usageObj instanceof Map<?, ?> usage) {
                long cacheHit = toLong(usage.get("prompt_cache_hit_tokens"));
                if (cacheHit <= 0) {
                    Object details = usage.get("prompt_tokens_details");
                    if (details instanceof Map<?, ?> d) {
                        cacheHit = toLong(d.get("cached_tokens"));
                    }
                }
                AiUsageCollector.add(
                        toLong(usage.get("prompt_tokens")),
                        toLong(usage.get("completion_tokens")),
                        toLong(usage.get("total_tokens")),
                        cacheHit);
            }
        } catch (Exception ignore) {
            // usage统计失败不影响AI主流程
        }
    }

    private long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    @Override
    public Map<String, Object> chatWithTools(String systemPrompt, String userMessage,
                                              List<Map<String, Object>> messages,
                                              List<Map<String, Object>> tools) {
        return chatWithTools(systemPrompt, userMessage, messages, tools, null);
    }

    /**
     * 函数调用主入口（2026-10-14 增加流式分档）：
     * 传了 reasoningDeltaCallback → 流式优先，模型思考期间逐段回调推理内容
     * （小助手界面"分步思考"实时展示 + 前端看门狗持续续期不再误报 90 秒超时）；
     * 流式失败自动回退非流式（兼容不支持 stream 的第三方服务商），回退后行为与旧版完全一致。
     * 不传回调（语音链路等）→ 直接非流式，与旧版行为一致。
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> chatWithTools(String systemPrompt, String userMessage,
                                              List<Map<String, Object>> messages,
                                              List<Map<String, Object>> tools,
                                              java.util.function.Consumer<String> reasoningDeltaCallback) {
        if (!isEnabled()) {
            throw new BusinessException("AI渠道 " + getChannel() + " 未启用，请先配置API Key");
        }
        if (reasoningDeltaCallback == null) {
            return chatWithToolsBlocking(systemPrompt, userMessage, messages, tools);
        }
        try {
            return chatWithToolsStreaming(systemPrompt, userMessage, messages, tools, reasoningDeltaCallback);
        } catch (Exception streamErr) {
            // 回退一次非流式：某些网关/老版本服务对 stream 参数报错，或流中断；
            // 回退后若上游本身有问题（key 无效/400）会在这里抛出真实错误。
            log.warn("[FunctionCall] 流式调用失败，回退非流式重试 - channel={}, model={}, 原因: {}",
                    getChannel(), getModel(), streamErr.getMessage());
            return chatWithToolsBlocking(systemPrompt, userMessage, messages, tools);
        }
    }

    /** 组装消息列表（system + 历史 + 本轮用户消息，出站统一脱敏）——流式与非流式共用 */
    private List<Map<String, Object>> buildChatMessages(String systemPrompt, String userMessage,
                                                         List<Map<String, Object>> messages) {
        List<Map<String, Object>> msgList = new ArrayList<>();
        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", maskOutbound(systemPrompt != null ? systemPrompt : "你是一个有用的助手"));
        msgList.add(sysMsg);

        if (messages != null && !messages.isEmpty()) {
            for (Map<String, Object> h : messages) {
                if (h == null) continue;
                Map<String, Object> msg = new HashMap<>(h);
                // 数据安全：只对工具结果（数据库数据）脱敏；用户输入与模型回复原样外发，
                // 避免打码值被模型回写进业务单据（打码单向不可还原，2026-11-17 方案A）
                Object content = msg.get("content");
                if (content instanceof String s) {
                    msg.put("content", maskOutboundByRole(String.valueOf(msg.get("role")), s));
                }
                msgList.add(msg);
            }
        }

        if (userMessage != null && !userMessage.isBlank()) {
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            msgList.add(userMsg);
        }
        return msgList;
    }

    /** 函数调用公共请求体（流式与非流式共用；stream 参数由各自分支自行附加） */
    private Map<String, Object> buildChatBody(List<Map<String, Object>> msgList,
                                               List<Map<String, Object>> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", getModel());
        body.put("messages", msgList);
        body.put("temperature", chatTemperature());
        // 工具调用场景：report_create_custom 等写工具参数 JSON 较复杂（多关联+多指标），
        // 参数被截断时模型会返回空/残缺参数（finish_reason=length），用户看到"缺少参数"。
        // DeepSeek 输出上限 8192，这里对 deepseek 放开到 8192，其它服务商保守 4096。
        // （不随"回答详细度"设置变化，避免高详细度挤占工具参数空间）
        int maxOut = 4096;
        String modelName = getModel() == null ? "" : getModel().toLowerCase();
        if (modelName.contains("deepseek")) {
            maxOut = 8192;
        }
        // 思考强度=极高（2026-11-22 三档）：单轮输出上限翻倍（各模型已有上限内取翻倍，DeepSeek 保持 8192），
        // 让复杂长任务的答复/写工具参数一次写完；light/high 走默认，行为与升级前一致
        if ("max".equals(com.erp.ai.util.AiThoughtContextHolder.get())) {
            maxOut = Math.min(maxOut * 2, 8192);
        }
        body.put("max_tokens", maxOut);
        // 联网搜索等附加参数（设置中心开启 + 服务商支持时才附加）
        applyExtraParams(body);

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    /** 非流式函数调用（旧逻辑原样保留：一次性等完整响应） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> chatWithToolsBlocking(String systemPrompt, String userMessage,
                                                      List<Map<String, Object>> messages,
                                                      List<Map<String, Object>> tools) {
        String url = getBaseUrl() + "/chat/completions";
        try {
            List<Map<String, Object>> msgList = buildChatMessages(systemPrompt, userMessage, messages);

            Map<String, Object> body = buildChatBody(msgList, tools);

            String json = mapper.writeValueAsString(body);
            if (tools != null && !tools.isEmpty()) {
                log.debug("[FunctionCall] 请求body: {}", json.substring(0, Math.min(8000, json.length())));
            }
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client().newCall(request).execute()) {
                if (response.body() == null) {
                    throw new BusinessException("AI服务返回空响应");
                }
                String respJson = response.body().string();
                if (!response.isSuccessful()) {
                    log.error("AI调用失败: {} - {}", response.code(), respJson);
                    String errorMsg = extractErrorFromResponse(respJson);
                    throw new BusinessException("AI服务调用失败(" + response.code() + "): " + errorMsg);
                }
                log.debug("[FunctionCall] 响应body: {}", respJson.substring(0, Math.min(8000, respJson.length())));

                Map<String, Object> result = new HashMap<>();
                Map<?, ?> resp = mapper.readValue(respJson, Map.class);
                collectUsage(resp);
                List<?> choices = (List<?>) resp.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                    Map<?, ?> message = (Map<?, ?>) choice.get("message");
                    if (message != null) {
                        String content = (String) message.get("content");
                        result.put("content", content);

                        // DeepSeek 思考模式（如 deepseek-flash）：带 tool_calls 的 assistant 消息必须把
                        // reasoning_content 原样回传，否则下一轮请求会被上游 400 拒绝：
                        // "The `reasoning_content` in the thinking mode must be passed back to the API."
                        // 历史问题：此前只取 content，导致"读文档→查字段→建单"这类需要 2 轮以上工具调用的
                        // 场景在第二轮整个失败。这里原样透出，由 Agent 写入会话历史。
                        Object reasoningContent = message.get("reasoning_content");
                        if (reasoningContent != null) {
                            result.put("reasoning_content", reasoningContent);
                        }

                        List<?> toolCalls = (List<?>) message.get("tool_calls");
                        if (toolCalls != null && !toolCalls.isEmpty()) {
                            List<Map<String, Object>> callList = new ArrayList<>();
                            for (Object tc : toolCalls) {
                                if (tc instanceof Map) {
                                    Map<?, ?> tcMap = (Map<?, ?>) tc;
                                    Map<String, Object> callInfo = new HashMap<>();
                                    callInfo.put("id", tcMap.get("id"));
                                    callInfo.put("type", tcMap.get("type"));
                                    Map<?, ?> func = (Map<?, ?>) tcMap.get("function");
                                    if (func != null) {
                                        callInfo.put("name", func.get("name"));
                                        callInfo.put("arguments", func.get("arguments"));
                                    }
                                    callList.add(callInfo);
                                }
                            }
                            result.put("tool_calls", callList);
                        }
                    }
                    result.put("finish_reason", choice.get("finish_reason"));
                }
                return result;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI函数调用异常 - channel={}, model={}", getChannel(), getModel(), e);
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new BusinessException("AI函数调用异常: " + errMsg);
        }
    }

    /**
     * 流式函数调用（OpenAI 兼容 SSE，2026-10-14）。
     * 与非流式返回完全一致的结果 Map（content / reasoning_content / tool_calls / finish_reason），另：
     * - 模型思考（reasoning_content）逐段回调 reasoningDeltaCallback（轻量节流：≥120ms 或 ≥200 字符），
     *   调用结束时把剩余缓冲冲刷出去；
     * - delta.tool_calls 按 index 聚合（id/name/arguments 逐段拼接），聚合完构造与非流式相同结构；
     * - usage 从带 usage 的 chunk 收集（stream_options.include_usage，DeepSeek/Qwen 兼容）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> chatWithToolsStreaming(String systemPrompt, String userMessage,
                                                        List<Map<String, Object>> messages,
                                                        List<Map<String, Object>> tools,
                                                        java.util.function.Consumer<String> reasoningDeltaCallback) throws IOException {
        String url = getBaseUrl() + "/chat/completions";
        List<Map<String, Object>> msgList = buildChatMessages(systemPrompt, userMessage, messages);
        Map<String, Object> body = buildChatBody(msgList, tools);
        body.put("stream", true);
        // 最后一个 chunk 携带 usage（OpenAI 规范参数；不支持的网关会忽略该参数）
        Map<String, Object> streamOptions = new HashMap<>();
        streamOptions.put("include_usage", true);
        body.put("stream_options", streamOptions);

        String json = mapper.writeValueAsString(body);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        StringBuilder contentBuf = new StringBuilder();
        StringBuilder reasoningBuf = new StringBuilder();
        // 推理增量节流：距上次回调 ≥120ms 或待发段 ≥200 字符才回调一次；
        // 未回调的增量暂存 pendingDelta，下次与新增量合并发出（回调序列拼接 = 完整推理，不丢字）
        StringBuilder pendingDelta = new StringBuilder();
        long lastFlushAt = 0L;

        // tool_calls 按 index 聚合：index -> [id, name, argumentsBuf]
        Map<Integer, Map<String, Object>> tcAgg = new java.util.TreeMap<>();
        String finishReason = null;
        Map<String, Object> usageHolder = null;

        try (Response response = client().newCall(request).execute()) {
            if (response.body() == null) {
                throw new BusinessException("AI服务返回空响应");
            }
            if (!response.isSuccessful()) {
                String errBody = response.body().string();
                log.error("AI流式调用失败: {} - {}", response.code(), errBody);
                String errorMsg = extractErrorFromResponse(errBody);
                throw new BusinessException("AI服务调用失败(" + response.code() + "): " + errorMsg);
            }

            okhttp3.ResponseBody responseBody = response.body();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(responseBody.byteStream(), java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String payload = line.substring(5).trim();
                if (payload.isEmpty()) continue;
                if ("[DONE]".equals(payload)) break;
                Map<?, ?> chunk;
                try {
                    chunk = mapper.readValue(payload, Map.class);
                } catch (Exception parseErr) {
                    continue; // 心跳/注释行等非 JSON 数据直接跳过
                }
                Object usageObj = chunk.get("usage");
                if (usageObj instanceof Map) {
                    usageHolder = (Map<String, Object>) usageObj;
                }
                List<?> choices = (List<?>) chunk.get("choices");
                if (choices == null || choices.isEmpty()) continue;
                Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                Object fr = choice.get("finish_reason");
                if (fr != null) finishReason = String.valueOf(fr);
                Map<?, ?> delta = (Map<?, ?>) choice.get("delta");
                if (delta == null) continue;

                Object rc = delta.get("reasoning_content");
                if (rc != null && !String.valueOf(rc).isEmpty()) {
                    String rcText = String.valueOf(rc);
                    reasoningBuf.append(rcText);
                    pendingDelta.append(rcText);
                    long now = System.currentTimeMillis();
                    if (now - lastFlushAt >= 120 || pendingDelta.length() >= 200) {
                        reasoningDeltaCallback.accept(pendingDelta.toString());
                        pendingDelta.setLength(0);
                        lastFlushAt = now;
                    }
                }

                Object dc = delta.get("content");
                if (dc != null && !String.valueOf(dc).isEmpty()) {
                    contentBuf.append(dc);
                }

                List<?> dTcs = (List<?>) delta.get("tool_calls");
                if (dTcs != null) {
                    for (Object o : dTcs) {
                        if (!(o instanceof Map)) continue;
                        Map<?, ?> tc = (Map<?, ?>) o;
                        int idx = (int) toLong(tc.get("index"));
                        Map<String, Object> agg = tcAgg.computeIfAbsent(idx, k -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", "");
                            m.put("name", "");
                            m.put("arguments", new StringBuilder());
                            return m;
                        });
                        if (tc.get("id") != null && !String.valueOf(tc.get("id")).isEmpty()) {
                            agg.put("id", String.valueOf(tc.get("id")));
                        }
                        Map<?, ?> func = (Map<?, ?>) tc.get("function");
                        if (func != null) {
                            if (func.get("name") != null && !String.valueOf(func.get("name")).isEmpty()) {
                                agg.put("name", String.valueOf(func.get("name")));
                            }
                            Object args = func.get("arguments");
                            if (args != null) {
                                ((StringBuilder) agg.get("arguments")).append(args);
                            }
                        }
                    }
                }
            }
        } finally {
            // 结束（含异常中断）时冲刷节流窗口里剩余的推理增量，保证界面拿到已生成的全部思考文本
            if (pendingDelta.length() > 0) {
                try {
                    reasoningDeltaCallback.accept(pendingDelta.toString());
                } catch (Exception ignore) {
                    // 回调失败不影响结果返回
                }
                pendingDelta.setLength(0);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", contentBuf.length() > 0 ? contentBuf.toString() : null);
        if (reasoningBuf.length() > 0) {
            result.put("reasoning_content", reasoningBuf.toString());
        }
        if (!tcAgg.isEmpty()) {
            List<Map<String, Object>> callList = new ArrayList<>();
            int seq = 0;
            for (Map<String, Object> agg : tcAgg.values()) {
                Map<String, Object> callInfo = new HashMap<>();
                String id = (String) agg.get("id");
                callInfo.put("id", id != null && !id.isEmpty() ? id : ("call_" + seq));
                callInfo.put("type", "function");
                // 结构必须与非流式解析完全一致：name/arguments 平铺在 callInfo 顶层
                // （历史坑：嵌套在 function 里 → Agent 读 name/arguments 拿到 null 写进会话历史 →
                //   第 2 轮请求被上游 422 "invalid type: null, expected a string" 拒绝）
                callInfo.put("name", agg.get("name"));
                callInfo.put("arguments", ((StringBuilder) agg.get("arguments")).toString());
                callList.add(callInfo);
                seq++;
            }
            result.put("tool_calls", callList);
        }
        result.put("finish_reason", finishReason);
        if (usageHolder != null) {
            collectUsage(usageHolder);
        }
        log.info("[FunctionCall] 流式完成 - channel={}, model={}, content={}字, reasoning={}字, toolCalls={}",
                getChannel(), getModel(), contentBuf.length(), reasoningBuf.length(), tcAgg.size());
        return result;
    }

    @Override
    public String visionChat(String systemPrompt, String userMessage, List<String> imageUrls) {
        if (!isEnabled()) {
            throw new BusinessException("AI渠道 " + getChannel() + " 未启用");
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            return chat(systemPrompt, userMessage);
        }
        String url = getBaseUrl() + "/chat/completions";
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", maskOutbound(systemPrompt != null ? systemPrompt : "你是一个有用的助手"));
            messages.add(sysMsg);

            List<Map<String, Object>> contentList = new ArrayList<>();
            Map<String, Object> textMsg = new HashMap<>();
            textMsg.put("type", "text");
            // 用户输入原样外发（打码单向不可还原，打码后会被模型回写进业务单据）
            textMsg.put("text", userMessage != null ? userMessage : "");
            contentList.add(textMsg);
            for (String imgUrl : imageUrls) {
                if (imgUrl == null) continue;
                Map<String, Object> imageUrlMap = new HashMap<>();
                imageUrlMap.put("url", imgUrl);
                Map<String, Object> imgMsg = new HashMap<>();
                imgMsg.put("type", "image_url");
                imgMsg.put("image_url", imageUrlMap);
                contentList.add(imgMsg);
            }
            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", contentList);
            messages.add(userMsg);

            String visionModel = getVisionModel();
            Map<String, Object> body = new HashMap<>();
            body.put("model", visionModel);
            body.put("messages", messages);
            body.put("temperature", 0.3);
            // 图片识别输出拉满（2026-10-21）：原 2000 会被模型的思考内容吃掉导致正文截断
            body.put("max_tokens", 8192);
            String json = mapper.writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .build();

            try (Response response = client().newCall(request).execute()) {
                if (response.body() == null) {
                    throw new BusinessException("图片识别服务返回空响应");
                }
                if (!response.isSuccessful()) {
                    String err = response.body().string();
                    log.error("视觉AI调用失败: {} - {}", response.code(), err);
                    String errorMsg = extractErrorFromResponse(err);
                    throw new BusinessException("图片识别服务调用失败(" + response.code() + "): " + errorMsg);
                }
                String respJson = response.body().string();
                Map<?, ?> resp = mapper.readValue(respJson, Map.class);
                collectUsage(resp);
                List<?> choices = (List<?>) resp.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                    Map<?, ?> message = (Map<?, ?>) choice.get("message");
                    if (message != null && message.get("content") != null) {
                        return (String) message.get("content");
                    }
                }
                throw new BusinessException("图片识别返回内容为空");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("视觉AI调用异常", e);
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new BusinessException("图片识别服务异常: " + errMsg);
        }
    }

    @Override
    public String getModelName() {
        return getModel();
    }

    /**
     * 图片识别使用的模型。
     * <p>
     * 默认 = 当前渠道所选模型（"我选哪个模型就用哪个模型"）：DeepSeek 等渠道只要把模型设成
     * 支持图片的模型（如 deepseek-flash）即可直接识图，不再被写死的模型名挡住。
     * 单独配置了视觉模型的渠道（通义千问 qwen-vl 系列 / 本地 Ollama / 自定义服务商的"视觉模型"）
     * 仍由各自子类覆盖本方法，保持原有行为。
     */
    protected String getVisionModel() {
        return getModel();
    }
}
