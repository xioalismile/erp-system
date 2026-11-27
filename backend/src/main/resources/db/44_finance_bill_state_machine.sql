-- ============================================================
-- 44_finance_bill_state_machine.sql
-- 收款单(cash_receipt)/付款单(cash_payment) 接入通用单据状态机
-- 补齐字段：close_status(结案)、bill_before_status(作废前状态)、
--           closed_by/closed_at/closed_reason(结案审计)、
--           voided_by/voided_at/void_reason(作废审计)
-- 说明：41号补丁误将 close_status 加到遗留表 payment_receipt；
--       实体 @TableName 实际映射 cash_receipt / cash_payment，此处对真实表补正
-- ============================================================

-- 1. 收款单 cash_receipt
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS close_status INT DEFAULT 0;
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS bill_before_status INT;
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(255);
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS void_reason VARCHAR(255);

COMMENT ON COLUMN cash_receipt.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN cash_receipt.bill_before_status IS '作废前状态码(0草稿/1待审核/2已确认)，反作废恢复用';
COMMENT ON COLUMN cash_receipt.closed_by IS '结案人';
COMMENT ON COLUMN cash_receipt.closed_at IS '结案时间';
COMMENT ON COLUMN cash_receipt.closed_reason IS '结案原因';
COMMENT ON COLUMN cash_receipt.voided_by IS '作废人';
COMMENT ON COLUMN cash_receipt.voided_at IS '作废时间';
COMMENT ON COLUMN cash_receipt.void_reason IS '作废原因';

-- 2. 付款单 cash_payment
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS close_status INT DEFAULT 0;
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS bill_before_status INT;
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(255);
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS void_reason VARCHAR(255);

COMMENT ON COLUMN cash_payment.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN cash_payment.bill_before_status IS '作废前状态码(0草稿/1待审核/2已确认)，反作废恢复用';
COMMENT ON COLUMN cash_payment.closed_by IS '结案人';
COMMENT ON COLUMN cash_payment.closed_at IS '结案时间';
COMMENT ON COLUMN cash_payment.closed_reason IS '结案原因';
COMMENT ON COLUMN cash_payment.voided_by IS '作废人';
COMMENT ON COLUMN cash_payment.voided_at IS '作废时间';
COMMENT ON COLUMN cash_payment.void_reason IS '作废原因';

-- 3. 存量数据兜底
UPDATE cash_receipt SET close_status = 0 WHERE close_status IS NULL;
UPDATE cash_payment SET close_status = 0 WHERE close_status IS NULL;
