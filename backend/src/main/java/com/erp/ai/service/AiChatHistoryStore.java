package com.erp.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 小助手 · 会话历史数据库持久化（2026-10-20）。
 *
 * 原实现把会话历史放在 Redis（key: ai:history:&lt;sessionId&gt;）：
 * Redis 不可用时自动降级为进程内存，服务一重启会话就断；且 Redis 数据不在数据库备份范围内。
 *
 * 现在改为落数据库表 ai_chat_history：
 * ① 永久保留（不删旧记录），注入模型的窗口仍由设置中心「会话记忆轮数」控制；
 * ② 每个库自动建表（多租户：切到租户独立库时按 current_database() 各建一次），幂等；
 * ③ Agent 链路与网关链路共用同一张表，口径天然一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatHistoryStore {

    private final JdbcTemplate jdbcTemplate;

    /** 已确认建表的库（每库一次，避免每次读写都查 DDL） */
    private final Set<String> ensured = ConcurrentHashMap.newKeySet();

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS ai_chat_history (
                id          BIGSERIAL PRIMARY KEY,
                session_id  VARCHAR(128) NOT NULL,
                user_id     BIGINT,
                role        VARCHAR(16)  NOT NULL,
                content     TEXT,
                created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String IDX =
            "CREATE INDEX IF NOT EXISTS idx_ai_chat_sess ON ai_chat_history(session_id, id)";

    /** 老库补列（幂等）：会话归属登录人，历史会话列表/删除只认本人 */
    private static final String MIGRATE_USER_ID =
            "ALTER TABLE ai_chat_history ADD COLUMN IF NOT EXISTS user_id BIGINT";

    private static final String IDX_USER =
            "CREATE INDEX IF NOT EXISTS idx_ai_chat_user ON ai_chat_history(user_id, session_id, id)";

    /**
     * 读取某会话最近的对话（按时间正序返回）。
     * 读取失败按空历史处理，不阻断本轮对话。
     */
    public List<Map<String, String>> load(String sessionId, int maxMessages) {
        return load(sessionId, null, maxMessages);
    }

    /**
     * 读取某会话最近的对话（按时间正序返回），并按登录人隔离。
     * userId 为空时不做归属过滤（兼容未带用户信息的旧链路）。
     */
    public List<Map<String, String>> load(String sessionId, Long userId, int maxMessages) {
        List<Map<String, String>> out = new ArrayList<>();
        if (sessionId == null || sessionId.isBlank()) return out;
        int cap = maxMessages <= 0 ? 120 : Math.min(maxMessages, 4000);
        try {
            ensureTable();
            String sql = userId == null
                    ? """
                    SELECT role, content FROM (
                        SELECT id, role, content FROM ai_chat_history
                        WHERE session_id = ? ORDER BY id DESC LIMIT ?
                    ) t ORDER BY id ASC
                    """
                    : """
                    SELECT role, content FROM (
                        SELECT id, role, content FROM ai_chat_history
                        WHERE session_id = ? AND user_id = ? ORDER BY id DESC LIMIT ?
                    ) t ORDER BY id ASC
                    """;
            org.springframework.jdbc.core.RowCallbackHandler handler = rs -> {
                Map<String, String> m = new HashMap<>();
                m.put("role", rs.getString("role"));
                m.put("content", rs.getString("content") == null ? "" : rs.getString("content"));
                out.add(m);
            };
            if (userId == null) jdbcTemplate.query(sql, handler, sessionId, cap);
            else jdbcTemplate.query(sql, handler, sessionId, userId, cap);
        } catch (Exception e) {
            log.warn("[AI会话] 读取历史失败 ({}): {}", sessionId, e.getMessage());
        }
        return out;
    }

    /** 追加一轮对话（用户 + 小楷），永久保留。 */
    public void append(String sessionId, String userMsg, String aiReply) {
        append(sessionId, null, userMsg, aiReply);
    }

    /** 追加一轮对话，并记录归属登录人（历史会话只对本人可见）。 */
    public void append(String sessionId, Long userId, String userMsg, String aiReply) {
        if (sessionId == null || sessionId.isBlank()) return;
        try {
            ensureTable();
            jdbcTemplate.update("INSERT INTO ai_chat_history (session_id, user_id, role, content) VALUES (?,?,?,?)",
                    sessionId, userId, "user", userMsg == null ? "" : userMsg);
            jdbcTemplate.update("INSERT INTO ai_chat_history (session_id, user_id, role, content) VALUES (?,?,?,?)",
                    sessionId, userId, "assistant", aiReply == null ? "" : aiReply);
        } catch (Exception e) {
            log.warn("[AI会话] 写入历史失败 ({}): {}", sessionId, e.getMessage());
        }
    }

    /**
     * 我的历史会话列表（按最后一条消息时间倒序）。
     * 标题取该会话第一条用户提问的前 30 字；没有用户提问时用"新对话"。
     */
    public List<Map<String, Object>> listSessions(Long userId, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (userId == null) return out;
        int cap = limit <= 0 ? 100 : Math.min(limit, 500);
        try {
            ensureTable();
            jdbcTemplate.query("""
                    SELECT s.session_id,
                           s.cnt,
                           s.last_at,
                           (SELECT left(content, 60) FROM ai_chat_history t
                             WHERE t.session_id = s.session_id AND t.user_id = s.user_id AND t.role = 'user'
                             ORDER BY t.id ASC LIMIT 1) AS title
                      FROM (
                        SELECT session_id, user_id, count(*) AS cnt, max(created_at) AS last_at
                          FROM ai_chat_history
                         WHERE user_id = ?
                         GROUP BY session_id, user_id
                         ORDER BY max(created_at) DESC
                         LIMIT ?
                      ) s
                    """, rs -> {
                Map<String, Object> m = new HashMap<>();
                m.put("sessionId", rs.getString("session_id"));
                m.put("title", rs.getString("title") == null ? "" : rs.getString("title"));
                m.put("messageCount", rs.getInt("cnt"));
                java.sql.Timestamp ts = rs.getTimestamp("last_at");
                m.put("lastAt", ts == null ? null : ts.toLocalDateTime().toString());
                out.add(m);
            }, userId, cap);
        } catch (Exception e) {
            log.warn("[AI会话] 读取会话列表失败 (userId={}): {}", userId, e.getMessage());
        }
        return out;
    }

    /** 某会话的全部消息（仅本人可读）。 */
    public List<Map<String, Object>> messages(Long userId, String sessionId) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (userId == null || sessionId == null || sessionId.isBlank()) return out;
        try {
            ensureTable();
            jdbcTemplate.query("""
                    SELECT role, content, created_at FROM ai_chat_history
                     WHERE user_id = ? AND session_id = ? ORDER BY id ASC LIMIT 2000
                    """, rs -> {
                Map<String, Object> m = new HashMap<>();
                m.put("role", rs.getString("role"));
                m.put("content", rs.getString("content") == null ? "" : rs.getString("content"));
                java.sql.Timestamp ts = rs.getTimestamp("created_at");
                m.put("createdAt", ts == null ? null : ts.toLocalDateTime().toString());
                out.add(m);
            }, userId, sessionId);
        } catch (Exception e) {
            log.warn("[AI会话] 读取会话详情失败 ({}): {}", sessionId, e.getMessage());
        }
        return out;
    }

    /** 删除自己的某个会话（返回删除条数；别人的会话一条都删不到）。 */
    public int deleteSession(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) return 0;
        try {
            ensureTable();
            return jdbcTemplate.update(
                    "DELETE FROM ai_chat_history WHERE user_id = ? AND session_id = ?", userId, sessionId);
        } catch (Exception e) {
            log.warn("[AI会话] 删除会话失败 ({}): {}", sessionId, e.getMessage());
            return 0;
        }
    }

    /** 会话删除时一并清理历史（可选能力，供会话管理调用）。 */
    public void clear(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        try {
            ensureTable();
            jdbcTemplate.update("DELETE FROM ai_chat_history WHERE session_id = ?", sessionId);
        } catch (Exception e) {
            log.warn("[AI会话] 清理历史失败 ({}): {}", sessionId, e.getMessage());
        }
    }

    /** 按库建表：多租户切换数据源后，各租户库首次使用自动补齐。 */
    private void ensureTable() {
        String db;
        try {
            db = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        } catch (Exception e) {
            db = "default";
        }
        if (db == null || db.isBlank()) db = "default";
        if (!ensured.add(db)) return;
        try {
            jdbcTemplate.execute(DDL);
            jdbcTemplate.execute(MIGRATE_USER_ID);
            jdbcTemplate.execute(IDX);
            jdbcTemplate.execute(IDX_USER);
        } catch (Exception e) {
            ensured.remove(db);
            throw e;
        }
    }
}
