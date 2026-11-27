-- 182 生产入库单明细行级来源工序（幂等）
-- 背景：2026-11-06 用户口径 —— 入库类型工序完工自动生成草稿入库单；生产入库单手动选来源后要回写对应工序节点。
-- 说明：source_operation_id 指向 work_order_operation.id（该明细行由哪道入库工序产生）。
-- 兼容：存量单据该列为 NULL，按表头来源工序口径统计（不重复计入）。

ALTER TABLE production_receipt_item ADD COLUMN IF NOT EXISTS source_operation_id BIGINT;

COMMENT ON COLUMN production_receipt_item.source_operation_id IS '来源工序行（work_order_operation.id），已入库量按行汇总';
