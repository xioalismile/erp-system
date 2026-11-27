-- 179: 批记录中心 菜单初始化 + 角色自动授权 + 按钮动作码（守则2.7.1-7 / 2.7.2-3）
-- 幂等：所有 INSERT 用 NOT EXISTS 去重。镜像落盘 backend/src/main/resources/db/。
-- 结构核对（2026-11-05 实查）：生产管理目录 menu_code='production'（menu_type=CATALOG），
--   子菜单 sort_order 现最大 101（工序质检报表），本菜单取 110 置于末位。
-- 按钮码落法（实查口径）：sys_role_permission.permission_type='按钮'，menu_id 列存「模块:页面:动作」。

BEGIN;

-- ---------- 1. 菜单：批记录中心（生产管理目录下） ----------
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status)
SELECT 'production_batch_record_center', '批记录中心',
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'production' AND menu_type = 'CATALOG'),
       'MENU', '/production/batch-record-center', 'production:batchrecord',
       110, 'DocumentChecked', 1, 1, 0, 1, '启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'production_batch_record_center');

-- ---------- 2. 菜单授权：超级管理员(ADMIN) + 平台超级管理员(SUPER_ADMIN) ----------
-- 口径与 56 号一致：sys_role_permission.menu_id 存 route_path，permission_type='菜单'
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND m.menu_code = 'production_batch_record_center'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
    WHERE p.role_id = r.role_id AND p.menu_id = m.route_path AND p.permission_type = '菜单'
  );

-- ---------- 3. 按钮动作码：8 个，统一前缀 production:batchrecord ----------
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, b.btn_code, '按钮', 1, 0
FROM sys_role r
CROSS JOIN (
  SELECT unnest(ARRAY[
    'production:batchrecord:view',
    'production:batchrecord:export',
    'production:batchrecord:trace',
    'production:batchrecord:inspect',
    'production:batchrecord:review',
    'production:batchrecord:release',
    'production:batchrecord:sample',
    'production:batchrecord:correct'
  ]) AS btn_code
) b
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
    WHERE p.role_id = r.role_id AND p.menu_id = b.btn_code AND p.permission_type = '按钮'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（执行后手动核对：菜单 1 行、菜单授权 2 行、按钮授权 16 行）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, parent_menu_id, route_path, permission_code, sort_order
--   FROM sys_menu WHERE menu_code = 'production_batch_record_center';
-- SELECT r.role_code, p.menu_id, p.permission_type FROM sys_role_permission p
--   JOIN sys_role r ON r.role_id = p.role_id
--  WHERE p.menu_id IN ('/production/batch-record-center') OR p.menu_id LIKE 'production:batchrecord:%';
