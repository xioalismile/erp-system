SET client_encoding = 'UTF8';
-- 真正的库存表叫inventory_current (不是inventory)
SELECT * FROM inventory_current WHERE stock_id=1 AND material_id IN (SELECT DISTINCT material_id FROM sale_delivery_item WHERE delivery_id IN (21,22,23,24,25)) LIMIT 20;
SELECT COUNT(*) AS total_items, COUNT(DISTINCT material_id) AS distinct_mats FROM sale_delivery_item WHERE delivery_id IN (21,22,23,24,25);
SELECT delivery_id, material_id, quantity, unit_id, batch_no, bin_id FROM sale_delivery_item WHERE delivery_id=22 ORDER BY line_no;
