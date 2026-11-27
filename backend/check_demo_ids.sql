SET client_encoding = 'UTF8';
SELECT delivery_id, delivery_no, status, approval_status, customer_id, saler_id, currency_id, warehouse_id, org_code
FROM sale_delivery
WHERE delivery_no BETWEEN 'DEMO000001' AND 'DEMO000005'
ORDER BY delivery_no;
