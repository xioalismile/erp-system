-- ========================================================
-- 智能ERP 权限改造 脚本1/2: 修改 sys_role_permission.menu_id 列类型
-- 用法: psql -U erp_user -d erp_db -f 29a_alter_menu_id_column.sql
-- ========================================================
BEGIN;

DO $$
DECLARE
    v_fk_name TEXT;
BEGIN
    -- 1. 删除外键约束 menu_id -> sys_menu.menu_id
    SELECT tc.constraint_name INTO v_fk_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
    WHERE tc.table_name = ''sys_role_permission''
      AND tc.constraint_type = ''FOREIGN KEY''
      AND kcu.column_name = ''menu_id''
    LIMIT 1;

    IF v_fk_name IS NOT NULL THEN
        EXECUTE ''ALTER TABLE sys_role_permission DROP CONSTRAINT '' || quote_ident(v_fk_name);
        RAISE NOTICE ''Dropped FK: %'', v_fk_name;
    END IF;

    -- 2. 删除旧唯一约束（后续重建）
    PERFORM 1 FROM information_schema.table_constraints
    WHERE table_name = ''sys_role_permission''
      AND constraint_type = ''UNIQUE'';

    -- 3. 修改 menu_id 列类型 BIGINT -> VARCHAR(200)
    EXECUTE ''ALTER TABLE sys_role_permission ALTER COLUMN menu_id TYPE VARCHAR(200) USING menu_id::VARCHAR(200)'';
    EXECUTE ''ALTER TABLE sys_role_permission ALTER COLUMN menu_id SET NOT NULL'';

    -- 4. 清理旧数据（旧数据是 sys_menu 的数字 menu_id，无意义）
    DELETE FROM sys_role_permission;
    RAISE NOTICE ''旧 sys_role_permission 数据已清空'';
END $$;

-- 5. 重建唯一约束
ALTER TABLE sys_role_permission DROP CONSTRAINT IF EXISTS sys_role_permission_role_id_menu_id_permission_type_key;
ALTER TABLE sys_role_permission
ADD CONSTRAINT sys_role_permission_role_id_menu_id_permission_type_key
UNIQUE (role_id, menu_id, permission_type);

-- 6. 重建索引
DROP INDEX IF EXISTS idx_sys_role_permission_menu;
CREATE INDEX idx_sys_role_permission_menu ON sys_role_permission(menu_id);
DROP INDEX IF EXISTS idx_sys_role_permission_role;
CREATE INDEX idx_sys_role_permission_role ON sys_role_permission(role_id);

-- 7. 重置序列
SELECT setval(''''sys_role_permission_permission_id_seq'''',
               COALESCE((SELECT MAX(permission_id) FROM sys_role_permission), 1), true);

COMMIT;
RAISE NOTICE ''======== 29a_alter_menu_id_column.sql 执行完成 ========'';
