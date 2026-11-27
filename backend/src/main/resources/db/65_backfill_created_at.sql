-- 65_backfill_created_at.sql
-- 2026-09-11 默认排序修复配套数据补丁
--
-- 背景：PostgreSQL 中 ORDER BY created_at DESC 默认把 NULL 排在最前（NULLS FIRST），
--       历史导入/直插产生的业务数据 created_at 为空时会被顶到列表最上方，
--       表现为“默认排序不是最新的单据在最前面”。查询层已改为空值排最后
--       （SortHelper.orderByNewest），本脚本负责把这些历史空值补齐，
--       让“创建时间”列有值、排序稳定。
--
-- 回填规则（幂等，可重复执行）：
--   1) 仅处理 public 下的业务表（排除 sys_* / *_bak*）
--   2) 只回填 created_at IS NULL 的行，已有值不动
--   3) 按主键倒序分配时间戳：主键越大越新，整体落在本表已有最早 created_at 之前
--      （全表为空时以当前时间为基准），保证不会把历史数据顶到最新
--   4) 重复执行时无空值可回填，零副作用
DO $$
DECLARE
  r record;
  pk text;
  null_cnt bigint;
  base_ts timestamp;
BEGIN
  FOR r IN
    SELECT c.table_name
    FROM information_schema.columns c
    JOIN information_schema.tables t
      ON t.table_name = c.table_name AND t.table_schema = c.table_schema
    WHERE c.table_schema = 'public'
      AND c.column_name = 'created_at'
      AND t.table_type = 'BASE TABLE'
      AND c.table_name NOT LIKE 'sys\_%'
      AND c.table_name NOT LIKE '%\_bak%'
    ORDER BY c.table_name
  LOOP
    EXECUTE format('SELECT count(*) FROM %I WHERE created_at IS NULL', r.table_name) INTO null_cnt;
    IF null_cnt = 0 THEN
      CONTINUE;
    END IF;

    SELECT kcu.column_name INTO pk
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
    WHERE tc.table_schema = 'public'
      AND tc.table_name = r.table_name
      AND tc.constraint_type = 'PRIMARY KEY'
    LIMIT 1;
    IF pk IS NULL THEN
      CONTINUE;
    END IF;

    EXECUTE format('SELECT min(created_at) FROM %I', r.table_name) INTO base_ts;
    IF base_ts IS NULL THEN
      base_ts := now();
    END IF;

    EXECUTE format(
      'UPDATE %I t SET created_at = $1 - make_interval(secs => sub.ord::double precision) '
      || 'FROM (SELECT %I AS k, row_number() OVER (ORDER BY %I DESC) AS ord '
      || 'FROM %I WHERE created_at IS NULL) sub '
      || 'WHERE t.%I = sub.k',
      r.table_name, pk, pk, r.table_name, pk
    ) USING base_ts;

    RAISE NOTICE 'backfill created_at: % rows=%', r.table_name, null_cnt;
  END LOOP;
END $$;
