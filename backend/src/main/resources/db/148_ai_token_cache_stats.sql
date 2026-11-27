-- 148: AI用量月表新增「缓存命中」统计列（小楷模型能力接入整合 2026-10-10）
-- 缓存命中 = 模型侧命中上下文缓存的输入 tokens（重复上下文只计一次费用，命中越多越省钱）
--   DeepSeek 口径: usage.prompt_cache_hit_tokens
--   OpenAI 通用口径: usage.prompt_tokens_details.cached_tokens
-- 幂等：ADD COLUMN IF NOT EXISTS 可重复执行；应用启动自愈也会自动补列（双保险）
ALTER TABLE ai_token_usage ADD COLUMN IF NOT EXISTS prompt_cache_hit_tokens BIGINT NOT NULL DEFAULT 0;
