-- ============================================================================
-- 125_operation_amount.sql
-- 第 13 期：车间工序可视化 —— 金额类型工序的「计划金额 / 已确认金额」
--
--   1) routing_operation     加列 plan_amount       计划金额（仅「金额类型」工序可填）
--   2) work_order_operation  加列 plan_amount       计划金额（工艺路线快照）
--                            加列 confirmed_amount  已确认金额（金额类型工序登记金额时累加）
--
-- 口径（已确认为唯一事实来源，代码不得偏离）：
--   · plan_amount 只对「金额类型」工序有意义，其余类型留空；
--   · 金额类型工序在操作面板登记「本次金额」时，累加到 confirmed_amount
--     （改造前只写操作日志备注、不落库 → 命中"字段不持久化"硬约束，本次补上）；
--   · 看板金额类型站点显示「已确认金额 / 计划金额」进度环。
--
-- 幂等：ADD COLUMN IF NOT EXISTS（可重复执行）。无 DELETE / DROP / 无 WHERE 的全表 UPDATE。
-- 同步落盘：deploy/sql/125_operation_amount.sql（部署镜像）
--           + SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动逐租户自愈（多租户物理隔离多库）
-- ============================================================================

BEGIN;

ALTER TABLE routing_operation    ADD COLUMN IF NOT EXISTS plan_amount NUMERIC(18,4);
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS plan_amount NUMERIC(18,4);
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS confirmed_amount NUMERIC(18,4);

COMMENT ON COLUMN routing_operation.plan_amount         IS '计划金额（仅「金额类型」工序可填；其它类型留空）';
COMMENT ON COLUMN work_order_operation.plan_amount      IS '计划金额（工艺路线快照，仅金额类型工序）';
COMMENT ON COLUMN work_order_operation.confirmed_amount IS '已确认金额（金额类型工序登记金额时累加；为空视为 0）';

COMMIT;

-- ============================================================
-- 验证 SQL（只读，预期：3 列齐）
-- ============================================================
SELECT 'operation_amount_ok' AS result,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'routing_operation'
           AND column_name = 'plan_amount') AS routing_col,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'work_order_operation'
           AND column_name IN ('plan_amount', 'confirmed_amount')) AS woo_cols;
