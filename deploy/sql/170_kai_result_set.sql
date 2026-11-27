-- 小楷独立版 · 查询结果集持久化（2026-10-20）
-- 目的：查询结果落库永久保留，支持二次分析、分组汇总与多结果集对比。
-- 幂等：可重复执行。

CREATE TABLE IF NOT EXISTS kai_query_result (
    id            BIGSERIAL PRIMARY KEY,
    kai_user_id   BIGINT       NOT NULL,
    session_id    BIGINT,
    connection_id BIGINT,
    label         VARCHAR(300),
    object_type   VARCHAR(64),
    filters_json  TEXT,
    columns_json  TEXT,
    row_count     INT          DEFAULT 0,
    data_json     TEXT,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kai_qr_user ON kai_query_result(kai_user_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_kai_qr_sess ON kai_query_result(session_id);
