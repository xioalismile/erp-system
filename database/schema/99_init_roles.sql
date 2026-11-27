-- 插入版本化的管理员角色
INSERT INTO sys_role (role_id, role_code, role_name, role_type, is_system, status, is_enabled) VALUES
(100, 'ADMIN_STANDARD', '标准版管理员', '系统角色', 1, '启用', 1),
(101, 'ADMIN_CRM', 'CRM版管理员', '系统角色', 1, '启用', 1),
(102, 'ADMIN_ULTIMATE', '旗舰版管理员', '系统角色', 1, '启用', 1)
ON CONFLICT (role_id) DO NOTHING;

-- 为标准版管理员分配 STANDARD 版本的菜单（使用 route_path 而非 menu_id）
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at)
SELECT 100, m.route_path, '菜单', 1, CURRENT_TIMESTAMP
FROM sys_menu m 
WHERE m.version = 'STANDARD' 
  AND m.route_path IS NOT NULL 
  AND m.route_path != ''
  AND m.route_path NOT IN (SELECT menu_id FROM sys_role_permission WHERE role_id = 100 AND permission_type = '菜单');

-- 为CRM版管理员分配 STANDARD 和 CRM 版本的菜单
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at)
SELECT 101, m.route_path, '菜单', 1, CURRENT_TIMESTAMP
FROM sys_menu m 
WHERE m.version IN ('STANDARD', 'CRM') 
  AND m.route_path IS NOT NULL 
  AND m.route_path != ''
  AND m.route_path NOT IN (SELECT menu_id FROM sys_role_permission WHERE role_id = 101 AND permission_type = '菜单');

-- 为旗舰版管理员分配全部菜单
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at)
SELECT 102, m.route_path, '菜单', 1, CURRENT_TIMESTAMP
FROM sys_menu m 
WHERE m.version IN ('STANDARD', 'CRM', 'ULTIMATE') 
  AND m.route_path IS NOT NULL 
  AND m.route_path != ''
  AND m.route_path NOT IN (SELECT menu_id FROM sys_role_permission WHERE role_id = 102 AND permission_type = '菜单');
