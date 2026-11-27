-- ============================================================
-- 60_pre_receipt_account_fields.sql
-- 销售预收款明细 / 采购预付款明细 增加银行账户字段
-- 明细行从银行账户基础资料(bank_account)选择账户，持久化账户信息
-- 幂等：ADD COLUMN IF NOT EXISTS
-- ============================================================

-- 销售预收款明细
ALTER TABLE sale_pre_receipt_item ADD COLUMN IF NOT EXISTS account_id BIGINT;
ALTER TABLE sale_pre_receipt_item ADD COLUMN IF NOT EXISTS account_name VARCHAR(200);
ALTER TABLE sale_pre_receipt_item ADD COLUMN IF NOT EXISTS account_no VARCHAR(100);
COMMENT ON COLUMN sale_pre_receipt_item.account_id IS '银行账户ID(bank_account.account_id)';
COMMENT ON COLUMN sale_pre_receipt_item.account_name IS '账户名称';
COMMENT ON COLUMN sale_pre_receipt_item.account_no IS '账户编码/账号';

-- 采购预付款明细
ALTER TABLE purchase_pre_payment_item ADD COLUMN IF NOT EXISTS account_id BIGINT;
ALTER TABLE purchase_pre_payment_item ADD COLUMN IF NOT EXISTS account_name VARCHAR(200);
ALTER TABLE purchase_pre_payment_item ADD COLUMN IF NOT EXISTS account_no VARCHAR(100);
COMMENT ON COLUMN purchase_pre_payment_item.account_id IS '银行账户ID(bank_account.account_id)';
COMMENT ON COLUMN purchase_pre_payment_item.account_name IS '账户名称';
COMMENT ON COLUMN purchase_pre_payment_item.account_no IS '账户编码/账号';
