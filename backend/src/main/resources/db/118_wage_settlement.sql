-- ============================================================
-- 118_wage_settlement.sql
-- 用途：财务管理 → 工序工资核算（wage_settlement / wage_settlement_item）
--       建表 + 员工工时费率列 + 菜单初始化 + 角色自动授权（守则 04 2.7.1 / 2.8.1-6）
--
-- 背景：按「已生效的工序汇报单」按员工归集
--       计件工资 = 合格数量 × 该工序计件单价（work_order_operation.piece_rate 优先，
--                 取不到回落 routing_operation.piece_rate，都取不到按 0 计）；
--       计时工资 = (工时 + 加班工时) × 员工工时费率（employee.hourly_rate，空/0 按 0 计）；
--       合计 = 计件工资 + 计时工资。生成一张「工序工资核算单」= 一个期间（明细行 = 员工）。
--
-- 口径（均已对本地实库核对）：
--   1. 父级 = 财务管理：取同组「成本核算」(/finance/cost-calculation) 的 parent_menu_id
--      （实库=财务管理分组 1000，与 71_inventory_detail_reports_menu.sql 同款子查询解析，不写死 magic number）；
--   2. route_path = /finance/wage-settlement（与前端 router/index.js、menuConfig.js 完全一致）；
--      permission_code = finance:wage-settlement；
--   3. 授权 ADMIN / SUPER_ADMIN / FINANCE / FINANCE_MANAGER（实库存在的财务角色码，不臆造其它角色码）；
--   4. sys_role_permission.menu_id 存的是 sys_menu.route_path（VARCHAR），不是数字 ID；
--   5. menu_id 走序列，禁止硬编码。
--
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / ADD COLUMN IF NOT EXISTS /
--       INSERT ... WHERE NOT EXISTS / UPDATE ... WHERE 列 IS NULL 兜底 / COMMENT ON COLUMN（可重复执行）。
--       无 DELETE / DROP / 无 WHERE 的全表 UPDATE。
-- 执行后无需重启后端，但用户需**重新登录**（menuRoutePaths 在登录时缓存）。
-- 同内容文件另存 deploy/sql/118_wage_settlement.sql（守则04 编号体系）。
-- ============================================================

BEGIN;

-- 1) 主表：工序工资核算单（一张单 = 一个期间）
CREATE TABLE IF NOT EXISTS wage_settlement (
    settlement_id      BIGSERIAL PRIMARY KEY,
    settlement_no      VARCHAR(50),
    period             VARCHAR(20),
    dept_id            BIGINT,
    dept_name          VARCHAR(100),
    employee_id        BIGINT,
    employee_name      VARCHAR(50),
    qualified_quantity NUMERIC(18,4) DEFAULT 0,
    piece_amount       NUMERIC(18,2) DEFAULT 0,
    time_amount        NUMERIC(18,2) DEFAULT 0,
    total_amount       NUMERIC(18,2) DEFAULT 0,
    status             VARCHAR(20) DEFAULT '草稿',
    approval_status    VARCHAR(20),
    approved_by        VARCHAR(50),
    approved_at        TIMESTAMP,
    void_reason        VARCHAR(500),
    voided_by          VARCHAR(50),
    voided_at          TIMESTAMP,
    remark             VARCHAR(500),
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP,
    created_by         VARCHAR(50),
    updated_by         VARCHAR(50)
);

-- 2) 明细表：每条工序汇报单一行
CREATE TABLE IF NOT EXISTS wage_settlement_item (
    item_id            BIGSERIAL PRIMARY KEY,
    settlement_id      BIGINT,
    line_no            INT,
    order_no           VARCHAR(50),
    operation_code     VARCHAR(50),
    operation_name     VARCHAR(200),
    employee_id        BIGINT,
    employee_name      VARCHAR(50),
    qualified_quantity NUMERIC(18,4) DEFAULT 0,
    piece_rate         NUMERIC(18,4) DEFAULT 0,
    piece_amount       NUMERIC(18,2) DEFAULT 0,
    work_hours         NUMERIC(18,4) DEFAULT 0,
    hourly_rate        NUMERIC(18,4) DEFAULT 0,
    time_amount        NUMERIC(18,2) DEFAULT 0,
    amount             NUMERIC(18,2) DEFAULT 0,
    remark             VARCHAR(500),
    created_at         TIMESTAMP
);

-- 3) 索引
CREATE INDEX IF NOT EXISTS idx_wage_settlement_period ON wage_settlement (period);
CREATE INDEX IF NOT EXISTS idx_wage_settlement_item_sid ON wage_settlement_item (settlement_id);

-- 4) 员工工时费率（元/小时，计时工资取数来源；空 = 不计算计时工资）
ALTER TABLE employee ADD COLUMN IF NOT EXISTS hourly_rate NUMERIC(18,4);
COMMENT ON COLUMN employee.hourly_rate IS '工时费率（元/小时，用于计时工资核算；空=不计算计时工资）';

-- 5) 菜单初始化（财务管理 → 工序工资核算）
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, component_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT 'wage_settlement', '工序工资核算',
       (SELECT parent_menu_id FROM sys_menu WHERE route_path = '/finance/cost-calculation'),
       'MENU', '/finance/wage-settlement', 'finance/WageSettlementList', 'finance:wage-settlement',
       11, 'Coin', 1, 1, 0, 1, '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_code = 'wage_settlement');

-- 兜底修复父子关系（父级子查询取不到时为 NULL，这里仅在 NULL 时补齐）
UPDATE sys_menu
   SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE route_path = '/finance/cost-calculation' LIMIT 1)
 WHERE menu_code = 'wage_settlement'
   AND parent_menu_id IS NULL;

-- 6) 角色授权（ADMIN / SUPER_ADMIN / FINANCE / FINANCE_MANAGER；幂等 NOT EXISTS）
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN', 'FINANCE', 'FINANCE_MANAGER')
  AND m.menu_code = 'wage_settlement'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id
       AND p.menu_id = m.route_path
       AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（预期：settlement_col_count=23 / item_col_count=17 / menu_exists=1）
-- ============================================================
SELECT
  (SELECT count(*) FROM information_schema.columns WHERE table_name = 'wage_settlement')      AS settlement_col_count,
  (SELECT count(*) FROM information_schema.columns WHERE table_name = 'wage_settlement_item') AS item_col_count,
  (SELECT count(*) FROM sys_menu WHERE menu_code = 'wage_settlement')                         AS menu_exists;
