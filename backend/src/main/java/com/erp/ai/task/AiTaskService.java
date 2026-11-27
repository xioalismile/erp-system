package com.erp.ai.task;

import com.erp.ai.dto.AiDataScope;
import com.erp.ai.service.AiCrudAgentService;
import com.erp.ai.tool.AiTool;
import com.erp.ai.tool.ToolRegistry;
import com.erp.masterdata.service.ReferenceCheckService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小楷多步骤任务链（2026-10-26）。
 *
 * <p>口径：
 * <ul>
 *   <li>一条任务 = 一张确认卡；确认后按步骤顺序执行，不再逐步弹卡；</li>
 *   <li>步骤可声明依赖（dependsOn）；前置步骤失败/未执行时，下游标"未执行（前置失败）"，无依赖的步骤继续；</li>
 *   <li>步骤参数可用 {{序号.字段}} 引用前面步骤的结果（如 {{1.code}}、{{2.results.0.code}}）；</li>
 *   <li>任务与步骤落库，支撑查询、重跑失败步骤、撤销本次新建；</li>
 *   <li>撤销只删本任务创建、且未被业务数据引用的记录；遇到不支持/被引用立即停止并如实说明。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskService {

    private final JdbcTemplate jdbcTemplate;
    /**
     * 用 ApplicationContext 延迟取 ToolRegistry，避免「任务工具 → 任务服务 → 工具注册表 → 任务工具」的构造期循环依赖
     * （表现为工具已编译进包但启动注册清单里没有它，模型永远看不到多步骤能力）。
     */
    private final ApplicationContext applicationContext;
    private final AiCrudAgentService aiCrudAgentService;
    private final ReferenceCheckService referenceCheckService;
    private final ObjectMapper mapper = new ObjectMapper();

    private ToolRegistry toolRegistry() {
        return applicationContext.getBean(ToolRegistry.class);
    }

    private static final int MAX_STEPS = 30;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\d+)\\.([a-zA-Z0-9_.]+)}}");

    public static final String TOOL_NAME = "task_plan_execute";

    public static final String POLICY_MARK_DOWNSTREAM = "MARK_DOWNSTREAM";
    public static final String POLICY_SKIP = "SKIP";
    public static final String POLICY_STOP = "STOP";

    // ---------------- 执行 ----------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(AiDataScope scope, Map<String, Object> params) {
        ensureSchema();
        Long userId = scope != null ? scope.getUserId() : null;
        String username = scope != null ? scope.getUsername() : null;
        String goal = str(params.get("goal"));
        String policy = normalizePolicy(str(params.get("failPolicy")));
        Object rawSteps = params.get("steps");
        if (!(rawSteps instanceof List<?> list) || list.isEmpty()) {
            return fail("这次没有拆出可执行的步骤，请把要做的事再说具体一点。");
        }
        if (list.size() > MAX_STEPS) {
            return fail("一次最多执行 " + MAX_STEPS + " 个步骤，当前 " + list.size() + " 个，请拆成两次说。");
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object one = list.get(i);
            if (!(one instanceof Map<?, ?> m)) {
                return fail("第 " + (i + 1) + " 步格式不正确，未执行任何操作。");
            }
            Map<String, Object> s = (Map<String, Object>) m;
            String tool = str(s.get("tool"));
            if (tool.isEmpty()) {
                return fail("第 " + (i + 1) + " 步没有指定要做什么，未执行任何操作。");
            }
            if (TOOL_NAME.equals(tool)) {
                return fail("任务里不能再套任务（第 " + (i + 1) + " 步），请把步骤直接列出来。");
            }
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("stepNo", i + 1);
            step.put("title", str(s.get("title")).isEmpty() ? ("第 " + (i + 1) + " 步") : str(s.get("title")));
            step.put("tool", tool);
            step.put("params", s.get("params") instanceof Map<?, ?> pm ? (Map<String, Object>) pm : new LinkedHashMap<>());
            step.put("dependsOn", parseDepends(s.get("dependsOn")));
            steps.add(step);
        }

        long taskId = insertTask(userId, username, goal, policy, steps.size());
        for (Map<String, Object> s : steps) {
            insertStep(taskId, s);
        }
        log.info("[小楷任务] 开始执行 taskId={} 步骤数={} 失败策略={}", taskId, steps.size(), policy);

        Map<Integer, Map<String, Object>> results = new LinkedHashMap<>();
        Set<Integer> blocked = new HashSet<>();
        int success = 0, failed = 0, skipped = 0;

        for (Map<String, Object> s : steps) {
            int no = (Integer) s.get("stepNo");
            @SuppressWarnings("unchecked")
            List<Integer> deps = (List<Integer>) s.get("dependsOn");

            String blockedReason = null;
            for (Integer d : deps) {
                if (blocked.contains(d) || results.get(d) == null || !Boolean.TRUE.equals(results.get(d).get("success"))) {
                    blockedReason = "未执行（前置第 " + d + " 步未成功）";
                    break;
                }
            }
            if (blockedReason != null) {
                skipped++;
                blocked.add(no);
                updateStep(taskId, no, "未执行", blockedReason, null, null, null, null);
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rawParams = (Map<String, Object>) s.get("params");
            Map<String, Object> stepParams;
            try {
                stepParams = substitute(rawParams, results);
            } catch (IllegalArgumentException e) {
                failed++;
                blocked.add(no);
                updateStep(taskId, no, "失败", e.getMessage(), null, null, null, null);
                applyPolicy(policy, steps, no, results, blocked, taskId);
                if (POLICY_STOP.equals(policy)) break;
                continue;
            }

            String toolName = (String) s.get("tool");
            AiTool tool = toolRegistry().getTool(toolName);
            if (tool == null) {
                failed++;
                blocked.add(no);
                updateStep(taskId, no, "失败", "系统里没有「" + toolName + "」这个能力，未执行", null, null, null, null);
                applyPolicy(policy, steps, no, results, blocked, taskId);
                if (POLICY_STOP.equals(policy)) break;
                continue;
            }
            String deny = toolRegistry().checkPermission(toolName, scope);
            if (deny != null) {
                failed++;
                blocked.add(no);
                updateStep(taskId, no, "失败", deny, null, null, null, null);
                applyPolicy(policy, steps, no, results, blocked, taskId);
                if (POLICY_STOP.equals(policy)) break;
                continue;
            }

            updateStepStatus(taskId, no, "执行中", null);
            Map<String, Object> result;
            // 打码值防呆（2026-11-17 C 方案）：含 **** 的参数一律不落库
            String maskedPath = toolRegistry().isWriteTool(toolName)
                    ? com.erp.ai.util.SensitiveDataMasker.findMaskedValuePath(stepParams, "") : null;
            if (maskedPath != null) {
                failed++;
                blocked.add(no);
                updateStep(taskId, no, "失败",
                        "参数「" + maskedPath + "」里包含打码值（形如 138****8000），不能保存。请提供真实内容后重试",
                        null, null, null, null);
                applyPolicy(policy, steps, no, results, blocked, taskId);
                if (POLICY_STOP.equals(policy)) break;
                continue;
            }
            try {
                result = tool.execute(stepParams, scope);
                if (result == null) result = Map.of("success", false, "message", "执行没有返回结果");
            } catch (Exception e) {
                log.warn("[小楷任务] 第{}步执行异常 tool={}: {}", no, toolName, e.getMessage());
                result = Map.of("success", false, "message", e.getMessage() == null ? "执行失败" : e.getMessage());
            }
            results.put(no, result);

            if (Boolean.TRUE.equals(result.get("success"))) {
                success++;
                updateStep(taskId, no, "成功", str(result.get("message")),
                        str(result.get("id")), str(result.get("code")), str(result.get("name")),
                        safeJson(result));
            } else {
                failed++;
                blocked.add(no);
                updateStep(taskId, no, "失败", str(result.get("message")),
                        str(result.get("id")), str(result.get("code")), str(result.get("name")),
                        safeJson(result));
                applyPolicy(policy, steps, no, results, blocked, taskId);
                if (POLICY_STOP.equals(policy)) break;
            }
        }

        String status = failed == 0 && skipped == 0 ? "完成" : (success > 0 ? "部分完成" : "失败");
        finishTask(taskId, status, success, failed, skipped);
        log.info("[小楷任务] 执行结束 taskId={} 状态={} 成功{} 失败{} 未执行{}", taskId, status, success, failed, skipped);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", failed == 0 && skipped == 0);
        out.put("partial", failed > 0 || skipped > 0);
        out.put("taskId", taskId);
        out.put("taskNo", taskNo(taskId));
        out.put("goal", goal);
        out.put("status", status);
        out.put("successCount", success);
        out.put("failCount", failed);
        out.put("skippedCount", skipped);
        out.put("steps", loadSteps(taskId));
        out.put("message", summaryText(taskId, status, success, failed, skipped));
        return out;
    }

    /** 失败策略：STOP=后续全部不执行；MARK_DOWNSTREAM=只把依赖失败步骤的下游标未执行；SKIP=继续。 */
    private void applyPolicy(String policy, List<Map<String, Object>> steps, int failedNo,
                             Map<Integer, Map<String, Object>> results, Set<Integer> blocked, long taskId) {
        if (POLICY_SKIP.equals(policy)) return;
        if (POLICY_STOP.equals(policy)) {
            for (Map<String, Object> s : steps) {
                int no = (Integer) s.get("stepNo");
                if (no <= failedNo || blocked.contains(no)) continue;
                updateStep(taskId, no, "未执行", "未执行（前一步失败，已按策略停止）", null, null, null, null);
                blocked.add(no);
            }
            return;
        }
        // MARK_DOWNSTREAM：迭代标记所有直接/间接依赖失败步骤的后续步骤
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map<String, Object> s : steps) {
                int no = (Integer) s.get("stepNo");
                if (blocked.contains(no)) continue;
                @SuppressWarnings("unchecked")
                List<Integer> deps = (List<Integer>) s.get("dependsOn");
                for (Integer d : deps) {
                    if (blocked.contains(d)) {
                        updateStep(taskId, no, "未执行", "未执行（前置第 " + d + " 步未成功）", null, null, null, null);
                        blocked.add(no);
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    // ---------------- 重跑失败步骤 ----------------

    public Map<String, Object> retryFailed(Long taskId, AiDataScope scope) {
        ensureSchema();
        Map<String, Object> task = loadTask(taskId);
        if (task == null) return fail("任务不存在");
        requireOwner(task, scope);

        List<Map<String, Object>> all = loadSteps(taskId);
        Map<Integer, Map<String, Object>> results = new LinkedHashMap<>();
        Set<Integer> blocked = new HashSet<>();
        int success = 0, failed = 0, skipped = 0;

        for (Map<String, Object> s : all) {
            int no = num(s.get("stepNo"));
            String status = str(s.get("status"));
            if ("成功".equals(status)) {
                success++;
                results.put(no, parseJsonMap(str(s.get("resultJson"))));
                continue;
            }
            if ("已撤销".equals(status)) continue;

            List<Integer> deps = parseDepends(s.get("dependsOn"));
            String blockedReason = null;
            for (Integer d : deps) {
                if (blocked.contains(d) || results.get(d) == null || !Boolean.TRUE.equals(results.get(d).get("success"))) {
                    blockedReason = "未执行（前置第 " + d + " 步未成功）";
                    break;
                }
            }
            if (blockedReason != null) {
                skipped++;
                blocked.add(no);
                updateStep(taskId, no, "未执行", blockedReason, null, null, null, null);
                continue;
            }

            AiTool tool = toolRegistry().getTool(str(s.get("tool")));
            if (tool == null) {
                failed++;
                blocked.add(no);
                updateStep(taskId, no, "失败", "系统里没有这个能力，未执行", null, null, null, null);
                continue;
            }
            String deny = toolRegistry().checkPermission(str(s.get("tool")), scope);
            if (deny != null) {
                failed++;
                blocked.add(no);
                updateStep(taskId, no, "失败", deny, null, null, null, null);
                continue;
            }
            Map<String, Object> stepParams;
            try {
                stepParams = substitute(parseJsonMap(str(s.get("paramsJson"))), results);
            } catch (IllegalArgumentException e) {
                failed++;
                blocked.add(no);
                updateStep(taskId, no, "失败", e.getMessage(), null, null, null, null);
                continue;
            }

            updateStepStatus(taskId, no, "执行中", null);
            Map<String, Object> result;
            // 打码值防呆（2026-11-17 C 方案）：含 **** 的参数一律不落库
            String maskedPath2 = toolRegistry().isWriteTool(str(s.get("tool")))
                    ? com.erp.ai.util.SensitiveDataMasker.findMaskedValuePath(stepParams, "") : null;
            if (maskedPath2 != null) {
                failed++;
                blocked.add(no);
                updateStep(taskId, no, "失败",
                        "参数「" + maskedPath2 + "」里包含打码值（形如 138****8000），不能保存。请提供真实内容后重试",
                        null, null, null, null);
                continue;
            }
            try {
                result = tool.execute(stepParams, scope);
                if (result == null) result = Map.of("success", false, "message", "执行没有返回结果");
            } catch (Exception e) {
                result = Map.of("success", false, "message", e.getMessage() == null ? "执行失败" : e.getMessage());
            }
            results.put(no, result);
            if (Boolean.TRUE.equals(result.get("success"))) {
                success++;
                updateStep(taskId, no, "成功", str(result.get("message")),
                        str(result.get("id")), str(result.get("code")), str(result.get("name")), safeJson(result));
            } else {
                failed++;
                blocked.add(no);
                updateStep(taskId, no, "失败", str(result.get("message")),
                        str(result.get("id")), str(result.get("code")), str(result.get("name")), safeJson(result));
            }
        }

        String status = failed == 0 && skipped == 0 ? "完成" : (success > 0 ? "部分完成" : "失败");
        finishTask(taskId, status, success, failed, skipped);
        Map<String, Object> out = taskPayload(taskId);
        out.put("message", summaryText(taskId, status, success, failed, skipped));
        return out;
    }

    // ---------------- 撤销本次新建 ----------------

    public Map<String, Object> undo(Long taskId, AiDataScope scope) {
        ensureSchema();
        Map<String, Object> task = loadTask(taskId);
        if (task == null) return fail("任务不存在");
        requireOwner(task, scope);
        Long userId = scope != null ? scope.getUserId() : null;

        List<Map<String, Object>> steps = loadSteps(taskId);
        Collections.reverse(steps);

        int undone = 0;
        String stopReason = null;
        for (Map<String, Object> s : steps) {
            String status = str(s.get("status"));
            if (!"成功".equals(status)) continue;
            String tool = str(s.get("tool"));
            String title = str(s.get("title"));
            int no = num(s.get("stepNo"));

            if (!tool.startsWith("create_")) {
                stopReason = "第 " + no + " 步「" + title + "」不是新建资料，暂不支持自动撤销；为避免留下悬空引用，已停止撤销。";
                break;
            }
            String intent = aiCrudAgentService.resolveIntent(tool.substring("create_".length()));
            if (intent == null) {
                stopReason = "第 " + no + " 步「" + title + "」的数据类型无法识别，暂不支持自动撤销。";
                break;
            }
            String id = str(s.get("id"));
            if (id.isEmpty()) {
                stopReason = "第 " + no + " 步「" + title + "」没有记录新建数据的主键，无法撤销。";
                break;
            }
            String deny = checkReferenced(intent, id);
            if (deny != null) {
                stopReason = "第 " + no + " 步「" + title + "」" + deny + "；已停止撤销。";
                break;
            }
            try {
                if ("QUERY_PRODUCTION_BOM".equals(intent)) {
                    // BOM 删除必须级联清子件（与 BOM 管理页面同一口径）
                    jdbcTemplate.update("DELETE FROM bom_item WHERE bom_id = ?", Long.valueOf(id));
                    jdbcTemplate.update("DELETE FROM bom WHERE bom_id = ?", Long.valueOf(id));
                    updateStep(taskId, no, "已撤销", "已撤销本次新建", null, null, null, null);
                } else {
                    Map<String, Object> del = aiCrudAgentService.delete(intent, Map.of("id", Long.valueOf(id)), userId);
                    if (!Boolean.TRUE.equals(del.get("success"))) {
                        stopReason = "第 " + no + " 步「" + title + "」撤销失败：" + str(del.get("message"));
                        break;
                    }
                    updateStep(taskId, no, "已撤销", "已撤销本次新建", null, null, null, null);
                }
                undone++;
            } catch (Exception e) {
                stopReason = "第 " + no + " 步「" + title + "」撤销失败：" + e.getMessage();
                break;
            }
        }

        String status = undone > 0 ? (stopReason == null ? "已撤销" : "部分撤销") : "撤销未执行";
        updateTaskStatus(taskId, status);
        Map<String, Object> out = taskPayload(taskId);
        out.put("success", undone > 0 && stopReason == null);
        out.put("undoneCount", undone);
        out.put("message", stopReason == null
                ? ("已撤销本次新建 " + undone + " 条记录")
                : ("已撤销 " + undone + " 条；" + stopReason));
        return out;
    }

    /** 撤销前的引用校验：被业务数据引用即禁止删除；不支持自动校验的类型一律拒绝。 */
    private String checkReferenced(String intent, String id) {
        try {
            Long pk = Long.valueOf(id);
            switch (intent) {
                case "QUERY_BASEDATA_MATERIAL":
                    return referenceCheckService.checkMaterial(pk);
                case "QUERY_BASEDATA_CUSTOMER":
                    return referenceCheckService.checkCustomer(pk);
                case "QUERY_BASEDATA_SUPPLIER":
                    return referenceCheckService.checkSupplier(pk);
                case "QUERY_PRODUCTION_BOM":
                    return checkBomReferenced(pk);
                default:
                    return "暂不支持自动撤销该数据类型，请到对应页面手工删除";
            }
        } catch (Exception e) {
            return "引用校验失败（" + e.getMessage() + "），为避免误删已停止撤销";
        }
    }

    private String checkBomReferenced(Long bomId) {
        String[][] refs = {
                {"work_order", "生产工单", "bom_id"},
                {"routing", "工艺路线", "bom_id"},
                {"sample_order", "打样单", "generated_bom_id"},
                {"subcontract_order_item", "委外订单明细", "bom_id"}
        };
        List<String> hits = new ArrayList<>();
        for (String[] r : refs) {
            try {
                Long cnt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + r[0] + " WHERE " + r[2] + " = ?", Long.class, bomId);
                if (cnt != null && cnt > 0) hits.add(r[1] + "(" + cnt + "条)");
            } catch (Exception ignore) {
                // 表在部分环境可能不存在，跳过
            }
        }
        return hits.isEmpty() ? null : "BOM已被业务数据引用，无法撤销：" + String.join("、", hits);
    }

    // ---------------- 查询 ----------------

    public Map<String, Object> getTask(Long taskId, AiDataScope scope) {
        ensureSchema();
        Map<String, Object> task = loadTask(taskId);
        if (task == null) return fail("任务不存在");
        requireOwner(task, scope);
        return taskPayload(taskId);
    }

    public List<Map<String, Object>> listBySession(String sessionId, AiDataScope scope) {
        ensureSchema();
        Long userId = scope != null ? scope.getUserId() : null;
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT * FROM ai_task WHERE session_id = ? AND user_id = ? ORDER BY task_id DESC LIMIT 20",
                sessionId, userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : tasks) {
            Long id = ((Number) t.get("task_id")).longValue();
            out.add(taskPayload(id));
        }
        return out;
    }

    private Map<String, Object> taskPayload(Long taskId) {
        Map<String, Object> t = loadTask(taskId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (t == null) return fail("任务不存在");
        out.put("success", true);
        out.put("taskId", taskId);
        out.put("taskNo", taskNo(taskId));
        out.put("goal", t.get("goal"));
        out.put("status", t.get("status"));
        out.put("failPolicy", t.get("fail_policy"));
        out.put("successCount", t.get("success_steps"));
        out.put("failCount", t.get("failed_steps"));
        out.put("skippedCount", t.get("skipped_steps"));
        out.put("steps", loadSteps(taskId));
        return out;
    }

    private Map<String, Object> loadTask(Long taskId) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT * FROM ai_task WHERE task_id = ?", taskId);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<Map<String, Object>> loadSteps(Long taskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ai_task_step WHERE task_id = ? ORDER BY step_no", taskId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("line", r.get("step_no"));
            // 内部执行/重跑/撤销按 stepNo 定位；line 仅用于前端展示，两者必须同时给
            s.put("stepNo", r.get("step_no"));
            s.put("title", r.get("title"));
            s.put("tool", r.get("tool"));
            s.put("status", r.get("status"));
            s.put("message", r.get("message"));
            s.put("id", r.get("result_id"));
            s.put("code", r.get("result_code"));
            s.put("name", r.get("result_name"));
            s.put("result", parseJsonMap(str(r.get("result_json"))));
            // 重跑必须拿回原始参数；此前漏给 paramsJson 会让重跑步骤以空参数执行（实测建出空物料）
            s.put("paramsJson", r.get("params_json"));
            s.put("dependsOn", r.get("depends_on"));
            out.add(s);
        }
        return out;
    }

    private void requireOwner(Map<String, Object> task, AiDataScope scope) {
        Object owner = task.get("user_id");
        Long userId = scope != null ? scope.getUserId() : null;
        if (owner == null || userId == null || !userId.equals(((Number) owner).longValue())) {
            throw new IllegalArgumentException("只能查看/操作自己发起的任务");
        }
    }

    // ---------------- 参数占位符 ----------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> substitute(Map<String, Object> params, Map<Integer, Map<String, Object>> results) {
        if (params == null || params.isEmpty()) return params;
        Object out = substituteValue(params, results);
        return (Map<String, Object>) out;
    }

    private Object substituteValue(Object value, Map<Integer, Map<String, Object>> results) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(String.valueOf(e.getKey()), substituteValue(e.getValue(), results));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object o : list) copy.add(substituteValue(o, results));
            return copy;
        }
        if (value instanceof String s) {
            Matcher matcher = PLACEHOLDER.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                int stepNo = Integer.parseInt(matcher.group(1));
                String path = matcher.group(2);
                Object resolved = resolvePath(stepNo, path, results);
                if (resolved == null) {
                    throw new IllegalArgumentException("第 " + stepNo + " 步没有可用的「" + path
                            + "」（可能前置步骤未成功），本步未执行");
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(resolved)));
            }
            matcher.appendTail(sb);
            return sb.toString();
        }
        return value;
    }

    private Object resolvePath(int stepNo, String path, Map<Integer, Map<String, Object>> results) {
        Object cur = results.get(stepNo);
        if (cur == null) return null;
        for (String key : path.split("\\.")) {
            if (cur instanceof Map<?, ?> m) {
                cur = m.get(key);
            } else if (cur instanceof List<?> l && key.matches("\\d+")) {
                int idx = Integer.parseInt(key);
                cur = idx < l.size() ? l.get(idx) : null;
            } else {
                return null;
            }
            if (cur == null) return null;
        }
        return cur;
    }

    // ---------------- 落库 ----------------

    private long insertTask(Long userId, String username, String goal, String policy, int total) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO ai_task(session_id, user_id, username, goal, fail_policy, status, total_steps, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, '执行中', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING task_id",
                Long.class, null, userId, username, cut(goal, 500), policy, total);
        return id == null ? 0L : id;
    }

    private void insertStep(long taskId, Map<String, Object> s) {
        jdbcTemplate.update(
                "INSERT INTO ai_task_step(task_id, step_no, title, tool, params_json, depends_on, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, '待执行')",
                taskId, s.get("stepNo"), cut(str(s.get("title")), 200), cut(str(s.get("tool")), 100),
                safeJson(s.get("params")), dependsText((List<?>) s.get("dependsOn")));
    }

    private void updateStep(long taskId, int stepNo, String status, String message,
                            String resultId, String resultCode, String resultName, String resultJson) {
        jdbcTemplate.update(
                "UPDATE ai_task_step SET status=?, message=?, result_id=?, result_code=?, result_name=?, result_json=?, " +
                        "finished_at=CASE WHEN ? IN ('成功','失败','未执行','已撤销') THEN CURRENT_TIMESTAMP ELSE finished_at END " +
                        "WHERE task_id=? AND step_no=?",
                status, cut(message, 2000), nullIfEmpty(resultId), nullIfEmpty(resultCode), nullIfEmpty(resultName),
                emptyToNull(resultJson), status, taskId, stepNo);
    }

    private void updateStepStatus(long taskId, int stepNo, String status, String message) {
        jdbcTemplate.update("UPDATE ai_task_step SET status=?, message=COALESCE(?, message), started_at=CURRENT_TIMESTAMP " +
                "WHERE task_id=? AND step_no=?", status, message, taskId, stepNo);
    }

    private void finishTask(long taskId, String status, int success, int failed, int skipped) {
        jdbcTemplate.update("UPDATE ai_task SET status=?, success_steps=?, failed_steps=?, skipped_steps=?, updated_at=CURRENT_TIMESTAMP " +
                "WHERE task_id=?", status, success, failed, skipped, taskId);
    }

    private void updateTaskStatus(long taskId, String status) {
        jdbcTemplate.update("UPDATE ai_task SET status=?, updated_at=CURRENT_TIMESTAMP WHERE task_id=?", status, taskId);
    }

    /** 表结构自愈：租户库/新环境首次使用时自动建表，幂等。 */
    public void ensureSchema() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_task (" +
                    "task_id BIGSERIAL PRIMARY KEY, task_no VARCHAR(64), session_id VARCHAR(128), user_id BIGINT, " +
                    "username VARCHAR(128), goal VARCHAR(500), fail_policy VARCHAR(32) DEFAULT 'MARK_DOWNSTREAM', " +
                    "status VARCHAR(32), total_steps INT DEFAULT 0, success_steps INT DEFAULT 0, failed_steps INT DEFAULT 0, " +
                    "skipped_steps INT DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ai_task_session ON ai_task(session_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ai_task_user ON ai_task(user_id)");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_task_step (" +
                    "step_id BIGSERIAL PRIMARY KEY, task_id BIGINT NOT NULL, step_no INT NOT NULL, title VARCHAR(200), " +
                    "tool VARCHAR(100), params_json TEXT, depends_on VARCHAR(200), status VARCHAR(32), message TEXT, " +
                    "result_id VARCHAR(64), result_code VARCHAR(128), result_name VARCHAR(255), result_json TEXT, " +
                    "started_at TIMESTAMP, finished_at TIMESTAMP)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ai_task_step_task ON ai_task_step(task_id)");
        } catch (Exception e) {
            log.warn("[小楷任务] 任务表自愈失败: {}", e.getMessage());
            throw new IllegalStateException("任务表初始化失败：" + e.getMessage());
        }
    }

    // ---------------- 小工具 ----------------

    private String summaryText(Long taskId, String status, int success, int failed, int skipped) {
        StringBuilder sb = new StringBuilder("任务执行").append(status).append("：成功 ").append(success)
                .append(" 步，失败 ").append(failed).append(" 步，未执行 ").append(skipped).append(" 步。");
        for (Map<String, Object> s : loadSteps(taskId)) {
            sb.append("\n· 第 ").append(s.get("line")).append(" 步【").append(s.get("title")).append("】")
                    .append(s.get("status"));
            String code = str(s.get("code"));
            if (!code.isEmpty()) sb.append("：").append(code);
            String msg = str(s.get("message"));
            if (!msg.isEmpty() && !"成功".equals(s.get("status"))) sb.append("（").append(msg).append("）");
        }
        return sb.toString();
    }

    private String taskNo(Long taskId) {
        return taskId == null ? "" : "TK-" + String.format("%06d", taskId);
    }

    private String normalizePolicy(String p) {
        if (POLICY_SKIP.equalsIgnoreCase(p)) return POLICY_SKIP;
        if (POLICY_STOP.equalsIgnoreCase(p)) return POLICY_STOP;
        return POLICY_MARK_DOWNSTREAM;
    }

    private List<Integer> parseDepends(Object raw) {
        List<Integer> out = new ArrayList<>();
        if (raw == null) return out;
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                try { out.add(Integer.parseInt(String.valueOf(o).trim())); } catch (Exception ignore) {}
            }
        } else {
            for (String p : String.valueOf(raw).split(",")) {
                if (p.isBlank()) continue;
                try { out.add(Integer.parseInt(p.trim())); } catch (Exception ignore) {}
            }
        }
        return out;
    }

    private String dependsText(List<?> deps) {
        if (deps == null || deps.isEmpty()) return null;
        StringJoiner joiner = new StringJoiner(",");
        for (Object d : deps) joiner.add(String.valueOf(d));
        return joiner.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String safeJson(Object o) {
        if (o == null) return null;
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", msg);
        return m;
    }
}
