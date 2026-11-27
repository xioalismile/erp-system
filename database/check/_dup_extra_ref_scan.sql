-- ===========================================================================
-- 重复编码"多余行"被引用情况扫描（只读，不改数据）
-- 说明：多余的 id 一旦被业务表引用，删除前必须先把引用重指到保留行；
--       本脚本列出所有非零引用，供决定"重指后删除"还是"改名保留"。
-- ===========================================================================
\pset pager off
SET client_encoding TO 'UTF8';

BEGIN;

CREATE TEMP TABLE _dup_map(master text, extra_id bigint, keep_id bigint, code text, extra_name text) ON COMMIT DROP;

INSERT INTO _dup_map
SELECT 'unit_of_measure', u.unit_id,
       (SELECT min(x.unit_id) FROM unit_of_measure x WHERE x.unit_code = u.unit_code), u.unit_code, u.unit_name
FROM unit_of_measure u
WHERE EXISTS (SELECT 1 FROM unit_of_measure y WHERE y.unit_code = u.unit_code AND y.unit_id < u.unit_id);

INSERT INTO _dup_map
SELECT 'warehouse', w.stock_id,
       (SELECT min(x.stock_id) FROM warehouse x WHERE x.stock_code = w.stock_code), w.stock_code, w.stock_name
FROM warehouse w
WHERE EXISTS (SELECT 1 FROM warehouse y WHERE y.stock_code = w.stock_code AND y.stock_id < w.stock_id);

INSERT INTO _dup_map
SELECT 'department', d.dept_id,
       (SELECT min(x.dept_id) FROM department x WHERE x.dept_code = d.dept_code), d.dept_code, d.dept_name
FROM department d
WHERE EXISTS (SELECT 1 FROM department y WHERE y.dept_code = d.dept_code AND y.dept_id < d.dept_id);

INSERT INTO _dup_map
SELECT 'customer', c.customer_id,
       (SELECT min(x.customer_id) FROM customer x WHERE x.customer_code = c.customer_code), c.customer_code, c.customer_name
FROM customer c
WHERE EXISTS (SELECT 1 FROM customer y WHERE y.customer_code = c.customer_code AND y.customer_id < c.customer_id);

INSERT INTO _dup_map
SELECT 'supplier', s.supplier_id,
       (SELECT min(x.supplier_id) FROM supplier x WHERE x.supplier_code = s.supplier_code), s.supplier_code, s.supplier_name
FROM supplier s
WHERE EXISTS (SELECT 1 FROM supplier y WHERE y.supplier_code = s.supplier_code AND y.supplier_id < s.supplier_id);

INSERT INTO _dup_map
SELECT 'payment_term', p.payment_term_id,
       (SELECT min(x.payment_term_id) FROM payment_term x WHERE x.payment_term_code = p.payment_term_code), p.payment_term_code, p.payment_term_name
FROM payment_term p
WHERE EXISTS (SELECT 1 FROM payment_term y WHERE y.payment_term_code = p.payment_term_code AND y.payment_term_id < p.payment_term_id);

INSERT INTO _dup_map
SELECT 'price_level', p.price_level_id,
       (SELECT min(x.price_level_id) FROM price_level x WHERE x.price_level_code = p.price_level_code), p.price_level_code, p.price_level_name
FROM price_level p
WHERE EXISTS (SELECT 1 FROM price_level y WHERE y.price_level_code = p.price_level_code AND y.price_level_id < p.price_level_id);

INSERT INTO _dup_map
SELECT 'settlement_mode', s.settle_mode_id,
       (SELECT min(x.settle_mode_id) FROM settlement_mode x WHERE x.settle_mode_code = s.settle_mode_code), s.settle_mode_code, s.settle_mode_name
FROM settlement_mode s
WHERE EXISTS (SELECT 1 FROM settlement_mode y WHERE y.settle_mode_code = s.settle_mode_code AND y.settle_mode_id < s.settle_mode_id);

CREATE TEMP TABLE _ref_scan(tbl text, col text, master text, code text, extra_name text, extra_id bigint, keep_id bigint, cnt bigint) ON COMMIT DROP;

DO $$
DECLARE
    m RECORD; c RECORD; d RECORD; n bigint; pat text;
BEGIN
    FOR m IN SELECT DISTINCT master FROM _dup_map LOOP
        pat := CASE m.master
                 WHEN 'unit_of_measure' THEN 'unit_id|base_unit_id|purchase_unit_id|sale_unit_id|stock_unit_id|production_unit_id'
                 WHEN 'warehouse'       THEN 'stock_id|warehouse_id|wh_id|from_stock_id|to_stock_id|from_warehouse_id|to_warehouse_id'
                 WHEN 'department'      THEN 'dept_id|department_id'
                 WHEN 'customer'        THEN 'customer_id'
                 WHEN 'supplier'        THEN 'supplier_id'
                 WHEN 'payment_term'    THEN 'payment_term_id'
                 WHEN 'price_level'     THEN 'price_level_id'
                 WHEN 'settlement_mode' THEN 'settle_mode_id'
                 ELSE 'category_id|material_category_id'
               END;
        FOR c IN
            SELECT table_name AS tn, column_name AS cn
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND column_name ~ ('^(' || pat || ')$')
              AND table_name <> m.master
        LOOP
            FOR d IN SELECT * FROM _dup_map WHERE master = m.master LOOP
                EXECUTE format('SELECT count(*) FROM %I WHERE %I = $1', c.tn, c.cn)
                    USING d.extra_id INTO n;
                IF n > 0 THEN
                    INSERT INTO _ref_scan VALUES (c.tn, c.cn, m.master, d.code, d.extra_name, d.extra_id, d.keep_id, n);
                END IF;
            END LOOP;
        END LOOP;
    END LOOP;
END $$;

\echo '--- 多余行清单（每行一个待处理 id） ---'
SELECT master, code, extra_id, keep_id, extra_name FROM _dup_map ORDER BY master, code, extra_id;

\echo '--- 非零引用（删除前必须重指） ---'
SELECT tbl, col, master, code, extra_id, keep_id, cnt FROM _ref_scan ORDER BY master, code, tbl, col;

\echo '--- 汇总：有引用的多余 id 数 / 无引用的多余 id 数 ---'
SELECT
  (SELECT count(DISTINCT (master || '#' || extra_id)) FROM _ref_scan) AS 有引用,
  (SELECT count(*) FROM _dup_map) - (SELECT count(DISTINCT (master || '#' || extra_id)) FROM _ref_scan) AS 无引用;

ROLLBACK;
