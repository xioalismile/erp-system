SET client_encoding = 'UTF8';
-- 找10条最近的出库单看看是哪个delivery_no/有哪些必填字段
SELECT delivery_id, delivery_no, substr(tracking_no, 1, 8) as tno,
       customer_id, warehouse_id, currency_id, saler_id, org_code,
       status, approval_status
FROM sale_delivery
WHERE tracking_no IS NOT NULL AND tracking_no <> ''
ORDER BY delivery_id DESC LIMIT 10;
