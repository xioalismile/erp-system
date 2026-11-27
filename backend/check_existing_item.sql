SET client_encoding = 'UTF8';
-- 看一条已有明细的字段值，照着填
SELECT
  delivery_item_id, delivery_id, line_no,
  material_id, unit_id, quantity, unit_price,
  amount, tax_rate, tax_amount, net_amount,
  discount_rate, discount_amount, tax_type, stock_id
FROM sale_delivery_item
ORDER BY delivery_item_id DESC LIMIT 1;
