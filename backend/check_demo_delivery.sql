SET client_encoding = 'UTF8';
SELECT delivery_no, substr(tracking_no, 1, 8) as tno8,
       customer_id, warehouse_id, currency_id, saler_id, org_code,
       status, approval_status, delivery_id
FROM sale_delivery
WHERE tracking_no IN ('SF1234567890','75812345678901','YT1234567890123','43211234567890','JD12345678901')
ORDER BY delivery_no;
