-- ============================================================
-- 145_version_role_regrant.sql
-- 版本划分重构（2026-10-10）配套：按「版本管理员角色」重算菜单授权
--   背景：菜单版本改完后，租户库管理员角色仍是旧的显式路径授权 → 看不到新划入的菜单
--         （如标准版看不到新纳入的财务）。登录时菜单 = 版本可见菜单 ∩ 角色授权，
--         所以必须把版本可见菜单补齐到对应管理员角色。
-- 口径（与 TenantService.createVersionAdminRole 的 menuFilter 完全一致）：
--   ADMIN_STANDARD  → version IS NULL OR version='STANDARD'
--   ADMIN_CRM       → version IS NULL OR version IN ('STANDARD','CRM')
--   ADMIN_ULTIMATE  → 全部
--   ADMIN           → 通配 '*'（系统既有口径：基础管理员全放行，仍受版本过滤约束）
-- 幂等：只补 NOT EXISTS 的缺失行；不删除既有授权（多余的授权会被版本过滤挡在登录外）
-- 执行范围：主库 + 所有 active 租户独立库 + 租户模板库
-- ============================================================
BEGIN;

-- 1) 基础管理员 ADMIN：补通配 '*'
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at)
SELECT r.role_id, '*', '菜单', 1, CURRENT_TIMESTAMP
FROM sys_role r
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission p
                  WHERE p.role_id = r.role_id AND p.menu_id = '*' AND p.permission_type = '菜单');

-- 2) 版本管理员角色：按版本补齐可见菜单
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, CURRENT_TIMESTAMP
FROM sys_role r
JOIN sys_menu m ON m.route_path IS NOT NULL AND m.route_path <> ''
WHERE r.role_code IN ('ADMIN_STANDARD', 'ADMIN_CRM', 'ADMIN_ULTIMATE')
  AND (
    (r.role_code = 'ADMIN_STANDARD' AND (m.version IS NULL OR m.version = 'STANDARD'))
    OR (r.role_code = 'ADMIN_CRM' AND (m.version IS NULL OR m.version IN ('STANDARD', 'CRM')))
    OR (r.role_code = 'ADMIN_ULTIMATE')
  )
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission p
                  WHERE p.role_id = r.role_id AND p.menu_id = m.route_path AND p.permission_type = '菜单');

COMMIT;
