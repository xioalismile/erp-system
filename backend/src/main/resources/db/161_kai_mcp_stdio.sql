-- ============================================================
-- 161 · 小楷独立版能力扩展第二批：MCP stdio 本地进程传输（2026-10-16）
-- @feature FEAT-KAI-STANDALONE-012
-- 幂等：全部 ADD COLUMN IF NOT EXISTS，跑 100 遍 = 跑 1 遍
-- 说明：kai_mcp_server 增加 stdio 所需四列（仅 command 必填，其余可空）；
--       env 环境变量 JSON 可能含密钥，AES-GCM 加密落库（ENC: 前缀，永不回显）；
--       表落主库，由 KaiSchemaService 启动自愈双保险（同构 DDL）。
-- ============================================================

ALTER TABLE kai_mcp_server ADD COLUMN IF NOT EXISTS command     VARCHAR(300);
ALTER TABLE kai_mcp_server ADD COLUMN IF NOT EXISTS args_json   TEXT;
ALTER TABLE kai_mcp_server ADD COLUMN IF NOT EXISTS cwd         VARCHAR(500);
ALTER TABLE kai_mcp_server ADD COLUMN IF NOT EXISTS env_enc     TEXT;
