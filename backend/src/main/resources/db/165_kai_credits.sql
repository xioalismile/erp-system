-- 165 · 小楷独立版积分体系（2026-10-18）
-- 幂等：ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS，跑 100 遍 = 跑 1 遍
-- 口径：1 积分 = 1000 tokens；注册赠送 1000 积分；用户自带模型不扣积分
ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS credit_balance  NUMERIC(14,4) DEFAULT 0;
ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS credit_granted  NUMERIC(14,4) DEFAULT 0;
ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS credit_consumed NUMERIC(14,4) DEFAULT 0;
ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS is_admin        INT           DEFAULT 0;

CREATE TABLE IF NOT EXISTS kai_credit_ledger (
    id            BIGSERIAL PRIMARY KEY,
    kai_user_id   BIGINT        NOT NULL,
    change_type   VARCHAR(32)   NOT NULL,
    credits       NUMERIC(14,4) NOT NULL DEFAULT 0,
    balance_after NUMERIC(14,4) NOT NULL DEFAULT 0,
    tokens        BIGINT        DEFAULT 0,
    model         VARCHAR(100),
    remark        VARCHAR(300),
    created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_credit_user ON kai_credit_ledger(kai_user_id, id DESC);

CREATE TABLE IF NOT EXISTS kai_usage_log (
    id                BIGSERIAL PRIMARY KEY,
    kai_user_id       BIGINT        NOT NULL,
    model             VARCHAR(100),
    prompt_tokens     BIGINT        DEFAULT 0,
    completion_tokens BIGINT        DEFAULT 0,
    total_tokens      BIGINT        DEFAULT 0,
    credits           NUMERIC(14,4) DEFAULT 0,
    own_model         INT           DEFAULT 0,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kai_usage_log_user ON kai_usage_log(kai_user_id, id DESC);

CREATE TABLE IF NOT EXISTS kai_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value TEXT,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
