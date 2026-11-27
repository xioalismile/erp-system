-- 130_kai_ui_size_seed.sql
-- 2026-09-28 小楷（AI 助手）全量能力登记 · 批次 7：设置中心「界面尺寸设置」6 键兜底种子
-- 同步落盘：deploy/sql/130_kai_ui_size_seed.sql（守则04 编号体系，同一份内容两个目录各留一份）
--
-- 背景：
--   小楷的设置类工具（setting_config_list / setting_config_set）只允许读写 sys_config 中**已存在**的键，
--   且分组必须命中白名单（SettingSupport.CONFIG_GROUPS 含 ui）。而「界面尺寸设置」的键此前只由前端保存时
--   动态 upsert 创建（db/84_ui_size_settings.sql 只种了 5 个数值键，2026-09-28 新增的界面密度 density 键从未种过），
--   在全新环境 / 从未在界面保存过的库上这些键不存在 → 小楷查不到、也改不了（"键不存在 → 小楷改不了"）。
--   本脚本把 6 个键补齐（仅补缺失的键），使小楷在任意环境都能"可读可改"。
--
-- 默认值 = 前端 frontend/src/stores/uiSize.js 的 UI_SIZE_DEFAULTS / UI_SIZE_DENSITY_DEFAULT：
--   menuFontSize=14 / listFontSize=14 / formFontSize=12 / searchWidth=280 / sectionGap=10 / density=compact
--   （= 2026-09-15 改造前 Playwright 实测现存值；未配置时界面与改造前完全一致，守则 2.7.2-6）
--
-- 幂等：全部 INSERT ... SELECT ... WHERE NOT EXISTS(config_key)，连跑 100 遍 = 跑 1 遍；
--   已存在的键（含管理员在界面上改过的值）一律不覆盖，绝不把客户的自定义值改回默认值。
--   刻意不用 ON CONFLICT：db/84_ui_size_settings.sql 铁律记载 PG 环境唯一约束可能缺失，
--   而 WHERE NOT EXISTS 与约束无关，任何环境都成立。

BEGIN;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.menuFontSize', '14', '左侧菜单文字大小', 'ui', '界面尺寸', 'number', 10,
       '左侧导航菜单每一项的文字大小（px，建议12~20）。全站所有页面共用；未配置时=14px（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.menuFontSize');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.listFontSize', '14', '列表字段文字大小', 'ui', '界面尺寸', 'number', 20,
       '列表表头与单元格文字大小（px，建议12~18）。未配置时=14px（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.listFontSize');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.formFontSize', '12', '编辑页字段文字大小', 'ui', '界面尺寸', 'number', 30,
       '编辑页表单字段标签与输入文字大小（px，建议12~18）。未配置时=12px（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.formFontSize');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.searchWidth', '280', '列表搜索框宽度', 'ui', '界面尺寸', 'number', 40,
       '列表页顶部搜索框宽度（px，建议200~600）。未配置时=280px（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.searchWidth');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.sectionGap', '10', '编辑页区块间距', 'ui', '界面尺寸', 'number', 50,
       '编辑页上下两个区块（卡片/分区）之间的高度（px，建议0~60）。未配置时=10px（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.sectionGap');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.size.density', 'compact', '界面密度', 'ui', '界面尺寸', 'string', 60,
       '界面密度：compact(紧凑，默认) / standard(标准) / loose(宽松)；未配置时=compact（系统原值）',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.size.density');

COMMIT;
