-- =====================================================================
-- 37_tighten_sales_role_menus.sql
-- 彻底收紧销售类角色的菜单授权：只保留销售域菜单
-- 幂等：可重复执行
-- 背景：00_init.sql 默认模板给 SALES/SALES_MANAGER 配了
--       group_basedata / group_inventory / group_acquisition / group_system
--       等整组权限，前端 menuConfig 见组 key 即放行整组全部子菜单，
--       导致销售员登录后几乎全部菜单可见。
-- 收紧后 SALES / SALES_MANAGER 仅保留：
--       工作台 + CRM客户 + 销售管理 + 样品管理 + 免费样品
-- =====================================================================

-- 1) 删除销售域之外的整组/单页授权（基础资料、库存、获客、系统管理、
--    采购、生产、财务及历史死数据组 key 全部清理）
DELETE FROM sys_role_permission rp
USING sys_role r
WHERE rp.role_id = r.role_id
  AND r.role_code IN ('SALES', 'SALES_MANAGER')
  AND rp.menu_id NOT IN (
      'group_dashboard', '/dashboard/index',
      'group_crm', 'group_sale', 'group_sample', 'group_freesample'
  );

-- 2) 幂等补齐应保留的授权（防止历史库缺项）
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_crm'),('group_sale'),('group_sample'),('group_freesample')
) AS ids(menu_id)
WHERE r.role_code IN ('SALES', 'SALES_MANAGER')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.role_id
        AND rp.menu_id = ids.menu_id
  );

-- 3) 校验输出：销售类角色剩余授权
SELECT r.role_code, rp.menu_id
FROM sys_role_permission rp
JOIN sys_role r ON rp.role_id = r.role_id
WHERE r.role_code IN ('SALES', 'SALES_MANAGER')
ORDER BY r.role_code, rp.menu_id;
