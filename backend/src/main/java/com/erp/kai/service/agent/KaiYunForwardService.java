package com.erp.kai.service.agent;

import com.erp.kai.entity.KaiExtConnection;
import com.erp.kai.service.KaiChatFileService;
import com.erp.kai.service.KaiChatStore;
import com.erp.kai.service.KaiConnectionService;
import com.erp.kai.service.KaiCryptoService;
import com.erp.kai.service.KaiExtTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小楷独立版 · 楷煜满血转发（FEAT-KAI-STANDALONE-009）。
 *
 * 连接类型为 KAIYUN（楷煜 ERP（满血））时，对话不走独立版本地工具循环，
 * 而是把用户消息转发给楷煜 ERP 内小楷的同步对话接口（全部 419 个内部工具），
 * 楷煜返回的步骤流（THOUGHT/ACTION/OBSERVATION/CONFIRM_NEEDED/FINAL_ANSWER/ERROR）
 * 映射为独立站步骤后原样呈现 —— 用户在独立站获得与楷煜 ERP 内小楷一致的能力。
 *
 * 口径：
 * ① 凭据 = 楷煜登录账号密码（连接凭据 AES-GCM 加密落库），登录 → 选公司换带租户令牌，令牌按连接缓存、过期自动重登；
 * ② 写操作确认卡由楷煜侧生成，独立站点确认/取消时转发回楷煜执行（令牌一次性、5 分钟有效），结果落独立站任务留痕与会话历史；
 * ③ 用量如实口径：模型消耗在楷煜侧计量，独立站不重复计 token；
 * ④ 楷煜 ERP 主系统零改动，只调用其已有对外接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiYunForwardService {

    private final KaiCryptoService cryptoService;
    private final KaiChatStore chatStore;
    private final KaiExtTaskService taskService;
    private final KaiConnectionService connectionService;
    private final KaiChatFileService chatFileService;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 对话转发超时：楷煜侧 Agent 多轮思考，与主系统 SSE 300 秒口径一致 */
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(300);
    /** 快速接口（登录/选公司/确认/取消）超时 */
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(30);
    /** 文件转传超时：文件上传到楷煜，留足慢网余量 */
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(120);
    /** 用户消息里的上传文件/图片标记（前端发送时拼入，文件标识为独立站暂存 token） */
    private static final Pattern FORWARD_FILE_TAG =
            Pattern.compile("【用户上传了(文件|图片)：(.+?)（文件标识 ([0-9a-fA-F-]{8,64})）】");
    /** 令牌缓存时长（楷煜 JWT 有效期通常数小时，30 分钟 + 401 自动重登双保险） */
    private static final long TOKEN_TTL_MS = 30 * 60 * 1000L;
    /** 楷煜侧确认令牌有效期 5 分钟，本地转发待确认记录同步该口径 */
    private static final long FORWARD_PENDING_TTL_MS = 5 * 60 * 1000L;
    /** 独立站转发会话在楷煜侧的会话标识前缀（与楷煜内小楷的会话空间隔离） */
    private static final String SESSION_PREFIX = "kai-standalone-";

    /** 楷煜登录令牌缓存：连接 ID → 令牌（含过期时间） */
    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();
    /** 楷煜侧待确认动作：楷煜确认令牌 → 转发待确认记录 */
    private final Map<String, ForwardPending> forwards = new ConcurrentHashMap<>();

    // ---------------- 对外：连接测试 ----------------

    /**
     * 登录结果：带租户令牌 + 本次登录返回的公司清单 + 实际登录的公司名。
     * 草稿连接（尚未保存、无连接 ID）也必须能把公司清单带回去给前端选，
     * 所以清单不再依赖"按连接 ID 的缓存"（旧实现草稿连接永远返回空清单）。
     */
    private record LoginResult(String token, List<Map<String, Object>> tenants, String tenantName) {}

    /** 测试连接 = 真实登录 + 选公司；公司清单直接从本次登录结果返回，供前端回填公司下拉。 */
    public Map<String, Object> loginTest(KaiExtConnection conn) {
        LoginResult r = login(conn);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "连接成功，已登录楷煜 ERP" + tenantSuffix(r.tenantName()));
        out.put("tenants", r.tenants() == null ? Collections.emptyList() : r.tenants());
        out.put("tenantName", r.tenantName() == null ? "" : r.tenantName());
        out.put("hasToken", r.token() != null && !r.token().isBlank());
        return out;
    }

    // ---------------- 对外：对话转发 ----------------

    /** 处理一轮满血对话（同步版，兼容既有调用）。 */
    public List<Map<String, Object>> chat(Long kaiUserId, Long sessionId, String userInput,
                                          KaiExtConnection conn) {
        return chat(kaiUserId, sessionId, userInput, conn, null);
    }

    /**
     * 处理一轮满血对话：保存用户消息 → 转发楷煜 → 步骤映射 → 保存小楷回复。
     *
     * @param onStep 非空时走楷煜的流式对话接口：每产生一个步骤（思考/调用工具/观察结果）
     *               立刻回调，用户能边跑边看到过程，而不是等整轮结束一次性弹出。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> chat(Long kaiUserId, Long sessionId, String userInput,
                                          KaiExtConnection conn,
                                          java.util.function.Consumer<Map<String, Object>> onStep) {
        return chat(kaiUserId, sessionId, userInput, conn, "detail", onStep);
    }

    /** 答复呈现方式（2026-10-22）：summary = 只报结果，detail = 明细；随 context 一并转给楷煜小楷。 */
    public List<Map<String, Object>> chat(Long kaiUserId, Long sessionId, String userInput,
                                          KaiExtConnection conn, String answerMode,
                                          java.util.function.Consumer<Map<String, Object>> onStep) {
        return chat(kaiUserId, sessionId, userInput, conn, answerMode, null, null, onStep);
    }

    /**
     * 思考强度三档 + 上下文窗口覆盖（2026-11-22 照参考设计）：随请求转给楷煜小楷。
     * 楷煜侧为同一套代码，字段自动映射；旧版楷煜不认识这两个字段时按其默认行为，等同升级前。
     */
    public List<Map<String, Object>> chat(Long kaiUserId, Long sessionId, String userInput,
                                          KaiExtConnection conn, String answerMode,
                                          String thoughtLevel, Integer contextLimitOverride,
                                          java.util.function.Consumer<Map<String, Object>> onStep) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (userInput == null || userInput.isBlank()) {
            emit(steps, onStep, step("ERROR", "请输入您要处理的内容"));
            return steps;
        }
        chatStore.existsOrThrow(kaiUserId, sessionId);
        chatStore.saveUserMessage(sessionId, kaiUserId, userInput);
        try {
            String token = resolveToken(conn);
            // 上传文件/图片转传：独立站暂存的文件楷煜读不到，先转传到楷煜上传接口，
            // 再把附件清单（{name,url}）随请求带给楷煜 —— 楷煜 Agent 系统提示词按此注入"本次用户上传的附件"
            List<Map<String, String>> forwardFiles = new ArrayList<>();
            List<String> forwardImages = new ArrayList<>();
            attachForwardFiles(kaiUserId, conn, token, userInput, forwardFiles, forwardImages);
            Map<String, Object> body = bodyOf("userInput", userInput, "sessionId", SESSION_PREFIX + sessionId);
            if (!forwardFiles.isEmpty()) body.put("files", forwardFiles);
            if (!forwardImages.isEmpty()) body.put("imageUrls", forwardImages);
            // 答复呈现方式随上下文转给楷煜小楷（不传/非法值时楷煜按默认行为，等同升级前）
            Map<String, Object> forwardContext = new LinkedHashMap<>();
            forwardContext.put("answerMode", "summary".equalsIgnoreCase(answerMode) ? "summary" : "detail");
            body.put("context", forwardContext);
            // 思考强度三档 + 上下文窗口覆盖（2026-11-22）：顶层字段随请求转发，楷煜按同一套口径生效
            if (thoughtLevel != null && !thoughtLevel.isBlank()) body.put("thoughtLevel", thoughtLevel);
            if (contextLimitOverride != null) body.put("contextLimit", contextLimitOverride);

            if (onStep != null) {
                // 流式：逐步回调，前端边收边渲染
                steps = streamKaiyun(conn, body, token, kaiUserId, sessionId, onStep);
            } else {
                // 楷煜同步对话接口返回裸步骤数组（List<AgentStep>，无 Result 包装）；兼容 Result 包装以防协议调整
                Object resp = callKaiyun(conn, "/api/ai/chat/agent/sync",
                        body, token, CHAT_TIMEOUT);
                List<Map<String, Object>> agentSteps;
                if (resp instanceof List) {
                    agentSteps = (List<Map<String, Object>>) resp;
                } else if (resp instanceof Map) {
                    agentSteps = asMapList(((Map<String, Object>) resp).get("data"));
                } else {
                    agentSteps = Collections.emptyList();
                }
                steps = mapSteps(agentSteps, kaiUserId, sessionId, conn);
            }
            if (steps.isEmpty()) {
                emit(steps, onStep, step("ERROR", "楷煜 ERP 的小楷没有返回内容，请重试或换个说法"));
            }
        } catch (IllegalArgumentException e) {
            emit(steps, onStep, step("ERROR", e.getMessage()));
        } catch (Exception e) {
            log.error("[小楷独立版] 楷煜满血转发失败: {}", e.getMessage(), e);
            emit(steps, onStep, step("ERROR", "连接楷煜 ERP 失败：" + rootMessage(e)));
        }
        chatStore.saveBotMessage(sessionId, kaiUserId, steps);
        return steps;
    }

    /** 追加步骤并即时回调（回调失败不影响本轮对话）。 */
    private void emit(List<Map<String, Object>> steps,
                      java.util.function.Consumer<Map<String, Object>> onStep,
                      Map<String, Object> s) {
        steps.add(s);
        if (onStep != null) {
            try {
                onStep.accept(s);
            } catch (Exception e) {
                log.debug("[小楷独立版] 转发步骤回调失败: {}", e.getMessage());
            }
        }
    }

    /** 流式转发（401 自动重登一次）。 */
    private List<Map<String, Object>> streamKaiyun(KaiExtConnection conn, Map<String, Object> body,
                                                   String token, Long kaiUserId, Long sessionId,
                                                   java.util.function.Consumer<Map<String, Object>> onStep) throws Exception {
        try {
            return doStream(conn, body, token, kaiUserId, sessionId, onStep);
        } catch (TokenExpiredException e) {
            clearToken(conn);
            return doStream(conn, body, resolveToken(conn), kaiUserId, sessionId, onStep);
        }
    }

    /** 读楷煜 SSE（event: step / data: {AgentStep}），逐条映射并回调。 */
    private List<Map<String, Object>> doStream(KaiExtConnection conn, Map<String, Object> body, String token,
                                               Long kaiUserId, Long sessionId,
                                               java.util.function.Consumer<Map<String, Object>> onStep) throws Exception {
        String payload = mapper.writeValueAsString(body);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(baseOf(conn) + "/api/ai/chat/agent"))
                .timeout(CHAT_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (token != null && !token.isBlank()) {
            rb.header("Authorization", "Bearer " + token);
        }
        HttpResponse<java.util.stream.Stream<String>> resp = HttpClient.newHttpClient()
                .send(rb.build(), HttpResponse.BodyHandlers.ofLines());
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            throw new TokenExpiredException();
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalArgumentException("楷煜 ERP 返回异常（" + resp.statusCode() + "），请检查服务地址是否正确");
        }
        List<Map<String, Object>> collected = new ArrayList<>();
        try (java.util.stream.Stream<String> lines = resp.body()) {
            java.util.Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                String line = it.next();
                if (line == null || !line.startsWith("data:")) continue;
                String json = line.substring(5).trim();
                if (json.isEmpty()) continue;
                Map<String, Object> agentStep;
                try {
                    agentStep = mapper.readValue(json, Map.class);
                } catch (Exception ignore) {
                    continue;
                }
                for (Map<String, Object> m : mapSteps(Collections.singletonList(agentStep),
                        kaiUserId, sessionId, conn)) {
                    collected.add(m);
                    if (onStep != null) {
                        try {
                            onStep.accept(m);
                        } catch (Exception ignore) {
                            // 前端断开不应影响楷煜侧执行
                        }
                    }
                }
            }
        }
        return collected;
    }

    /** 楷煜步骤流 → 独立站步骤（确认卡登记转发待确认，供 confirmForward 接手）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapSteps(List<Map<String, Object>> agentSteps,
                                               Long kaiUserId, Long sessionId, KaiExtConnection conn) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (agentSteps == null) return steps;
        for (Map<String, Object> s : agentSteps) {
            String type = str(s.get("type"));
            switch (type) {
                case "THOUGHT" -> {
                    // 楷煜的思考文案已是人话（不暴露内部工具名），作为执行过程步骤展示
                    if (!str(s.get("content")).isBlank()) {
                        steps.add(step("ACTION", str(s.get("content"))));
                    }
                }
                case "ACTION" -> {
                    // 楷煜的 ACTION 只含内部工具名与参数，信息与思考步骤重复，跳过（避免英文工具名暴露给用户）
                }
                case "OBSERVATION" -> {
                    Map<String, Object> obs = step("OBSERVATION", observationText(s.get("toolResult")));
                    Object rows = unwrapRows(s.get("toolResult"));
                    if (rows != null) obs.put("data", rows);
                    // 透传主键/业务编码列元数据：前端据此隐藏内部主键列（与主系统小楷表现一致，2026-10-19）
                    if (s.get("toolResult") instanceof Map<?, ?> tr) {
                        if (tr.get("primaryKeyField") != null) obs.put("primaryKeyField", tr.get("primaryKeyField"));
                        if (tr.get("codeField") != null) obs.put("codeField", tr.get("codeField"));
                    }
                    steps.add(obs);
                }
                case "CONFIRM_NEEDED" -> {
                    String confirmToken = str(s.get("confirmToken"));
                    ForwardPending p = new ForwardPending(confirmToken, kaiUserId, sessionId, conn.getId(),
                            str(s.get("toolName")), str(s.get("content")),
                            System.currentTimeMillis() + FORWARD_PENDING_TTL_MS);
                    forwards.put(confirmToken, p);
                    Map<String, Object> confirm = step("CONFIRM_NEEDED", p.summary);
                    confirm.put("confirmToken", confirmToken);
                    confirm.put("tool", str(s.get("toolName")));
                    confirm.put("detail", s.get("toolParams"));
                    confirm.put("forwarded", true);
                    steps.add(confirm);
                }
                case "FINAL_ANSWER" -> steps.add(step("FINAL_ANSWER", str(s.get("content"))));
                case "ERROR" -> steps.add(step("ERROR", str(s.get("content"))));
                default -> { /* 未知步骤类型忽略，不影响其余步骤呈现 */ }
            }
        }
        return steps;
    }

    // ---------------- 上传文件/图片转传（楷煜满血） ----------------

    /**
     * 把用户消息里的上传标记转成楷煜侧真实附件：
     * 独立站暂存 token → 从暂存取原始字节 → 转传楷煜 /api/files/upload → 得到楷煜内地址，
     * 按类型分别收进 files（{name,url}）/ imageUrls（楷煜 Agent 系统提示词的附件/图片清单来源）。
     * 单个文件转传失败不阻断整轮对话（楷煜侧会按"附件缺失"自行回应），只留排查日志。
     */
    private void attachForwardFiles(Long kaiUserId, KaiExtConnection conn, String token, String userInput,
                                    List<Map<String, String>> outFiles, List<String> outImages) {
        if (userInput == null || userInput.indexOf("文件标识") < 0) return;
        Matcher m = FORWARD_FILE_TAG.matcher(userInput);
        while (m.find()) {
            String kind = m.group(1);
            String name = m.group(2);
            String fileToken = m.group(3);
            try {
                KaiChatFileService.Entry entry = chatFileService.own(kaiUserId, fileToken);
                byte[] content = entry.content();
                if (content == null || content.length == 0) {
                    log.warn("[小楷独立版] 楷煜转传跳过（暂存无原始字节，可能为升级前旧条目）: {}", fileToken);
                    continue;
                }
                String erpUrl = uploadFileToKaiyun(conn, token, entry.fileName(), content);
                if ("IMAGE".equals(kind)) {
                    outImages.add(erpUrl);
                } else {
                    Map<String, String> f = new LinkedHashMap<>();
                    f.put("name", entry.fileName() == null ? name : entry.fileName());
                    f.put("url", erpUrl);
                    outFiles.add(f);
                }
            } catch (TokenExpiredException e) {
                // 令牌失效：重登一次再转传本文件
                try {
                    clearToken(conn);
                    String fresh = resolveToken(conn);
                    KaiChatFileService.Entry entry = chatFileService.own(kaiUserId, fileToken);
                    String erpUrl = uploadFileToKaiyun(conn, fresh, entry.fileName(), entry.content());
                    if ("IMAGE".equals(kind)) outImages.add(erpUrl);
                    else outFiles.add(Map.of("name", entry.fileName() == null ? name : entry.fileName(), "url", erpUrl));
                } catch (Exception retry) {
                    log.warn("[小楷独立版] 楷煜文件转传失败（重登后仍失败）: token={}, {}", fileToken, rootMessage(retry));
                }
            } catch (Exception e) {
                log.warn("[小楷独立版] 楷煜文件转传失败: token={}, {}", fileToken, rootMessage(e));
            }
        }
    }

    /** 把文件字节上传到楷煜通用上传接口，返回楷煜内访问地址（/uploads/...）。 */
    private String uploadFileToKaiyun(KaiExtConnection conn, String token, String fileName, byte[] content) {
        String base = baseOf(conn);
        String boundary = "----KaiForward" + UUID.randomUUID().toString().replace("-", "");
        byte[] head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + asciiFileName(fileName) + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"type\"\r\n\r\nai-chat\r\n"
                + "--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        try {
            HttpRequest rb = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/api/files/upload"))
                    .timeout(UPLOAD_TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Bearer " + (token == null ? "" : token))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> resp = HttpClient.newHttpClient().send(rb, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                throw new TokenExpiredException();
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalArgumentException("楷煜 ERP 文件转传返回异常（" + resp.statusCode() + "）");
            }
            Map<String, Object> parsed = asMap(mapper.readValue(resp.body(), Object.class));
            Object code = parsed.get("code");
            if (code instanceof Number n && n.intValue() == 401) {
                throw new TokenExpiredException();
            }
            Map<String, Object> result = resultOf(parsed, "/api/files/upload");
            String url = str(result.get("data"));
            if (url.isBlank() || !url.startsWith("/uploads/")) {
                throw new IllegalArgumentException("楷煜 ERP 文件转传未返回有效地址");
            }
            return url;
        } catch (TokenExpiredException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("楷煜 ERP 文件转传失败：" + rootMessage(e));
        }
    }

    /**
     * multipart 头里的文件名只放 ASCII 安全值（楷煜按扩展名落盘，中文原名经附件清单 name 传递）：
     * 无扩展名的文本类兜底 .txt（独立站直读前已做过二进制拦截）。
     */
    private String asciiFileName(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            ext = name.substring(dot).toLowerCase();
            name = name.substring(0, dot);
        }
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
        if (safe.replaceAll("_", "").isBlank()) safe = "file_" + System.currentTimeMillis();
        if (safe.length() > 60) safe = safe.substring(0, 60);
        if (ext.isBlank()) ext = ".txt";
        return safe + ext;
    }

    // ---------------- 对外：确认 / 取消（转发回楷煜） ----------------

    /** 确认执行楷煜侧待确认写操作：转发楷煜确认接口，结果落任务留痕与对话历史。 */
    public Map<String, Object> confirmForward(Long kaiUserId, String confirmToken) {
        ForwardPending p = forwards.remove(confirmToken);
        if (p == null) {
            throw new IllegalArgumentException("没有找到待确认的操作（可能已超时或已处理）");
        }
        if (!p.kaiUserId.equals(kaiUserId)) {
            throw new IllegalArgumentException("只能确认自己的操作");
        }
        if (System.currentTimeMillis() > p.expireAt) {
            throw new IllegalArgumentException("该操作已超时（确认卡 5 分钟内有效），请重新发起");
        }
        KaiExtConnection conn = connectionService.getUsable(p.kaiUserId, p.connectionId);
        Map<String, Object> result;
        try {
            String token = resolveToken(conn);
            Map<String, Object> resp = resultOf(callKaiyun(conn, "/api/ai/chat/confirm-tool",
                    bodyOf("token", confirmToken), token, SHORT_TIMEOUT), "/api/ai/chat/confirm-tool");
            result = asMap(resp.get("data"));
            if (result.isEmpty()) result = Map.of("message", "已确认执行");
            taskService.log(kaiUserId, p.connectionId, p.tool,
                    "", p.summary, safeJson(result), "DONE");
        } catch (IllegalArgumentException e) {
            taskService.log(kaiUserId, p.connectionId, "", "", p.summary, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            taskService.log(kaiUserId, p.connectionId, "", "", p.summary, e.getMessage(), "FAILED");
            throw new IllegalArgumentException("确认执行失败：" + rootMessage(e));
        }
        // 执行结果作为一条小楷回复落库（历史完整可回看）
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("OBSERVATION", "已执行：" + p.summary));
        Object rows = unwrapRows(result);
        if (rows != null) steps.get(0).put("data", rows);
        steps.add(step("FINAL_ANSWER", "楷煜 ERP 已完成该操作，结果见上方明细"));
        chatStore.saveBotMessage(p.sessionId, kaiUserId, steps);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", p.summary);
        out.put("result", result);
        return out;
    }

    /** 取消楷煜侧待确认写操作：转发楷煜取消接口，不执行任何数据变更。 */
    public Map<String, Object> cancelForward(Long kaiUserId, String confirmToken) {
        ForwardPending p = forwards.remove(confirmToken);
        if (p == null) {
            throw new IllegalArgumentException("没有找到待确认的操作（可能已超时或已处理）");
        }
        if (!p.kaiUserId.equals(kaiUserId)) {
            throw new IllegalArgumentException("只能取消自己的操作");
        }
        try {
            KaiExtConnection conn = connectionService.getUsable(p.kaiUserId, p.connectionId);
            String token = resolveToken(conn);
            resultOf(callKaiyun(conn, "/api/ai/chat/cancel-tool", bodyOf("token", confirmToken), token, SHORT_TIMEOUT),
                    "/api/ai/chat/cancel-tool");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("取消失败：" + rootMessage(e));
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("FINAL_ANSWER", "已取消「" + p.summary + "」，没有执行任何操作"));
        chatStore.saveBotMessage(p.sessionId, kaiUserId, steps);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", p.summary);
        out.put("result", "已取消，没有执行任何操作");
        return out;
    }

    // ---------------- 楷煜登录与令牌 ----------------

    /** 取楷煜带租户令牌：缓存优先，未命中/过期则登录换发。草稿连接（无 ID）不缓存。 */
    private String resolveToken(KaiExtConnection conn) {
        if (conn.getId() != null) {
            CachedToken cached = tokenCache.get(conn.getId());
            if (cached != null && System.currentTimeMillis() < cached.expireAt) {
                return cached.token;
            }
        }
        LoginResult r = login(conn);
        String token = r.token();
        if (conn.getId() != null) {
            tokenCache.put(conn.getId(), new CachedToken(token, System.currentTimeMillis() + TOKEN_TTL_MS));
        }
        return token;
    }

    private void clearToken(KaiExtConnection conn) {
        if (conn.getId() != null) {
            tokenCache.remove(conn.getId());
        }
    }

    /**
     * 楷煜登录链：账号密码登录 →（按连接配置或第一个公司）选公司 → 换带租户令牌。
     * 返回本次登录的完整结果（令牌 + 公司清单 + 所选公司名），草稿连接同样可用。
     */
    private LoginResult login(KaiExtConnection conn) {
        Map<String, Object> cred = readCredential(conn);
        String base = baseOf(conn);
        Map<String, Object> loginResp = resultOf(postAny(base + "/api/auth/login",
                bodyOf("username", cred.get("username"), "password", cred.get("password")), null, SHORT_TIMEOUT,
                "/api/auth/login"), "/api/auth/login");
        Map<String, Object> data = asMap(loginResp.get("data"));
        String firstToken = str(data.get("token"));
        if (firstToken.isBlank()) {
            throw new IllegalArgumentException("楷煜 ERP 登录失败：未返回登录凭证，请检查服务地址");
        }
        List<Map<String, Object>> tenants = asMapList(data.get("tenants"));
        rememberTenants(conn, tenants);
        if (tenants.isEmpty()) {
            // 楷煜未返回公司清单（单租户部署）：直接使用首令牌
            return new LoginResult(firstToken, tenants, null);
        }
        Long tenantId = configuredTenantId(conn);
        if (tenantId == null) {
            tenantId = asLong(tenants.get(0).get("tenantId"));
        }
        Map<String, Object> selResp = resultOf(postAny(base + "/api/tenant/select",
                bodyOf("tenantId", tenantId), firstToken, SHORT_TIMEOUT, "/api/tenant/select"), "/api/tenant/select");
        Map<String, Object> selData = asMap(selResp.get("data"));
        String tenantToken = str(selData.get("token"));
        if (tenantToken.isBlank()) {
            throw new IllegalArgumentException("楷煜 ERP 选公司失败：请到「系统连接」编辑该连接填写公司后再试");
        }
        String selectedName = str(selData.get("tenantName"));
        if (selectedName.isBlank()) {
            // 楷煜选公司接口未回公司名时，从清单里按所选 ID 兜底取名称
            for (Map<String, Object> t : tenants) {
                if (Objects.equals(asLong(t.get("tenantId")), tenantId)) {
                    selectedName = str(t.get("tenantName"));
                    break;
                }
            }
        }
        rememberTenantName(conn, selectedName);
        return new LoginResult(tenantToken, tenants, selectedName);
    }

    /** 读取连接凭据（加密串 → JSON {username,password}）。 */
    private Map<String, Object> readCredential(KaiExtConnection conn) {
        if (conn.getCredentialEnc() == null || conn.getCredentialEnc().isBlank()) {
            throw new IllegalArgumentException("该连接还没有填写楷煜账号密码，请到「系统连接」编辑");
        }
        String plain;
        try {
            plain = cryptoService.decrypt(conn.getCredentialEnc());
        } catch (Exception e) {
            throw new IllegalArgumentException("连接凭据解密失败，请重新编辑连接填写楷煜账号密码");
        }
        try {
            Map<String, Object> cred = mapper.readValue(plain, Map.class);
            if (str(cred.get("username")).isBlank() || str(cred.get("password")).isBlank()) {
                throw new IllegalArgumentException("凭据缺少账号或密码");
            }
            return cred;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("该连接凭据不是账号密码格式，请重新编辑连接填写楷煜账号密码");
        }
    }

    // ---------------- 带认证的 HTTP 调用（401 自动重登一次） ----------------

    /**
     * 调楷煜接口（401 自动重登一次），返回解析后的原始响应对象：
     * 对话接口返回裸步骤数组（List）；Result 类接口返回 {code,message,data} Map。
     */
    private Object callKaiyun(KaiExtConnection conn, String path,
                              Map<String, Object> body, String token, Duration timeout) {
        String base = baseOf(conn);
        try {
            return postAny(base + path, body, token, timeout, path);
        } catch (TokenExpiredException e) {
            // 令牌失效：清缓存重登再试一次
            clearToken(conn);
            String fresh = resolveToken(conn);
            return postAny(base + path, body, fresh, timeout, path);
        }
    }

    /** Result 类接口：校验 code=200 后返回整个 Map（调用方取 data）。 */
    private Map<String, Object> resultOf(Object parsed, String path) {
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("楷煜 ERP 响应格式异常（" + path + "）");
        }
        Map<String, Object> resp = (Map<String, Object>) parsed;
        Object code = resp.get("code");
        int c = code instanceof Number n ? n.intValue() : 500;
        if (c == 401) {
            throw new TokenExpiredException();
        }
        if (c != 200) {
            String msg = str(resp.get("message"));
            throw new IllegalArgumentException("楷煜 ERP 侧处理失败：" + (msg.isBlank() ? "未知原因（" + path + "）" : msg));
        }
        return resp;
    }

    /** 发起 POST 并解析 JSON 响应（对象或数组都接受）；HTTP/Result 401 走令牌重登。 */
    private Object postAny(String url, Map<String, Object> body, String bearer, Duration timeout, String path) {
        try {
            String payload = mapper.writeValueAsString(body == null ? Map.of() : body);
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));
            if (bearer != null && !bearer.isBlank()) {
                rb.header("Authorization", "Bearer " + bearer);
            }
            HttpResponse<String> resp = HttpClient.newHttpClient().send(rb.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                throw new TokenExpiredException();
            }
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalArgumentException("楷煜 ERP 返回异常（" + resp.statusCode() + "），请检查服务地址是否正确");
            }
            Object parsed = mapper.readValue(resp.body(), Object.class);
            if (parsed instanceof Map) {
                Object code = ((Map<?, ?>) parsed).get("code");
                if (code instanceof Number n && n.intValue() == 401) {
                    throw new TokenExpiredException();
                }
            }
            return parsed;
        } catch (TokenExpiredException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法连接楷煜 ERP（" + rootMessage(e) + "），请检查服务地址与网络");
        }
    }

    private static class TokenExpiredException extends RuntimeException {
    }

    // ---------------- 步骤/结果呈现辅助 ----------------

    /** 观察步骤文案：优先楷煜结果里的 message，否则通用文案（不许出现英文工具名）。 */
    private String observationText(Object toolResult) {
        if (toolResult instanceof Map<?, ?> m) {
            Object message = m.get("message");
            if (message != null && !String.valueOf(message).isBlank()) {
                return String.valueOf(message);
            }
        }
        return "该步骤已完成";
    }

    /** 从楷煜工具结果里解包记录数组（第一个集合值），供前端渲染结果表；无数组返回 null。 */
    private Object unwrapRows(Object toolResult) {
        if (!(toolResult instanceof Map<?, ?> m)) return null;
        for (String key : new String[]{"list", "data", "rows", "records", "items", "results", "success"}) {
            Object v = m.get(key);
            if (v instanceof List<?> l && !l.isEmpty()) {
                return l;
            }
        }
        for (Object v : m.values()) {
            if (v instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map) {
                return l;
            }
        }
        return null;
    }

    private Map<String, Object> step(String type, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("step", type);
        m.put("text", text);
        return m;
    }

    // ---------------- 连接辅助 ----------------

    private String baseOf(KaiExtConnection conn) {
        String base = conn.getBaseUrl() == null ? "" : conn.getBaseUrl().trim();
        if (base.isBlank()) {
            throw new IllegalArgumentException("该连接没有填写楷煜服务地址，请到「系统连接」编辑");
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        return base;
    }

    /** 连接附加配置里的公司（租户）ID，可空。 */
    private Long configuredTenantId(KaiExtConnection conn) {
        String extra = conn.getExtraConfig();
        if (extra == null || extra.isBlank()) return null;
        try {
            Map<String, Object> cfg = mapper.readValue(extra, Map.class);
            return asLong(cfg.get("tenantId"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 本次登录拿到的公司清单（测试连接后供前端回填公司下拉）。 */
    private final Map<Long, List<Map<String, Object>>> tenantCache = new ConcurrentHashMap<>();
    private final Map<Long, String> tenantNameCache = new ConcurrentHashMap<>();

    private void rememberTenants(KaiExtConnection conn, List<Map<String, Object>> tenants) {
        if (conn.getId() != null && tenants != null && !tenants.isEmpty()) {
            tenantCache.put(conn.getId(), tenants);
        }
    }

    private void rememberTenantName(KaiExtConnection conn, String tenantName) {
        if (conn.getId() != null && tenantName != null && !tenantName.isBlank()) {
            tenantNameCache.put(conn.getId(), tenantName);
        }
    }

    private List<Map<String, Object>> cachedTenants(KaiExtConnection conn) {
        if (conn.getId() == null) return Collections.emptyList();
        List<Map<String, Object>> list = tenantCache.get(conn.getId());
        return list == null ? Collections.emptyList() : list;
    }

    private String tenantSuffix(String tenantName) {
        return tenantName == null || tenantName.isBlank() ? "" : "（公司：" + tenantName + "）";
    }

    // ---------------- 通用小工具 ----------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object o) {
        if (!(o instanceof List<?> l)) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : l) {
            if (item instanceof Map) out.add((Map<String, Object>) item);
        }
        return out;
    }

    private Long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(o);
            return s.isBlank() ? null : Long.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> bodyOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String rootMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) msg = e.getClass().getSimpleName();
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    private String safeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    // ---------------- 内部结构 ----------------

    private static class CachedToken {
        final String token;
        final long expireAt;

        CachedToken(String token, long expireAt) {
            this.token = token;
            this.expireAt = expireAt;
        }
    }

    /** 楷煜侧待确认动作的本地登记（确认/取消时转发回楷煜）。 */
    private static class ForwardPending {
        final String confirmToken;
        final Long kaiUserId;
        final Long sessionId;
        final Long connectionId;
        final String tool;
        final String summary;
        final long expireAt;

        ForwardPending(String confirmToken, Long kaiUserId, Long sessionId,
                       Long connectionId, String tool, String summary, long expireAt) {
            this.confirmToken = confirmToken;
            this.kaiUserId = kaiUserId;
            this.sessionId = sessionId;
            this.connectionId = connectionId;
            this.tool = tool;
            this.summary = summary;
            this.expireAt = expireAt;
        }
    }
}
