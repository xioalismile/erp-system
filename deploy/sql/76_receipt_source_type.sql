-- ============================================================
-- 166_receipt_source_type.sql
-- 收款单来源标识（设计图 v3 · 第二批）
-- ============================================================
-- 背景：系统不再生成应收单；业务单据审核后按「收款单生成方式」直接生成收款单草稿。
--       自动生成的草稿与手工新增的单据在同一张列表里，用「来源」列区分。
-- 口径：source_type = 系统自动生成 / 手工新增（历史单据一律视为手工新增）。
-- 幂等：ADD COLUMN IF NOT EXISTS + 只回填空值；重复执行结果一致。
-- 同内容文件另存 deploy/sql/76_receipt_source_type.sql

BEGIN;

ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS source_type VARCHAR(20);

UPDATE cash_receipt
SET source_type = '手工新增'
WHERE source_type IS NULL OR source_type = '';

COMMENT ON COLUMN cash_receipt.source_type IS '收款单来源：系统自动生成 / 手工新增';

COMMIT;
