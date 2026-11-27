-- ============================================================
-- 152_onboarding_industry_init.sql
-- 用途：开账引导（2026-10-11）主库初始化 —— 设置中心「字段名称设置」菜单 + 角色授权
--       + 小楷"按行业初始化"开关 + 引导状态种子（主库）
-- 背景：新客户初始管理员第一次登录 → 小楷自动弹出开账引导 → 按行业生成
--       部门/计量单位/业务角色/仓库/演示资料/字段叫法（引擎在应用内，幂等）。
--       本文件只负责主库（erp_db）的菜单与配置种子；各租户库的同类种子由
--       SchemaUpgradeEnsurer 启动自愈逐库补齐（首次部署无需手工逐库执行）。
-- 幂等：所有 INSERT 用 NOT EXISTS 去重，重复执行 0 副作用。
-- 执行：psql -h 127.0.0.1 -U erp_user -d erp_db -f 152_onboarding_industry_init.sql
--       （首次部署时由 SchemaUpgradeEnsurer 自动执行相同逻辑，本文件是独立部署/手动修复的备用入口）
-- ============================================================

BEGIN;

-- ---------- 1. 字段显示名表（主库；租户库由启动自愈建） ----------
CREATE TABLE IF NOT EXISTS sys_field_label (
    id           BIGSERIAL PRIMARY KEY,
    field_key    VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(50)
);
COMMENT ON TABLE sys_field_label IS '字段显示名覆盖（每租户一份；全站按原字段名统一改显示，只影响显示文字）';

-- ---------- 2. 设置中心「自定义」组：字段名称设置（route_path 带 ?item= 定位） ----------
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status)
SELECT 'system_field_name_settings', '字段名称设置',
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'system_settings_center'),
       'MENU', '/system/settings?item=field-name', NULL,
       21, 'Edit', 1, 1, 0, 1, '启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_field_name_settings');

-- ---------- 3. 角色授权：超级管理员(ADMIN) + 平台超级管理员(SUPER_ADMIN) ----------
-- sys_role_permission.menu_id 存 route_path（与设置中心子项同一口径），permission_type='菜单'
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND m.menu_code = 'system_field_name_settings'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
    WHERE p.role_id = r.role_id AND p.menu_id = m.route_path AND p.permission_type = '菜单'
  );

-- ---------- 4. 小楷"按行业初始化"开关（默认开：只加数据不删改、强制确认卡、仅管理员） ----------
INSERT INTO sys_config (config_key, config_value, config_name, config_group)
SELECT 'ai.tool.initialize.enabled', 'true', 'AI开账初始化', 'ai'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.tool.initialize.enabled');

-- ---------- 5. 主库（DEFAULT 租户）引导状态种子：done（主库非"新客户开账"场景，不弹向导） ----------
-- 各租户库的种子由启动自愈逐库补：无记录=存量租户 → done；新租户在建租户时显式写 pending。
INSERT INTO sys_config (config_key, config_value, config_name, config_group)
SELECT 'tenant.onboarding.status', 'done', '开账引导状态', 'onboarding'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'tenant.onboarding.status');

COMMIT;

-- ============================================================
-- 验证 SQL（执行后手动核对）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, route_path FROM sys_menu WHERE menu_code = 'system_field_name_settings';
-- SELECT p.role_id, r.role_code FROM sys_role_permission p JOIN sys_role r ON r.role_id = p.role_id
--  WHERE p.menu_id = '/system/settings?item=field-name' AND p.permission_type = '菜单';
-- SELECT config_key, config_value FROM sys_config WHERE config_key IN ('ai.tool.initialize.enabled','tenant.onboarding.status');
