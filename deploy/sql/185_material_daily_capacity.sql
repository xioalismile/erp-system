-- 185 物料日产能（幂等）
-- 背景：2026-11-07 用户口径 —— 排程需要使用物料自己的产能，物料档案新增「日产能」并参与 APS 占用天数计算。
-- 口径：日产能单位=件/天；0 或空 = 不限制，排程结果与升级前完全一致。
-- 兼容：存量物料默认 0，不改变任何历史排程结果。

ALTER TABLE material ADD COLUMN IF NOT EXISTS daily_capacity NUMERIC(18,4) DEFAULT 0;

UPDATE material SET daily_capacity = 0 WHERE daily_capacity IS NULL;

COMMENT ON COLUMN material.daily_capacity IS '物料日产能（件/天）；0 或空=不限制，参与 APS 排程占用天数计算';
