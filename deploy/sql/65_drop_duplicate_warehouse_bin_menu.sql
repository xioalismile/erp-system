-- 65_drop_duplicate_warehouse_bin_menu.sql
-- 目的：下线与「基础资料 → 仓库档案 / 货位档案」重复、且字段整页错配的两个库存菜单：
--   702 inventory_warehouse 「仓库管理」/inventory/warehouses  → inventory/WarehouseList
--   703 inventory_bin       「储位管理」/inventory/locations   → inventory/LocationList
-- 背景：这两个页面的下拉/表格字段与实体属性整体错配（点进去整页空白），
--       正确入口是 basedata/warehouse 与 basedata/location（仓库档案、货位档案）。
-- 口径：幂等。菜单不存在时无副作用；重复执行结果一致。同时清理角色授权，避免悬挂权限。
-- 注意：menu_id 在 sys_role_permission 中是 VARCHAR(200)，故统一按文本比较。
DO $$
DECLARE
    v_ids TEXT[];
BEGIN
    IF to_regclass('sys_menu') IS NULL THEN
        RAISE NOTICE '65: sys_menu 不存在，跳过';
        RETURN;
    END IF;

    SELECT array_agg(menu_id::text) INTO v_ids
      FROM sys_menu
     WHERE menu_code IN ('inventory_warehouse', 'inventory_bin')
        OR route_path IN ('/inventory/warehouses', '/inventory/locations');

    IF v_ids IS NULL THEN
        RAISE NOTICE '65: 无待清理菜单（已清理过或本环境未初始化）';
        RETURN;
    END IF;

    IF to_regclass('sys_role_permission') IS NOT NULL THEN
        DELETE FROM sys_role_permission WHERE menu_id IN (SELECT unnest(v_ids));
    END IF;
    IF to_regclass('sys_role_menu') IS NOT NULL THEN
        DELETE FROM sys_role_menu WHERE menu_id IN (SELECT unnest(v_ids));
    END IF;

    DELETE FROM sys_menu WHERE menu_id::text IN (SELECT unnest(v_ids));

    RAISE NOTICE '65: 已清理重复的仓库/储位菜单 % 条', array_length(v_ids, 1);
END $$;
