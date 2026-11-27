-- 修复2: erp_db（自营总库）清理多余版本角色与废弃演示账号
-- 删除废弃演示账号及其角色关联（testfix4 租户已删的孤儿管理员 + 旧共享库模式版本管理员演示账号）
DELETE FROM sys_user_role WHERE user_id IN (SELECT user_id FROM sys_user WHERE username IN ('testfix4_admin','tenant_admin_s','tenant_admin_c'));
DELETE FROM sys_user WHERE username IN ('testfix4_admin','tenant_admin_s','tenant_admin_c');
-- 删除三个版本管理员角色（自营库无用途；新租户的版本角色在其独立库内）
DELETE FROM sys_role_permission WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code IN ('ADMIN_STANDARD','ADMIN_CRM','ADMIN_ULTIMATE'));
DELETE FROM sys_role_field_permission WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code IN ('ADMIN_STANDARD','ADMIN_CRM','ADMIN_ULTIMATE'));
DELETE FROM sys_user_role WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code IN ('ADMIN_STANDARD','ADMIN_CRM','ADMIN_ULTIMATE'));
DELETE FROM sys_role WHERE role_code IN ('ADMIN_STANDARD','ADMIN_CRM','ADMIN_ULTIMATE');
-- SUPER_ADMIN 保留（平台超管 admin 持有），仅中文名规范化
UPDATE sys_role SET role_name = '平台超级管理员' WHERE role_code = 'SUPER_ADMIN';
