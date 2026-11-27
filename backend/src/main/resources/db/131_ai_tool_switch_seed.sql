-- 131_ai_tool_switch_seed.sql
-- 2026-10-01 小楷（AI 助手）全量能力登记 · 三个新增工具开关的存量库兜底种子
-- 同步落盘：deploy/sql/131_ai_tool_switch_seed.sql（守则04 编号体系，同一份内容两个目录各留一份）
--
-- 背景：
--   2026-09-28~30 为小楷新增了三项能力，各自带一个工具开关（默认关闭）：
--     1) 单据批量操作   ai.tool.bill_batch.enabled
--     2) 工序操作       ai.tool.work_operation.enabled
--     3) 平台级数据查询 ai.tool.platform_query.enabled
--   这三个键此前只写进了「空库初始化脚本」(db/sys_config_init.sql)，**没有存量库升级种子**。
--   存量库（本地 / 8cloud / 121 等已部署环境）里不存在这些键 → 管理员在
--   「设置中心 → AI → AI 工具权限」页面上虽有兜底渲染能勾选，但库里无记录、后端读取无据，
--   属初始化缺口（守则 2.7.1-1：新增功能的默认配置必须落在 deploy/sql/NN_*.sql）。
--   本脚本把三个键补齐（仅补缺失的键），默认值一律 = 关闭，使升级后行为与升级前完全一致
--   （守则 2.7.2-6：新增开关默认值必须对齐旧行为）。
--
-- 幂等：全部 INSERT ... SELECT ... WHERE NOT EXISTS(config_key)，连跑 100 遍 = 跑 1 遍；
--   已存在的键（含管理员在界面上改过的值）一律不覆盖。
--   刻意不用 ON CONFLICT：db/84_ui_size_settings.sql 铁律记载 PG 环境唯一约束可能缺失，
--   而 WHERE NOT EXISTS 与约束无关，任何环境都成立（与 130_kai_ui_size_seed.sql 同一口径）。

BEGIN;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options,
                        sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ai.tool.bill_batch.enabled', 'false', '单据批量操作', 'ai', 'boolean', NULL, 10,
       '允许AI对多张同类业务单据批量执行流程动作（批量提交/批量审核/批量删除/批量重置草稿），单次最多50张，逐张执行、逐张返回结果。实际可用范围取决于角色权限。需先开启总开关',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.tool.bill_batch.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options,
                        sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ai.tool.work_operation.enabled', 'false', '工序操作', 'ai', 'boolean', NULL, 11,
       '允许AI对生产工单的工序执行「工序操作面板」的7个操作（状态流转/返工/报废/一键推补料/一键推入库/录入质检/发通知）。实际可用范围取决于角色权限：用户须拥有「生产工单」菜单权限。需先开启总开关',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.tool.work_operation.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options,
                        sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ai.tool.platform_query.enabled', 'false', '平台级数据查询', 'ai', 'boolean', NULL, 12,
       '允许AI查询平台级数据（租户清单/部署版本）。这两类数据不在租户库（租户在平台库、部署版本在部署控制台库），不能走通用查询。仅平台管理员（SUPER_ADMIN/ADMIN）可用；默认关闭',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.tool.platform_query.enabled');

COMMIT;
