package com.erp.kai.controller;

import com.erp.common.Result;
import com.erp.kai.service.KaiChatFileService;
import com.erp.kai.service.agent.KaiAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 小楷独立版 · 对话 / 确认 / 会话管理（FEAT-KAI-STANDALONE-001 / 007）。
 * 同步对话（返回完整步骤列表，前端按 Trae 式顺序渲染）；
 * 会话持久化：列表 / 新建 / 重命名 / 置顶 / 删除，历史从库恢复。
 */
@Slf4j
@RestController
@RequestMapping("/api/kai-standalone")
@RequiredArgsConstructor
public class KaiAgentController {

    private final KaiAgentService agentService;
    private final KaiChatFileService chatFileService;

    private final ObjectMapper mapper = new ObjectMapper();
    /** 流式对话线程池（每轮对话一个线程；SSE 长连接不占用 Web 容器线程） */
    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(8);

    /** 聊天上传文件/图片：图片即识别即扣积分（用客户所选模型），文档提取文字；暂存 30 分钟，返回文件标识供接入工具使用。 */
    @PostMapping("/chat/upload")
    public Result<?> upload(HttpServletRequest request, @RequestParam("file") MultipartFile file,
                            @RequestParam(value = "modelProviderId", required = false) Long modelProviderId) {
        try {
            Long kaiUserId = kaiUserId(request);
            KaiChatFileService.SaveResult saved = chatFileService.save(kaiUserId, file, modelProviderId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("fileToken", saved.token());
            out.put("fileName", saved.fileName());
            out.put("kind", saved.kind());
            out.put("credits", saved.credits());
            out.put("totalTokens", saved.totalTokens());
            out.put("warning", saved.warning());
            out.put("hint", "IMAGE".equals(saved.kind())
                    ? (saved.warning() == null
                        ? "图片已识别，可直接就图片内容继续提问"
                        : "图片已收下，但本站没能识别内容；发送后会交给接入的系统看原图")
                    : "文件已就绪（30 分钟内有效）。发送\"接入这个系统\"即可让小楷自动分析并接入");
            return Result.success(out);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 上下文窗口覆盖解析（2026-11-22 三档）：空/非数字按 null 处理（走模型窗口默认），范围校验在服务层 */
    private Integer parseContextLimit(Object raw) {
        if (raw == null || str(raw).isBlank()) return null;
        try {
            return Integer.valueOf(str(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 发送一轮对话。body: {sessionId(会话库ID), message, connectionId?(空=纯聊天), modelProviderId?(空=默认模型)} */
    @PostMapping("/chat/sync")
    public Result<?> sync(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long kaiUserId = kaiUserId(request);
        Long sessionId = requireSessionId(body);
        String message = str(body.get("message"));
        Long connectionId = body.get("connectionId") == null || str(body.get("connectionId")).isBlank()
                ? null : Long.valueOf(body.get("connectionId").toString());
        Long modelProviderId = body.get("modelProviderId") == null || str(body.get("modelProviderId")).isBlank()
                ? null : Long.valueOf(body.get("modelProviderId").toString());
        // 按会话挂知识库：可空 = 全部启用中的库（默认口径）
        Long kbBaseId = body.get("kbBaseId") == null || str(body.get("kbBaseId")).isBlank()
                ? null : Long.valueOf(body.get("kbBaseId").toString());
        // 深度思考开关（客户显式选择，2026-10-19）：true 才按倍率计费
        boolean deepThinking = body.get("deepThinking") != null
                && ("true".equalsIgnoreCase(str(body.get("deepThinking"))) || "1".equals(str(body.get("deepThinking"))));
        String answerMode = "summary".equalsIgnoreCase(str(body.get("answerMode"))) ? "summary" : "detail";
        // 思考强度三档 + 上下文窗口覆盖（2026-11-22 照参考设计）：旧前端不传 = 行为与升级前一致
        String thoughtLevel = str(body.get("thoughtLevel"));
        Integer contextLimitOverride = parseContextLimit(body.get("contextLimit"));
        try {
            List<Map<String, Object>> steps = agentService.chat(kaiUserId,
                    String.valueOf(request.getAttribute("kaiDisplayName")), sessionId, message, connectionId,
                    modelProviderId, kbBaseId, deepThinking, answerMode, thoughtLevel, contextLimitOverride);
            return Result.success(steps);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 流式对话：边执行边推送步骤（思考 / 调用工具 / 观察结果），前端逐条渲染。
     * 与 /chat/sync 同一套业务逻辑，只是把"攒完一次性返回"改为"产生即推送"。
     * 事件：event=step，data=单个步骤 JSON；结束即关闭连接。
     */
    @PostMapping("/chat/stream")
    public SseEmitter stream(HttpServletRequest request, HttpServletResponse response,
                             @RequestBody Map<String, Object> body) {
        // 关闭中间反向代理（nginx）对响应流的缓冲，否则后端「产生一步推一步」会被代理攒成一次性下发，
        // 用户看到的仍是「憋到最后一次性弹出」，与流式的目的相违背
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setCharacterEncoding("UTF-8");
        SseEmitter emitter = new SseEmitter(300000L);
        // request 上下文只能在请求线程取，异步线程里拿不到
        final Long kaiUserId = kaiUserId(request);
        final String displayName = String.valueOf(request.getAttribute("kaiDisplayName"));
        final Long sessionId = requireSessionId(body);
        final String message = str(body.get("message"));
        final Long connectionId = body.get("connectionId") == null || str(body.get("connectionId")).isBlank()
                ? null : Long.valueOf(body.get("connectionId").toString());
        final Long modelProviderId = body.get("modelProviderId") == null || str(body.get("modelProviderId")).isBlank()
                ? null : Long.valueOf(body.get("modelProviderId").toString());
        final Long kbBaseId = body.get("kbBaseId") == null || str(body.get("kbBaseId")).isBlank()
                ? null : Long.valueOf(body.get("kbBaseId").toString());
        final boolean deepThinking = body.get("deepThinking") != null
                && ("true".equalsIgnoreCase(str(body.get("deepThinking"))) || "1".equals(str(body.get("deepThinking"))));
        // 答复呈现方式（2026-10-22）：detail=明细（默认，看执行过程）/ summary=汇总（只报结果）
        final String answerMode = "summary".equalsIgnoreCase(str(body.get("answerMode"))) ? "summary" : "detail";
        // 思考强度三档 + 上下文窗口覆盖（2026-11-22 照参考设计）：旧前端不传 = 行为与升级前一致
        final String thoughtLevel = str(body.get("thoughtLevel"));
        final Integer contextLimitOverride = parseContextLimit(body.get("contextLimit"));

        streamExecutor.submit(() -> {
            try {
                agentService.chat(kaiUserId, displayName, sessionId, message, connectionId,
                        modelProviderId, kbBaseId, deepThinking, answerMode, thoughtLevel, contextLimitOverride, step -> {
                            try {
                                emitter.send(SseEmitter.event().name("step")
                                        .data(mapper.writeValueAsString(step)));
                            } catch (Exception e) {
                                // 前端断开/网络抖动：不再写，但让后端继续跑完并落库
                                log.debug("[小楷独立版] 步骤推送失败: {}", e.getMessage());
                            }
                        });
                emitter.complete();
            } catch (Exception e) {
                log.error("[小楷独立版] 流式对话失败: {}", e.getMessage(), e);
                try {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("step", "ERROR");
                    err.put("text", "处理失败：" + e.getMessage());
                    emitter.send(SseEmitter.event().name("step").data(mapper.writeValueAsString(err)));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });
        emitter.onTimeout(() -> log.warn("[小楷独立版] 流式对话超时"));
        emitter.onError(e -> log.debug("[小楷独立版] 流式连接错误: {}", e.getMessage()));
        return emitter;
    }

    /** 确认执行写操作。body: {sessionId, confirmToken} */
    @PostMapping("/chat/confirm")
    public Result<?> confirm(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> out = agentService.confirm(kaiUserId(request), str(body.get("confirmToken")));
            return Result.success(out);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 取消写操作。body: {sessionId, confirmToken} */
    @PostMapping("/chat/cancel")
    public Result<?> cancel(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> out = agentService.cancel(kaiUserId(request), str(body.get("confirmToken")));
            return Result.success(out);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 会话历史（从库恢复，含完整步骤）。 */
    @GetMapping("/chat/history")
    public Result<?> history(HttpServletRequest request, @RequestParam Long sessionId) {
        return Result.success(agentService.history(kaiUserId(request), sessionId));
    }

    // ---------------- 会话管理（持久化） ----------------

    @GetMapping("/sessions")
    public Result<?> sessions(HttpServletRequest request) {
        return Result.success(agentService.listSessions(kaiUserId(request)));
    }

    @PostMapping("/sessions")
    public Result<?> createSession(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> body) {
        String title = body == null || body.get("title") == null ? "新对话" : str(body.get("title"));
        return Result.success(Map.of("id", agentService.createSession(kaiUserId(request), title)));
    }

    @PostMapping("/sessions/{id}/rename")
    public Result<?> rename(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            agentService.renameSession(kaiUserId(request), id, str(body.get("title")));
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/sessions/{id}/pin")
    public Result<?> pin(HttpServletRequest request, @PathVariable Long id, @RequestParam boolean pinned) {
        try {
            agentService.pinSession(kaiUserId(request), id, pinned);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/sessions/{id}")
    public Result<?> deleteSession(HttpServletRequest request, @PathVariable Long id) {
        try {
            agentService.clear(kaiUserId(request), id);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private Long requireSessionId(Map<String, Object> body) {
        Object v = body.get("sessionId");
        if (v == null || str(v).isBlank()) {
            throw new IllegalArgumentException("缺少会话标识，请先新建会话");
        }
        return Long.valueOf(str(v));
    }

    private Long kaiUserId(HttpServletRequest request) {
        Object v = request.getAttribute("kaiUserId");
        if (v == null) {
            throw new IllegalArgumentException("请先登录小楷");
        }
        return (Long) v;
    }
}
