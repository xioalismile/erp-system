package com.erp.kai.service.agent;

import com.erp.ai.gateway.AiChannelAdapter;
import com.erp.ai.util.AiUsageCollector;
import com.erp.ai.util.AiThoughtContextHolder;
import com.erp.kai.entity.KaiExtConnection;
import com.erp.kai.service.KaiChatFileService;
import com.erp.kai.service.KaiChatStore;
import com.erp.kai.service.KaiConnectionService;
import com.erp.kai.service.KaiCryptoService;
import com.erp.kai.service.KaiDocAnalyzerService;
import com.erp.kai.service.KaiExtTaskService;
import com.erp.kai.service.KaiKnowledgeService;
import com.erp.kai.service.KaiMcpService;
import com.erp.kai.service.KaiModelService;
import com.erp.kai.service.KaiPluginService;
import com.erp.kai.service.KaiResultSetService;
import com.erp.kai.service.KaiSkillService;
import com.erp.kai.service.KaiUsageService;
import com.erp.kai.service.KaiCreditService;
import com.erp.kai.service.KaiSessionTitleService;
import com.erp.kai.service.adapter.KaiAdapterFactory;
import com.erp.kai.service.adapter.KaiExtAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小楷独立版 · 独立对话 Agent（FEAT-KAI-STANDALONE-001）。
 *
 * 与主系统小楷（ReActAgentService，419 个内部工具）完全隔离：
 * 独立版只暴露「外部系统操作」工具组 + 用户插件工具，全部经 KaiExtAdapter/插件转发；
 * 模型解析用户自有配置优先（KaiModelService），未配置兜底系统默认。
 *
 * 口径：
 * ① 写操作（导入资料/智能分类/开单/提交单据）一律两段式确认——工具先返回"计划"，用户在确认卡拍板后才真正执行；
 * ② 聊天持久化：会话与每轮消息落库（KaiChatStore），重启不丢；内存会话冷启动从库重建最近 40 条；
 * ③ 用量：每轮对话采集 token 落库并判定月配额，超额拒绝；
 * ④ 写操作执行后落任务留痕。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiAgentService {

    private final KaiAdapterFactory adapterFactory;
    private final KaiConnectionService connectionService;
    private final KaiExtTaskService taskService;
    private final KaiChatStore chatStore;
    private final KaiModelService modelService;
    private final KaiUsageService usageService;
    private final KaiCreditService creditService;
    private final KaiSessionTitleService titleService;
    private final KaiPluginService pluginService;
    private final KaiYunForwardService kaiYunForwardService;
    private final KaiChatFileService chatFileService;
    private final KaiDocAnalyzerService docAnalyzerService;
    private final KaiSkillService skillService;
    private final KaiKnowledgeService knowledgeService;
    private final KaiMcpService mcpService;
    // 结果集持久化：查询结果落库，支撑二次分析（分组汇总 / 两份结果对比）
    private final KaiResultSetService resultSetService;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 上下文注入不再写死条数（旧值 40 条会把大窗口模型的能力直接浪费掉）：
     * 每轮由 KaiContextBudget 按模型真实窗口算出字符预算，超预算时按"整轮"淘汰最老对话，
     * 不会切开 assistant(tool_calls) 与其 tool 应答（否则模型接口会 400）。
     */
    private static final int DEFAULT_HISTORY_CHARS = KaiContextBudget.historyChars("deepseek");
    private static final long SESSION_TTL_MS = 60 * 60 * 1000L;
    private static final long PENDING_TTL_MS = 10 * 60 * 1000L;
    private static final int MAX_ROUNDS = 8;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, PendingAction> pendings = new ConcurrentHashMap<>();

    private String memKey(Long kaiUserId, Long sessionId) {
        return kaiUserId + ":" + sessionId;
    }

    // ---------------- 对外：对话 ----------------

    /**
     * 处理一轮对话，返回步骤列表（前端按顺序渲染）。
     * 步骤：THOUGHT / ACTION / OBSERVATION / CONFIRM_NEEDED / ERROR / FINAL_ANSWER。
     * sessionId = 会话库表 ID（前端先建会话）。
     */
    public List<Map<String, Object>> chat(Long kaiUserId, String displayName, Long sessionId,
                                          String userInput, Long connectionId, Long modelProviderId, Long kbBaseId,
                                          boolean deepThinking) {
        return chat(kaiUserId, displayName, sessionId, userInput, connectionId, modelProviderId, kbBaseId,
                deepThinking, "detail", null);
    }

    /** 答复呈现方式（2026-10-22）：summary = 只报结果，detail = 明细（展示执行过程，默认）。 */
    public List<Map<String, Object>> chat(Long kaiUserId, String displayName, Long sessionId,
                                          String userInput, Long connectionId, Long modelProviderId, Long kbBaseId,
                                          boolean deepThinking, String answerMode) {
        return chat(kaiUserId, displayName, sessionId, userInput, connectionId, modelProviderId, kbBaseId,
                deepThinking, answerMode, null);
    }

    /**
     * 处理一轮对话，返回步骤列表（前端按顺序渲染）。
     * 步骤：THOUGHT / ACTION / OBSERVATION / CONFIRM_NEEDED / ERROR / FINAL_ANSWER。
     *
     * @param onStep 步骤产生即回调（流式端点用，让用户边跑边看到思考与工具调用过程）；
     *               为 null 时行为与同步接口完全一致
     */
    public List<Map<String, Object>> chat(Long kaiUserId, String displayName, Long sessionId,
                                          String userInput, Long connectionId, Long modelProviderId, Long kbBaseId,
                                          boolean deepThinking, java.util.function.Consumer<Map<String, Object>> onStep) {
        return chat(kaiUserId, displayName, sessionId, userInput, connectionId, modelProviderId, kbBaseId,
                deepThinking, "detail", onStep);
    }

    public List<Map<String, Object>> chat(Long kaiUserId, String displayName, Long sessionId,
                                          String userInput, Long connectionId, Long modelProviderId, Long kbBaseId,
                                          boolean deepThinking, String answerMode,
                                          java.util.function.Consumer<Map<String, Object>> onStep) {
        return chat(kaiUserId, displayName, sessionId, userInput, connectionId, modelProviderId, kbBaseId,
                deepThinking, answerMode, null, null, onStep);
    }

    /**
     * 思考强度三档 + 上下文窗口覆盖（2026-11-22 照参考设计）。
     * thoughtLevel = light（标准直答）/ high（深度思考，深度倍率计费）/ max（更长思考空间+更高输出上限，计费同高）；
     * contextLimitOverride = 上下文档位覆盖（512000 / 1000000），null=按模型真实窗口默认。
     * 旧调用（不传两参）行为与升级前完全一致。
     */
    public List<Map<String, Object>> chat(Long kaiUserId, String displayName, Long sessionId,
                                          String userInput, Long connectionId, Long modelProviderId, Long kbBaseId,
                                          boolean deepThinking, String answerMode,
                                          String thoughtLevelArg, Integer contextLimitOverride) {
        return chat(kaiUserId, displayName, sessionId, userInput, connectionId, modelProviderId, kbBaseId,
                deepThinking, answerMode, thoughtLevelArg, contextLimitOverride, null);
    }

    public List<Map<String, Object>> chat(Long kaiUserId, String displayName, Long sessionId,
                                          String userInput, Long connectionId, Long modelProviderId, Long kbBaseId,
                                          boolean deepThinking, String answerMode,
                                          String thoughtLevelArg, Integer contextLimitOverride,
                                          java.util.function.Consumer<Map<String, Object>> onStep) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (userInput == null || userInput.isBlank()) {
            emit(steps, onStep, step("ERROR", "请输入您要处理的内容"));
            return steps;
        }
        // 思考强度归一（2026-11-22 三档）：旧调用只传 deepThinking 布尔时按 true=high / false=light 兜底
        final String thoughtLevel = normalizeThoughtLevel(thoughtLevelArg, deepThinking);
        // 计费合并（用户 2026-11-22 拍板）：高/极高都按深度倍率；light 按标准倍率
        final boolean deepBilling = !"light".equals(thoughtLevel);
        // 思考强度=极高：轮数兜底同步放宽（防空转），历史预算仍按上下文档位硬约束
        final int maxRounds = "max".equals(thoughtLevel) ? MAX_ROUNDS * 2 : MAX_ROUNDS;
        currentUserId.set(kaiUserId);
        try {
            // 连接可不绑：纯聊天模式（仍可分析文件接入新系统 / 管理已有连接 / 用插件）。
            // 连接校验/配额检查放进 try：任何失败都以中文步骤呈现在对话流里，不再让整轮请求变 500（前端只会显示"处理失败"）
            KaiExtConnection conn;
            try {
                conn = connectionId == null ? null : connectionService.getUsable(kaiUserId, connectionId);
            } catch (IllegalArgumentException e) {
                // 选中的连接不可用（已删除/他人连接等）：降级为纯聊天并如实告知
                conn = null;
                emit(steps, onStep, step("ERROR", "当前选择的系统连接不可用（" + e.getMessage() + "），本轮按未接系统处理，可在右上角重新选择"));
            }
            usageService.checkQuota(kaiUserId);
            if (conn != null && "KAIYUN".equals(conn.getType())) {
                // 楷煜满血连接：对话整轮转发给楷煜 ERP 内小楷（全部工具），独立站不重复计 token
                return kaiYunForwardService.chat(kaiUserId, sessionId, userInput, conn, answerMode, thoughtLevel, contextLimitOverride, onStep);
            }
            KaiExtAdapter extAdapter = conn == null ? null : adapterFactory.of(conn);
            chatStore.existsOrThrow(kaiUserId, sessionId);
            // 首轮判定（自动命名用）：本会话此前没有用户消息
            boolean firstRound = !chatStore.hasUserMessage(sessionId);
            chatStore.saveUserMessage(sessionId, kaiUserId, userInput);

            // 先解析模型：上下文预算要按该模型真实窗口算（不再写死条数）
            KaiModelService.ResolvedModel resolved = modelService.resolveModel(kaiUserId, modelProviderId);
            AiChannelAdapter model = resolved.adapter();
            boolean ownModel = resolved.ownModel();
            String modelName = model.getModelName();
            // 历史预算按"上下文档位与模型真实窗口取小"算（2026-11-22 三档；不传=按模型窗口默认，行为不变）
            int historyChars = KaiContextBudget.historyChars(modelName, contextLimitOverride);
            int historyCap = KaiContextBudget.messageCap(modelName);
            // 思考强度登记到本次请求线程：适配器据此放大单轮输出上限（2026-11-22 三档）
            AiThoughtContextHolder.set(thoughtLevel);

            String memKey = memKey(kaiUserId, sessionId);
            Session session = sessions.computeIfAbsent(memKey, k -> {
                Session s = new Session();
                s.budgetChars = historyChars;
                // 冷启动：按模型窗口从库重建历史（含每轮的工具执行记录与结果集编号）
                s.messages.addAll(chatStore.loadAsModelMessages(kaiUserId, sessionId, historyChars, historyCap));
                return s;
            });
            session.expireAt = System.currentTimeMillis() + SESSION_TTL_MS;
            session.budgetChars = historyChars;

            String skillBlock = skillService.skillPromptBlock(kaiUserId);
            String kbBlock = knowledgeService.retrieveContext(kaiUserId, userInput, kbBaseId);
            String systemPrompt = buildSystemPrompt(conn, extAdapter, displayName, skillBlock, kbBlock, answerMode);
            List<Map<String, Object>> tools = buildTools(extAdapter, kaiUserId);
            String userMessage = userInput;
            // 用户输入立即入历史（模型多轮时上下文完整）
            session.messages.add(msg("user", userInput));
            trim(session);

            try {
            for (int round = 1; round <= maxRounds; round++) {
                // 每轮调用前判定积分（自带模型免费时直接放行）；不足则中文拦截，不再调用模型
                creditService.checkBeforeCall(kaiUserId, ownModel);
                Map<String, Object> result = chatWithUsage(model, systemPrompt, userMessage, session, tools, ownModel, deepBilling, resolved.builtinRate());
                userMessage = null;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> toolCalls =
                        (List<Map<String, Object>>) result.getOrDefault("tool_calls", Collections.emptyList());
                // 思考过程透出（DeepSeek 等模型的 reasoning_content）：作为执行过程第一步展示，全文不截断（用户要求全部可看）
                String reasoning = str(result.get("reasoning_content"));
                if (!reasoning.isBlank()) {
                    emit(steps, onStep, step("THOUGHT", reasoning));
                }
                if (toolCalls.isEmpty()) {
                    String content = str(result.get("content"));
                    if (content.isBlank()) {
                        emit(steps, onStep, step("ERROR", "模型没有返回内容，请重试或换个说法"));
                        chatStore.saveBotMessage(sessionId, kaiUserId, steps);
                        return steps;
                    }
                    session.messages.add(msg("assistant", content));
                    trim(session);
                    emit(steps, onStep, step("FINAL_ANSWER", content));
                    chatStore.saveBotMessage(sessionId, kaiUserId, steps);
                    autoTitleIfNeeded(kaiUserId, sessionId, firstRound, userInput);
                    return steps;
                }
                // assistant 工具调用消息入历史（DeepSeek 思考模式需回传 reasoning_content）
                Map<String, Object> assistantMsg = msg("assistant", str(result.get("content")));
                if (result.get("reasoning_content") != null) {
                    assistantMsg.put("reasoning_content", result.get("reasoning_content"));
                }
                assistantMsg.put("tool_calls", rawToolCalls(toolCalls));
                session.messages.add(assistantMsg);

                for (int ci = 0; ci < toolCalls.size(); ci++) {
                    Map<String, Object> call = toolCalls.get(ci);
                    String name = str(call.get("name"));
                    String callId = str(call.get("id"));
                    Map<String, Object> args;
                    try {
                        args = parseArgs(call.get("arguments"));
                    } catch (Exception e) {
                        // 必须给这条 tool_call 补应答，否则历史悬空，下一轮被模型接口整体拒绝
                        session.messages.add(toolMsg(callId, "参数解析失败：" + e.getMessage()));
                        emit(steps, onStep, step("ERROR", "模型参数无法解析，已跳过该步骤"));
                        continue;
                    }
                    emit(steps, onStep, actionStep(name, args));
                    // 写操作（内置写工具或插件声明为写的工具）：不执行，生成待确认动作
                    if (isWriteTool(name)) {
                        String token = UUID.randomUUID().toString().replace("-", "");
                        // 纯聊天模式下无绑定连接（接入新系统/改配置类写操作不需要连接），连接 id 允许为空
                        PendingAction p = new PendingAction(kaiUserId, sessionId,
                                conn == null ? null : conn.getId(), name, args,
                                summarize(name, args), System.currentTimeMillis() + PENDING_TTL_MS);
                        pendings.put(token, p);
                        Map<String, Object> confirm = step("CONFIRM_NEEDED", p.summary);
                        confirm.put("confirmToken", token);
                        confirm.put("tool", name);
                        // 接入新系统：确认卡只放提炼后的关键信息，不把整段接口文档原文本塞进卡片
                        confirm.put("detail", "ext_integrate_system".equals(name) ? integrateConfirmDetail(args) : args);
                        emit(steps, onStep, confirm);
                        // 该轮每个 tool_call 都要有应答（否则历史悬空，下一轮请求被模型接口 400 拒绝——云枢实测实锤）
                        session.messages.add(toolMsg(callId, "已生成确认卡片，等待用户在界面上确认或取消"));
                        for (int ri = ci + 1; ri < toolCalls.size(); ri++) {
                            session.messages.add(toolMsg(str(toolCalls.get(ri).get("id")), "已跳过：本轮有写操作等待用户确认，请用户处理后再发起"));
                        }
                        session.pendingConfirm = true;
                        chatStore.saveBotMessage(sessionId, kaiUserId, steps);
                        autoTitleIfNeeded(kaiUserId, sessionId, firstRound, userInput);
                        return steps;
                    }
                    // 只读工具（含插件只读工具）：直接执行
                    String resultJson;
                    try {
                        Object raw = executeTool(extAdapter, name, args, conn, kaiUserId, sessionId);
                        // 工具可分别给出「前端展示数据」与「模型上下文数据」：查询类工具展示全量行、给模型只给元数据+样本
                        Object modelPayload = raw;
                        Object displayPayload = raw;
                        if (raw instanceof ToolOutput to) {
                            modelPayload = to.model();
                            displayPayload = to.display();
                        }
                        resultJson = mapper.writeValueAsString(modelPayload);
                        Map<String, Object> obs = step("OBSERVATION", describeResult(name, displayPayload));
                        obs.put("data", displayPayload);
                        // 结果集编号落到步骤里：会话冷启动恢复时模型还能知道"之前查过哪份数据"
                        if (modelPayload instanceof Map<?, ?> mp && mp.get("resultSetId") != null) {
                            obs.put("resultSetId", mp.get("resultSetId"));
                        }
                        emit(steps, onStep, obs);
                    } catch (Exception e) {
                        resultJson = "执行失败：" + e.getMessage();
                        emit(steps, onStep, step("ERROR", resultJson));
                    }
                    session.messages.add(toolMsg(callId, resultJson));
                }
                trim(session);
            }
            emit(steps, onStep, step("ERROR", "连续 " + MAX_ROUNDS + " 轮没能完成操作，已停止。您可以把需求拆小一点再说"));
            chatStore.saveBotMessage(sessionId, kaiUserId, steps);
            return steps;
        } catch (Exception e) {
            log.error("[小楷独立版] 对话处理失败: {}", e.getMessage(), e);
            emit(steps, onStep, step("ERROR", friendlyModel(e)));
            chatStore.saveBotMessage(sessionId, kaiUserId, steps);
            return steps;
        }
        } finally {
            currentUserId.remove();
            // 思考强度同步清理（2026-11-22 三档，防线程池复用串账）
            AiThoughtContextHolder.clear();
        }
    }

    /**
     * 模型调用并采集 token 用量（每轮真实调用计入月用量 + 按积分口径扣减）。
     * 深度档判定以客户显式开关为准（2026-10-19），不再看模型是否返回思考内容。
     */
    private Map<String, Object> chatWithUsage(AiChannelAdapter model, String systemPrompt, String userMessage,
                                              Session session, List<Map<String, Object>> tools, boolean ownModel,
                                              boolean deepThinking, java.math.BigDecimal builtinRate) {
        AiUsageCollector.begin();
        try {
            return model.chatWithTools(systemPrompt, userMessage, session.messages, tools);
        } finally {
            long[] u = AiUsageCollector.end();
            if (u != null && u.length >= 3) {
                Long uid = currentUserId.get();
                usageService.collect(uid, u[0], u[1], u[2]);
                creditService.consume(uid, u[0], u[1], u[2], model.getModelName(), ownModel, deepThinking, null, builtinRate);
            }
        }
    }

    /** 当前线程正在服务的用户（用量归属）；ThreadLocal 由 chat() 入口设置。 */
    private final ThreadLocal<Long> currentUserId = new ThreadLocal<>();

    /**
     * 首轮结束自动命名（2026-10-19）：仅当标题仍为默认「新对话」时写入；
     * 用平台默认模型总结（不扣客户积分、不计客户用量），失败静默保持原标题。
     */
    private void autoTitleIfNeeded(Long kaiUserId, Long sessionId, boolean firstRound, String firstInput) {
        try {
            if (!firstRound || firstInput == null || firstInput.isBlank()) return;
            String cur = chatStore.getTitle(sessionId);
            if (cur != null && !"新对话".equals(cur.trim())) return;
            String title = titleService.generateTitle(firstInput);
            if (title != null && !title.isBlank()) {
                chatStore.renameSession(kaiUserId, sessionId, title);
            }
        } catch (Exception e) {
            log.info("[小楷独立版] 会话自动命名跳过: {}", e.getMessage());
        }
    }

    // ---------------- 对外：确认 / 取消 ----------------

    /** 确认执行待确认动作（写操作真正落地的唯一入口）。 */
    public Map<String, Object> confirm(Long kaiUserId, String confirmToken) {
        PendingAction p = pendings.remove(confirmToken);
        if (p == null) {
            // 不是本地待确认动作 → 尝试楷煜满血转发型待确认（楷煜侧令牌）
            return kaiYunForwardService.confirmForward(kaiUserId, confirmToken);
        }
        if (!p.kaiUserId.equals(kaiUserId)) {
            throw new IllegalArgumentException("只能确认自己的操作");
        }
        // 纯聊天模式的写操作（接入新系统/修改连接配置）没有绑定连接，连接与适配器允许为空
        KaiExtConnection conn = p.connectionId == null ? null : connectionService.getUsable(p.kaiUserId, p.connectionId);
        KaiExtAdapter extAdapter = conn == null ? null : adapterFactory.of(conn);
        Object data;
        try {
            data = executeWriteTool(extAdapter, p.tool, p.args, conn, kaiUserId);
            taskService.log(kaiUserId, p.connectionId, p.tool, str(p.args.get("objectType")),
                    p.summary, safeJson(data), "DONE");
            injectSystemNote(session(memKey(p.kaiUserId, p.sessionId)), "用户已确认执行" + p.summary + "，执行结果：" + safeJson(data));
        } catch (Exception e) {
            taskService.log(kaiUserId, p.connectionId, p.tool, str(p.args.get("objectType")),
                    p.summary, e.getMessage(), "FAILED");
            throw e instanceof IllegalArgumentException iae ? iae
                    : new IllegalArgumentException("执行失败：" + e.getMessage());
        }
        Session session = sessions.get(memKey(p.kaiUserId, p.sessionId));
        if (session != null) session.pendingConfirm = false;
        // 执行结果作为一条小楷回复落库（历史完整可回看）
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("OBSERVATION", "已执行：" + p.summary));
        steps.get(0).put("data", data);
        steps.add(step("FINAL_ANSWER", summarizeResult(p.tool, data)));
        chatStore.saveBotMessage(p.sessionId, kaiUserId, steps);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", p.summary);
        out.put("result", data);
        return out;
    }

    /** 取消待确认动作。 */
    public Map<String, Object> cancel(Long kaiUserId, String confirmToken) {
        PendingAction p = pendings.remove(confirmToken);
        if (p == null) {
            // 不是本地待确认动作 → 尝试楷煜满血转发型待确认（楷煜侧令牌）
            return kaiYunForwardService.cancelForward(kaiUserId, confirmToken);
        }
        if (!p.kaiUserId.equals(kaiUserId)) {
            throw new IllegalArgumentException("只能取消自己的操作");
        }
        injectSystemNote(session(memKey(p.kaiUserId, p.sessionId)), "用户取消了该操作：" + p.summary);
        Session session = sessions.get(memKey(p.kaiUserId, p.sessionId));
        if (session != null) session.pendingConfirm = false;
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("FINAL_ANSWER", "已取消「" + p.summary + "」，没有执行任何操作"));
        chatStore.saveBotMessage(p.sessionId, kaiUserId, steps);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", p.summary);
        out.put("result", "已取消，没有执行任何操作");
        return out;
    }

    /** 写操作执行结果的一句话总结（确认后回复用）。 */
    private String summarizeResult(String tool, Object data) {
        if (data instanceof Map<?, ?> m) {
            if ("ext_integrate_system".equals(tool) && m.get("connectionId") != null) {
                return "完成。已保存为系统连接「" + m.get("name") + "」，右上角系统下拉里现在就能选到它";
            }
            Object billNo = m.get("billNo");
            if (billNo != null) return "完成。单据号 " + billNo + "，状态「" + (m.get("status") == null ? "草稿" : m.get("status")) + "」";
            Object successCount = m.get("successCount");
            Object failCount = m.get("failCount");
            if (successCount != null) {
                int ok = ((Number) successCount).intValue();
                int bad = failCount == null ? 0 : ((Number) failCount).intValue();
                if (bad > 0) {
                    return "写入完成：成功 " + ok + " 条，失败 " + bad + " 条（失败明细见上方报表，含每条原因）。"
                            + "您可以把失败条目的修正值发给我（例如「名称 XX 的编码改成 YY」），我逐条重新导入补齐";
                }
                return "写入完成：成功 " + ok + " 条，全部成功，无失败";
            }
        }
        return "完成。结果见上方明细";
    }

    /** 会话历史（刷新恢复用）：从库按时间序读。 */
    public List<Map<String, Object>> history(Long kaiUserId, Long sessionId) {
        return chatStore.loadHistory(kaiUserId, sessionId);
    }

    public void clear(Long kaiUserId, Long sessionId) {
        sessions.remove(memKey(kaiUserId, sessionId));
        pendings.values().removeIf(p -> kaiUserId.equals(p.kaiUserId) && sessionId.equals(p.sessionId));
        chatStore.deleteSession(kaiUserId, sessionId);
    }

    /** 会话列表（侧栏）。 */
    public List<Map<String, Object>> listSessions(Long kaiUserId) {
        return chatStore.listSessions(kaiUserId);
    }

    public Long createSession(Long kaiUserId, String title) {
        return chatStore.createSession(kaiUserId, title);
    }

    public void renameSession(Long kaiUserId, Long sessionId, String title) {
        chatStore.renameSession(kaiUserId, sessionId, title);
    }

    public void pinSession(Long kaiUserId, Long sessionId, boolean pinned) {
        chatStore.pinSession(kaiUserId, sessionId, pinned);
    }

    // ---------------- 工具定义与执行 ----------------

    private static final Set<String> WRITE_TOOLS = Set.of(
            "ext_import_records", "ext_categorize", "ext_create_bill", "ext_submit_bill", "ext_integrate_system",
            "ext_update_connection");

    /** 插件声明为"写"的工具名缓存（发现时写入；write:true 的插件工具走确认卡）。 */
    private final Set<String> pluginWriteTools = ConcurrentHashMap.newKeySet();

    private boolean isWriteTool(String name) {
        return WRITE_TOOLS.contains(name) || pluginWriteTools.contains(name) || mcpWriteTools.contains(name);
    }

    /** MCP 写类工具名缓存（服务器开启「写操作需确认」且工具名/描述命中写类关键词 → 走确认卡）。 */
    private final Set<String> mcpWriteTools = ConcurrentHashMap.newKeySet();

    private static final List<String> WRITE_KEYWORDS = List.of(
            "create", "add", "insert", "update", "delete", "remove", "write", "post", "put", "import", "send", "submit", "save",
            "创建", "新增", "添加", "删除", "修改", "更新", "写入", "导入", "提交", "发送", "保存");

    /** MCP 工具写类判定：名称或描述命中写类关键词即视为写操作（走确认卡）。 */
    private boolean isWriteishTool(String toolName, String description) {
        String s = (toolName + " " + (description == null ? "" : description)).toLowerCase();
        for (String k : WRITE_KEYWORDS) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    /** 内置工具 + 启用插件的工具（插件不可达时跳过并告警，不阻断对话）。 */
    private List<Map<String, Object>> buildTools(KaiExtAdapter extAdapter, Long kaiUserId) {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(fn("ext_list_connections", "列出用户已保存的全部系统连接（只读）：连接id、名称、类型、鉴权方式、附加配置（字段映射/接口地址）。用户想查看或修改某个接入系统的配置时先调这个。",
                params().build()));
        tools.add(fn("ext_update_connection",
                "修改用户某个已保存系统连接的配置（写操作需用户确认）：可改字段映射（fieldMap，按对象）、接口地址（endpoints，按对象）、连接名称。用户在聊天里纠正'某个字段应该映射成 XX'、'某张表的地址应该是 XX'时使用。改动只合并传入的键，其余保持不变，改完立即生效（后续查询/写入即用新配置）。",
                params().req("connectionId", "integer", "要修改的连接 id（先用 ext_list_connections 查到）")
                        .opt("fieldMap", "object", "要合并的字段映射，如 {\"material\":{\"code\":\"part_no\"}}")
                        .opt("endpoints", "object", "要合并的接口地址，如 {\"sale_order\":{\"list\":\"https://...\",\"create\":\"https://...\"}}")
                        .opt("name", "string", "新的连接名称").build()));
        if (extAdapter != null) {
            Map<String, String> types = extAdapter.supportedObjectTypes();
            String typeDesc = "对象类型取值：" + types.entrySet().stream()
                    .map(e -> e.getKey() + "（" + e.getValue() + "）")
                    .reduce((a, b) -> a + " / " + b).orElse("");
            tools.add(fn("ext_query_data", "查询用户接入的第三方ERP里的资料（只读）。每次查询的结果会自动整份保存为「结果集」并返回结果集编号 resultSetId；"
                    + "之后的统计、对比都基于该编号在本地计算，不必重复查询对方系统。" + typeDesc, params()
                .req("objectType", "string", typeDesc)
                .opt("keyword", "string", "关键词（编码或名称片段），可空")
                .opt("startDate", "string", "起始日期 YYYY-MM-DD，可空")
                .opt("endDate", "string", "截止日期 YYYY-MM-DD，可空")
                .opt("label", "string", "这份结果的人话标签（如「销售订单 2026-10-01 至 2026-10-31」），便于后续识别")
                .opt("limit", "integer", "最多返回条数，默认 50、最多 500")
                .build()));
        tools.add(fn("ext_import_records", "把一批资料导入（建档）到第三方ERP（物料/客户/供应商），写操作需用户确认后执行。" + typeDesc,
                params().req("objectType", "string", typeDesc)
                        .req("records", "array", "要建档的记录数组，每条含编码/名称等字段；字段见系统提示中该对象的可写字段")
                        .build()));
        tools.add(fn("ext_categorize", "智能分类：把第三方ERP里一批已存在的资料归入指定分类（写操作需用户确认）。" + typeDesc,
                params().req("objectType", "string", typeDesc)
                        .req("assignments", "array", "分类清单，每条 {code: 资料编码, category: 目标分类名}")
                        .build()));
        tools.add(fn("ext_create_bill", "在第三方ERP里创建销售订单草稿（写操作需用户确认后执行，提交另用 ext_submit_bill）。",
                params().req("customerName", "string", "客户名称（必须已在对方系统建档）")
                        .opt("orderDate", "string", "单据日期 YYYY-MM-DD，可空=今天")
                        .req("items", "array", "明细行数组，每行 {materialName: 物料名或编码, quantity: 数量, unitPrice: 单价(可空取默认)}")
                        .build()));
        tools.add(fn("ext_submit_bill", "把第三方ERP里的草稿单据提交为正式单据（写操作需用户确认）。",
                params().req("billNo", "string", "要提交的单据号").build()));
        tools.add(fn("ext_result_query", "查看某份结果集的明细（只读，分页）。想再看某几行、核对具体记录时用它，不要重新查对方系统。",
                params().req("resultSetId", "integer", "结果集编号（查询结果里返回的 resultSetId）")
                        .opt("keyword", "string", "在该结果集内按关键词过滤，可空")
                        .opt("offset", "integer", "从第几条开始，默认 0")
                        .opt("limit", "integer", "返回多少条，默认 200、最多 1000")
                        .build()));
        tools.add(fn("ext_result_aggregate", "对某份结果集做分组统计（只读）：按字段分组，做求和/计数/平均/最大/最小。"
                + "「按客户统计销售额」「按物料统计销量」「按月统计金额」都用它；在已保存的结果集上直接算，不重复查对方系统。",
                params().req("resultSetId", "integer", "结果集编号")
                        .opt("groupBy", "array", "分组字段名数组，如 [\"客户名称\"]；留空 = 全表汇总一行")
                        .opt("granularity", "string", "分组字段是日期时的粒度：day/month/quarter/year，可空")
                        .req("metrics", "array", "统计指标数组，每项 {field: 字段名, func: SUM/COUNT/AVG/MAX/MIN, label: 输出列名(可空)}；只计数时 func 用 COUNT、field 可空")
                        .opt("orderByMetric", "integer", "按第几个指标排序（从 0 开始），可空")
                        .opt("orderDesc", "boolean", "是否降序，默认 true")
                        .opt("limit", "integer", "最多返回多少个分组，默认 200")
                        .build()));
        tools.add(fn("ext_result_compare", "对比两份结果集（只读）：按同一分组字段对齐，逐组给出两期数值、差值与变化率，并给出总计与整体变化率。"
                + "「本月对比上月」「今年对比去年」「两个客户/两个仓库对比」都用它——先分别查两次拿到两个结果集编号，再调本工具。",
                params().req("resultSetIdA", "integer", "基准期结果集编号")
                        .req("resultSetIdB", "integer", "对比期结果集编号")
                        .opt("groupBy", "array", "对齐用的分组字段名数组，如 [\"客户名称\"]；留空 = 只比总计")
                        .opt("granularity", "string", "分组字段是日期时的粒度：day/month/quarter/year，可空")
                        .opt("field", "string", "要比较的数值字段名（金额/数量等）；只比条数时不传")
                        .opt("func", "string", "比较方式 SUM/COUNT/AVG/MAX/MIN，默认 SUM")
                        .opt("labelA", "string", "基准期的显示名，如「9月」")
                        .opt("labelB", "string", "对比期的显示名，如「10月」")
                        .build()));
        }
        tools.add(fn("ext_analyze_integration",
                "只分析不保存：读取第三方系统的接入资料并理解（用户上传的文件 / 消息里粘贴的资料 / 文档网页直链，三种给法任选其一），返回识别出的对象、接口、鉴权方式、还缺什么。接入新系统的第一步先用这个；拿到结果后把理解到的内容用人话讲给用户听，缺什么就列问题清单问用户（如访问密钥、账号密码），等用户补齐再调 ext_integrate_system 保存。识别不出也不算失败，会返回缺什么，照着问即可。",
                params().opt("fileToken", "string", "用户上传文件时对话里给出的文件标识")
                        .opt("docText", "string", "用户直接粘贴在消息里的接口接入资料原文（含接口地址、密钥、字段说明）")
                        .opt("docUrl", "string", "接口文档的网页直链（系统自动拉取正文）")
                        .build()));
        tools.add(fn("ext_integrate_system",
                "把分析好的接入资料保存为新的系统连接（写操作需用户确认）。推荐先用 ext_analyze_integration 分析并把缺的信息问齐，再调用本工具：传分析时返回的 analysisToken（不再重新分析）；用户补充了新资料就放在 supplement 里一并分析。用户在聊天里给的凭据用 apiKey（访问密钥/令牌）/ authUsername + authPassword（账号密码）传入——缺凭据时先问用户要，不要保存一个空凭据的连接。没有 analysisToken 时也可以直接给 fileToken/docText/docUrl 三选一。",
                params().opt("analysisToken", "string", "ext_analyze_integration 返回的分析令牌（推荐，免二次分析）")
                        .opt("supplement", "string", "用户补充的接入信息（与原资料合并重新分析）")
                        .opt("fileToken", "string", "用户上传文件时对话里给出的文件标识")
                        .opt("docText", "string", "用户直接粘贴在消息里的接口接入资料原文")
                        .opt("docUrl", "string", "接口文档的网页直链（系统自动拉取正文）")
                        .opt("systemName", "string", "连接名称，可空（默认按上传文件名或自动命名）")
                        .opt("apiKey", "string", "访问密钥或令牌（用户在聊天里给的）")
                        .opt("authUsername", "string", "登录账号（账号密码类鉴权时用户在聊天里给的）")
                        .opt("authPassword", "string", "登录密码（账号密码类鉴权时用户在聊天里给的）")
                        .opt("credentialJson", "string", "完整凭据报文 JSON——登录换令牌类且对方登录接口的报文字段名不是 username/password 时必用（原样作为登录接口请求体，如 {\"appKey\":\"..\",\"appSecret\":\"..\"}）；或单凭据直接放这里")
                        .build()));
        // 插件工具并入（FEAT-KAI-STANDALONE-005）
        try {
            for (Map<String, Object> t : pluginService.enabledTools(kaiUserId)) {
                String pluginId = String.valueOf(t.get("pluginId"));
                String toolName = String.valueOf(t.get("name"));
                String exposed = "ext_plugin_" + pluginId + "_" + toolName;
                boolean write = Boolean.parseBoolean(String.valueOf(t.get("write")));
                if (write) pluginWriteTools.add(exposed);
                else pluginWriteTools.remove(exposed);
                tools.add(fn(exposed,
                        "插件「" + t.get("pluginName") + "」提供的工具：" + str(t.get("description")),
                        t.get("parameters") instanceof Map ? (Map<String, Object>) t.get("parameters") : params().build()));
            }
        } catch (Exception e) {
            log.warn("[小楷独立版] 插件工具并入失败（不影响内置工具）: {}", e.getMessage());
        }
        // MCP 工具并入（FEAT-KAI-STANDALONE-011）：读库内缓存清单，服务器开启写确认且命中写类关键词的工具走确认卡
        try {
            for (Map<String, Object> t : mcpService.enabledTools(kaiUserId)) {
                String serverId = String.valueOf(t.get("serverId"));
                String toolName = String.valueOf(t.get("name"));
                String exposed = "ext_mcp_" + serverId + "_" + toolName;
                boolean requireConfirm = Boolean.parseBoolean(String.valueOf(t.get("requireConfirm")));
                if (requireConfirm && isWriteishTool(toolName, str(t.get("description")))) {
                    mcpWriteTools.add(exposed);
                } else {
                    mcpWriteTools.remove(exposed);
                }
                tools.add(fn(exposed,
                        "MCP「" + t.get("serverName") + "」提供的工具：" + str(t.get("description")),
                        t.get("parameters") instanceof Map ? (Map<String, Object>) t.get("parameters") : params().build()));
            }
        } catch (Exception e) {
            log.warn("[小楷独立版] MCP 工具并入失败（不影响其它工具）: {}", e.getMessage());
        }
        return tools;
    }

    /** 统一工具执行：内置只读工具 → 适配器；插件工具 → 插件服务转发。 */
    private Object executeTool(KaiExtAdapter a, String name, Map<String, Object> args,
                               KaiExtConnection conn, Long kaiUserId, Long sessionId) {
        if (name.startsWith("ext_mcp_")) {
            String rest = name.substring("ext_mcp_".length());
            int sep = rest.indexOf('_');
            if (sep <= 0) throw new IllegalArgumentException("MCP 工具名无法解析：" + name);
            Long serverId = Long.valueOf(rest.substring(0, sep));
            String tool = rest.substring(sep + 1);
            Map<String, Object> resp = mcpService.call(kaiUserId, serverId, tool, args);
            return resp.getOrDefault("data", resp);
        }
        if (name.startsWith("ext_plugin_")) {
            String rest = name.substring("ext_plugin_".length());
            int sep = rest.indexOf('_');
            if (sep <= 0) throw new IllegalArgumentException("插件工具名无法解析：" + name);
            Long pluginId = Long.valueOf(rest.substring(0, sep));
            String tool = rest.substring(sep + 1);
            Map<String, Object> resp = pluginService.call(kaiUserId, pluginId, tool, args);
            return resp.getOrDefault("data", resp);
        }
        return switch (name) {
            case "ext_query_data" -> {
                if (a == null) yield "当前未绑定系统，查询需要先在右上角选择或接入一个系统（纯聊天模式可让我帮你分析文件并接入）";
                String objectType = str(args.get("objectType"));
                // 分析场景要尽量取全：未显式给条数时按 500 取（适配器会按自身上限收敛）
                if (args.get("limit") == null) args.put("limit", 500);
                List<Map<String, Object>> rows = a.listRecords(conn, objectType, args);
                String typeLabel = a.supportedObjectTypes().getOrDefault(objectType, objectType);
                Map<String, Object> saved = resultSetService.save(kaiUserId, sessionId,
                        conn == null ? null : conn.getId(), resultSetLabel(typeLabel, args),
                        objectType, args, rows);
                // 前端结果表格要全量行；模型上下文只放结果集编号 + 元数据 + 样本，避免把整份数据灌进上下文
                yield new ToolOutput(rows, saved);
            }
            case "ext_result_query" -> resultSetService.query(kaiUserId, reqLong(args, "resultSetId"),
                    optInt(args, "offset"), optInt(args, "limit"), str(args.get("keyword")));
            case "ext_result_aggregate" -> resultSetService.aggregate(kaiUserId, reqLong(args, "resultSetId"),
                    strList(args.get("groupBy")), str(args.get("granularity")),
                    castList(args.get("metrics")), optInt(args, "orderByMetric"),
                    args.get("orderDesc") == null ? null : Boolean.parseBoolean(str(args.get("orderDesc"))),
                    optInt(args, "limit"));
            case "ext_result_compare" -> resultSetService.compare(kaiUserId, reqLong(args, "resultSetIdA"),
                    reqLong(args, "resultSetIdB"), strList(args.get("groupBy")), str(args.get("granularity")),
                    str(args.get("field")), str(args.get("func")), str(args.get("labelA")), str(args.get("labelB")));
            case "ext_list_connections" -> sanitizeDates(connectionService.list(kaiUserId));
            case "ext_analyze_integration" -> {
                // 只分析不落库：识别不出也不报错，返回"缺什么"，由小楷向用户提问
                KaiDocAnalyzerService.DocSource src = resolveIntegrationSource(kaiUserId, args);
                if (src == null || src.text == null || src.text.isBlank()) {
                    yield "没有可分析的资料。请用户上传接口文档、把资料粘贴在消息里，或给一个无需登录的文档直链";
                }
                yield docAnalyzerService.analyzeOnly(kaiUserId, src.text, src.origin);
            }
            default -> throw new IllegalArgumentException("未知工具：" + name);
        };
    }

    /** 接入资料三通道取文本：上传文件 > 消息粘贴原文 > 文档直链（系统拉取正文，并带回来源主机）。 */
    private KaiDocAnalyzerService.DocSource resolveIntegrationSource(Long kaiUserId, Map<String, Object> args) {
        String text = null;
        String fileToken = str(args.get("fileToken"));
        if (!fileToken.isBlank()) {
            KaiChatFileService.Entry f = chatFileService.own(kaiUserId, fileToken);
            text = f.text();
        }
        if (text == null || text.isBlank()) {
            text = str(args.get("docText"));
        }
        if (text == null || text.isBlank()) {
            String docUrl = str(args.get("docUrl"));
            if (!docUrl.isBlank()) {
                return docAnalyzerService.fetchDocSource(docUrl);
            }
        }
        return text == null ? null : new KaiDocAnalyzerService.DocSource(text, null);
    }

    /** 按令牌取「只分析」会话（过期/不存在返回 null，模型会走重新分析兜底）。 */
    private KaiDocAnalyzerService.AnalysisSession analysisSession(String token) {
        return docAnalyzerService.analysisSession(token);
    }

    /** 递归把 LocalDateTime/LocalDate/LocalTime 转为字符串（工具结果要序列化进模型上下文，Jackson 默认不支持日期时间类型）。 */
    private Object sanitizeDates(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), sanitizeDates(e.getValue()));
            return out;
        }
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object x : l) out.add(sanitizeDates(x));
            return out;
        }
        if (o instanceof java.time.LocalDateTime || o instanceof java.time.LocalDate
                || o instanceof java.time.LocalTime || o instanceof java.time.ZonedDateTime) {
            return String.valueOf(o);
        }
        return o;
    }

    private Object executeWriteTool(KaiExtAdapter a, String name, Map<String, Object> args, KaiExtConnection conn, Long kaiUserId) {
        if (name.startsWith("ext_mcp_")) {
            // 确认卡通过后真正执行：与只读同一调用链
            return executeTool(a, name, args, conn, kaiUserId, null);
        }
        if (name.startsWith("ext_plugin_")) {
            return executeTool(a, name, args, conn, kaiUserId, null);
        }
        return switch (name) {
            case "ext_import_records" -> {
                if (a == null) throw new IllegalArgumentException("当前未绑定系统，导入需要先在右上角选择或接入一个系统");
                yield a.createRecords(conn, str(args.get("objectType")), castList(args.get("records")));
            }
            case "ext_categorize" -> {
                if (a == null) throw new IllegalArgumentException("当前未绑定系统，分类需要先在右上角选择或接入一个系统");
                yield a.executeAction(conn, "categorize", args);
            }
            case "ext_create_bill" -> {
                if (a == null) throw new IllegalArgumentException("当前未绑定系统，开单需要先在右上角选择或接入一个系统");
                yield a.createBill(conn, args);
            }
            case "ext_submit_bill" -> {
                if (a == null) throw new IllegalArgumentException("当前未绑定系统，提交需要先在右上角选择或接入一个系统");
                yield a.executeAction(conn, "submit", args);
            }
            case "ext_integrate_system" -> {
                // 推荐路径：先 ext_analyze_integration 分析（缓存于 analysisToken），保存时直接复用
                String analysisToken = str(args.get("analysisToken"));
                KaiDocAnalyzerService.AnalysisSession session = analysisSession(analysisToken);
                // credentialJson = 完整凭据报文（登录换令牌自定义字段名等场景），走 credential 单值原样落库
                String apiKey = str(args.get("apiKey"));
                if (apiKey.isBlank() && !str(args.get("credentialJson")).isBlank()) {
                    apiKey = str(args.get("credentialJson"));
                }
                String authUsername = str(args.get("authUsername"));
                String authPassword = str(args.get("authPassword"));
                if (session != null) {
                    yield docAnalyzerService.connectFromSession(kaiUserId, session, str(args.get("supplement")),
                            str(args.get("systemName")), apiKey, authUsername, authPassword);
                }
                KaiDocAnalyzerService.DocSource text2 = resolveIntegrationSource(kaiUserId, args);
                if (text2 == null || text2.text == null || text2.text.isBlank()) {
                    throw new IllegalArgumentException("没有可保存的接入资料：请先用 ext_analyze_integration 分析，或提供上传文件/粘贴资料/文档直链其中之一");
                }
                yield docAnalyzerService.connectFromDoc(kaiUserId, text2.text, str(args.get("systemName")), text2.origin);
            }
            case "ext_update_connection" -> {
                Object cid = args.get("connectionId");
                if (cid == null) throw new IllegalArgumentException("请提供要修改的连接 id（可先用 ext_list_connections 查到）");
                Long id = Long.valueOf(String.valueOf(cid));
                Map<String, Object> fm = args.get("fieldMap") instanceof Map ? (Map<String, Object>) args.get("fieldMap") : null;
                Map<String, Object> ep = args.get("endpoints") instanceof Map ? (Map<String, Object>) args.get("endpoints") : null;
                yield connectionService.updateConfig(kaiUserId, id, str(args.get("name")), fm, ep);
            }
            default -> throw new IllegalArgumentException("未知工具：" + name);
        };
    }

    // ---------------- 提示词与步骤 ----------------

    private String buildSystemPrompt(KaiExtConnection conn, KaiExtAdapter extAdapter, String displayName,
                                     String skillBlock, String kbBlock, String answerMode) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「小楷」，一个帮助用户操作其接入的第三方ERP系统的智能助手。当前服务对象：")
                .append(displayName == null ? "用户" : displayName).append("。\n\n");
        if (conn == null || extAdapter == null) {
            sb.append("【当前模式：纯聊天】\n");
            sb.append("- 未绑定任何系统：可正常聊天问答，也可以自己完成第三方系统接入——流程：①用户给资料（上传文件/粘贴在消息里/文档网页直链，给链接就直接拉取分析，不要说打不开链接；只有动态页面拉不到正文时才请用户复制粘贴）→ ②调 ext_analyze_integration 分析 → ③把理解到的内容用人话讲给用户，缺什么（密钥、账号密码、接口地址）列问题清单问用户 → ④资料齐了调 ext_integrate_system 保存（带 analysisToken 和用户给的凭据）→ ⑤提醒可以试试测试连接和查询。查看/修改已有连接配置（ext_list_connections / ext_update_connection）。\n");
            sb.append("- 涉及查询/导入/开单等系统操作时，提示用户先在右上角选择或接入一个系统。\n");
        } else {
            sb.append("【当前已接入的系统】\n");
            sb.append("- 名称：").append(conn.getName()).append("\n");
            sb.append("- 类型：").append(KaiConnectionService.TYPE_LABELS.getOrDefault(conn.getType(), conn.getType())).append("\n");
            Map<String, String> types = extAdapter.supportedObjectTypes();
            sb.append("- 可操作的对象：").append(types.entrySet().stream()
                    .map(e -> e.getValue() + "（" + e.getKey() + "）").reduce((a, b) -> a + "、" + b).orElse("无")).append("\n");
            for (Map.Entry<String, String> e : types.entrySet()) {
                Map<String, String> fields = extAdapter.writableFields(e.getKey());
                if (!fields.isEmpty()) {
                    sb.append("- ").append(e.getValue()).append("可写字段：").append(fields.entrySet().stream()
                            .map(f -> f.getKey() + "=" + f.getValue()).reduce((a, b) -> a + "；" + b).orElse("")).append("\n");
                }
            }
        }
        // 技能（用户定制的行为设定，启用中才注入）与知识库检索资料（命中才注入）——都不改变默认行为
        if (skillBlock != null && !skillBlock.isBlank()) {
            sb.append("\n").append(skillBlock).append("\n");
        }
        if (kbBlock != null && !kbBlock.isBlank()) {
            sb.append("\n").append(kbBlock).append("\n");
        }
        sb.append("\n【数据分析链路（必须按此顺序，不要跳过）】\n");
        sb.append("1. 先查：用 ext_query_data 查数据。每次查询的结果都会自动保存成一份「结果集」，返回 resultSetId 与行数、可用字段清单。\n");
        sb.append("2. 再存：记住每次查询返回的 resultSetId 与标签。同一轮里后续的统计、对比都基于这些编号，不要再查一遍对方系统。\n");
        sb.append("3. 后算：需要分组汇总（按客户/物料/月份统计金额或数量）时用 ext_result_aggregate；需要看明细用 ext_result_query。\n");
        sb.append("4. 对比：需要「本期 vs 上期」「今年 vs 去年」时，先分别查询两次拿到两个结果集编号，再用 ext_result_compare 按同一维度对齐对比。\n");
        sb.append("5. 汇总：最后用工具算出来的真实数字组织结论（总量、增减、变化率、主要贡献方），不要自己在文字里心算或估算。\n");
        sb.append("铁律：\n");
        sb.append("- 所有金额、数量、占比都必须来自工具返回，严禁凭印象写数字；结果集里没有的字段不要假设存在（工具会告诉你可用字段）。\n");
        sb.append("- 查询时把日期区间写进 startDate/endDate，不要先拉全量再自己筛（单次返回有条数上限，全量会漏数据）。\n");
        sb.append("- 用户在后续对话里说「刚才那份」「上次查的」，优先用之前返回的 resultSetId；不确定时用 ext_result_query 按编号核对。\n");
        sb.append("- 上下文里以【本轮执行记录】【回答】开头的消息是历史轮次留下的记录，只作参考；你的新回答不要带这些标记，直接给结论。\n");
        sb.append("\n【行为铁律】\n");
        sb.append("1. 所有数据必须来自工具返回结果或用户原话，严禁编造编码、名称、数量、金额。\n");
        sb.append("2. 写操作（导入建档/智能分类/开单/提交单据/接入新系统/修改连接配置）调用工具后会出现确认卡片等待用户拍板，你只负责把计划讲清楚，绝不可以说「已经完成」。\n");
        sb.append("3. 用户提供的资料不完整时，先把缺什么清单式列出来问清楚，不要猜。\n");
        sb.append("4. 全程用简体中文回复，简洁、直接、有条理。\n");
        sb.append("5. 对方的系统里没有的东西（查不到的客户/物料）就如实说没有，并建议先导入建档。\n");
        sb.append("\n【能力自查与数据准确性（必须遵守）】\n");
        sb.append("1. 用户问「你有没有 XX 能力 / 能不能改 XX」时，先按本提示词列出的能力清单核对：\n");
        sb.append("   有就直接调用工具并把结果摆给用户看，禁止先回答「我没有权限」「没有这个工具」。\n");
        sb.append("2. 用户纠正你（「你不是支持吗」）时，不要立刻改口，也不要临时编一个新解释来圆：\n");
        sb.append("   先核对能力清单——确实有就承认并立即执行；确实没有就说明具体缺哪一项。\n");
        sb.append("   同一次对话里，不允许对同一个能力给出两次相反的答案。\n");
        sb.append("3. 数据准确性：用户会拿这里的结果去做采购、发货、对账，错一个数字就是业务事故。因此：\n");
        sb.append("   - 所有数字只能来自工具返回，严禁心算、估算、用「大约/左右」糊过去；\n");
        sb.append("   - 查不到就如实说查不到，不许拿全量清单顶替，不许编造编码、名称、数量、金额；\n");
        sb.append("   - 给结论必须写清统计口径：时间区间、是否含草稿、按哪个字段汇总；\n");
        sb.append("   - 口径不明或信息不足时先问，不要猜着算。\n");
        sb.append("4. 不确定的事情说不确定，并给出可核对的路径（去哪个页面看、用什么条件筛）。\n");
        // 答复呈现方式（2026-10-22）：用户在下拉里选或对话里明确说过，按此组织最终答复
        if ("summary".equalsIgnoreCase(answerMode)) {
            sb.append("\n【答复呈现方式：汇总（本轮必须遵守）】\n");
            sb.append("1. 只报最终结果：直接给出结论、关键数字或操作结果，不描述执行步骤、不罗列中间查询过程。\n");
            sb.append("2. 答复必须自洽：用户不看过程也能看懂，禁止出现「见上方明细」「见执行过程」「见表格」这类指向过程的说法。\n");
            sb.append("3. 需要列举时最多三条要点，其余用一句话概括。\n");
        } else {
            sb.append("\n【答复呈现方式：明细（本轮必须遵守）】\n");
            sb.append("1. 先给结论，再按需说明关键口径与关键步骤（时间区间、是否含草稿、按哪个字段汇总、做了哪几步查询）。\n");
            sb.append("2. 过程说明只写与结论相关的部分，不要复述工具参数。\n");
        }
        return sb.toString();
    }

    /** 追加步骤并即时回调（流式端点据此逐步推给前端；回调异常不影响对话本身）。 */
    private void emit(List<Map<String, Object>> steps, java.util.function.Consumer<Map<String, Object>> onStep,
                      Map<String, Object> s) {
        steps.add(s);
        if (onStep != null) {
            try {
                onStep.accept(s);
            } catch (Exception e) {
                log.debug("[小楷独立版] 步骤回调失败: {}", e.getMessage());
            }
        }
    }

    private Map<String, Object> step(String type, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("step", type);
        m.put("text", text);
        return m;
    }

    private Map<String, Object> actionStep(String tool, Map<String, Object> args) {
        Map<String, Object> m = step("ACTION", describeToolAction(tool));
        m.put("tool", tool);
        return m;
    }

    private String describeToolAction(String tool) {
        return switch (tool) {
            case "ext_query_data" -> "正在查询接入系统里的资料";
            case "ext_import_records" -> "正在整理要导入的资料";
            case "ext_categorize" -> "正在整理分类计划";
            case "ext_create_bill" -> "正在整理开单信息";
            case "ext_submit_bill" -> "正在准备提交单据";
            case "ext_list_connections" -> "正在查看已接入的系统";
            case "ext_analyze_integration" -> "正在分析接入资料（识别对象、接口与鉴权方式）";
            case "ext_integrate_system" -> "正在生成接入配置";
            case "ext_update_connection" -> "正在整理连接配置修改内容";
            case "ext_result_query" -> "正在查看已保存的查询结果";
            case "ext_result_aggregate" -> "正在对查询结果做统计";
            case "ext_result_compare" -> "正在对比两期查询结果";
            default -> "正在处理";
        };
    }

    /** 结果集人话标签：优先用模型给的 label，否则按对象名 + 日期区间拼接。 */
    private String resultSetLabel(String typeLabel, Map<String, Object> args) {
        String custom = str(args.get("label"));
        if (!custom.isBlank()) return custom;
        String start = str(args.get("startDate"));
        String end = str(args.get("endDate"));
        String kw = str(args.get("keyword"));
        StringBuilder sb = new StringBuilder(typeLabel == null || typeLabel.isBlank() ? "查询结果" : typeLabel);
        if (!start.isBlank() || !end.isBlank()) {
            sb.append(" ").append(start.isBlank() ? "最早" : start)
                    .append(" 至 ").append(end.isBlank() ? "至今" : end);
        }
        if (!kw.isBlank()) sb.append(" 关键词 ").append(kw);
        return sb.toString();
    }

    private Long reqLong(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) throw new IllegalArgumentException("请给出 " + key + "（查询结果里会返回）");
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(key + " 必须是数字编号，收到的是：" + v);
        }
    }

    private Integer optInt(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null || String.valueOf(v).isBlank()) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) if (x != null && !String.valueOf(x).isBlank()) out.add(String.valueOf(x));
        } else if (o != null && !String.valueOf(o).isBlank()) {
            for (String s : String.valueOf(o).split("[,，]")) if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }

    private String describeResult(String tool, Object data) {
        if (data instanceof List<?> l) {
            if ("ext_list_connections".equals(tool)) return "共接入 " + l.size() + " 个系统";
            // 带上字段清单：会话恢复后模型仍知道这份结果有哪些字段，避免拿中文别名去试错
            String cols = "";
            if (!l.isEmpty() && l.get(0) instanceof Map<?, ?> m && !m.isEmpty()) {
                List<String> keys = m.keySet().stream().map(String::valueOf).limit(12).toList();
                cols = "，字段：" + String.join("、", keys) + (m.size() > 12 ? " 等" : "");
            }
            return "查到 " + l.size() + " 条记录" + cols;
        }
        if (data instanceof Map<?, ?> m) {
            switch (tool) {
                case "ext_analyze_integration" -> {
                    Object eps = m.get("endpoints");
                    int n = eps instanceof Map<?, ?> em ? em.size() : 0;
                    return "分析完成" + (n > 0 ? "：识别出 " + n + " 类可操作对象" : "");
                }
                case "ext_result_aggregate" -> {
                    return "统计完成：共 " + str(m.get("groupCount")) + " 个分组，参与统计 "
                            + str(m.get("inputRows")) + " 行";
                }
                case "ext_result_compare" -> {
                    return "对比完成：" + str(m.get("labelA")) + " 总计 " + str(m.get("totalA"))
                            + "，" + str(m.get("labelB")) + " 总计 " + str(m.get("totalB"))
                            + "，变化 " + str(m.get("totalChangePct")) + "%";
                }
                case "ext_result_query" -> {
                    return "取回 " + str(m.get("total")) + " 条明细";
                }
                default -> {
                    // 落到下面的通用返回
                }
            }
        }
        return "处理完成";
    }

    /** 接入确认卡展示内容：提炼关键字段，避免整段接口文档原文本进卡片。 */
    private Map<String, Object> integrateConfirmDetail(Map<String, Object> args) {
        Map<String, Object> d = new LinkedHashMap<>();
        String sysName = str(args.get("systemName"));
        if (!sysName.isBlank()) d.put("系统名称", sysName);
        String doc = str(args.get("docText"));
        if (!doc.isBlank()) {
            String head = doc.replaceAll("\\s+", " ").trim();
            d.put("接入资料摘要", head.length() > 60 ? head.substring(0, 60) + "…" : head);
        }
        if (!str(args.get("analysisToken")).isBlank()) d.put("配置来源", "此前 AI 分析的识别结果");
        if (!str(args.get("apiKey")).isBlank()) d.put("凭据", "已提供（加密保存，不回显）");
        if (!str(args.get("authUsername")).isBlank()) d.put("登录账号", str(args.get("authUsername")));
        return d;
    }

    private String summarize(String tool, Map<String, Object> args) {
        return switch (tool) {
            case "ext_import_records" -> {
                List<?> recs = castList(args.get("records"));
                String names = recs == null ? "" : recs.stream().limit(5)
                        .map(r -> str(((Map<?, ?>) r).get("name"))).filter(s -> !s.isEmpty())
                        .reduce((a, b) -> a + "、" + b).orElse("");
                yield "在接入系统里导入 " + str(args.get("objectType")) + " 档案 "
                        + (recs == null ? 0 : recs.size()) + " 条" + (names.isEmpty() ? "" : "：" + names)
                        + (recs != null && recs.size() > 5 ? " 等" : "");
            }
            case "ext_categorize" -> {
                List<?> recs = castList(args.get("assignments"));
                yield "调整 " + (recs == null ? 0 : recs.size()) + " 条资料的分类";
            }
            case "ext_create_bill" -> {
                Object items = args.get("items");
                int n = items instanceof List<?> l ? l.size() : 0;
                yield "在接入系统里创建销售订单（客户：" + str(args.get("customerName")) + "，明细 " + n + " 行）";
            }
            case "ext_submit_bill" -> "提交单据 " + str(args.get("billNo"));
            case "ext_integrate_system" -> {
                String sysName = str(args.get("systemName"));
                String doc = str(args.get("docText"));
                String src;
                if (!sysName.isBlank()) {
                    src = "「" + sysName + "」";
                } else {
                    String head = doc.replaceAll("\\s+", " ").trim();
                    src = head.length() > 30 ? "新系统（依据资料：" + head.substring(0, 30) + "…）"
                            : (head.isEmpty() ? "新系统" : "新系统（" + head + "）");
                }
                yield "接入" + src + "：保存接口配置并加密保存凭据（需要您确认）";
            }
            case "ext_update_connection" -> {
                StringBuilder sb = new StringBuilder("修改系统连接（编号 " + str(args.get("connectionId")) + "）的");
                List<String> parts = new ArrayList<>();
                if (args.get("fieldMap") instanceof Map<?, ?> fm && !fm.isEmpty()) parts.add("字段映射");
                if (args.get("endpoints") instanceof Map<?, ?> ep && !ep.isEmpty()) parts.add("接口地址");
                if (!str(args.get("name")).isBlank()) parts.add("连接名称");
                if (parts.isEmpty()) yield "修改系统连接配置";
                yield sb.append(String.join("、", parts)).append("（需要您确认）").toString();
            }
            default -> "执行操作";
        };
    }

    // ---------------- 会话与工具消息 ----------------

    /** 工具双份结果：display 给界面渲染（全量行），model 给模型上下文（元数据+样本，防撑爆）。 */
    private record ToolOutput(Object display, Object model) {}

    private static class Session {
        final List<Map<String, Object>> messages = Collections.synchronizedList(new ArrayList<>());
        long expireAt = System.currentTimeMillis() + 60 * 60 * 1000L;
        /** 本会话按当前模型窗口算出的历史字符预算 */
        volatile int budgetChars = DEFAULT_HISTORY_CHARS;
        volatile boolean pendingConfirm = false;
    }

    private static class PendingAction {
        final Long kaiUserId;
        final Long sessionId;
        final Long connectionId;
        final String tool;
        final Map<String, Object> args;
        final String summary;
        final long expireAt;

        PendingAction(Long kaiUserId, Long sessionId, Long connectionId, String tool,
                      Map<String, Object> args, String summary, long expireAt) {
            this.kaiUserId = kaiUserId;
            this.sessionId = sessionId;
            this.connectionId = connectionId;
            this.tool = tool;
            this.args = args;
            this.summary = summary;
            this.expireAt = expireAt;
        }
    }

    private Session session(String memKey) {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> now > s.expireAt);
        Session s = sessions.computeIfAbsent(memKey, k -> new Session());
        s.expireAt = now + SESSION_TTL_MS;
        return s;
    }

    private void injectSystemNote(Session s, String note) {
        s.messages.add(msg("user", "（系统提示）" + note));
        trim(s);
    }

    /** 按字符预算淘汰最老对话：整轮删除（保留至少一轮），不切开工具调用配对。 */
    private void trim(Session s) {
        int budget = s.budgetChars > 0 ? s.budgetChars : DEFAULT_HISTORY_CHARS;
        while (s.messages.size() > 4 && charsOf(s.messages) > budget) {
            int cut = firstTurnEnd(s.messages);
            if (cut <= 0) break;
            s.messages.subList(0, cut).clear();
        }
    }

    /** 第一条后续 user 消息的位置 = 第一条对话轮次的结束边界。 */
    private int firstTurnEnd(List<Map<String, Object>> msgs) {
        for (int i = 1; i < msgs.size(); i++) {
            if ("user".equals(String.valueOf(msgs.get(i).get("role")))) return i;
        }
        return 0;
    }

    private long charsOf(List<Map<String, Object>> msgs) {
        long n = 0;
        for (Map<String, Object> m : msgs) {
            Object c = m.get("content");
            if (c != null) n += String.valueOf(c).length();
            Object tc = m.get("tool_calls");
            if (tc != null) n += String.valueOf(tc).length();
        }
        return n;
    }

    private Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private Map<String, Object> toolMsg(String callId, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("tool_call_id", callId);
        m.put("content", content);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rawToolCalls(List<Map<String, Object>> callList) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : callList) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", c.get("name"));
            fn.put("arguments", str(c.get("arguments")));
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", c.get("id"));
            t.put("type", "function");
            t.put("function", fn);
            out.add(t);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(Object arguments) {
        if (arguments instanceof Map) return (Map<String, Object>) arguments;
        String s = str(arguments);
        if (s.isBlank()) return new LinkedHashMap<>();
        try {
            return mapper.readValue(s, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("模型参数无法解析，请重试");
        }
    }

    private Map<String, Object> fn(String name, String desc, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", desc);
        function.put("parameters", parameters);
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("type", "function");
        t.put("function", function);
        return t;
    }

    private static class ParamsBuilder {
        final Map<String, Object> props = new LinkedHashMap<>();
        final List<String> required = new ArrayList<>();

        ParamsBuilder req(String name, String type, String desc) {
            prop(name, type, desc);
            required.add(name);
            return this;
        }

        ParamsBuilder opt(String name, String type, String desc) {
            prop(name, type, desc);
            return this;
        }

        private void prop(String name, String type, String desc) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", type);
            p.put("description", desc);
            props.put(name, p);
        }

        Map<String, Object> build() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("type", "object");
            out.put("properties", props);
            out.put("required", required);
            return out;
        }
    }

    private ParamsBuilder params() {
        return new ParamsBuilder();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object o) {
        if (!(o instanceof List<?> l)) return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object x : l) {
            if (x instanceof Map) out.add((Map<String, Object>) x);
        }
        return out;
    }

    private String safeJson(Object o) {
        try {
            String s = mapper.writeValueAsString(o);
            return s.length() > 2000 ? s.substring(0, 2000) + "…" : s;
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    /** 思考强度归一（2026-11-22 三档）：只认 light/high/max；旧调用只传 deepThinking 布尔时按 true=high / false=light 兜底 */
    private String normalizeThoughtLevel(String level, boolean deepThinking) {
        if (level != null) {
            String l = level.trim().toLowerCase(java.util.Locale.ROOT);
            if ("light".equals(l) || "high".equals(l) || "max".equals(l)) return l;
        }
        return deepThinking ? "high" : "light";
    }

    private String friendlyModel(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (m.startsWith("积分不足")) return m;
        if (m.contains("401") || m.contains("Authentication Fails") || m.contains("api key")) {
            return "AI 模型密钥无效或无权限（对方拒绝认证 401）。请到「设置 → 模型服务商」核对该服务商的 API 密钥，点「测试连接」通过后再对话";
        }
        if (m.contains("403")) {
            return "AI 模型服务拒绝访问（403）。请到「设置 → 模型服务商」检查密钥与该模型是否已开通权限，点「测试连接」验证";
        }
        if (m.contains("402")) return "AI 模型账户余额不足，请到「设置 → 模型服务商」检查对应账户余额后充值再试";
        if (m.contains("没有可用的AI渠道")) return "AI 模型服务未配置，请到「设置 → 模型服务商」添加并启用一个服务商后再对话";
        return "处理时出现问题：" + m;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
