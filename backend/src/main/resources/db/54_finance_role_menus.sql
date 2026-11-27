-- ========================================================
-- 54_finance_role_menus.sql
-- 财务角色（FINANCE/FINANCE_MANAGER）补齐财务模块具体菜单路径授权
--
-- 背景/根因：29b 种子脚本只给财务角色写了 group_finance 分组键，
--   而 AuthController.userinfo 对权限键做精确匹配（不展开分组键），
--   导致纯财务角色用户 menuRoutePaths 中 0 条 /finance/* 路径——
--   会计登录后看不到收款单/付款单等任何财务菜单（守卫亦拦截 403）。
--   （销售角色因曾在设置中心人工保存过权限，存的是具体路径，不受影响。）
--
-- 修复：按"角色权限设置界面保存后的格式"（叶子路径 + permission_type='菜单'）
--   给两个财务角色补齐财务模块全部叶子菜单路径。既有 group_* 键保留无害。
--
-- 幂等：INSERT ... WHERE NOT EXISTS，重复执行零副作用。
-- 注意：路径清单与 menuConfig.js 财务分组及 sys_menu(finance_*) 记录一一对应；
--   /finance/account（账户维护，菜单 finance_bank_account）必须包含——
--   收款单的银行账户即在此录入。
-- ========================================================

INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, is_inherited, created_at)
SELECT r.role_id, ids.menu_id, '菜单', 1, 0, CURRENT_TIMESTAMP
FROM sys_role r
CROSS JOIN (VALUES
    -- 财务模块全部叶子菜单（与 menuConfig.js finance 分组对齐）
    ('/finance/account'),            -- 账户维护（银行卡录入）
    ('/finance/sale-pre-receipts'),  -- 销售预收款单
    ('/finance/purchase-pre-payments'), -- 采购预付款单
    ('/finance/receipt'),            -- 收款单
    ('/finance/payment'),            -- 付款单
    ('/finance/operation-expense'),  -- 费用分摊
    ('/finance/cost-calculation'),   -- 成本核算
    ('/finance/cost-config'),        -- 成本核算配置
    ('/finance/report/analysis'),    -- 分析报表
    ('/finance/report/ar-summary'),  -- 应收账款汇总表
    ('/finance/report/ap-summary'),  -- 应付账款汇总表
    ('/finance/help')                -- 操作手册
) AS ids(menu_id)
WHERE r.role_code IN ('FINANCE', 'FINANCE_MANAGER')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission p
      WHERE p.role_id = r.role_id AND p.menu_id = ids.menu_id AND p.permission_type = '菜单'
  );

-- 重置序列
SELECT setval('sys_role_permission_permission_id_seq',
    COALESCE((SELECT MAX(permission_id) FROM sys_role_permission), 1), true);
