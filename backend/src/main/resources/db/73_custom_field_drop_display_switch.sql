-- 73_custom_field_drop_display_switch.sql
-- 2026-09-14 「自定义字段下拉显示」定制功能运行时开关
--
-- 背景：单据明细「选择物料」下拉里追加展示物料自定义字段内容，属定制功能（客户需求）。
--       用本开关控制"本部署是否可见"，避免与客户服务器版本产生分叉：
--         true  = 开启（默认，与未加开关前的行为完全一致）
--         false = 关闭（自定义字段配置页不显示「下拉显示」开关与属性标签；
--                 销售订单明细的物料下拉不再追加自定义字段内容）
--
-- 落位：config_group='sys_param' → 设置中心「系统参数」页签自动渲染（KgwSysParam 数据驱动），
--       config_type='boolean'  → 渲染成开关；config_section='界面显示' 与 ui.bill_link.enabled 同分组。
--       保存走 PUT /batch upsert，服务端 refreshCache 后即时生效。
--
-- 注意：本开关只覆盖"下拉显示"；物料库弹窗的「全字段 + 全部自定义字段」与列设置恒定生效，与本开关无关。
--
-- 幂等：WHERE NOT EXISTS —— 已存在的用户配置绝不会被覆盖（不依赖唯一约束，避免 PG 缺约束时整批部署中止，
--       该坑见 66_ui_bill_link_switch.sql 的修正说明）。
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_section, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ui.custom_field.drop_display.enabled', 'true', '自定义字段下拉显示', 'sys_param', 'boolean', '界面显示', 97,
       '关闭后：自定义字段配置页不显示「下拉显示」项，销售订单明细的物料下拉不再追加自定义字段内容（定制功能隔离用）；默认开启',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ui.custom_field.drop_display.enabled');
