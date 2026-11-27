-- 收款单/付款单明细持久化表 + 核销明细冲销/余数不收字段
-- 背景：收款单/付款单此前只有主表，明细（账户/金额/银行账号/备注）无处落库，
--       导致保存后重新打开明细全部丢失；核销明细虽落 bill_writeoff_detail 但详情接口未回填。
-- 本次补齐：①收款明细表 ②付款明细表 ③核销明细的冲销状态与"余数不收"标记

-- 1. 收款单明细表（现金/转账实际收款行）
CREATE TABLE IF NOT EXISTS cash_receipt_item (
    item_id       BIGSERIAL PRIMARY KEY,
    receipt_id    BIGINT       NOT NULL,               -- 收款单ID
    account_id    BIGINT,                              -- 账户ID（银行账户）
    account_name  VARCHAR(100),                        -- 账户名称
    account_no    VARCHAR(100),                        -- 账户编码/账号
    bank_account  VARCHAR(100),                        -- 银行账号（手填）
    amount        NUMERIC(18,2) DEFAULT 0.00,          -- 金额（原币）
    base_amount   NUMERIC(18,2) DEFAULT 0.00,          -- 本位币金额
    remark        VARCHAR(255),                        -- 备注
    sort_no       INTEGER      DEFAULT 0,              -- 行序
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cash_receipt_item_receipt ON cash_receipt_item(receipt_id);

COMMENT ON TABLE cash_receipt_item IS '收款单明细表（实际收款行：账户/金额/银行账号/备注）';

-- 2. 付款单明细表（现金/转账实际付款行）
CREATE TABLE IF NOT EXISTS cash_payment_item (
    item_id       BIGSERIAL PRIMARY KEY,
    payment_id    BIGINT       NOT NULL,               -- 付款单ID
    account_id    BIGINT,
    account_name  VARCHAR(100),
    account_no    VARCHAR(100),
    bank_account  VARCHAR(100),
    amount        NUMERIC(18,2) DEFAULT 0.00,
    base_amount   NUMERIC(18,2) DEFAULT 0.00,
    remark        VARCHAR(255),
    sort_no       INTEGER      DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cash_payment_item_payment ON cash_payment_item(payment_id);

COMMENT ON TABLE cash_payment_item IS '付款单明细表（实际付款行：账户/金额/银行账号/备注）';

-- 3. 核销明细扩展：独立冲销状态 + 余数不收（尾差结案）
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS settled_qty      VARCHAR(10) DEFAULT '未冲销';
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS settled_at       TIMESTAMP;
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS waive_remainder  SMALLINT    DEFAULT 0;

COMMENT ON COLUMN bill_writeoff_detail.settled_qty     IS '冲销状态：未冲销/已冲销（已冲销的行确认收款时跳过，不重复核销）';
COMMENT ON COLUMN bill_writeoff_detail.settled_at      IS '独立冲销时间';
COMMENT ON COLUMN bill_writeoff_detail.waive_remainder IS '余数不收：1=本行来源单剩余金额不再收/付款（结案）';

-- 4. 预收/预付款单：新增来源单据ID（收款单多收 / 付款单多付自动转入时关联原单，用于幂等与回滚）
ALTER TABLE sale_pre_receipt     ADD COLUMN IF NOT EXISTS source_bill_id BIGINT;
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS source_bill_id BIGINT;

COMMENT ON COLUMN sale_pre_receipt.source_bill_id     IS '来源单据ID（如收款单多收转入时存收款单ID）';
COMMENT ON COLUMN purchase_pre_payment.source_bill_id IS '来源单据ID（如付款单多付转入时存付款单ID）';

