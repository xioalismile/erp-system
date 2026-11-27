SET client_encoding = 'UTF8';

-- 取一个真实存在的物料，看它的 unit_id 列名是什么
SELECT column_name FROM information_schema.columns
WHERE table_name = 'material' AND column_name LIKE '%unit%'
ORDER BY column_name;
