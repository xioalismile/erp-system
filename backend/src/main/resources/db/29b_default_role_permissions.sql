-- ========================================================
-- 智能ERP 权限改造 脚本2/2: 默认角色菜单权限初始化
-- 前置依赖: 必须先执行 29a_alter_menu_id_column.sql
-- 新库初始化时会在 00_init.sql 中内嵌执行, 老库单独跑此脚本
--
-- 权限设计参考: 金蝶K3 Cloud / 用友U8 岗位权限最佳实践
--   - 主模块: 岗位核心业务模块的全部权限
--   - 跨模块查看: 仓库可参照销售/采购单据出入库, 财务可稽核全部业务单据
--   - 数据范围(data_scope): 主管=本部门, 员工=本人, 财务主管=全部
--   - 多角色叠加: 权限取并集, data_scope取最宽值
-- ========================================================
BEGIN;

-- 0. 配置 data_scope 数据范围（标准完整中文值，与前端下拉选项一致）
UPDATE sys_role SET data_scope = '全部数据' WHERE role_code IN ('ADMIN','MANAGER') AND (data_scope IS NULL OR data_scope <> '全部数据');
UPDATE sys_role SET data_scope = '全部数据' WHERE role_code = 'FINANCE_MANAGER' AND (data_scope IS NULL OR data_scope <> '全部数据');
UPDATE sys_role SET data_scope = '本部门数据' WHERE role_code IN ('SALES_MANAGER','PURCHASE_MANAGER','WAREHOUSE_MANAGER','PRODUCTION_MANAGER') AND (data_scope IS NULL OR data_scope <> '本部门数据');
UPDATE sys_role SET data_scope = '仅本人数据' WHERE role_code IN ('SALES','PURCHASE','WAREHOUSE','PRODUCTION','FINANCE') AND (data_scope IS NULL OR data_scope <> '仅本人数据');

-- 1. 超级管理员 / 系统管理员 -> 通配 *, 全部菜单可见
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, '*', '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
WHERE r.role_code IN ('SUPER_ADMIN', 'ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = '*' AND p.permission_type = '菜单'
  );

-- 2. 销售主管(SALES_MANAGER) -> 销售+CRM+获客+物流+报价+价格+打样+免费样品
--    跨模块查看: 库存(可用量)+采购(关联查询)
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_system'),('/system/settings'),
    ('group_report'),
    -- 主模块
    ('group_sale'),('group_crm'),('group_acquisition'),('group_logistics'),
    ('group_quote'),('group_price'),('group_sample'),('group_freesample'),
    -- 跨模块查看权限
    ('group_inventory'),('group_purchase')
) AS ids(menu_id)
WHERE r.role_code = 'SALES_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 3. 销售员(SALES) -> 销售+CRM+获客+物流+报价+打样+免费样品
--    跨模块查看: 库存(可用量查询)
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_system'),('/system/settings'),
    ('group_report'),
    -- 主模块
    ('group_sale'),('group_crm'),('group_acquisition'),('group_logistics'),
    ('group_quote'),('group_sample'),('group_freesample'),
    -- 跨模块查看权限（仅库存可用量）
    ('group_inventory')
) AS ids(menu_id)
WHERE r.role_code = 'SALES'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 4. 采购主管(PURCHASE_MANAGER) -> 采购+价格
--    跨模块查看: 库存+销售(关联查询)
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_system'),('/system/settings'),
    ('group_report'),
    -- 主模块
    ('group_purchase'),('group_price'),
    -- 跨模块查看权限
    ('group_inventory'),('group_sale')
) AS ids(menu_id)
WHERE r.role_code = 'PURCHASE_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 5. 采购员(PURCHASE) -> 采购
--    跨模块查看: 库存(可用量查询)
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_system'),('/system/settings'),
    ('group_report'),
    -- 主模块
    ('group_purchase'),
    -- 跨模块查看权限（仅库存可用量）
    ('group_inventory')
) AS ids(menu_id)
WHERE r.role_code = 'PURCHASE'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 6. 仓库主管(WAREHOUSE_MANAGER) -> 库存
--    跨模块查看: 销售+采购+生产(参照出入库)
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_system'),('/system/settings'),
    ('group_report'),
    -- 主模块
    ('group_inventory'),
    -- 跨模块查看权限（参照销售/采购/生产单据出入库）
    ('group_sale'),('group_purchase'),('group_production')
) AS ids(menu_id)
WHERE r.role_code = 'WAREHOUSE_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 7. 仓管员(WAREHOUSE) -> 库存
--    跨模块查看: 销售+采购(参照出入库)
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_system'),('/system/settings'),
    ('group_report'),
    -- 主模块
    ('group_inventory'),
    -- 跨模块查看权限（参照销售/采购单据出入库）
    ('group_sale'),('group_purchase')
) AS ids(menu_id)
WHERE r.role_code = 'WAREHOUSE'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 8. 生产主管(PRODUCTION_MANAGER) -> 生产+委外+BOM
--    跨模块查看: 库存+销售(关联查询)
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_system'),('/system/settings'),
    ('group_report'),
    -- 主模块
    ('group_production'),('group_subcontract'),('group_masterdata'),('/masterdata/bom'),
    -- 跨模块查看权限
    ('group_inventory'),('group_sale')
) AS ids(menu_id)
WHERE r.role_code = 'PRODUCTION_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 9. 生产员(PRODUCTION) -> 生产+委外+BOM
--    跨模块查看: 库存(可用量查询)
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_system'),('/system/settings'),
    ('group_report'),
    -- 主模块
    ('group_production'),('group_subcontract'),('group_masterdata'),('/masterdata/bom'),
    -- 跨模块查看权限（仅库存可用量）
    ('group_inventory')
) AS ids(menu_id)
WHERE r.role_code = 'PRODUCTION'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 10. 财务主管(FINANCE_MANAGER) -> 财务
--     跨模块查看: 销售+采购+库存+生产(全部稽核)
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_system'),('/system/settings'),
    ('group_report'),
    -- 主模块
    ('group_finance'),
    -- 跨模块查看权限（财务稽核需要查看所有业务单据）
    ('group_sale'),('group_purchase'),('group_inventory'),('group_production')
) AS ids(menu_id)
WHERE r.role_code = 'FINANCE_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 11. 会计(FINANCE) -> 财务
--     跨模块查看: 销售+采购(发票核对)
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_system'),('/system/settings'),
    ('group_report'),
    -- 主模块
    ('group_finance'),
    -- 跨模块查看权限（仅查看销售/采购发票用于核对）
    ('group_sale'),('group_purchase')
) AS ids(menu_id)
WHERE r.role_code = 'FINANCE'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 12. 查看者(VIEWER) -> 仪表盘 + 基础资料 + 报表 + 系统设置
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    ('group_dashboard'),('/dashboard/index'),
    ('group_basedata'),('group_report'),('group_system'),('/system/settings')
) AS ids(menu_id)
WHERE r.role_code = 'VIEWER'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type='菜单'
  );

-- 13. 重置序列
SELECT setval('sys_role_permission_permission_id_seq',
    COALESCE((SELECT MAX(permission_id) FROM sys_role_permission),1), true);

COMMIT;
