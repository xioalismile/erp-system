-- 删除生产链路孤儿明细（明细存在但主表已不存在）
\pset pager off
BEGIN;

SELECT 'before: work_order_product' AS step, count(*) FROM work_order_product
UNION ALL SELECT 'before: production_receipt_item', count(*) FROM production_receipt_item;

-- 待删清单（先看再删，便于追溯）
SELECT 'work_order_product' AS tbl, id AS pk, order_id AS parent_id, material_code, quantity
FROM work_order_product d WHERE NOT EXISTS (SELECT 1 FROM production_order m WHERE m.order_id = d.order_id)
UNION ALL
SELECT 'production_receipt_item', receipt_item_id, receipt_id, material_code, quantity
FROM production_receipt_item d WHERE NOT EXISTS (SELECT 1 FROM production_receipt m WHERE m.receipt_id = d.receipt_id);

DELETE FROM work_order_product d
WHERE NOT EXISTS (SELECT 1 FROM production_order m WHERE m.order_id = d.order_id);

DELETE FROM production_receipt_item d
WHERE NOT EXISTS (SELECT 1 FROM production_receipt m WHERE m.receipt_id = d.receipt_id);

-- 复核：应为 0
SELECT 'after: work_order_product_orphan' AS check_name, count(*) AS orphan_rows
FROM work_order_product d WHERE NOT EXISTS (SELECT 1 FROM production_order m WHERE m.order_id = d.order_id)
UNION ALL SELECT 'after: production_receipt_item_orphan', count(*)
FROM production_receipt_item d WHERE NOT EXISTS (SELECT 1 FROM production_receipt m WHERE m.receipt_id = d.receipt_id);

COMMIT;
