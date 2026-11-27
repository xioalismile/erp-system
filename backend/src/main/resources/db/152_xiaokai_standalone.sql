-- ============================================================
-- 152 · 小楷独立版（独立用户 + 外部系统连接 + 任务记录）
-- @feature FEAT-KAI-STANDALONE-001
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，跑 100 遍 = 跑 1 遍
-- 说明：独立站不进楷煜 sys_menu（无菜单/权限 SQL）；
--       表落在主库，由 KaiSchemaService 启动自愈双保险
-- ============================================================

-- 1. 独立版用户（与 sys_user 完全隔离，禁止外键关联主系统用户）
CREATE TABLE IF NOT EXISTS kai_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(64),
    company_name  VARCHAR(128),
    status        INT          DEFAULT 1,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP
);

-- 2. 外部系统连接（凭据 AES-GCM 加密落库，前缀 ENC:，永不回显明文）
CREATE TABLE IF NOT EXISTS kai_ext_connection (
    id              BIGSERIAL PRIMARY KEY,
    kai_user_id     BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(32)  NOT NULL DEFAULT 'DEMO',
    base_url        VARCHAR(500),
    auth_type       VARCHAR(32)  DEFAULT 'API_KEY',
    credential_enc  TEXT,
    extra_config    TEXT,
    enabled         INT          DEFAULT 1,
    timeout_seconds INT          DEFAULT 30,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_conn_user ON kai_ext_connection(kai_user_id);

-- 3. 外部操作任务记录（写操作留痕：谁在哪个连接做了什么、结果如何）
CREATE TABLE IF NOT EXISTS kai_ext_task_log (
    id            BIGSERIAL PRIMARY KEY,
    kai_user_id   BIGINT      NOT NULL,
    connection_id BIGINT,
    action        VARCHAR(32),
    object_type   VARCHAR(64),
    summary       VARCHAR(500),
    detail        TEXT,
    status        VARCHAR(16) DEFAULT 'DONE',
    created_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_task_user ON kai_ext_task_log(kai_user_id);
