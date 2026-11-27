-- 修复4: erp_db_b（COMPANY_B 演示库）清理平台专属对象
-- 只删 SUPER_ADMIN（平台专属角色，租户库不允许存在；B库 admin 还有 ADMIN 角色兜底）
DELETE FROM sys_role_permission WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code = 'SUPER_ADMIN');
DELETE FROM sys_user_role WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code = 'SUPER_ADMIN');
DELETE FROM sys_role WHERE role_code = 'SUPER_ADMIN';
-- 删平台管理菜单及关联权限
DELETE FROM sys_role_permission WHERE menu_id IN (SELECT menu_id::text FROM sys_menu WHERE route_path LIKE '/admin%');
DELETE FROM sys_menu WHERE route_path LIKE '/admin%';
