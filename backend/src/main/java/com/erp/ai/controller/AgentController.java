package com.erp.ai.controller;

import com.erp.ai.agent.AgentStep;
import com.erp.ai.agent.ReActAgentService;
import com.erp.ai.bill.BillCodeResolver;
import com.erp.ai.dto.AiDataScope;
import com.erp.ai.dto.UnifiedRequest;
import com.erp.ai.service.AiConfirmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.Result;
import com.erp.tenant.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AgentController {

    private final ReActAgentService reActAgentService;
    private final AiConfirmService aiConfirmService;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    @GetMapping(value = "/chat/agent")
    public SseEmitter chatAgentGet(
            @RequestParam String message,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Boolean deepThinking,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        UnifiedRequest request = new UnifiedRequest();
        request.setUserInput(message);
        request.setChannel(channel);
        request.setSessionId(sessionId);
        request.setErpUserId(userId);
        request.setDeepThinking(Boolean.TRUE.equals(deepThinking));
        return executeAgent(request, httpRequest, httpResponse);
    }

    @PostMapping(value = "/chat/agent")
    public SseEmitter chatAgent(@RequestBody UnifiedRequest request, HttpServletRequest httpRequest,
                                HttpServletResponse httpResponse) {
        return executeAgent(request, httpRequest, httpResponse);
    }

    private SseEmitter executeAgent(UnifiedRequest request, HttpServletRequest httpRequest,
                                    HttpServletResponse httpResponse) {
        // 关闭中间反向代理（nginx）对响应流的缓冲，保证「产生一步推一步」真的逐步到达调用方
        // （否则代理会先把整轮攒在缓冲区，调用方看到的仍是一次性结果）
        httpResponse.setHeader("X-Accel-Buffering", "no");
        httpResponse.setHeader("Cache-Control", "no-cache, no-transform");
        SseEmitter emitter = new SseEmitter(300000L);
        // 数据权限上下文：服务端从认证上下文构建，绝不信任前端传参
        AiDataScope scope = AiDataScope.from(httpRequest);
        if (scope.getUserId() != null) {
            request.setErpUserId(scope.getUserId());
        }
        // 展示口径偏好（单据标识方式）：纯文案，无权限语义
        applyDisplayPrefs(scope, request.getContext());

        // 线程池线程不带请求线程的租户上下文（ThreadLocal 不继承），必须显式透传，
        // 否则 Agent 内的查库工具会落到 default 数据源（= 租户1的库）
        final Long tenantId = TenantContextHolder.getTenantId();
        final String tenantDbName = TenantContextHolder.getTenantDbName();

        executor.submit(() -> {
            if (tenantId != null) {
                TenantContextHolder.setTenantId(tenantId);
            }
            if (tenantDbName != null) {
                TenantContextHolder.setTenantDbName(tenantDbName);
            }
            try {
                reActAgentService.runAgent(request, scope, step -> {
                    try {
                        String json = mapper.writeValueAsString(step);
                        emitter.send(SseEmitter.event()
                                .name("step")
                                .data(json));
                    } catch (IOException e) {
                        log.warn("[Agent SSE] 发送步骤失败，连接可能已关闭", e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("[Agent SSE] 执行失败", e);
                try {
                    AgentStep errorStep = AgentStep.error("执行失败: " + e.getMessage());
                    String json = mapper.writeValueAsString(errorStep);
                    emitter.send(SseEmitter.event().name("step").data(json));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                // 线程池线程会被复用，必须清理上下文，否则残留会污染下一个任务
                TenantContextHolder.clear();
            }
        });

        emitter.onCompletion(() -> log.debug("[Agent SSE] 连接完成"));
        emitter.onTimeout(() -> log.warn("[Agent SSE] 连接超时"));
        emitter.onError(e -> log.warn("[Agent SSE] 连接错误: {}", e.getMessage()));

        return emitter;
    }

    @PostMapping("/chat/agent/sync")
    public List<AgentStep> chatAgentSync(@RequestBody UnifiedRequest request, HttpServletRequest httpRequest) {
        AiDataScope scope = AiDataScope.from(httpRequest);
        if (scope.getUserId() != null) {
            request.setErpUserId(scope.getUserId());
        }
        applyDisplayPrefs(scope, request.getContext());
        return reActAgentService.runAgent(request, scope, null);
    }

    /**
     * 注入用户选择的「单据标识方式」（小助手 → ⚙️ 设置）。
     *
     * <p>该偏好只决定确认卡片/执行结果/语音播报里的单据怎么写（单号 / 客户名 / 单号+客户名），
     * 不参与身份与数据权限判定；取值白名单外一律忽略（保持服务端默认=单号）。
     */
    @SuppressWarnings("unchecked")
    private void applyDisplayPrefs(AiDataScope scope, Map<String, Object> context) {
        if (scope == null || context == null) return;
        Object prefs = context.get("displayPrefs");
        if (!(prefs instanceof Map)) return;
        Map<String, Object> raw = (Map<String, Object>) prefs;
        String style = raw.get("billLabelStyle") == null ? null : String.valueOf(raw.get("billLabelStyle")).trim();
        if (style == null || !BillCodeResolver.allowedStyles().contains(style)) return;
        Map<String, String> clean = new LinkedHashMap<>();
        clean.put(BillCodeResolver.PREF_KEY_BILL_LABEL_STYLE, style);
        scope.setDisplayPrefs(clean);
    }

    /**
     * 确认执行待确认的写操作（两段式确认的第二次提交）。
     * 服务端二次校验：用户一致性 + 工具权限开关；token一次性，确认后即失效。
     */
    @PostMapping("/chat/confirm-tool")
    public Result<Map<String, Object>> confirmTool(@RequestBody Map<String, String> body,
                                                   HttpServletRequest httpRequest) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return Result.error("缺少确认令牌");
        }
        AiDataScope scope = AiDataScope.from(httpRequest);
        return Result.success(aiConfirmService.confirm(token, scope));
    }

    /** 取消待确认的写操作（不执行任何数据变更） */
    @PostMapping("/chat/cancel-tool")
    public Result<Map<String, Object>> cancelTool(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return Result.error("缺少确认令牌");
        }
        return Result.success(aiConfirmService.cancel(token));
    }
}
