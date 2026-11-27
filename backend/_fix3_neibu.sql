-- 修复3: neibu001 独立库租户裁剪（保留 ziq 持有的 ADMIN_STANDARD + 业务角色）
-- 删平台专属/多余角色
DELETE FROM sys_role_permission WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code IN ('SUPER_ADMIN','ADMIN','ADMIN_CRM','ADMIN_ULTIMATE'));
DELETE FROM sys_user_role WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code IN ('SUPER_ADMIN','ADMIN','ADMIN_CRM','ADMIN_ULTIMATE'));
DELETE FROM sys_role WHERE role_code IN ('SUPER_ADMIN','ADMIN','ADMIN_CRM','ADMIN_ULTIMATE');
-- 删平台管理菜单及关联权限（租户库不得持有平台菜单记录）
DELETE FROM sys_role_permission WHERE menu_id IN (SELECT menu_id::text FROM sys_menu WHERE route_path LIKE '/admin%');
DELETE FROM sys_menu WHERE route_path LIKE '/admin%';
