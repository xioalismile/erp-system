-- ============================================================================
-- 117_work_operation_rework_scrap.sql
-- 第 4 期：工序「回退确认 + 判废确认 + 一键下推补料」所需的工单工序列
--   rework_quantity        回退/返修数量（下道工序退回本道的数量）
--   scrap_quantity         判废数量（本道师傅判定"回退料中不能用"的数量）
--   scrap_pushed_quantity  已下推补料的判废数量（可下推量 = 判废数量 - 已下推数量）
--   supplement_bill_no     最近一次下推生成的补料领料单号
--
-- 幂等：本文件可重复执行（守则 04 2.7.1-2）。
-- 同步落盘：deploy/sql/117_work_operation_rework_scrap.sql（部署镜像）
--           + SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动逐租户自愈
-- ============================================================================

ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS rework_quantity NUMERIC(18,4) DEFAULT 0;
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS scrap_quantity NUMERIC(18,4) DEFAULT 0;
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS scrap_pushed_quantity NUMERIC(18,4) DEFAULT 0;
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS supplement_bill_no VARCHAR(64);

COMMENT ON COLUMN work_order_operation.rework_quantity IS '回退/返修数量：下道工序发现不良后退回本道的数量';
COMMENT ON COLUMN work_order_operation.scrap_quantity IS '判废数量：本道师傅判定回退料中不能用的数量（决定能否下推补料）';
COMMENT ON COLUMN work_order_operation.scrap_pushed_quantity IS '已下推补料的判废数量（可下推量 = 判废数量 - 已下推数量）';
COMMENT ON COLUMN work_order_operation.supplement_bill_no IS '最近一次下推生成的补料领料单号';

-- 存量兜底：老行 NULL → 0（否则"可下推量"会算成 NULL）
UPDATE work_order_operation SET rework_quantity = 0 WHERE rework_quantity IS NULL;
UPDATE work_order_operation SET scrap_quantity = 0 WHERE scrap_quantity IS NULL;
UPDATE work_order_operation SET scrap_pushed_quantity = 0 WHERE scrap_pushed_quantity IS NULL;

-- ---------- 验证 ----------
SELECT 'work_operation_rework_scrap_ok' AS result,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'work_order_operation'
           AND column_name IN ('rework_quantity', 'scrap_quantity', 'scrap_pushed_quantity', 'supplement_bill_no')) AS cols;
