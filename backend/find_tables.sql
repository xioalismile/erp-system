SET client_encoding = 'UTF8';
-- 所有表名大写的postgresql，用双引号取
SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND (table_name ILIKE '%inventory%' OR table_name ILIKE '%org%' OR table_name ILIKE '%warehouse%') ORDER BY 1 LIMIT 20;
SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name ILIKE 'sys_%' ORDER BY 1 LIMIT 50;
