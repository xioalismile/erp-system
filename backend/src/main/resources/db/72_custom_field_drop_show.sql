-- 72_custom_field_drop_show.sql
-- 2026-09-14 自定义字段「下拉显示」开关：单据明细选择物料的下拉列表中，追加展示所勾选的自定义字段
--
-- 背景：单据明细行的「物料」下拉目前只渲染 编码 + 名称 + 规格型号 三段；
--       客户希望把自建的物料自定义字段内容也显示在下拉里，且由自己决定显示哪几个。
--       「显示哪几个」在设置中心→自定义字段配置页（实体=物料）用本开关逐字段勾选。
--
-- 语义：is_drop_show = 0 不显示（默认，与改造前行为完全一致，零回归）
--       is_drop_show = 1 显示在该物料下拉列表中（仅对 entity_type='material' 生效）
--
-- 幂等：ADD COLUMN IF NOT EXISTS，可重复执行零副作用；新列 NOT NULL DEFAULT 0
--       → 存量自定义字段一律回落为"不显示"，不动任何既有配置与数据。
--
-- 说明：本脚本只加 1 列，不改表结构、不删改既有列、不写业务数据。
ALTER TABLE sys_custom_field ADD COLUMN IF NOT EXISTS is_drop_show SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN sys_custom_field.is_drop_show IS '是否在物料选择下拉中显示：0否 1是（仅 entity_type=material 生效）';
