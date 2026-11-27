-- ============================================================
-- 57_ai_knowledge.sql
-- @feature FEAT-AIKB-001 AI知识库管理模块
-- 表：sys_ai_knowledge_doc（文档主表）、sys_ai_knowledge_chunk（切片向量表）
-- 幂等脚本：CREATE TABLE IF NOT EXISTS
-- 生成时间：2026-09-05
-- ============================================================

-- 文档主表
CREATE TABLE IF NOT EXISTS sys_ai_knowledge_doc (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    module          VARCHAR(50)  DEFAULT 'general',
    file_path       VARCHAR(500) NOT NULL,
    file_type       VARCHAR(20)  NOT NULL,
    file_size       BIGINT       DEFAULT 0,
    status          VARCHAR(20)  DEFAULT 'parsing',
    chunk_count     INT          DEFAULT 0,
    error_msg       TEXT,
    created_by      VARCHAR(50),
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_doc_status ON sys_ai_knowledge_doc (status);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_doc_module ON sys_ai_knowledge_doc (module);

COMMENT ON TABLE sys_ai_knowledge_doc IS 'AI知识库-文档主表';
COMMENT ON COLUMN sys_ai_knowledge_doc.status IS '状态：parsing解析中/ready就绪/failed失败';
COMMENT ON COLUMN sys_ai_knowledge_doc.file_type IS '文件类型：pdf/docx/xlsx/txt/md';

-- 切片向量表
CREATE TABLE IF NOT EXISTS sys_ai_knowledge_chunk (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          BIGINT       NOT NULL,
    chunk_index     INT          NOT NULL,
    content         TEXT         NOT NULL,
    keywords        VARCHAR(500),
    embedding       TEXT         NOT NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_doc ON sys_ai_knowledge_chunk (doc_id);

COMMENT ON TABLE sys_ai_knowledge_chunk IS 'AI知识库-文档切片向量表';
COMMENT ON COLUMN sys_ai_knowledge_chunk.embedding IS '向量JSON数组（DashScope text-embedding-v3，1024维）';
COMMENT ON COLUMN sys_ai_knowledge_chunk.keywords IS '提取的关键词，逗号分隔（辅助关键词检索）';
