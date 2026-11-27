-- 74_notification_voice.sql
-- 2026-09-14 小助手语音提醒（小铃铛「待审批」通知 → AI 小助手语音播报）
--
-- 背景：小铃铛（sys_notification）出现新的「待审批」未读时，AI 小助手用**本机浏览器语音**
--       播报「您有 N 条待审批单据，最新一条 XXX，请及时处理」。
--
-- 落位：config_group='notification' → 设置中心「消息通知」页（KgwNotification.vue）
--       新增「语音提醒」卡片，本页为自定义渲染，不依赖 config_section。
--       后端不参与发声（语音由前端浏览器本机合成，零 Key、零成本），因此本脚本只初始化开关。
--
-- 默认值：notification.voice.enabled = 'false'
--   —— 与升级前行为完全一致（升级前无语音播报），管理员在设置中心手动开启后才生效。
--
-- 幂等：WHERE NOT EXISTS —— 已存在的用户配置绝不被覆盖（重复执行零副作用）。
--   【铁律】禁止用 ON CONFLICT (config_key)：PG 环境建表时该唯一约束可能缺失
--   （sys_config_schema.sql 里是 MySQL 的 UNIQUE KEY），会报「没有匹配 ON CONFLICT 说明的
--    唯一或者排除约束」并中止整次部署（2026-09-12 事故，见 66_ui_bill_link_switch.sql 注释）。
--
-- 影响范围：仅新增 2 条配置数据，无 DDL、不改任何业务表、不动历史数据。

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'notification.voice.enabled', 'false', '启用语音提醒', 'notification', 'boolean', 9,
       '开启后 AI 小助手会用本机语音播报新的待审批单据；默认关闭', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'notification.voice.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'notification.voice.minInterval', '60', '语音播报最小间隔(秒)', 'notification', 'number', 10,
       '两次语音播报之间的最小间隔秒数，防止批量通知连续刷屏（默认 60 秒）', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'notification.voice.minInterval');
