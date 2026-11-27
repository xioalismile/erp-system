package com.erp.kai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * 小楷独立版 · 启动自愈建表（FEAT-KAI-STANDALONE-001）。
 * 与 deploy/sql/152_xiaokai_standalone.sql 同构，双保险：
 * 空库由 SQL 初始化，存量库由这里启动时幂等补齐，免手工。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KaiSchemaService {

    private final JdbcTemplate jdbcTemplate;

    private static final String[] DDL = {
            """
            CREATE TABLE IF NOT EXISTS kai_user (
                id            BIGSERIAL PRIMARY KEY,
                username      VARCHAR(64)  NOT NULL UNIQUE,
                password_hash VARCHAR(100) NOT NULL,
                display_name  VARCHAR(64),
                company_name  VARCHAR(128),
                status        INT          DEFAULT 1,
                created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                last_login_at TIMESTAMP
            )
            """,
            """
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
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_conn_user ON kai_ext_connection(kai_user_id)",
            """
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
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_task_user ON kai_ext_task_log(kai_user_id)",
            // ===== 二期五件套（2026-10-12）：聊天持久化 / 模型配置 / 用量 / 自动化任务 / 插件 =====
            """
            CREATE TABLE IF NOT EXISTS kai_chat_session (
                id          BIGSERIAL PRIMARY KEY,
                kai_user_id BIGINT NOT NULL,
                title       VARCHAR(200),
                pinned      INT DEFAULT 0,
                deleted     INT DEFAULT 0,
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_sess_user ON kai_chat_session(kai_user_id)",
            """
            CREATE TABLE IF NOT EXISTS kai_chat_message (
                id         BIGSERIAL PRIMARY KEY,
                session_id BIGINT NOT NULL,
                kai_user_id BIGINT NOT NULL,
                role       VARCHAR(16),
                steps      TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_msg_sess ON kai_chat_message(session_id)",
            """
            CREATE TABLE IF NOT EXISTS kai_model_provider (
                id          BIGSERIAL PRIMARY KEY,
                kai_user_id BIGINT NOT NULL,
                name        VARCHAR(100),
                base_url    VARCHAR(300),
                api_key_enc TEXT,
                models      VARCHAR(500),
                enabled     INT DEFAULT 1,
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_mp_user ON kai_model_provider(kai_user_id)",
            """
            CREATE TABLE IF NOT EXISTS kai_usage (
                id               BIGSERIAL PRIMARY KEY,
                kai_user_id      BIGINT NOT NULL,
                month            VARCHAR(7),
                prompt_tokens    BIGINT DEFAULT 0,
                completion_tokens BIGINT DEFAULT 0,
                total_tokens     BIGINT DEFAULT 0,
                call_count       INT DEFAULT 0,
                UNIQUE(kai_user_id, month)
            )
            """,
            "ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS monthly_quota BIGINT DEFAULT 0",
            """
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
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_task2_user ON kai_auto_task(kai_user_id)",
            """
            CREATE TABLE IF NOT EXISTS kai_auto_task_log (
                id          BIGSERIAL PRIMARY KEY,
                task_id     BIGINT NOT NULL,
                started_at  TIMESTAMP,
                finished_at TIMESTAMP,
                status      VARCHAR(16),
                summary     VARCHAR(500),
                result      TEXT
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_atl_task ON kai_auto_task_log(task_id)",
            """
            CREATE TABLE IF NOT EXISTS kai_plugin (
                id          BIGSERIAL PRIMARY KEY,
                kai_user_id BIGINT NOT NULL,
                name        VARCHAR(100),
                description VARCHAR(300),
                base_url    VARCHAR(300),
                api_key_enc TEXT,
                enabled     INT DEFAULT 1,
                created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_plugin_user ON kai_plugin(kai_user_id)",
            // ALTER 放最后：确保目标表已存在（首次启动时 kai_auto_task 由上面 CREATE 建立）
            "ALTER TABLE kai_auto_task ADD COLUMN IF NOT EXISTS chat_session_id BIGINT",
            // ===== 三期能力扩展（2026-10-15）：技能库 / 知识库 / MCP 工具（与 db/160 同构） =====
            """
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
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_skill_user ON kai_skill(kai_user_id)",
            """
            CREATE TABLE IF NOT EXISTS kai_kb_base (
                id          BIGSERIAL PRIMARY KEY,
                kai_user_id BIGINT       NOT NULL,
                name        VARCHAR(100) NOT NULL,
                description VARCHAR(300),
                enabled     INT          DEFAULT 1,
                created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_kb_base_user ON kai_kb_base(kai_user_id)",
            """
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
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_kb_doc_base ON kai_kb_doc(base_id)",
            "CREATE INDEX IF NOT EXISTS idx_kai_kb_doc_user ON kai_kb_doc(kai_user_id)",
            """
            CREATE TABLE IF NOT EXISTS kai_kb_chunk (
                id         BIGSERIAL PRIMARY KEY,
                kai_user_id BIGINT NOT NULL,
                doc_id     BIGINT NOT NULL,
                base_id    BIGINT NOT NULL,
                seq        INT    DEFAULT 0,
                content    TEXT   NOT NULL,
                embedding  TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_kb_chunk_base ON kai_kb_chunk(base_id)",
            "CREATE INDEX IF NOT EXISTS idx_kai_kb_chunk_doc ON kai_kb_chunk(doc_id)",
            """
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
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_mcp_user ON kai_mcp_server(kai_user_id)",
            // ===== 二批（2026-10-16）：MCP stdio 本地进程传输（与 db/161 同构） =====
            "ALTER TABLE kai_mcp_server ADD COLUMN IF NOT EXISTS command VARCHAR(300)",
            "ALTER TABLE kai_mcp_server ADD COLUMN IF NOT EXISTS args_json TEXT",
            "ALTER TABLE kai_mcp_server ADD COLUMN IF NOT EXISTS cwd VARCHAR(500)",
            "ALTER TABLE kai_mcp_server ADD COLUMN IF NOT EXISTS env_enc TEXT",
            // ===== 积分体系（2026-10-18，与 db/165 同构） =====
            "ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS credit_balance  NUMERIC(14,4) DEFAULT 0",
            "ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS credit_granted  NUMERIC(14,4) DEFAULT 0",
            "ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS credit_consumed NUMERIC(14,4) DEFAULT 0",
            "ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS is_admin        INT           DEFAULT 0",
            // ===== 客户资料补充（2026-10-19，与 db/167 同构） =====
            "ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS phone  VARCHAR(32)",
            "ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS email  VARCHAR(128)",
            "ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS remark VARCHAR(500)",
            """
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
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_credit_user ON kai_credit_ledger(kai_user_id, id DESC)",
            """
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
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_usage_log_user ON kai_usage_log(kai_user_id, id DESC)",
            """
            CREATE TABLE IF NOT EXISTS kai_config (
                config_key   VARCHAR(100) PRIMARY KEY,
                config_value TEXT,
                updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            // ===== 内置模型（2026-10-20，与 db/169 同构） =====
            """
            CREATE TABLE IF NOT EXISTS kai_builtin_model (
                id          BIGSERIAL PRIMARY KEY,
                name        VARCHAR(100)  NOT NULL,
                base_url    VARCHAR(300)  NOT NULL,
                model       VARCHAR(100)  NOT NULL,
                api_key_enc TEXT          NOT NULL,
                rate        NUMERIC(6,2)  NOT NULL DEFAULT 1.00,
                enabled     INT           DEFAULT 1,
                sort        INT           DEFAULT 0,
                created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
                updated_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
            )
            """,
            // 旧「默认模型」单条配置迁移为内置模型一条（密文直拷；幂等：密钥非空才迁 + 同地址同模型已存在则跳过）
            """
            INSERT INTO kai_builtin_model (name, base_url, model, api_key_enc, rate, enabled)
            SELECT
                COALESCE(NULLIF((SELECT config_value FROM kai_config WHERE config_key = 'model.default.name'), ''), 'DeepSeek'),
                COALESCE(NULLIF((SELECT config_value FROM kai_config WHERE config_key = 'model.default.base_url'), ''), 'https://api.deepseek.com/v1'),
                COALESCE(NULLIF((SELECT config_value FROM kai_config WHERE config_key = 'model.default.model'), ''), 'deepseek-flash'),
                (SELECT config_value FROM kai_config WHERE config_key = 'model.default.api_key'),
                1.00,
                1
            WHERE COALESCE((SELECT config_value FROM kai_config WHERE config_key = 'model.default.api_key'), '') <> ''
              AND NOT EXISTS (
                  SELECT 1 FROM kai_builtin_model b
                  WHERE b.base_url = COALESCE(NULLIF((SELECT config_value FROM kai_config WHERE config_key = 'model.default.base_url'), ''), 'https://api.deepseek.com/v1')
                    AND b.model    = COALESCE(NULLIF((SELECT config_value FROM kai_config WHERE config_key = 'model.default.model'), ''), 'deepseek-flash')
              )
            """,
            // ===== 结果集持久化（2026-10-20）：查询结果落库，支持二次分析与多结果集对比，永久保留 =====
            """
            CREATE TABLE IF NOT EXISTS kai_query_result (
                id            BIGSERIAL PRIMARY KEY,
                kai_user_id   BIGINT       NOT NULL,
                session_id    BIGINT,
                connection_id BIGINT,
                label         VARCHAR(300),
                object_type   VARCHAR(64),
                filters_json  TEXT,
                columns_json  TEXT,
                row_count     INT          DEFAULT 0,
                data_json     TEXT,
                created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_qr_user ON kai_query_result(kai_user_id, id DESC)",
            "CREATE INDEX IF NOT EXISTS idx_kai_qr_sess ON kai_query_result(session_id)",
            // ===== 支付宝充值（2026-11-07，与 db/187 同构） =====
            """
            CREATE TABLE IF NOT EXISTS kai_recharge_package (
                id         BIGSERIAL PRIMARY KEY,
                name       VARCHAR(100)  NOT NULL,
                price      NUMERIC(12,2) NOT NULL DEFAULT 0,
                credits    NUMERIC(14,4) NOT NULL DEFAULT 0,
                sort_no    INT           DEFAULT 0,
                enabled    INT           DEFAULT 1,
                created_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_recharge_pkg_sort ON kai_recharge_package(enabled, sort_no, id)",
            """
            CREATE TABLE IF NOT EXISTS kai_recharge_order (
                id           BIGSERIAL PRIMARY KEY,
                order_no     VARCHAR(64)   NOT NULL UNIQUE,
                kai_user_id  BIGINT        NOT NULL,
                package_id   BIGINT,
                package_name VARCHAR(100),
                amount       NUMERIC(12,2) NOT NULL DEFAULT 0,
                credits      NUMERIC(14,4) NOT NULL DEFAULT 0,
                status       VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
                trade_no     VARCHAR(64),
                pay_time     TIMESTAMP,
                expire_at    TIMESTAMP,
                created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
                updated_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_kai_recharge_user ON kai_recharge_order(kai_user_id, id DESC)",
            "CREATE INDEX IF NOT EXISTS idx_kai_recharge_status ON kai_recharge_order(status, id DESC)"
    };

    @PostConstruct
    public void ensureSchema() {
        int ok = 0;
        for (String sql : DDL) {
            try {
                jdbcTemplate.execute(sql);
                ok++;
            } catch (Exception e) {
                // 单条失败不中断（如 ALTER 早于建表的历史场景），下一轮启动自愈补齐
                log.warn("[小楷独立版] DDL 未就绪（{}）：{}", e.getMessage(), sql.substring(0, Math.min(60, sql.length())).replace('\n', ' '));
            }
        }
        log.info("[小楷独立版] 表就绪自检：{}/{} 条通过（未过项下次启动重试），演示适配器已注册", ok, DDL.length);
    }
}
