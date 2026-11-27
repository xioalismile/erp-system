SET client_encoding = 'UTF8';
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
  AND (table_name LIKE '%unit%' OR table_name LIKE 'sys_unit' OR table_name LIKE 'measure%')
ORDER BY table_name;
