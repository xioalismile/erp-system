-- ============================================================
-- 160 · 小楷独立版能力扩展第一批：技能库 + 知识库 + MCP 工具（2026-10-15）
-- @feature FEAT-KAI-STANDALONE-011
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，跑 100 遍 = 跑 1 遍
-- 说明：表落主库，由 KaiSchemaService 启动自愈双保险（同构 DDL）；
--       MCP 凭据 AES-GCM 加密落库（ENC: 前缀，同 kai_ext_connection 口径），永不回显明文；
--       知识库分块向量存 JSON 文本（可空），向量不可用时自动关键词检索兜底，不装向量库。
-- ============================================================

-- 1. 技能（启用后整段拼进小楷系统提示词，改行为设定不再需要改代码发版）
CREATE TABLE IF NOT EXISTS kai_skill (
    id          BIGSERIAL PRIMARY KEY,
    kai_user_id BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    content     TEXT         NOT NULL,
    enabled     INT          DEFAULT 1,
    sort_order  INT          DEFAULT 0,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_skill_user ON kai_skill(kai_user_id);

-- 2. 知识库
CREATE TABLE IF NOT EXISTS kai_kb_base (
    id          BIGSERIAL PRIMARY KEY,
    kai_user_id BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    enabled     INT          DEFAULT 1,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_kb_base_user ON kai_kb_base(kai_user_id);

-- 3. 知识库文档（status：PROCESSING 处理中 / READY 已就绪 / FAILED 失败）
CREATE TABLE IF NOT EXISTS kai_kb_doc (
    id          BIGSERIAL PRIMARY KEY,
    kai_user_id BIGINT       NOT NULL,
    base_id     BIGINT       NOT NULL,
    file_name   VARCHAR(300) NOT NULL,
    file_size   BIGINT       DEFAULT 0,
    status      VARCHAR(16)  DEFAULT 'PROCESSING',
    chunk_count INT          DEFAULT 0,
    error       VARCHAR(500),
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_kb_doc_base ON kai_kb_doc(base_id);
CREATE INDEX IF NOT EXISTS idx_kai_kb_doc_user ON kai_kb_doc(kai_user_id);

-- 4. 知识库分块（embedding 为 JSON 浮点数组文本；可空 = 该分块只参与关键词检索）
CREATE TABLE IF NOT EXISTS kai_kb_chunk (
    id         BIGSERIAL PRIMARY KEY,
    kai_user_id BIGINT NOT NULL,
    doc_id     BIGINT NOT NULL,
    base_id    BIGINT NOT NULL,
    seq        INT    DEFAULT 0,
    content    TEXT   NOT NULL,
    embedding  TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_kb_chunk_base ON kai_kb_chunk(base_id);
CREATE INDEX IF NOT EXISTS idx_kai_kb_chunk_doc ON kai_kb_chunk(doc_id);

-- 5. MCP 服务器（auth_headers 加密落库；工具清单缓存 JSON，避免每轮对话实时握手）
CREATE TABLE IF NOT EXISTS kai_mcp_server (
    id                BIGSERIAL PRIMARY KEY,
    kai_user_id       BIGINT       NOT NULL,
    name              VARCHAR(100) NOT NULL,
    url               VARCHAR(500) NOT NULL,
    transport         VARCHAR(16)  DEFAULT 'HTTP',
    auth_headers_enc  TEXT,
    enabled           INT          DEFAULT 1,
    require_confirm   INT          DEFAULT 1,
    tool_cache        TEXT,
    last_error        VARCHAR(500),
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_mcp_user ON kai_mcp_server(kai_user_id);
