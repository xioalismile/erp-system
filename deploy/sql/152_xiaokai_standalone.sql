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

-- ============================================================
-- 二期五件套（2026-10-12）：聊天持久化 / 模型配置 / 用量 / 自动化任务 / 插件
-- ============================================================

-- 4. 聊天会话（持久化：重启不丢、长期保存；pinned=置顶 deleted=软删）
CREATE TABLE IF NOT EXISTS kai_chat_session (
    id          BIGSERIAL PRIMARY KEY,
    kai_user_id BIGINT NOT NULL,
    title       VARCHAR(200),
    pinned      INT DEFAULT 0,
    deleted     INT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_sess_user ON kai_chat_session(kai_user_id);

-- 5. 聊天消息（steps 为该轮完整步骤 JSON）
CREATE TABLE IF NOT EXISTS kai_chat_message (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT NOT NULL,
    kai_user_id BIGINT NOT NULL,
    role        VARCHAR(16),
    steps       TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_msg_sess ON kai_chat_message(session_id);

-- 6. 用户级模型服务商（密钥加密落库；无配置时兜底系统默认模型）
CREATE TABLE IF NOT EXISTS kai_model_provider (
    id          BIGSERIAL PRIMARY KEY,
    kai_user_id BIGINT NOT NULL,
    name        VARCHAR(100),
    base_url    VARCHAR(300),
    api_key_enc TEXT,
    models      VARCHAR(500),
    enabled     INT DEFAULT 1,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_mp_user ON kai_model_provider(kai_user_id);

-- 7. 用量（按用户按月聚合）+ 用户表补月配额列（0=不限）
CREATE TABLE IF NOT EXISTS kai_usage (
    id                BIGSERIAL PRIMARY KEY,
    kai_user_id       BIGINT NOT NULL,
    month             VARCHAR(7),
    prompt_tokens     BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    total_tokens      BIGINT DEFAULT 0,
    call_count        INT DEFAULT 0,
    UNIQUE(kai_user_id, month)
);
ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS monthly_quota BIGINT DEFAULT 0;

-- 8. 自动化任务（定时让小楷执行对话指令）+ 执行记录
CREATE TABLE IF NOT EXISTS kai_auto_task (
    id             BIGSERIAL PRIMARY KEY,
    kai_user_id    BIGINT NOT NULL,
    name           VARCHAR(100),
    connection_id  BIGINT,
    instruction    TEXT,
    schedule_type  VARCHAR(16),
    schedule_value VARCHAR(32),
    enabled        INT DEFAULT 1,
    last_run_at    TIMESTAMP,
    next_run_at    TIMESTAMP,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_task2_user ON kai_auto_task(kai_user_id);

CREATE TABLE IF NOT EXISTS kai_auto_task_log (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT NOT NULL,
    started_at  TIMESTAMP,
    finished_at TIMESTAMP,
    status      VARCHAR(16),
    summary     VARCHAR(500),
    result      TEXT
);
CREATE INDEX IF NOT EXISTS idx_kai_atl_task ON kai_auto_task_log(task_id);

-- 9. 插件（外部工具服务：GET {base}/tools 发现 + POST {base}/call 调用）
CREATE TABLE IF NOT EXISTS kai_plugin (
    id          BIGSERIAL PRIMARY KEY,
    kai_user_id BIGINT NOT NULL,
    name        VARCHAR(100),
    description VARCHAR(300),
    base_url    VARCHAR(300),
    api_key_enc TEXT,
    enabled     INT DEFAULT 1,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_plugin_user ON kai_plugin(kai_user_id);
