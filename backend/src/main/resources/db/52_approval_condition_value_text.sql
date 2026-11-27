-- ====================================================================
-- 52: 审批条件模板支持字符串/日期/下拉类型的条件值
-- 背景：设置中心「条件模板」原先只支持金额类数字条件（value_low/value_high 为 numeric），
--       本次升级为表头全部字段 + 自定义字段可用：字符串/日期/下拉条件的值存 value_text。
-- 幂等：ADD COLUMN IF NOT EXISTS，重复执行零副作用。
-- ====================================================================

ALTER TABLE approval_condition ADD COLUMN IF NOT EXISTS value_text varchar(500);

COMMENT ON COLUMN approval_condition.value_text IS '条件值（文本类：字符串等于/不等于/包含、日期、下拉选项；数字类条件仍用 value_low/value_high）';
