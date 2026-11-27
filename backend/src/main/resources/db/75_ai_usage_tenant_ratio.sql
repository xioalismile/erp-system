-- 75_ai_usage_tenant_ratio.sql
-- 2026-09-14 AI 租户消耗系数（消耗按 (1+系数) 倍计量，默认 0.5 即 1.5 倍）
--
-- 口径（用户明确，勿改动语义）：
--   · 分配给租户 / 用户的额度是正常的 —— 额度分配【不乘】系数（ai.quota.* 一行不改）
--   · 只是"消耗"多算 0.5 倍 —— 实际消耗 100 记 150，配额也按 150 扣减
--   · 系数属内部商务参数 —— 终端用户在用量页看不到系数，也看不到原始值
--
-- 本脚本做三件事：
--   1) ai_token_usage 增加 3 个原始值列（平台内部对账用，界面不展示）
--   2) 新增配置项 ai.usage.tenant.ratio（默认 0.5）
--   3) 幂等回填历史行的原始值 = 当时的 total_tokens（历史视为"未加价"；只兜底、不覆盖新行）
--
-- 幂等与安全：
--   ADD COLUMN IF NOT EXISTS / WHERE NOT EXISTS / 回填带 WHERE raw_total_tokens = 0
--   ALTER 用 IF EXISTS、UPDATE 用 DO 块判表存在 —— 新库（表尚未由代码创建）执行本脚本也不会中止部署
--   【铁律】禁用 ON CONFLICT (config_key)：PG 环境该唯一约束可能缺失，报错会中止整次部署（2026-09-12 事故）

ALTER TABLE IF EXISTS ai_token_usage ADD COLUMN IF NOT EXISTS raw_prompt_tokens     BIGINT NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS ai_token_usage ADD COLUMN IF NOT EXISTS raw_completion_tokens BIGINT NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS ai_token_usage ADD COLUMN IF NOT EXISTS raw_total_tokens      BIGINT NOT NULL DEFAULT 0;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ai.usage.tenant.ratio', '0.5', 'AI租户消耗系数', 'ai', 'number', 30,
       '消耗计量系数：记账token = 实际token × (1+系数)，默认0.5（即1.5倍）；仅影响消耗与配额扣减，不影响额度分配', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.usage.tenant.ratio');

-- 历史行兜底回填：老记录视为"未加价"，raw = 当时的 total_tokens
-- （新行 raw_total_tokens 写入后必然 > 0，不会被本语句覆盖）
DO $$
BEGIN
    IF to_regclass('public.ai_token_usage') IS NOT NULL THEN
        UPDATE ai_token_usage
           SET raw_prompt_tokens     = prompt_tokens,
               raw_completion_tokens = completion_tokens,
               raw_total_tokens      = total_tokens
         WHERE raw_total_tokens = 0;
    END IF;
END $$;
