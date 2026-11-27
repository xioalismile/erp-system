-- AI 小助手 · 会话历史数据库持久化（2026-10-20）
-- 目的：会话记忆从 Redis 迁到数据库，永久保留、随库备份、服务重启不丢。
-- 多租户：每个租户独立库各自执行一次（应用启动/首次使用也会自动建表，双保险）。
-- 幂等：可重复执行。

CREATE TABLE IF NOT EXISTS ai_chat_history (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(128) NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    content     TEXT,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_chat_sess ON ai_chat_history(session_id, id);
