-- ============================================================================
-- 56_deploy_platform.sql - 客户升级部署平台（自营/平台管理用，客户包不含）
-- 表：deploy_server 部署目标服务器 / deploy_log 部署与回滚日志
-- ============================================================================

CREATE TABLE IF NOT EXISTS deploy_server (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(200) NOT NULL,
    ssh_port INT NOT NULL DEFAULT 22,
    ssh_user VARCHAR(100) NOT NULL DEFAULT 'Administrator',
    ssh_password VARCHAR(200) NOT NULL DEFAULT '',
    remote_work_dir VARCHAR(300) NOT NULL DEFAULT 'C:/smart-erp-update',
    jar_target VARCHAR(300) NOT NULL DEFAULT '',
    frontend_targets VARCHAR(600) NOT NULL DEFAULT '',
    stop_command VARCHAR(600) NOT NULL DEFAULT '',
    start_command VARCHAR(600) NOT NULL DEFAULT '',
    health_url VARCHAR(300) NOT NULL DEFAULT 'http://localhost/api/auth/login',
    db_name VARCHAR(100) NOT NULL DEFAULT '',
    db_user VARCHAR(100) NOT NULL DEFAULT '',
    db_password VARCHAR(200) NOT NULL DEFAULT '',
    remark VARCHAR(500),
    enabled SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

COMMENT ON TABLE deploy_server IS '部署目标客户服务器（SSH信息+路径+启停命令）';
COMMENT ON COLUMN deploy_server.jar_target IS '服务器上后端jar绝对路径，如 C:/smart-erp/backend/smart-erp.jar';
COMMENT ON COLUMN deploy_server.frontend_targets IS '前端目录列表，分号分隔，存在即全拷';
COMMENT ON COLUMN deploy_server.stop_command IS '停服务命令，如 schtasks /End /TN SmartERP-Backend';
COMMENT ON COLUMN deploy_server.start_command IS '启服务命令，如 schtasks /Run /TN SmartERP-Backend';
COMMENT ON COLUMN deploy_server.db_name IS '业务库名(如 erp_db)。三项齐全则部署时agent执行幂等补列(schema sync)，防老库缺列卡登录';

-- 老环境升级（表已存在的库补列，幂等）：
ALTER TABLE deploy_server ADD COLUMN IF NOT EXISTS db_name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE deploy_server ADD COLUMN IF NOT EXISTS db_user VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE deploy_server ADD COLUMN IF NOT EXISTS db_password VARCHAR(200) NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS deploy_log (
    id BIGSERIAL PRIMARY KEY,
    server_id BIGINT NOT NULL,
    server_name VARCHAR(100),
    action VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    version_note VARCHAR(500),
    log_text TEXT,
    operator VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

COMMENT ON TABLE deploy_log IS '部署/回滚执行日志（action: DEPLOY/ROLLBACK; status: RUNNING/SUCCESS/FAILED/ROLLED_BACK）';

CREATE INDEX IF NOT EXISTS idx_deploy_log_server ON deploy_log(server_id);
CREATE INDEX IF NOT EXISTS idx_deploy_log_created ON deploy_log(created_at DESC);
