-- ============================================================
-- 47_custom_dashboard.sql
-- 自定义报表批次3：仪表盘（多个报表组合为一页网格展示）
-- 表：custom_dashboard（layout_json 存卡片配置 [{reportId,title}]，上限8卡）
-- 幂等脚本：CREATE TABLE IF NOT EXISTS / 按路由路径判定菜单存在
-- 生成时间：2026-08-27
-- ============================================================

CREATE TABLE IF NOT EXISTS custom_dashboard (
    dash_id         BIGSERIAL PRIMARY KEY,
    dash_code       VARCHAR(50)  NOT NULL,
    dash_name       VARCHAR(100) NOT NULL,
    layout_json     TEXT,
    status          VARCHAR(20)  DEFAULT '启用',
    share_scope     VARCHAR(20)  DEFAULT '私有',
    share_roles     VARCHAR(500),
    created_by      VARCHAR(50),
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    remark          VARCHAR(500)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_custom_dashboard_code ON custom_dashboard (dash_code);
CREATE INDEX IF NOT EXISTS idx_custom_dashboard_creator ON custom_dashboard (created_by);

COMMENT ON TABLE custom_dashboard IS '自定义仪表盘（报表组合展示）';
COMMENT ON COLUMN custom_dashboard.dash_code IS '仪表盘编码（CD+日期+流水，自动生成）';
COMMENT ON COLUMN custom_dashboard.layout_json IS '卡片配置JSON：{cards:[{reportId,title}]}，上限8张卡片';

-- ==================== 菜单（报表中心 → 仪表盘） ====================

-- 1) 序列对齐（防显式ID插入后序列落后撞主键）
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

-- 2) 子菜单：仪表盘（排最前，sort_order=0）
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, icon, route_path, component_path, is_visible, sort_order, permission_code, status, version)
SELECT 'report_dashboard', '仪表盘',
       (SELECT menu_id FROM sys_menu WHERE route_path = '/report'),
       'MENU', NULL, '/report/dashboard', 'views/report/CustomDashboardList', 1, 0, 'report:dashboard', '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/report/dashboard');

-- 3) 兜底修复孤儿父子关系
UPDATE sys_menu SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE route_path = '/report')
WHERE route_path = '/report/dashboard' AND parent_menu_id IS NULL;

-- 4) 授权：所有角色可见仪表盘菜单（数据隔离靠报表可见性双重校验）
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, permission_value, created_at)
SELECT r.role_id, m.route_path, '菜单', 1, CURRENT_TIMESTAMP
FROM sys_role r
JOIN sys_menu m ON m.route_path = '/report/dashboard'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
    WHERE p.role_id = r.role_id AND p.menu_id = m.route_path
);
