-- ============================================================
-- 180_spc_custom_board.sql
-- 用途：SPC质量看板 + 卡片式自定义看板（数据看板两页 + 工作台自定义卡）
--
-- 内容：
--   1) 新表 custom_board（自定义看板 / 工作台自定义卡，两形态共用一张表）
--   2) 新菜单：数据看板 → SPC质量看板（/databoard/spc）、自定义看板（/databoard/custom）
--   3) 角色自动授权（超级管理员 + 系统管理员 + 生产/业务经理口径）
--
-- 口径（对齐既有系统做法，非推测）：
--   - 菜单挂「数据看板」下（parent 取自已有 /databoard/sale 的父级），sort_order = 5 / 6（现有四张为 1~4）
--   - permission_code = databoard:spc、databoard:custom（对象名 = 前端 menuConfig 的 itemKey）
--   - sys_role_permission.menu_id 实际存 route_path（与 136 号脚本同口径）
--   - SPC 可见角色 = ADMIN / SUPER_ADMIN / PRODUCTION_MANAGER / PRODUCTION
--     （系统无独立「质检员」角色编码，质检动作由生产角色承载，与工序质检报表一致）
--   - 自定义看板可见角色 = ADMIN / SUPER_ADMIN / SALES_MANAGER / FINANCE_MANAGER /
--     PRODUCTION_MANAGER / WAREHOUSE_MANAGER / PURCHASE_MANAGER
--
-- 幂等：建表 IF NOT EXISTS、菜单与授权全部 NOT EXISTS，重复执行 0 副作用。
-- 启动自愈：SchemaUpgradeEnsurer.ensureSpcCustomBoard（主库 + 逐租户库，等价逻辑）。
-- 执行后用户需重新登录（菜单路径在登录时缓存）。
-- ============================================================

BEGIN;

-- ---------- 1) 新表：自定义看板 / 工作台自定义卡 ----------
CREATE TABLE IF NOT EXISTS custom_board (
    board_id     BIGSERIAL PRIMARY KEY,
    board_type   VARCHAR(20)  NOT NULL DEFAULT 'board',
    board_code   VARCHAR(40),
    board_name   VARCHAR(120),
    layout_json  TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT '启用',
    share_scope  VARCHAR(20)  NOT NULL DEFAULT '私有',
    share_roles  VARCHAR(500),
    created_by   VARCHAR(80),
    created_at   TIMESTAMP DEFAULT NOW(),
    updated_at   TIMESTAMP DEFAULT NOW(),
    remark       VARCHAR(500)
);

COMMENT ON TABLE  custom_board IS '自定义看板：board=数据看板自定义看板 / workbench=工作台自定义卡（每人最多6张）';
COMMENT ON COLUMN custom_board.board_type IS '形态：board=自定义看板（可多套可共享）/ workbench=工作台自定义卡';
COMMENT ON COLUMN custom_board.layout_json IS '卡片配置JSON：{cards:[{id,type,title,icon,color,size,source,timeField,period,dim,field,agg,tokens,compare,style,fields,rows}]}';
COMMENT ON COLUMN custom_board.share_scope IS '可见范围：私有 / 共享（共享时按 share_roles 角色编码匹配）';
COMMENT ON COLUMN custom_board.share_roles IS '共享角色编码，逗号分隔';

CREATE INDEX IF NOT EXISTS idx_custom_board_type  ON custom_board (board_type);
CREATE INDEX IF NOT EXISTS idx_custom_board_owner ON custom_board (created_by);

-- ---------- 2) 菜单：数据看板 → SPC质量看板 / 自定义看板 ----------
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT 'databoard_spc', 'SPC质量看板',
       (SELECT parent_menu_id FROM sys_menu WHERE route_path = '/databoard/sale' AND parent_menu_id IS NOT NULL LIMIT 1),
       'MENU', '/databoard/spc', 'databoard:spc',
       5, 'DataAnalysis', 1, 1, 0, 1, '启用', 'ULTIMATE'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'databoard_spc');

-- 2026-11-06 口径修正（用户原话：不要新增看板，改现有看板）：自定义能力并入现有看板，
-- 「自定义看板」独立菜单下线；幂等删除其授权与菜单行，不影响其它菜单。
DELETE FROM sys_role_permission WHERE menu_id = '/databoard/custom';
DELETE FROM sys_menu WHERE menu_code = 'databoard_custom';

-- 现有看板页（财务/销售/仓储/经营）的卡片配置复用本表：补页标识列
ALTER TABLE custom_board ADD COLUMN IF NOT EXISTS page_key VARCHAR(40);

-- 兜底修复父子关系（父级解析失败重跑场景）
UPDATE sys_menu
   SET parent_menu_id = (SELECT parent_menu_id FROM sys_menu WHERE route_path = '/databoard/sale' AND parent_menu_id IS NOT NULL LIMIT 1)
 WHERE menu_code IN ('databoard_spc', 'databoard_custom') AND parent_menu_id IS NULL;

-- ---------- 3) 角色授权（幂等） ----------
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN', 'PRODUCTION_MANAGER', 'PRODUCTION')
  AND m.menu_code = 'databoard_spc'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id AND p.menu_id = m.route_path AND p.permission_type = '菜单'
  );

INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN', 'SALES_MANAGER', 'FINANCE_MANAGER',
                      'PRODUCTION_MANAGER', 'WAREHOUSE_MANAGER', 'PURCHASE_MANAGER')
  AND m.menu_code = 'databoard_custom'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id AND p.menu_id = m.route_path AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（执行后手动核对）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, parent_menu_id, route_path, permission_code, sort_order
--   FROM sys_menu WHERE menu_code IN ('databoard_spc', 'databoard_custom');
--
-- SELECT r.role_code, p.menu_id
--   FROM sys_role_permission p JOIN sys_role r ON r.role_id = p.role_id
--  WHERE p.menu_id IN ('/databoard/spc', '/databoard/custom');
--
-- SELECT board_type, COUNT(*) FROM custom_board GROUP BY board_type;
