-- ============================================================================
-- 116_work_operation_log.sql
-- 车间工序可视化 · 第 2 期：工序操作日志表 + 工序状态 8 值口径统一
--
-- 幂等：本文件可重复执行（守则 04 2.7.1-2）。
-- 同步落盘：deploy/sql/116_work_operation_log.sql（部署镜像）
--           + SchemaUpgradeEnsurer.ensureWorkOperationLog() 启动逐租户自愈
-- ============================================================================

-- ---------- 1. 工序操作日志（只增不改不删，用于追溯与绩效） ----------
CREATE TABLE IF NOT EXISTS work_operation_log (
    log_id            BIGSERIAL PRIMARY KEY,
    order_id          BIGINT,
    order_no          VARCHAR(50),
    operation_row_id  BIGINT,
    operation_code    VARCHAR(50),
    operation_name    VARCHAR(200),
    action_type       VARCHAR(30),
    from_status       VARCHAR(20),
    to_status         VARCHAR(20),
    quantity          NUMERIC(18,4),
    rejected_quantity NUMERIC(18,4),
    operator_id       BIGINT,
    operator_name     VARCHAR(50),
    operate_time      TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    remark            VARCHAR(500)
);
COMMENT ON TABLE work_operation_log IS '工单工序操作日志：谁、什么时间、对哪道工序做了什么、状态从哪到哪';
COMMENT ON COLUMN work_operation_log.action_type IS '动作：派工/开工/暂停/恢复/完工/检验通过/检验退回/取消/数量报工/入库/金额登记/单据驱动';
CREATE INDEX IF NOT EXISTS idx_work_operation_log_order ON work_operation_log(order_id);
CREATE INDEX IF NOT EXISTS idx_work_operation_log_row ON work_operation_log(operation_row_id);

-- ---------- 2. 工序状态统一为 8 值 ----------
-- 新口径：待派工 / 已派工 / 已开工 / 进行中 / 暂停 / 完工待检 / 已完工 / 已取消
-- 旧口径（历史代码写入）：PENDING / 待开始 / PROCESSING / 进行中 / COMPLETED / 已完成
-- 迁移：未开始 → 待派工；进行中 → 进行中；已完成 → 已完工
UPDATE work_order_operation SET operation_status = '待派工'
 WHERE operation_status IS NULL OR operation_status IN ('PENDING', '待开始', '未开始');
UPDATE work_order_operation SET operation_status = '进行中'
 WHERE operation_status IN ('PROCESSING');
UPDATE work_order_operation SET operation_status = '已完工'
 WHERE operation_status IN ('COMPLETED', '已完成');
ALTER TABLE work_order_operation ALTER COLUMN operation_status SET DEFAULT '待派工';

-- ---------- 验证 ----------
SELECT 'work_operation_log_ok' AS result,
       (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_name = 'work_operation_log') AS log_table,
       (SELECT COUNT(*) FROM work_order_operation
         WHERE operation_status NOT IN ('待派工','已派工','已开工','进行中','暂停','完工待检','已完工','已取消')) AS bad_status_rows;
