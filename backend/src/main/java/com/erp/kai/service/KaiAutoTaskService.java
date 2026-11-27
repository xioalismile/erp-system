package com.erp.kai.service;

import com.erp.kai.service.agent.KaiAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 自动化任务（FEAT-KAI-STANDALONE-006）。
 * 用户配置"让小楷定时执行一句话指令"（如每天 9 点汇总昨日订单），调度器每 30 秒扫描到期任务，
 * 以该用户身份走同一对话引擎执行，结果落执行记录。
 * 频率口径（人话化）：DAILY=每天 schedule_value="HH:mm"；WEEKLY=每周几 schedule_value="周N,HH:mm"（N=1~7 周一~周日）；
 * INTERVAL=每 N 分钟 schedule_value="N"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiAutoTaskService {

    private final JdbcTemplate jdbcTemplate;
    private final KaiAgentService agentService;
    private final KaiChatStore chatStore;

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] WEEK = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    public List<Map<String, Object>> list(Long kaiUserId) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT t.id, t.name, t.connection_id, t.instruction, t.schedule_type, t.schedule_value,
                       t.enabled, t.last_run_at, t.next_run_at, c.name AS conn_name
                FROM kai_auto_task t LEFT JOIN kai_ext_connection c ON c.id = t.connection_id
                WHERE t.kai_user_id = ? ORDER BY t.id DESC
                """, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("connectionId", rs.getObject("connection_id"));
            m.put("connectionName", rs.getString("conn_name"));
            m.put("instruction", rs.getString("instruction"));
            m.put("scheduleType", rs.getString("schedule_type"));
            m.put("scheduleValue", rs.getString("schedule_value"));
            m.put("scheduleLabel", label(rs.getString("schedule_type"), rs.getString("schedule_value")));
            m.put("enabled", rs.getInt("enabled") == 1);
            m.put("lastRunAt", rs.getTimestamp("last_run_at"));
            m.put("nextRunAt", rs.getTimestamp("next_run_at"));
            out.add(m);
        }, kaiUserId);
        return out;
    }

    private String label(String type, String value) {
        if (type == null) return "";
        return switch (type) {
            case "DAILY" -> "每天 " + value;
            case "WEEKLY" -> {
                String[] p = value.split(",", 2);
                int n = Integer.parseInt(p[0]);
                yield "每" + (n >= 1 && n <= 7 ? WEEK[n] : "?") + " " + (p.length > 1 ? p[1] : "");
            }
            case "INTERVAL" -> "每 " + value + " 分钟";
            default -> value;
        };
    }

    public Long save(Long kaiUserId, Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(body.get("id").toString());
        String name = str(body.get("name"));
        String instruction = str(body.get("instruction"));
        String type = str(body.get("scheduleType"));
        String value = str(body.get("scheduleValue"));
        Long connectionId = body.get("connectionId") == null || str(body.get("connectionId")).isBlank()
                ? null : Long.valueOf(body.get("connectionId").toString());
        if (name.isBlank()) throw new IllegalArgumentException("请填写任务名称");
        if (instruction.isBlank()) throw new IllegalArgumentException("请填写要小楷执行的内容");
        if (!List.of("DAILY", "WEEKLY", "INTERVAL").contains(type)) {
            throw new IllegalArgumentException("不支持的执行频率类型");
        }
        validateSchedule(type, value);
        Timestamp next = nextRun(type, value, null);
        if (id == null) {
            jdbcTemplate.update("""
                    INSERT INTO kai_auto_task (kai_user_id, name, connection_id, instruction, schedule_type, schedule_value, enabled, next_run_at)
                    VALUES (?,?,?,?,?,?,1,?)
                    """, kaiUserId, name.trim(), connectionId, instruction.trim(), type, value.trim(), next);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM kai_auto_task WHERE kai_user_id=? ORDER BY id DESC LIMIT 1", Long.class, kaiUserId);
        }
        Integer owner = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kai_auto_task WHERE id=? AND kai_user_id=?", Integer.class, id, kaiUserId);
        if (owner == null || owner == 0) throw new IllegalArgumentException("任务不存在或无权操作");
        jdbcTemplate.update("""
                UPDATE kai_auto_task SET name=?, connection_id=?, instruction=?, schedule_type=?, schedule_value=?, next_run_at=? WHERE id=?
                """, name.trim(), connectionId, instruction.trim(), type, value.trim(), next, id);
        return id;
    }

    private void validateSchedule(String type, String value) {
        try {
            switch (type) {
                case "DAILY" -> LocalTime.parse(value, HM);
                case "WEEKLY" -> {
                    String[] p = value.split(",", 2);
                    int n = Integer.parseInt(p[0]);
                    if (n < 1 || n > 7 || p.length < 2) throw new IllegalArgumentException();
                    LocalTime.parse(p[1], HM);
                }
                case "INTERVAL" -> {
                    int n = Integer.parseInt(value);
                    if (n < 5) throw new IllegalArgumentException();
                }
                default -> throw new IllegalArgumentException("不支持的频率类型");
            }
        } catch (IllegalArgumentException e) {
            throw e instanceof NumberFormatException || e.getMessage() == null
                    ? new IllegalArgumentException("执行频率格式不正确（如 09:30 / 1,09:30 / 30）") : e;
        }
    }

    /** 计算下一次执行时间（from 为 null 表示从现在起算）。 */
    private Timestamp nextRun(String type, String value, LocalDateTime from) {
        LocalDateTime now = from == null ? LocalDateTime.now() : from.minusSeconds(1);
        LocalDateTime next = null;
        switch (type) {
            case "DAILY" -> {
                LocalTime t = LocalTime.parse(value, HM);
                next = LocalDateTime.of(now.toLocalDate(), t);
                if (!next.isAfter(now)) next = next.plusDays(1);
            }
            case "WEEKLY" -> {
                String[] p = value.split(",", 2);
                int target = Integer.parseInt(p[0]);
                LocalTime t = LocalTime.parse(p[1], HM);
                next = LocalDateTime.of(now.toLocalDate(), t);
                while (!next.isAfter(now) || next.getDayOfWeek().getValue() != target) {
                    next = next.plusDays(1);
                    if (next.getDayOfWeek().getValue() == target && next.isAfter(now)) break;
                }
            }
            case "INTERVAL" -> next = now.plusMinutes(Integer.parseInt(value));
            default -> { /* 已在保存时校验 */ }
        }
        return next == null ? null : Timestamp.valueOf(next);
    }

    public void delete(Long kaiUserId, Long id) {
        jdbcTemplate.update("DELETE FROM kai_auto_task WHERE id=? AND kai_user_id=?", id, kaiUserId);
        jdbcTemplate.update("DELETE FROM kai_auto_task_log WHERE task_id=?", id);
    }

    public void toggle(Long kaiUserId, Long id, boolean enabled) {
        Timestamp next = enabled ? nextRun(
                jdbcTemplate.queryForMap("SELECT schedule_type, schedule_value FROM kai_auto_task WHERE id=?", id)
                        .get("schedule_type").toString(),
                jdbcTemplate.queryForMap("SELECT schedule_value FROM kai_auto_task WHERE id=?", id)
                        .get("schedule_value").toString(), null) : null;
        jdbcTemplate.update("UPDATE kai_auto_task SET enabled=?, next_run_at=? WHERE id=? AND kai_user_id=?",
                enabled ? 1 : 0, next, id, kaiUserId);
    }

    public List<Map<String, Object>> logs(Long kaiUserId, Long taskId) {
        Integer owner = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kai_auto_task WHERE id=? AND kai_user_id=?", Integer.class, taskId, kaiUserId);
        if (owner == null || owner == 0) throw new IllegalArgumentException("任务不存在或无权操作");
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT id, started_at, finished_at, status, summary, result
                FROM kai_auto_task_log WHERE task_id = ? ORDER BY id DESC LIMIT 50
                """, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("startedAt", rs.getTimestamp("started_at"));
            m.put("finishedAt", rs.getTimestamp("finished_at"));
            m.put("status", rs.getString("status"));
            m.put("summary", rs.getString("summary"));
            m.put("result", rs.getString("result"));
            out.add(m);
        }, taskId);
        return out;
    }

    /** 调度器：每 30 秒扫描到期且启用的任务并执行（同一任务同一时刻只执行一次）。 */
    @Scheduled(fixedDelay = 30_000)
    public void scheduler() {
        List<Map<String, Object>> due;
        try {
            due = jdbcTemplate.queryForList("""
                    SELECT id, kai_user_id, name, connection_id, instruction, schedule_type, schedule_value
                    FROM kai_auto_task
                    WHERE enabled = 1 AND next_run_at IS NOT NULL AND next_run_at <= CURRENT_TIMESTAMP
                    """);
        } catch (Exception e) {
            return; // 表未就绪等场景静默
        }
        for (Map<String, Object> t : due) {
            Long taskId = ((Number) t.get("id")).longValue();
            // 抢占标记：把 next_run_at 推迟到下轮，防止并发重复执行
            int claimed = jdbcTemplate.update("""
                    UPDATE kai_auto_task SET next_run_at = ?, last_run_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND next_run_at <= CURRENT_TIMESTAMP
                    """, nextRun(str(t.get("schedule_type")), str(t.get("schedule_value")), null), taskId);
            if (claimed != 1) continue;
            Long userId = ((Number) t.get("kai_user_id")).longValue();
            Long sessionId = ensureTaskSession(userId, taskId, str(t.get("name")));
            Timestamp started = Timestamp.valueOf(LocalDateTime.now());
            try {
                List<Map<String, Object>> steps = agentService.chat(userId, "自动化任务",
                        sessionId, str(t.get("instruction")),
                        t.get("connection_id") == null ? null : ((Number) t.get("connection_id")).longValue(),
                        null, null, false);
                String summary = steps.stream().filter(s -> "FINAL_ANSWER".equals(s.get("step")))
                        .map(s -> String.valueOf(s.get("text"))).findFirst().orElse("已执行");
                jdbcTemplate.update("""
                        INSERT INTO kai_auto_task_log (task_id, started_at, finished_at, status, summary, result)
                        VALUES (?,?,?,?,?,?)
                        """, taskId, started, Timestamp.valueOf(LocalDateTime.now()),
                        "DONE", truncate(summary, 500), truncate(compose(steps), 20000));
            } catch (Exception e) {
                log.warn("[小楷独立版] 自动化任务 {} 执行失败: {}", taskId, e.getMessage());
                jdbcTemplate.update("""
                        INSERT INTO kai_auto_task_log (task_id, started_at, finished_at, status, summary, result)
                        VALUES (?,?,?,?,?,?)
                        """, taskId, started, Timestamp.valueOf(LocalDateTime.now()),
                        "FAILED", truncate(e.getMessage(), 500), null);
            }
        }
    }

    /** 任务专属会话（不存在则建）：所有执行轮次落同一会话，历史可回看。 */
    private Long ensureTaskSession(Long userId, Long taskId, String name) {
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COALESCE(chat_session_id, 0) FROM kai_auto_task WHERE id = ?", Long.class, taskId);
        if (existing != null && existing > 0) {
            // 会话可能被用户删除：删了就重建
            Integer alive = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM kai_chat_session WHERE id=? AND deleted=0", Integer.class, existing);
            if (alive != null && alive > 0) return existing;
        }
        Long sid = chatStore.createSession(userId, "【自动】" + name);
        jdbcTemplate.update("UPDATE kai_auto_task SET chat_session_id = ? WHERE id = ?", sid, taskId);
        return sid;
    }

    private String compose(List<Map<String, Object>> steps) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> s : steps) {
            sb.append(s.get("step")).append(": ").append(str(s.get("text"))).append('\n');
        }
        return sb.toString();
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) : s;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
