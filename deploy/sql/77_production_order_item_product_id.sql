-- 2026-11-10 生产工单原料明细：新增「归属成品行」非主键列
-- 背景：此前把成品行主键写进原料明细主键 order_item_id，成品表与原料表主键序列独立、
--       值域重合时保存报「数据重复」（唯一约束 production_order_item_pkey 冲突）。
-- 口径：归属只写本列；order_item_id 一律自增，不再承载归属。
-- 幂等：重复执行结果一致。
ALTER TABLE production_order_item ADD COLUMN IF NOT EXISTS order_product_id BIGINT;

COMMENT ON COLUMN production_order_item.order_product_id IS '归属成品行主键（work_order_product.id），多成品行各自一套用料';

-- 历史数据不回填：旧记录的 order_item_id 是自增主键，已被错误当作归属使用但无法可靠还原，
-- 保持 NULL = 旧行为（显示全部时可见，按成品行查看时无归属）。
