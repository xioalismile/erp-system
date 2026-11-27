-- ============================================================
-- 57_ai_quota_permissions.sql
-- AI 两级额度管理 + 租户自有Key 权限码初始化
-- 权限码：
--   ai:key:tenant     管理租户自有API Key
--   ai:quota:tenant   管理租户总额度
--   ai:quota:user     管理用户专属额度
-- 幂等：NOT EXISTS 防重复
-- ============================================================

BEGIN;

-- 1. 新增3条隐藏菜单（承载权限码，is_visible=false 不显示在UI）
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, icon, route_path,
                       component_path, is_visible, is_cache, is_enabled, keepalive,
                       sort_order, permission_code, status, created_at, updated_at, remark)
SELECT 'system_ai_tenant_key', 'AI租户自有Key', 1132, 'C', 'Key', '/system/ai/tenant-key',
       '', 0, 0, 1, 0, 1, 'ai:key:tenant', 'active',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '租户自有API Key管理权限（隐藏菜单，仅承载权限码）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_ai_tenant_key');

INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, icon, route_path,
                       component_path, is_visible, is_cache, is_enabled, keepalive,
                       sort_order, permission_code, status, created_at, updated_at, remark)
SELECT 'system_ai_tenant_quota', 'AI租户额度', 1132, 'C', 'Coin', '/system/ai/tenant-quota',
       '', 0, 0, 1, 0, 2, 'ai:quota:tenant', 'active',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '租户总额度管理权限（隐藏菜单，仅承载权限码）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_ai_tenant_quota');

INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, icon, route_path,
                       component_path, is_visible, is_cache, is_enabled, keepalive,
                       sort_order, permission_code, status, created_at, updated_at, remark)
SELECT 'system_ai_user_quota', 'AI用户额度', 1132, 'C', 'User', '/system/ai/user-quota',
       '', 0, 0, 1, 0, 3, 'ai:quota:user', 'active',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '用户专属额度管理权限（隐藏菜单，仅承载权限码）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_ai_user_quota');

-- 2. 给 ADMIN 和 SUPER_ADMIN 角色授权这3个权限码（route_path 作为 menu_id）
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at, created_by)
SELECT r.role_id, p.route_path, '菜单', 1, 0, CURRENT_TIMESTAMP, 0
FROM sys_role r
CROSS JOIN sys_menu p
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND p.menu_code IN ('system_ai_tenant_key', 'system_ai_tenant_quota', 'system_ai_user_quota')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.role_id AND rp.menu_id = p.route_path AND rp.permission_type = '菜单'
  );

COMMIT;
