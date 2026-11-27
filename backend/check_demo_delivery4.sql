SET client_encoding = 'UTF8';
SELECT count(*) FROM sale_delivery WHERE substr(delivery_no, 1, 8) = 'DN202608';
SELECT max(delivery_id) FROM sale_delivery;
