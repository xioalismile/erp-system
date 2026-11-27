-- ============================================================================
-- 122_source_operation_id.sql
-- 第 11 期：生产入库单记录「来源工序行」，支撑「入库类型工序 → 一键发起入库单」按工序防重复
--   source_operation_id  来源工单工序行ID（work_order_operation.id）
--                        记录这张入库单是由哪道工序发起的；
--                        剩余可入库量 = 该工序合格产出 − 已下推入库量（按本列汇总）
--
-- 幂等：本文件可重复执行（守则 04 2.7.1-2）。
-- 同步落盘：deploy/sql/122_source_operation_id.sql（部署镜像）
--           + SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动逐租户自愈
-- ============================================================================

ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS source_operation_id BIGINT;
COMMENT ON COLUMN production_receipt.source_operation_id IS '来源工单工序行ID（work_order_operation.id）：入库类型工序一键发起入库单时写入，用于按工序控制剩余可入库量、防重复';

CREATE INDEX IF NOT EXISTS idx_production_receipt_source_operation
    ON production_receipt (source_operation_id);

-- ---------- 验证 ----------
SELECT 'production_receipt_source_operation_ok' AS result,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'production_receipt'
           AND column_name = 'source_operation_id') AS cols;
