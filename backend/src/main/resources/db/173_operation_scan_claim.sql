-- ============================================================================
-- 173_operation_scan_claim.sql
-- 工序扫码认领与按人报工（2026-10-25）
--
--   背景（用户口径）：
--     ① 临时工/顶班的人扫工序码，可直接「我来做」加入为共同负责人，不覆盖系统派工的主负责人；
--     ② 每人报自己的数量；认领时可填「我打算做多少」，填了按此卡个人上限，不填只计入总量；
--     ③ 可退出、可多人加入；
--     ④ 工序级开关「允许扫码认领」，默认「是」。
--
--   新增：
--     routing_operation.allow_scan_claim   每道工序是否允许扫码认领（是/否，默认是）
--     work_order_operation_worker          工序共同负责人（在岗/已退出/已结束 + 认领数量 + 个人已报）
--     operation_report_flow                工序报工流水（按人记账；工序已报 = 流水汇总）
--
--   幂等：ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS / 只补空值。
--   同步落盘：deploy/sql/173_operation_scan_claim.sql（部署镜像）
--             + backend/src/main/resources/db/173_operation_scan_claim.sql
--             + SchemaUpgradeEnsurer 启动逐库自愈（主库 + 每个租户库 + 模板库）
-- ============================================================================

BEGIN;

-- 1) 工序级「允许扫码认领」开关（默认是 = 与用户口径一致；关闭后扫码只能看不能认领）
ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS allow_scan_claim VARCHAR(8);
COMMENT ON COLUMN routing_operation.allow_scan_claim IS '本道工序是否允许扫码认领：是/否（空按“是”处理）';
UPDATE routing_operation SET allow_scan_claim = '是' WHERE allow_scan_claim IS NULL OR allow_scan_claim = '';
ALTER TABLE routing_operation ALTER COLUMN allow_scan_claim SET DEFAULT '是';

-- 2) 工序共同负责人（扫码认领 + 管理员增删）
CREATE TABLE IF NOT EXISTS work_order_operation_worker (
    id                BIGSERIAL PRIMARY KEY,
    operation_row_id  BIGINT NOT NULL,
    employee_id       BIGINT NOT NULL,
    employee_name     VARCHAR(100),
    claim_quantity    NUMERIC(18,4),
    reported_quantity NUMERIC(18,4) NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL DEFAULT '在岗',
    claim_time        TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    exit_time         TIMESTAMP WITHOUT TIME ZONE,
    claimed_by        BIGINT,
    claimed_by_name   VARCHAR(100),
    remark            VARCHAR(255)
);
COMMENT ON TABLE work_order_operation_worker IS '工序共同负责人：扫码认领或管理员添加；在岗期间可报自己的数量';
COMMENT ON COLUMN work_order_operation_worker.claim_quantity IS '认领数量（可空；空=不设个人上限）';
COMMENT ON COLUMN work_order_operation_worker.reported_quantity IS '个人累计已报（由报工流水汇总，缓存列）';
COMMENT ON COLUMN work_order_operation_worker.status IS '在岗/已退出/已结束';
CREATE INDEX IF NOT EXISTS idx_wop_worker_op ON work_order_operation_worker(operation_row_id);
CREATE INDEX IF NOT EXISTS idx_wop_worker_emp ON work_order_operation_worker(employee_id);
-- 同一道工序同一人只允许一条「在岗」记录（退出后可再次认领，历史保留）
CREATE UNIQUE INDEX IF NOT EXISTS uk_wop_worker_active
    ON work_order_operation_worker(operation_row_id, employee_id) WHERE status = '在岗';

-- 3) 工序报工流水（一本账：工序已报 / 个人已报都从这里汇总）
CREATE TABLE IF NOT EXISTS operation_report_flow (
    id                BIGSERIAL PRIMARY KEY,
    operation_row_id  BIGINT NOT NULL,
    order_id          BIGINT,
    order_no          VARCHAR(64),
    employee_id       BIGINT,
    employee_name     VARCHAR(100),
    quantity          NUMERIC(18,4) NOT NULL DEFAULT 0,
    rejected_quantity NUMERIC(18,4) NOT NULL DEFAULT 0,
    source            VARCHAR(20) NOT NULL DEFAULT '扫码报工',
    source_bill_id    BIGINT,
    source_bill_no    VARCHAR(64),
    operator_id       BIGINT,
    operator_name     VARCHAR(100),
    is_proxy          INTEGER NOT NULL DEFAULT 0,
    flow_status       VARCHAR(20) NOT NULL DEFAULT '有效',
    idem_key          VARCHAR(80),
    report_time       TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    remark            VARCHAR(255)
);
COMMENT ON TABLE operation_report_flow IS '工序报工流水：每笔报工一条，工序已报与个人已报均由它汇总';
COMMENT ON COLUMN operation_report_flow.source IS '扫码报工/工序汇报单';
COMMENT ON COLUMN operation_report_flow.is_proxy IS '1=代报（登录人代替所选员工报工）';
COMMENT ON COLUMN operation_report_flow.flow_status IS '有效/已冲销';
CREATE INDEX IF NOT EXISTS idx_orf_op ON operation_report_flow(operation_row_id);
CREATE INDEX IF NOT EXISTS idx_orf_emp ON operation_report_flow(employee_id);
CREATE INDEX IF NOT EXISTS idx_orf_order ON operation_report_flow(order_id);
-- 防连点/重试：同一防重号只允许一条
CREATE UNIQUE INDEX IF NOT EXISTS uk_orf_idem
    ON operation_report_flow(idem_key) WHERE idem_key IS NOT NULL;

COMMIT;

-- 验证 SQL（只读，预期：1 / 1 / 1）
SELECT COUNT(*) AS ro_allow_claim FROM information_schema.columns
 WHERE table_name = 'routing_operation' AND column_name = 'allow_scan_claim';
SELECT COUNT(*) AS worker_table FROM information_schema.tables WHERE table_name = 'work_order_operation_worker';
SELECT COUNT(*) AS flow_table FROM information_schema.tables WHERE table_name = 'operation_report_flow';
