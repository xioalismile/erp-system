package com.erp.ai.credit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 小楷助手积分表结构（2026-10-24）。
 * <p>主库、每个租户独立库、租户模板库共用同一份 DDL；建表/补列全部幂等。</p>
 * <p>等价脚本：deploy/sql/172_ai_credit_system.sql、db/172_ai_credit_system.sql。</p>
 */
@Slf4j
public final class AiCreditSchema {

    private AiCreditSchema() {
    }

    /** 在指定库幂等建表 / 补列；失败只告警，不阻断主流程。 */
    public static void ensure(JdbcTemplate target) {
        if (target == null) {
            return;
        }
        try {
            target.execute("CREATE TABLE IF NOT EXISTS ai_credit_account ("
                    + " id BIGSERIAL PRIMARY KEY,"
                    + " owner_type VARCHAR(10) NOT NULL,"
                    + " owner_id BIGINT NOT NULL DEFAULT 0,"
                    + " user_id BIGINT,"
                    + " username VARCHAR(100),"
                    + " credit_balance NUMERIC(14,4) NOT NULL DEFAULT 0,"
                    + " credit_granted NUMERIC(14,4) NOT NULL DEFAULT 0,"
                    + " credit_consumed NUMERIC(14,4) NOT NULL DEFAULT 0,"
                    + " enforced INT NOT NULL DEFAULT 0,"
                    + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + " CONSTRAINT uk_ai_credit_owner UNIQUE (owner_type, owner_id))");
            target.execute("CREATE INDEX IF NOT EXISTS idx_ai_credit_account_user"
                    + " ON ai_credit_account (owner_type, user_id)");
            target.execute("CREATE TABLE IF NOT EXISTS ai_credit_ledger ("
                    + " id BIGSERIAL PRIMARY KEY,"
                    + " owner_type VARCHAR(10) NOT NULL,"
                    + " owner_id BIGINT NOT NULL DEFAULT 0,"
                    + " user_id BIGINT,"
                    + " username VARCHAR(100),"
                    + " change_type VARCHAR(32) NOT NULL,"
                    + " credits NUMERIC(14,4) NOT NULL DEFAULT 0,"
                    + " balance_after NUMERIC(14,4) NOT NULL DEFAULT 0,"
                    + " tokens BIGINT NOT NULL DEFAULT 0,"
                    + " model VARCHAR(100),"
                    + " remark VARCHAR(300),"
                    + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_ai_credit_ledger_user"
                    + " ON ai_credit_ledger (owner_type, owner_id, id DESC)");
            target.execute("CREATE INDEX IF NOT EXISTS idx_ai_credit_ledger_time"
                    + " ON ai_credit_ledger (created_at DESC)");
            target.execute("ALTER TABLE ai_token_usage ADD COLUMN IF NOT EXISTS credits NUMERIC(14,4) NOT NULL DEFAULT 0");
        } catch (Exception e) {
            log.warn("[AI积分] 表结构自愈跳过: {}", e.getMessage());
        }
    }
}
