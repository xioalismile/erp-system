-- 收付款核销明细表：统一记录收款单/付款单核销的每一笔来源单，支持生效时核销 + 重置草稿/作废对称回减
CREATE TABLE IF NOT EXISTS bill_writeoff_detail (
    writeoff_id     BIGSERIAL PRIMARY KEY,
    bill_type       VARCHAR(20)  NOT NULL,            -- 收款单 / 付款单
    bill_id         BIGINT       NOT NULL,            -- receipt_id / payment_id
    bill_no         VARCHAR(50)  NOT NULL,
    source_type     VARCHAR(20)  NOT NULL,            -- 预收款单 / 销售订单 / 应收单 / 预付款单 / 应付单
    source_id       BIGINT       NOT NULL,            -- 来源单ID
    source_no       VARCHAR(50)  NOT NULL,            -- 来源单号
    source_amount   NUMERIC(18,2) DEFAULT 0.00,       -- 来源单金额
    used_amount     NUMERIC(18,2) DEFAULT 0.00,       -- 已核金额
    writeoff_amount NUMERIC(18,2) NOT NULL,           -- 本次核销金额
    writeoff_date   DATE         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_bill_writeoff_bill ON bill_writeoff_detail(bill_type, bill_id);
CREATE INDEX IF NOT EXISTS idx_bill_writeoff_source ON bill_writeoff_detail(source_type, source_id);

COMMENT ON TABLE bill_writeoff_detail IS '收付款核销明细表（收款单/付款单核销来源单的统一明细）';
