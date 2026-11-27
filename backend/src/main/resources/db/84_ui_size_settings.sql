-- 84_ui_size_settings.sql
-- 2026-09-15 设置中心-自定义：「界面尺寸设置」菜单初始化 + 角色自动授权 + 5 项默认值
--
-- 背景（用户需求原话）：
--   「系统优化：菜单字段，列表字段，编辑页字段大小可以在设置中心调整，搜索框也可以调整宽度，
--     编辑页两个 div 之间的高度也可以调整」
--   配置存 sys_config（config_group='ui'，键前缀 ui.size.），前端读取后注入 CSS 变量全站生效。
--
-- 关键约定：
--   ① 默认值 = 2026-09-15 改造前 Playwright 实测的现存值
--      （菜单14px / 列表字段14px / 编辑页字段12px / 搜索框280px / 区块间距10px）
--      → 未配置或配置缺失时，界面与改造前完全一致（守则 2.7.2-6 默认值对齐旧行为）。
--   ② 键前缀 ui.size. 与主题配置 ui.theme. 严格分开：主题 store 只认 'ui.theme.' 前缀
--      （stores/theme.js 的 SYS_CONFIG_KEY_PREFIX），两边互不干扰，不会互相污染。
--   ③ 【铁律】禁用 ON CONFLICT（PG 环境该唯一约束可能缺失，报错会中止整次部署，见 2026-09-12 事故），
--      一律 WHERE NOT EXISTS；smallint 语义的列一律写 0/1，禁止写 true/false
--      （57_ai_quota_permissions.sql 曾因写 false 报「字段 is_visible 的类型为 smallint」整批失败）。
--   ④ sys_role_permission.menu_id 存的是 sys_menu.route_path 字符串（不是数字 menu_id），
--      permission_type='菜单'；子项 route_path 必须带 ?item=ui-size ——
--      因为设置中心按 `/system/settings?item=<key>` 判子项可见性（SettingsCenter.vue canAccessItem）。
--   ⑤ 本期生效范围：左侧菜单（全局）+ 销售订单列表 + 销售订单编辑页。
--      其余 55 个编辑页 / 91 个列表页保持原样，二期再逐页替换为变量。
--
-- 幂等：本脚本连跑多次结果一致（3 组 INSERT 全部 NOT EXISTS 去重）。
-- 执行：psql -h 127.0.0.1 -U erp_user -d erp_db -f 84_ui_size_settings.sql

BEGIN;

-- ---------- 1. 菜单：界面尺寸设置（挂在设置中心下，route_path 必须带 ?item=）----------
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status)
SELECT 'system_ui_size', '界面尺寸设置',
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'system_settings_center'),
       'MENU', '/system/settings?item=ui-size', 'system:ui-size',
       100, 'Histogram', 1, 1, 0, 1, '启用'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_ui_size');

-- ---------- 2. 角色权限：超级管理员(ADMIN) + 平台超级管理员(SUPER_ADMIN) 自动授权 ----------
-- 用 role_code 动态匹配 role_id，不硬编码 ID（不同库 ID 可能不一致）
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND m.menu_code = 'system_ui_size'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
    WHERE p.role_id = r.role_id AND p.menu_id = m.route_path AND p.permission_type = '菜单'
  );

-- ---------- 3. 5 项配置默认值（= 改造前实测现存值，管理员可在设置中心随意改）----------
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.menuFontSize', '14', '左侧菜单文字大小', 'ui', '界面尺寸', 'number', 10,
       '左侧导航菜单每一项的文字大小（px，建议12~20）。全站所有页面共用；未配置时=14px（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.menuFontSize');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.listFontSize', '14', '列表字段文字大小', 'ui', '界面尺寸', 'number', 20,
       '列表表头与单元格文字大小（px，建议12~18）。本期只对销售订单列表生效；未配置时=14px（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.listFontSize');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.formFontSize', '12', '编辑页字段文字大小', 'ui', '界面尺寸', 'number', 30,
       '编辑页表单字段标签与输入文字大小（px，建议12~18）。本期只对销售订单编辑页生效；未配置时=12px（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.formFontSize');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.searchWidth', '280', '列表搜索框宽度', 'ui', '界面尺寸', 'number', 40,
       '列表页顶部搜索框宽度（px，建议200~600）。本期只对销售订单列表生效；未配置时=280px（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.searchWidth');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.sectionGap', '10', '编辑页区块间距', 'ui', '界面尺寸', 'number', 50,
       '编辑页上下两个区块（卡片/分区）之间的高度（px，建议0~60）。本期只对销售订单编辑页生效；未配置时=10px（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.sectionGap');

COMMIT;

-- ============================================================
-- 验证 SQL（执行后手动核对）
-- 预期：1 行菜单 + 2 行角色权限 + 5 行配置
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, route_path, permission_code
--   FROM sys_menu WHERE menu_code = 'system_ui_size';
--
-- SELECT p.role_id, r.role_code, p.menu_id
--   FROM sys_role_permission p JOIN sys_role r ON r.role_id = p.role_id
--  WHERE p.menu_id = '/system/settings?item=ui-size';
--
-- SELECT config_key, config_value, config_group, config_type
--   FROM sys_config WHERE config_key LIKE 'ui.size.%' ORDER BY sort_order;
