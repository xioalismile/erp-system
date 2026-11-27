-- 生产链路孤儿明细扫描（明细有行、主表无对应行）
SELECT 'work_order_product -> production_order' AS rel, count(*) AS orphan_rows
FROM work_order_product d WHERE NOT EXISTS (SELECT 1 FROM production_order m WHERE m.order_id = d.order_id)
UNION ALL SELECT 'production_order_item -> production_order', count(*)
FROM production_order_item d WHERE NOT EXISTS (SELECT 1 FROM production_order m WHERE m.order_id = d.order_id)
UNION ALL SELECT 'material_requisition_item -> material_requisition', count(*)
FROM material_requisition_item d WHERE NOT EXISTS (SELECT 1 FROM material_requisition m WHERE m.requisition_id = d.requisition_id)
UNION ALL SELECT 'production_receipt_item -> production_receipt', count(*)
FROM production_receipt_item d WHERE NOT EXISTS (SELECT 1 FROM production_receipt m WHERE m.receipt_id = d.receipt_id);
