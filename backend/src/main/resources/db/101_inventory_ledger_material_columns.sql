-- =============================================================================
-- 101_inventory_ledger_material_columns.sql （2026-09-19）
-- 库存台账补「物料冗余展示列」+ 存量回填（幂等，可反复执行）
--
-- 背景（用户报障实锤）：库存流水页「物料编码 / 物料名称 / 规格」列全空。
--   根因：inventory_ledger 只存 material_id，展示字段全靠列表接口实时 join material 表；
--         物料主档被删除或被整批换 id 之后，历史台账永久无法还原
--         （本机库实测：231 行流水的 material_id 在 material 表 231/231 全部查不到）。
--   对照组：other_receipt_item / sale_order_item 等明细表都冗余保存 material_code+material_name，
--         所以物料被删后它们的列表照常显示 —— 台账缺这两列属于设计缺口。
--
-- 本脚本三步（全部幂等）：
--   ① 台账补 material_code / material_name / specification 三列
--   ② 回填：物料主档仍在的，按 material_id 直取
--   ③ 回填：物料主档已不在的，从历史单据明细 / 现存量里冗余保存的编码反查
--          （同一 material_id 只对应唯一编码时才采用，避免取到错值）
--
-- 幂等保证：ADD COLUMN IF NOT EXISTS；两条 UPDATE 都带「列为空」条件，重复执行命中 0 行。
-- =============================================================================

-- ① 补列
ALTER TABLE inventory_ledger ADD COLUMN IF NOT EXISTS material_code VARCHAR(50);
ALTER TABLE inventory_ledger ADD COLUMN IF NOT EXISTS material_name VARCHAR(200);
ALTER TABLE inventory_ledger ADD COLUMN IF NOT EXISTS specification VARCHAR(200);

-- ② 回填：物料主档仍在（正常环境 / 未删过物料的行）
UPDATE inventory_ledger l
   SET material_code = m.material_code,
       material_name = m.material_name,
       specification = m.model
  FROM material m
 WHERE m.material_id = l.material_id
   AND (l.material_code IS NULL OR l.material_code = '');

-- ③ 回填：物料主档已不在（被删除 / 换过 id），用历史明细里冗余保存的编码反查。
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
                                AND s.column_name = 'specification')
                THEN 'COALESCE(specification,'''')'
                ELSE '''''' END ||
           ' AS spec FROM ' || quote_ident(c.table_name) ||
           ' WHERE material_code IS NOT NULL AND material_code <> '''' AND material_id IS NOT NULL',
           ' UNION ALL ' ORDER BY c.table_name)
    INTO v_union
    FROM information_schema.columns c
   WHERE c.table_schema = current_schema()
     AND c.column_name = 'material_id'
     AND c.table_name NOT IN ('material', 'inventory_ledger')
     AND EXISTS (SELECT 1 FROM information_schema.columns a
                  WHERE a.table_schema = c.table_schema AND a.table_name = c.table_name
                    AND a.column_name = 'material_code')
     AND EXISTS (SELECT 1 FROM information_schema.columns b
                  WHERE b.table_schema = c.table_schema AND b.table_name = c.table_name
                    AND b.column_name = 'material_name');

  IF v_union IS NULL THEN
    RAISE NOTICE '101: 无历史编码来源表，跳过回填③';
    RETURN;
  END IF;

  v_sql :=
    'UPDATE inventory_ledger l SET material_code = u.material_code,'
    || ' material_name = u.material_name, specification = COALESCE(l.specification, u.spec) '
    || 'FROM (SELECT material_id, MIN(material_code) AS material_code,'
    || ' MIN(material_name) AS material_name, MIN(spec) AS spec '
    || '  FROM (' || v_union || ') h GROUP BY material_id'
    || '  HAVING COUNT(DISTINCT material_code) = 1) u '
    || 'WHERE u.material_id = l.material_id AND (l.material_code IS NULL OR l.material_code = '''')';

  EXECUTE v_sql;
  GET DIAGNOSTICS v_rows = ROW_COUNT;
  RAISE NOTICE '101: 历史编码回填 % 行', v_rows;
END $$;
