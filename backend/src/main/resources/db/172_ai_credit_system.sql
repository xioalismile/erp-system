-- 172 · ERP 小楷助手积分制（2026-10-24）
-- 口径：1 积分 = 1000 tokens；公司总积分默认全员共用；管理员可给员工单独分配；
--      对话按 tokens ÷ 每积分tokens × 模型倍率 ×（深度思考倍率）扣减。
-- 幂等：CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS，跑 100 遍 = 跑 1 遍。

-- 积分账户：公司（owner_type='TENANT'，owner_id=0）与员工（owner_type='USER'，owner_id=员工账号主键）
CREATE TABLE IF NOT EXISTS ai_credit_account (
    id              BIGSERIAL PRIMARY KEY,
    owner_type      VARCHAR(10)   NOT NULL,
    owner_id        BIGINT        NOT NULL DEFAULT 0,
    user_id         BIGINT,
    username        VARCHAR(100),
    credit_balance  NUMERIC(14,4) NOT NULL DEFAULT 0,
    credit_granted  NUMERIC(14,4) NOT NULL DEFAULT 0,
    credit_consumed NUMERIC(14,4) NOT NULL DEFAULT 0,
    enforced        INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_credit_owner UNIQUE (owner_type, owner_id)
);
CREATE INDEX IF NOT EXISTS idx_ai_credit_account_user ON ai_credit_account (owner_type, user_id);

-- 积分流水：每次赠送 / 分配 / 收回 / 消耗各一行
CREATE TABLE IF NOT EXISTS ai_credit_ledger (
    id            BIGSERIAL PRIMARY KEY,
    owner_type    VARCHAR(10)   NOT NULL,
    owner_id      BIGINT        NOT NULL DEFAULT 0,
    user_id       BIGINT,
    username      VARCHAR(100),
    change_type   VARCHAR(32)   NOT NULL,
    credits       NUMERIC(14,4) NOT NULL DEFAULT 0,
    balance_after NUMERIC(14,4) NOT NULL DEFAULT 0,
    tokens        BIGINT        NOT NULL DEFAULT 0,
    model         VARCHAR(100),
    remark        VARCHAR(300),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_credit_ledger_user ON ai_credit_ledger (owner_type, owner_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_ai_credit_ledger_time ON ai_credit_ledger (created_at DESC);

-- 月用量表补积分消耗列（原 token 统计保留）
ALTER TABLE ai_token_usage ADD COLUMN IF NOT EXISTS credits NUMERIC(14,4) NOT NULL DEFAULT 0;
