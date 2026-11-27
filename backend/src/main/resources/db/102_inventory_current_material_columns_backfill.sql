-- =============================================================================
-- 102_inventory_current_material_columns_backfill.sql （2026-09-19）
-- 即时库存物料冗余列兜底 + 存量回填（幂等，可反复执行）
--
-- 背景：库存流水「物料编码」全空的同源问题。inventory_current 表本身有
--   material_code / material_name / spec 三列（41 号补丁加的），但实体把这三个字段
--   标成"非数据库字段"丢弃了库里的值 → 物料主档被删的行（本机库 24/24 行）编码同样空白。
--   实体已改为按真实列映射（2026-09-19），本脚本负责把存量数据补齐：
--     ① 物料主档仍在的，按 material_id 直取
--     ② 物料主档已被删的，从历史单据明细 / 库存台账里冗余保存的编码反查
--        （同一 material_id 只对应唯一编码时才采用，避免取到错值）
--
-- 幂等保证：ADD COLUMN IF NOT EXISTS；两条 UPDATE 都带「列为空」条件，重复执行命中 0 行。
-- =============================================================================

ALTER TABLE inventory_current ADD COLUMN IF NOT EXISTS material_code VARCHAR(50);
ALTER TABLE inventory_current ADD COLUMN IF NOT EXISTS material_name VARCHAR(200);
ALTER TABLE inventory_current ADD COLUMN IF NOT EXISTS spec VARCHAR(200);

-- ① 回填：物料主档仍在
UPDATE inventory_current c
   SET material_code = m.material_code,
       material_name = m.material_name,
       spec = m.model
  FROM material m
 WHERE m.material_id = c.material_id
   AND (c.material_code IS NULL OR c.material_code = '');

-- ② 回填：物料主档已不在（被删除 / 换过 id），用历史明细/台账里冗余保存的编码反查。
--    动态扫描 information_schema，缺表缺列自动跳过，不会因某个环境少一张表而报错。
DO $$
DECLARE
  v_union text;
  v_sql   text;
  v_rows  integer;
BEGIN
  SELECT string_agg(
           'SELECT material_id, material_code, material_name, ' ||
           CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns s
                              WHERE s.table_schema = c.table_schema
                                AND s.table_name = c.table_name
                                AND s.column_name IN ('spec', 'specification'))
                THEN 'COALESCE(' || (SELECT min(s.column_name) FROM information_schema.columns s
                                      WHERE s.table_schema = c.table_schema AND s.table_name = c.table_name
                                        AND s.column_name IN ('spec', 'specification')) || ','''')'
                ELSE '''''' END ||
           ' AS spec FROM ' || quote_ident(c.table_name) ||
           ' WHERE material_code IS NOT NULL AND material_code <> '''' AND material_id IS NOT NULL',
           ' UNION ALL ' ORDER BY c.table_name)
    INTO v_union
    FROM information_schema.columns c
   WHERE c.table_schema = current_schema()
     AND c.column_name = 'material_id'
     AND c.table_name NOT IN ('material', 'inventory_current')
     AND EXISTS (SELECT 1 FROM information_schema.columns a
                  WHERE a.table_schema = c.table_schema AND a.table_name = c.table_name
                    AND a.column_name = 'material_code')
     AND EXISTS (SELECT 1 FROM information_schema.columns b
                  WHERE b.table_schema = c.table_schema AND b.table_name = c.table_name
                    AND b.column_name = 'material_name');

  IF v_union IS NULL THEN
    RAISE NOTICE '102: 无历史编码来源表，跳过回填②';
    RETURN;
  END IF;

  v_sql :=
    'UPDATE inventory_current c SET material_code = u.material_code,'
    || ' material_name = u.material_name, spec = COALESCE(c.spec, u.spec) '
    || 'FROM (SELECT material_id, MIN(material_code) AS material_code,'
    || ' MIN(material_name) AS material_name, MIN(spec) AS spec '
    || '  FROM (' || v_union || ') h GROUP BY material_id'
    || '  HAVING COUNT(DISTINCT material_code) = 1) u '
    || 'WHERE u.material_id = c.material_id AND (c.material_code IS NULL OR c.material_code = '''')';

  EXECUTE v_sql;
  GET DIAGNOSTICS v_rows = ROW_COUNT;
  RAISE NOTICE '102: 历史编码回填 % 行', v_rows;
END $$;
