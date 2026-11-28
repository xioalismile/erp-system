-- 202_free_sample_stock_trace.sql
-- 免费样品单：记录发货是否已扣库存、冻结出库成本，作废/删除时冲回
-- 2026-11-28

ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS stock_deducted SMALLINT DEFAULT 0;
UPDATE free_sample_order SET stock_deducted = 0 WHERE stock_deducted IS NULL;
COMMENT ON COLUMN free_sample_order.stock_deducted IS '发货是否已扣库存：1=已扣，作废/删除时冲回；0=未扣';

ALTER TABLE free_sample_order_item ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,6);
COMMENT ON COLUMN free_sample_order_item.avg_cost IS '发货时冻结的出库成本，作废/删除按此成本退回库存';
