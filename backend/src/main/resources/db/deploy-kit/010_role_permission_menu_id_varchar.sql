-- ============================================================
-- 010_role_permission_menu_id_varchar.sql（deploy-kit 守卫版）
-- 来源：29a_alter_menu_id_column.sql 的安全重写。
-- 原版问题：无条件 DELETE FROM sys_role_permission + ALTER，重跑会清空全部角色权限。
-- 本版：仅当 menu_id 列还是 bigint（旧 schema）时才迁移；varchar 库上零动作。
-- 幂等：可安全重复执行；正确性依赖：必须先于 29b/46/47（写 route_path 权限行）执行。
-- ============================================================

DO $$
DECLARE
    v_type TEXT;
    v_fk   TEXT;
BEGIN
    SELECT data_type INTO v_type
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'sys_role_permission'
      AND column_name = 'menu_id';

    IF v_type IS DISTINCT FROM 'bigint' THEN
        RAISE NOTICE 'menu_id already non-bigint (%), skip migration', v_type;
        RETURN;
    END IF;

    -- 1. 删除指向 sys_menu 的外键（数字 menu_id 模型遗留）
    SELECT tc.constraint_name INTO v_fk
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
    WHERE tc.table_name = 'sys_role_permission'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND kcu.column_name = 'menu_id'
    LIMIT 1;

    IF v_fk IS NOT NULL THEN
        EXECUTE 'ALTER TABLE sys_role_permission DROP CONSTRAINT ' || quote_ident(v_fk);
        RAISE NOTICE 'dropped FK: %', v_fk;
    END IF;

    -- 2. 旧数据是数字 menu_id，在 route_path 权限模型下无意义，迁移前必须清空
    DELETE FROM sys_role_permission;
    RAISE NOTICE 'legacy numeric permission rows cleared';

    -- 3. 列类型 bigint -> varchar(200)
    EXECUTE 'ALTER TABLE sys_role_permission ALTER COLUMN menu_id TYPE VARCHAR(200) USING menu_id::VARCHAR(200)';
    RAISE NOTICE 'menu_id migrated to varchar(200)';
END $$;

-- 4. 唯一约束（存在则不重建）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'sys_role_permission_role_id_menu_id_permission_type_key') THEN
        ALTER TABLE sys_role_permission
            ADD CONSTRAINT sys_role_permission_role_id_menu_id_permission_type_key
            UNIQUE (role_id, menu_id, permission_type);
    END IF;
END $$;

-- 5. 索引（幂等）
CREATE INDEX IF NOT EXISTS idx_sys_role_permission_menu ON sys_role_permission(menu_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_permission_role ON sys_role_permission(role_id);

-- 6. 序列对齐
SELECT setval('sys_role_permission_permission_id_seq',
              COALESCE((SELECT MAX(permission_id) FROM sys_role_permission), 1), true);
