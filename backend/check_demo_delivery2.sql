SET client_encoding = 'UTF8';
SELECT delivery_no, substr(delivery_no, 1, 14) as dno14,
       substr(tracking_no, 1, 8) as tno8,
       customer_id, warehouse_id, currency_id, saler_id, org_code,
       status, approval_status, delivery_id, substr(remark, 1, 30) as rmk
FROM sale_delivery
WHERE delivery_no LIKE 'DN20260814%'
ORDER BY delivery_no;
