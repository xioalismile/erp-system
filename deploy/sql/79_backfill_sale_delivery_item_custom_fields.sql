-- =====================================================================
-- 79_backfill_sale_delivery_item_custom_fields.sql
-- 销售出库单明细「自定义字段」历史值一次性回填（2026-11-16）
--
-- 背景：旧版「销售订单 → 下推销售出库单」只复制系统字段，没有复制明细
--       自定义字段（芯片 / 型号 / 工艺），历史出库单在列表页「明细模式」
--       下这几列为空；新版本下推已修复，历史单据需要一次性回填。
-- 口径：
--   1. 只按「销售出库明细.source_item_id = 销售订单明细.order_item_id」回填；
--   2. 只补目标为空（无记录 / 空串）的字段，已有值一律不覆盖；
--   3. 只回填销售出库明细实体已启用的自定义字段（按字段标识对齐）；
--   4. 幂等：重复执行不会新增或修改任何行（第二次执行影响 0 行）；
--   5. 一次性数据修复，手工执行一次，不挂启动自愈、不随服务启动反复跑。
-- 执行后核对：文末三条只读 SQL 的缺失行数应全部为 0。
-- =====================================================================

BEGIN;

-- 1) 已有记录但值为空：补值（不覆盖已有非空值）
UPDATE sys_custom_field_value dv
   SET field_value = sv.field_value,
       updated_at  = now()
  FROM sale_delivery_item i
  JOIN sale_order_item oi ON oi.order_item_id = i.source_item_id
  JOIN sys_custom_field d ON d.entity_type = 'sale_delivery_item' AND d.is_enabled = 1
  JOIN sys_custom_field_value sv ON sv.entity_type = 'sale_order_item'
                                AND sv.entity_id   = oi.order_item_id
                                AND sv.field_key   = d.field_key
                                AND sv.field_value IS NOT NULL
                                AND sv.field_value <> ''
 WHERE dv.entity_type = 'sale_delivery_item'
   AND dv.entity_id   = i.delivery_item_id
   AND dv.field_key   = d.field_key
   AND (dv.field_value IS NULL OR dv.field_value = '');

-- 2) 完全没有记录的：插入缺失值
INSERT INTO sys_custom_field_value
       (entity_type, entity_id, field_key, field_value, created_at, updated_at, created_by, updated_by)
SELECT 'sale_delivery_item', i.delivery_item_id, d.field_key, sv.field_value,
       now(), now(), 'system-repair', 'system-repair'
  FROM sale_delivery_item i
  JOIN sale_order_item oi ON oi.order_item_id = i.source_item_id
  JOIN sys_custom_field d ON d.entity_type = 'sale_delivery_item' AND d.is_enabled = 1
  JOIN sys_custom_field_value sv ON sv.entity_type = 'sale_order_item'
                                AND sv.entity_id   = oi.order_item_id
                                AND sv.field_key   = d.field_key
                                AND sv.field_value IS NOT NULL
                                AND sv.field_value <> ''
 WHERE NOT EXISTS (
       SELECT 1 FROM sys_custom_field_value dv
        WHERE dv.entity_type = 'sale_delivery_item'
          AND dv.entity_id   = i.delivery_item_id
          AND dv.field_key   = d.field_key
 );

COMMIT;

-- =====================================================================
-- 核对（只读）：以下查询的 missing_rows 应全部为 0
-- =====================================================================
-- SELECT d.field_key, d.field_label, COUNT(*) AS missing_rows
--   FROM sale_delivery_item i
--   JOIN sale_order_item oi ON oi.order_item_id = i.source_item_id
--   JOIN sys_custom_field d ON d.entity_type = 'sale_delivery_item' AND d.is_enabled = 1
--   JOIN sys_custom_field_value sv ON sv.entity_type = 'sale_order_item'
--                                 AND sv.entity_id = oi.order_item_id
--                                 AND sv.field_key = d.field_key
--                                 AND sv.field_value IS NOT NULL AND sv.field_value <> ''
--   LEFT JOIN sys_custom_field_value dv ON dv.entity_type = 'sale_delivery_item'
--                                      AND dv.entity_id = i.delivery_item_id
--                                      AND dv.field_key = d.field_key
--                                      AND dv.field_value IS NOT NULL AND dv.field_value <> ''
--  WHERE dv.entity_id IS NULL
--  GROUP BY d.field_key, d.field_label
--  ORDER BY d.field_key;
