package com.erp.kai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小楷独立版 · 聊天持久化（FEAT-KAI-STANDALONE-007）。
 * 会话与每轮消息（含完整步骤 JSON）全部落库：重启不丢、长期保存、侧栏会话列表真实化。
 * 内存会话（模型上下文）冷启动时从库重建最近 40 条。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiChatStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 校验会话归属（不存在/非本人则拒绝——会话必须由前端或自动化任务显式创建，禁止隐式错建）。 */
    public void existsOrThrow(Long kaiUserId, Long sessionId) {
        assertOwner(kaiUserId, sessionId);
    }

    /** 新建空会话。 */
    public Long createSession(Long kaiUserId, String title) {
        jdbcTemplate.update("INSERT INTO kai_chat_session (kai_user_id, title) VALUES (?,?)",
                kaiUserId, title == null || title.isBlank() ? "新对话" : truncate(title, 200));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM kai_chat_session WHERE kai_user_id=? ORDER BY id DESC LIMIT 1", Long.class, kaiUserId);
    }

    /** 我的会话列表（置顶在前，其后按最近活跃排序）。 */
    public List<Map<String, Object>> listSessions(Long kaiUserId) {
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT id, title, pinned, created_at, updated_at
                FROM kai_chat_session WHERE kai_user_id = ? AND deleted = 0
                ORDER BY pinned DESC, updated_at DESC
                """, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("title", rs.getString("title"));
            m.put("pinned", rs.getInt("pinned") == 1);
            m.put("updatedAt", rs.getTimestamp("updated_at"));
            out.add(m);
        }, kaiUserId);
        return out;
    }

    public void renameSession(Long kaiUserId, Long sessionId, String title) {
        assertOwner(kaiUserId, sessionId);
        jdbcTemplate.update("UPDATE kai_chat_session SET title=? WHERE id=?", truncate(title, 200), sessionId);
    }

    /** 当前会话标题（不存在时返回 null）。 */
    public String getTitle(Long sessionId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT title FROM kai_chat_session WHERE id = ?", String.class, sessionId);
        } catch (Exception e) {
            return null;
        }
    }

    /** 会话是否已有用户消息（判断是否首轮，用于自动命名）。 */
    public boolean hasUserMessage(Long sessionId) {
        try {
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM kai_chat_message WHERE session_id = ? AND role = 'user'",
                    Integer.class, sessionId);
            return c != null && c > 0;
        } catch (Exception e) {
            return true;
        }
    }

    public void pinSession(Long kaiUserId, Long sessionId, boolean pinned) {
        assertOwner(kaiUserId, sessionId);
        jdbcTemplate.update("UPDATE kai_chat_session SET pinned=? WHERE id=?", pinned ? 1 : 0, sessionId);
    }

    public void deleteSession(Long kaiUserId, Long sessionId) {
        assertOwner(kaiUserId, sessionId);
        jdbcTemplate.update("UPDATE kai_chat_session SET deleted=1 WHERE id=?", sessionId);
        jdbcTemplate.update("DELETE FROM kai_chat_message WHERE session_id=?", sessionId);
    }

    private void assertOwner(Long kaiUserId, Long sessionId) {
        Integer owner = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kai_chat_session WHERE id=? AND kai_user_id=? AND deleted=0",
                Integer.class, sessionId, kaiUserId);
        if (owner == null || owner == 0) throw new IllegalArgumentException("会话不存在或无权操作");
    }

    /** 用户消息落库并刷新会话活跃时间。 */
    public void saveUserMessage(Long sessionId, Long kaiUserId, String text) {
        jdbcTemplate.update(
                "INSERT INTO kai_chat_message (session_id, kai_user_id, role, steps) VALUES (?,?,?,?)",
                sessionId, kaiUserId, "user", text);
        touch(sessionId);
    }

    /** 小楷回复（完整步骤列表 JSON）落库。 */
    public void saveBotMessage(Long sessionId, Long kaiUserId, List<Map<String, Object>> steps) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO kai_chat_message (session_id, kai_user_id, role, steps) VALUES (?,?,?,?)",
                    sessionId, kaiUserId, "bot", mapper.writeValueAsString(steps));
            touch(sessionId);
        } catch (Exception e) {
            log.warn("[小楷独立版] 消息落库失败: {}", e.getMessage());
        }
    }

    private void touch(Long sessionId) {
        jdbcTemplate.update("UPDATE kai_chat_session SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", sessionId);
    }

    /** 读会话历史（前端恢复用）：按时间序返回 user 文本 / bot 完整步骤。 */
    public List<Map<String, Object>> loadHistory(Long kaiUserId, Long sessionId) {
        assertOwner(kaiUserId, sessionId);
        List<Map<String, Object>> out = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT role, steps, created_at FROM kai_chat_message
                WHERE session_id = ? ORDER BY id ASC
                """, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", rs.getString("role"));
            String steps = rs.getString("steps");
            if ("user".equals(m.get("role"))) {
                m.put("text", steps);
            } else {
                try {
                    m.put("steps", mapper.readValue(steps == null ? "[]" : steps, List.class));
                } catch (Exception e) {
                    m.put("steps", new ArrayList<>());
                }
            }
            m.put("createdAt", rs.getTimestamp("created_at"));
            out.add(m);
        }, sessionId);
        return out;
    }

    /**
     * 供模型上下文冷启动重建。
     *
     * 旧实现只恢复「用户原文 + 最终答复」，把中间查到的数据与结果集编号全丢了 ——
     * 用户接着问「刚才那批数据按客户再算一下」时，模型只能重查或凭印象编。
     *
     * 现在每轮的 assistant 侧恢复为「本轮执行记录 + 最终答复」：
     * 执行记录含每次查询的行数、对象、结果集编号与统计/对比结论（只取步骤文本，不取全量行数据）。
     *
     * @param maxChars    字符预算（按模型上下文窗口换算，见 KaiContextBudget）
     * @param maxMessages 消息条数硬上限（兜底）
     */
    public List<Map<String, Object>> loadAsModelMessages(Long kaiUserId, Long sessionId,
                                                         int maxChars, int maxMessages) {
        List<Map<String, Object>> msgs = loadHistory(kaiUserId, sessionId);
        List<Map<String, Object>> picked = new ArrayList<>();
        int chars = 0;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Map<String, Object> m = msgs.get(i);
            Map<String, Object> built = buildModelMessage(m);
            if (built == null) continue;
            String content = String.valueOf(built.get("content"));
            if (!picked.isEmpty() && (chars + content.length() > maxChars || picked.size() >= maxMessages)) {
                break;
            }
            chars += content.length();
            picked.add(built);
        }
        Collections.reverse(picked);
        return picked;
    }

    /** 兼容旧签名（固定条数，字符预算按每条约 800 字估算）。 */
    public List<Map<String, Object>> loadAsModelMessages(Long kaiUserId, Long sessionId, int maxMessages) {
        return loadAsModelMessages(kaiUserId, sessionId, Math.max(8000, maxMessages * 800), maxMessages);
    }

    /** 单条历史消息 → 模型消息（bot 侧拼上本轮执行记录）。 */
    private Map<String, Object> buildModelMessage(Map<String, Object> m) {
        if ("user".equals(m.get("role"))) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("role", "user");
            mm.put("content", String.valueOf(m.get("text")));
            return mm;
        }
        List<Map<String, Object>> steps = castSteps(m.get("steps"));
        String answer = extractFinalAnswer(steps);
        String trace = buildTrace(steps);
        if ((answer == null || answer.isBlank()) && trace.isBlank()) return null;
        StringBuilder content = new StringBuilder();
        if (!trace.isBlank()) {
            content.append("【本轮执行记录】\n").append(trace).append("\n");
        }
        if (answer != null && !answer.isBlank()) {
            content.append("【回答】\n").append(answer);
        }
        Map<String, Object> mm = new LinkedHashMap<>();
        mm.put("role", "assistant");
        mm.put("content", content.toString());
        return mm;
    }

    /**
     * 本轮执行记录：每次查询/统计/对比一行，含结果集编号与关键结论。
     * 只取步骤文本与结果集编号，不取全量行数据（避免冷启动把上下文撑爆）。
     */
    private String buildTrace(List<Map<String, Object>> steps) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> s : steps) {
            String type = String.valueOf(s.get("step"));
            if ("OBSERVATION".equals(type)) {
                String text = String.valueOf(s.get("text") == null ? "" : s.get("text"));
                if (text.isBlank() || "处理完成".equals(text)) continue;
                sb.append("- ").append(text);
                Object rsId = s.get("resultSetId");
                if (rsId != null) sb.append("；结果集编号 ").append(rsId);
                sb.append("\n");
            } else if ("ERROR".equals(type)) {
                String text = String.valueOf(s.get("text") == null ? "" : s.get("text"));
                if (!text.isBlank()) sb.append("- 失败：").append(text).append("\n");
            }
        }
        if (sb.length() > 4000) {
            return sb.substring(0, 4000) + "…（更早的执行记录已省略）";
        }
        return sb.toString().trim();
    }

    private String extractFinalAnswer(List<Map<String, Object>> steps) {
        String last = null;
        for (Map<String, Object> s : steps) {
            if ("FINAL_ANSWER".equals(s.get("step"))) last = String.valueOf(s.get("text"));
        }
        return last;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castSteps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) if (x instanceof Map) out.add((Map<String, Object>) x);
        }
        return out;
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) : s;
    }
}
