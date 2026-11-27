-- ============================================================================
-- 121_work_order_product_routing.sql
-- 第 10 期：工序按「成品明细行」绑定（支持一张工单多个商品）
--   1) 成品明细行自己的工艺路线：work_order_product.routing_id / routing_version
--   2) 工单工序归属到成品明细行：work_order_operation.order_product_id
--
-- 幂等：本文件可重复执行（守则 04 2.7.1-2）。
-- 同步落盘：deploy/sql/121_work_order_product_routing.sql（部署镜像）
--           + SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动逐租户自愈
-- ============================================================================

-- ---------- 1. 成品明细行：各自选工艺路线 ----------
ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS routing_id BIGINT;
ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS routing_version VARCHAR(50);
COMMENT ON COLUMN work_order_product.routing_id IS '该成品明细行使用的工艺路线（第10期：多商品工单每个成品各自一套工序）';
COMMENT ON COLUMN work_order_product.routing_version IS '工艺路线版本快照';

-- ---------- 2. 工单工序：归属到哪个成品明细行 ----------
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS order_product_id BIGINT;
COMMENT ON COLUMN work_order_operation.order_product_id IS '所属成品明细行ID（work_order_product.id）；为空表示老数据/整单共用';

-- ---------- 3. 存量兜底：老工单的工序没有成品行归属，按"工单的第一个成品行"补齐 ----------
--   只补空值；找不到成品行的保持为空（前端按"未指定成品"显示，不丢数据）
UPDATE work_order_operation w
   SET order_product_id = (
       SELECT p.id FROM work_order_product p
        WHERE p.order_id = w.order_id
        ORDER BY p.line_no NULLS LAST, p.id
        LIMIT 1)
 WHERE w.order_product_id IS NULL
   AND EXISTS (SELECT 1 FROM work_order_product p2 WHERE p2.order_id = w.order_id);

-- 老工单的成品行没有自己的路线时，回填整单的工艺路线（保证生效逻辑仍能按成品行生成）
UPDATE work_order_product p
   SET routing_id = o.routing_id,
       routing_version = o.routing_version
  FROM production_order o
 WHERE p.order_id = o.order_id
   AND p.routing_id IS NULL
   AND o.routing_id IS NOT NULL;

-- ---------- 验证 ----------
SELECT 'work_order_product_routing_ok' AS result,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'work_order_product'
           AND column_name IN ('routing_id', 'routing_version')) AS product_cols,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'work_order_operation'
           AND column_name = 'order_product_id') AS op_cols;
