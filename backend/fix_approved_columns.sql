-- 为所有带 approval_status 列的业务表补齐 approved_by / approved_at 字段
-- 实体类已定义这两列，但部分表的迁移脚本漏了
DO $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT table_schema, table_name
        FROM information_schema.columns
        WHERE column_name = 'approval_status'
          AND table_schema NOT IN ('pg_catalog', 'information_schema')
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = rec.table_schema
              AND table_name = rec.table_name
              AND column_name = 'approved_by'
        ) THEN
            EXECUTE format('ALTER TABLE %I.%I ADD COLUMN approved_by VARCHAR(64)', rec.table_schema, rec.table_name);
            RAISE NOTICE 'Added approved_by to %.%', rec.table_schema, rec.table_name;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = rec.table_schema
              AND table_name = rec.table_name
              AND column_name = 'approved_at'
        ) THEN
            EXECUTE format('ALTER TABLE %I.%I ADD COLUMN approved_at TIMESTAMP', rec.table_schema, rec.table_name);
            RAISE NOTICE 'Added approved_at to %.%', rec.table_schema, rec.table_name;
        END IF;
    END LOOP;
END $$;
