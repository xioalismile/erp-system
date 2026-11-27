-- ===========================================================================
-- 预收款单和预付款单模块 - PostgreSQL 建表脚本
-- 包含四张表：
--   1. sale_pre_receipt            预收款单主表
--   2. sale_pre_receipt_item       预收款单核销明细表
--   3. purchase_pre_payment        预付款单主表
--   4. purchase_pre_payment_item   预付款单核销明细表
-- 状态说明:
--   0 草稿  1 待审核  2 已生效  3 已核销  4 已作废
-- 源单类型:
--   预收款: SALE_DELIVERY(销售出库单) / SALE_INVOICE(销售发票)
--   预付款: PURCHASE_INBOUND(采购入库单) / PURCHASE_INVOICE(采购发票)
-- ===========================================================================

-- 1. 预收款单主表
CREATE TABLE IF NOT EXISTS sale_pre_receipt (
    id BIGSERIAL PRIMARY KEY,
    receipt_no VARCHAR(50) NOT NULL,                             -- 预收款单号
    customer_id BIGINT,                                           -- 客户ID
    customer_name VARCHAR(200),                                   -- 客户名称
    order_id BIGINT,                                              -- 关联销售订单ID
    order_no VARCHAR(50),                                         -- 关联销售订单号
    receipt_date DATE,                                            -- 收款日期
    amount NUMERIC(18,2) DEFAULT 0,                               -- 收款金额
    base_amount NUMERIC(18,2) DEFAULT 0,                          -- 本位币金额
    currency_code VARCHAR(10),                                    -- 币别
    exchange_rate NUMERIC(18,6) DEFAULT 1,                        -- 汇率
    payment_method VARCHAR(30),                                   -- 付款方式: 现金/银行转账/支票等
    account_id BIGINT,                                            -- 收款账户ID
    account_name VARCHAR(200),                                    -- 收款账户名称
    status SMALLINT DEFAULT 0,                                    -- 状态: 0草稿 1待审核 2已生效 3已核销 4已作废
    used_amount NUMERIC(18,2) DEFAULT 0,                          -- 已使用金额
    remain_amount NUMERIC(18,2) DEFAULT 0,                        -- 剩余金额
    remark VARCHAR(500),                                          -- 备注
    create_by VARCHAR(50),                                        -- 创建人
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,              -- 创建时间
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP               -- 更新时间
);

COMMENT ON TABLE sale_pre_receipt IS '预收款单';
COMMENT ON COLUMN sale_pre_receipt.receipt_no IS '预收款单号';
COMMENT ON COLUMN sale_pre_receipt.customer_id IS '客户ID';
COMMENT ON COLUMN sale_pre_receipt.customer_name IS '客户名称';
COMMENT ON COLUMN sale_pre_receipt.order_id IS '关联销售订单ID';
COMMENT ON COLUMN sale_pre_receipt.order_no IS '关联销售订单号';
COMMENT ON COLUMN sale_pre_receipt.receipt_date IS '收款日期';
COMMENT ON COLUMN sale_pre_receipt.amount IS '收款金额';
COMMENT ON COLUMN sale_pre_receipt.base_amount IS '本位币金额';
COMMENT ON COLUMN sale_pre_receipt.currency_code IS '币别';
COMMENT ON COLUMN sale_pre_receipt.exchange_rate IS '汇率';
COMMENT ON COLUMN sale_pre_receipt.payment_method IS '付款方式: 现金/银行转账/支票等';
COMMENT ON COLUMN sale_pre_receipt.account_id IS '收款账户ID';
COMMENT ON COLUMN sale_pre_receipt.account_name IS '收款账户名称';
COMMENT ON COLUMN sale_pre_receipt.status IS '状态: 0草稿 1待审核 2已生效 3已核销 4已作废';
COMMENT ON COLUMN sale_pre_receipt.used_amount IS '已使用金额';
COMMENT ON COLUMN sale_pre_receipt.remain_amount IS '剩余金额';
COMMENT ON COLUMN sale_pre_receipt.remark IS '备注';
COMMENT ON COLUMN sale_pre_receipt.create_by IS '创建人';
COMMENT ON COLUMN sale_pre_receipt.create_time IS '创建时间';
COMMENT ON COLUMN sale_pre_receipt.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_sale_pre_receipt_no ON sale_pre_receipt(receipt_no);
CREATE INDEX IF NOT EXISTS idx_sale_pre_receipt_customer ON sale_pre_receipt(customer_id);
CREATE INDEX IF NOT EXISTS idx_sale_pre_receipt_order ON sale_pre_receipt(order_id);
CREATE INDEX IF NOT EXISTS idx_sale_pre_receipt_status ON sale_pre_receipt(status);
CREATE INDEX IF NOT EXISTS idx_sale_pre_receipt_date ON sale_pre_receipt(receipt_date);

-- 2. 预收款单核销明细表
CREATE TABLE IF NOT EXISTS sale_pre_receipt_item (
    id BIGSERIAL PRIMARY KEY,
    pre_receipt_id BIGINT NOT NULL,                               -- 预收款单ID
    source_id BIGINT,                                             -- 源单ID
    source_no VARCHAR(50),                                        -- 源单编号
    source_type VARCHAR(30),                                      -- 源单类型: SALE_DELIVERY/SALE_INVOICE
    amount NUMERIC(18,2) DEFAULT 0,                               -- 核销金额
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP               -- 创建时间
);

COMMENT ON TABLE sale_pre_receipt_item IS '预收款单核销明细';
COMMENT ON COLUMN sale_pre_receipt_item.pre_receipt_id IS '预收款单ID';
COMMENT ON COLUMN sale_pre_receipt_item.source_id IS '源单ID';
COMMENT ON COLUMN sale_pre_receipt_item.source_no IS '源单编号';
COMMENT ON COLUMN sale_pre_receipt_item.source_type IS '源单类型: SALE_DELIVERY/SALE_INVOICE';
COMMENT ON COLUMN sale_pre_receipt_item.amount IS '核销金额';
COMMENT ON COLUMN sale_pre_receipt_item.create_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_sale_pre_receipt_item_receipt ON sale_pre_receipt_item(pre_receipt_id);
CREATE INDEX IF NOT EXISTS idx_sale_pre_receipt_item_source ON sale_pre_receipt_item(source_type, source_id);

-- 3. 预付款单主表
CREATE TABLE IF NOT EXISTS purchase_pre_payment (
    id BIGSERIAL PRIMARY KEY,
    payment_no VARCHAR(50) NOT NULL,                              -- 预付款单号
    supplier_id BIGINT,                                           -- 供应商ID
    supplier_name VARCHAR(200),                                   -- 供应商名称
    order_id BIGINT,                                              -- 关联采购订单ID
    order_no VARCHAR(50),                                         -- 关联采购订单号
    payment_date DATE,                                            -- 付款日期
    amount NUMERIC(18,2) DEFAULT 0,                               -- 付款金额
    base_amount NUMERIC(18,2) DEFAULT 0,                          -- 本位币金额
    currency_code VARCHAR(10),                                    -- 币别
    exchange_rate NUMERIC(18,6) DEFAULT 1,                        -- 汇率
    payment_method VARCHAR(30),                                   -- 付款方式: 现金/银行转账/支票等
    account_id BIGINT,                                            -- 付款账户ID
    account_name VARCHAR(200),                                    -- 付款账户名称
    status SMALLINT DEFAULT 0,                                    -- 状态: 0草稿 1待审核 2已生效 3已核销 4已作废
    used_amount NUMERIC(18,2) DEFAULT 0,                          -- 已使用金额
    remain_amount NUMERIC(18,2) DEFAULT 0,                        -- 剩余金额
    remark VARCHAR(500),                                          -- 备注
    create_by VARCHAR(50),                                        -- 创建人
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,              -- 创建时间
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP               -- 更新时间
);

COMMENT ON TABLE purchase_pre_payment IS '预付款单';
COMMENT ON COLUMN purchase_pre_payment.payment_no IS '预付款单号';
COMMENT ON COLUMN purchase_pre_payment.supplier_id IS '供应商ID';
COMMENT ON COLUMN purchase_pre_payment.supplier_name IS '供应商名称';
COMMENT ON COLUMN purchase_pre_payment.order_id IS '关联采购订单ID';
COMMENT ON COLUMN purchase_pre_payment.order_no IS '关联采购订单号';
COMMENT ON COLUMN purchase_pre_payment.payment_date IS '付款日期';
COMMENT ON COLUMN purchase_pre_payment.amount IS '付款金额';
COMMENT ON COLUMN purchase_pre_payment.base_amount IS '本位币金额';
COMMENT ON COLUMN purchase_pre_payment.currency_code IS '币别';
COMMENT ON COLUMN purchase_pre_payment.exchange_rate IS '汇率';
COMMENT ON COLUMN purchase_pre_payment.payment_method IS '付款方式: 现金/银行转账/支票等';
COMMENT ON COLUMN purchase_pre_payment.account_id IS '付款账户ID';
COMMENT ON COLUMN purchase_pre_payment.account_name IS '付款账户名称';
COMMENT ON COLUMN purchase_pre_payment.status IS '状态: 0草稿 1待审核 2已生效 3已核销 4已作废';
COMMENT ON COLUMN purchase_pre_payment.used_amount IS '已使用金额';
COMMENT ON COLUMN purchase_pre_payment.remain_amount IS '剩余金额';
COMMENT ON COLUMN purchase_pre_payment.remark IS '备注';
COMMENT ON COLUMN purchase_pre_payment.create_by IS '创建人';
COMMENT ON COLUMN purchase_pre_payment.create_time IS '创建时间';
COMMENT ON COLUMN purchase_pre_payment.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_purchase_pre_payment_no ON purchase_pre_payment(payment_no);
CREATE INDEX IF NOT EXISTS idx_purchase_pre_payment_supplier ON purchase_pre_payment(supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_pre_payment_order ON purchase_pre_payment(order_id);
CREATE INDEX IF NOT EXISTS idx_purchase_pre_payment_status ON purchase_pre_payment(status);
CREATE INDEX IF NOT EXISTS idx_purchase_pre_payment_date ON purchase_pre_payment(payment_date);

-- 4. 预付款单核销明细表
CREATE TABLE IF NOT EXISTS purchase_pre_payment_item (
    id BIGSERIAL PRIMARY KEY,
    pre_payment_id BIGINT NOT NULL,                               -- 预付款单ID
    source_id BIGINT,                                             -- 源单ID
    source_no VARCHAR(50),                                        -- 源单编号
    source_type VARCHAR(30),                                      -- 源单类型: PURCHASE_INBOUND/PURCHASE_INVOICE
    amount NUMERIC(18,2) DEFAULT 0,                               -- 核销金额
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP               -- 创建时间
);

COMMENT ON TABLE purchase_pre_payment_item IS '预付款单核销明细';
COMMENT ON COLUMN purchase_pre_payment_item.pre_payment_id IS '预付款单ID';
COMMENT ON COLUMN purchase_pre_payment_item.source_id IS '源单ID';
COMMENT ON COLUMN purchase_pre_payment_item.source_no IS '源单编号';
COMMENT ON COLUMN purchase_pre_payment_item.source_type IS '源单类型: PURCHASE_INBOUND/PURCHASE_INVOICE';
COMMENT ON COLUMN purchase_pre_payment_item.amount IS '核销金额';
COMMENT ON COLUMN purchase_pre_payment_item.create_time IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_purchase_pre_payment_item_payment ON purchase_pre_payment_item(pre_payment_id);
CREATE INDEX IF NOT EXISTS idx_purchase_pre_payment_item_source ON purchase_pre_payment_item(source_type, source_id);
