-- ===========================================================================
-- 销售对账单和采购对账单模块 - PostgreSQL 建表脚本
-- 包含四张表：
--   1. sale_statement            销售对账单主表
--   2. sale_statement_item       销售对账单明细表
--   3. purchase_statement        采购对账单主表
--   4. purchase_statement_item   采购对账单明细表
-- 状态说明:
--   0 草稿  1 待确认  2 已确认  3 已关闭
-- 源单类型:
--   销售: SALE_DELIVERY(销售出库单) / SALE_INVOICE(销售发票) / RECEIPT(收款单)
--   采购: PURCHASE_INBOUND(采购入库单) / PURCHASE_INVOICE(采购发票) / PAYMENT(付款单)
-- ===========================================================================

-- 1. 销售对账单主表
CREATE TABLE IF NOT EXISTS sale_statement (
    id BIGSERIAL PRIMARY KEY,
    statement_no VARCHAR(50) NOT NULL,                         -- 对账单编号
    customer_id BIGINT,                                         -- 客户ID
    customer_name VARCHAR(200),                                 -- 客户名称
    start_date DATE,                                            -- 对账开始日期
    end_date DATE,                                              -- 对账结束日期
    total_amount NUMERIC(18,2) DEFAULT 0,                       -- 对账总金额
    received_amount NUMERIC(18,2) DEFAULT 0,                    -- 已收金额
    unpaid_amount NUMERIC(18,2) DEFAULT 0,                      -- 未收金额
    status SMALLINT DEFAULT 0,                                  -- 状态: 0草稿 1待确认 2已确认 3已关闭
    remark VARCHAR(500),                                        -- 备注
    create_by VARCHAR(50),                                      -- 创建人
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,            -- 创建时间
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP             -- 更新时间
);

COMMENT ON TABLE sale_statement IS '销售对账单';
COMMENT ON COLUMN sale_statement.statement_no IS '对账单编号';
COMMENT ON COLUMN sale_statement.customer_id IS '客户ID';
COMMENT ON COLUMN sale_statement.customer_name IS '客户名称';
COMMENT ON COLUMN sale_statement.start_date IS '对账开始日期';
COMMENT ON COLUMN sale_statement.end_date IS '对账结束日期';
COMMENT ON COLUMN sale_statement.total_amount IS '对账总金额';
COMMENT ON COLUMN sale_statement.received_amount IS '已收金额';
COMMENT ON COLUMN sale_statement.unpaid_amount IS '未收金额';
COMMENT ON COLUMN sale_statement.status IS '状态: 0草稿 1待确认 2已确认 3已关闭';
COMMENT ON COLUMN sale_statement.remark IS '备注';
COMMENT ON COLUMN sale_statement.create_by IS '创建人';
COMMENT ON COLUMN sale_statement.create_time IS '创建时间';
COMMENT ON COLUMN sale_statement.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_sale_statement_no ON sale_statement(statement_no);
CREATE INDEX IF NOT EXISTS idx_sale_statement_customer ON sale_statement(customer_id);
CREATE INDEX IF NOT EXISTS idx_sale_statement_status ON sale_statement(status);
CREATE INDEX IF NOT EXISTS idx_sale_statement_date ON sale_statement(start_date, end_date);

-- 2. 销售对账单明细表
CREATE TABLE IF NOT EXISTS sale_statement_item (
    id BIGSERIAL PRIMARY KEY,
    statement_id BIGINT NOT NULL,                               -- 对账单ID
    source_id BIGINT,                                           -- 源单ID
    source_no VARCHAR(50),                                      -- 源单编号
    source_type VARCHAR(30),                                    -- 源单类型: SALE_DELIVERY/SALE_INVOICE/RECEIPT
    source_date DATE,                                           -- 源单日期
    amount NUMERIC(18,2) DEFAULT 0,                             -- 金额
    received_amount NUMERIC(18,2) DEFAULT 0,                    -- 已收金额
    unpaid_amount NUMERIC(18,2) DEFAULT 0,                      -- 未收金额
    remark VARCHAR(500)                                         -- 备注
);

COMMENT ON TABLE sale_statement_item IS '销售对账单明细';
COMMENT ON COLUMN sale_statement_item.statement_id IS '对账单ID';
COMMENT ON COLUMN sale_statement_item.source_id IS '源单ID';
COMMENT ON COLUMN sale_statement_item.source_no IS '源单编号';
COMMENT ON COLUMN sale_statement_item.source_type IS '源单类型: SALE_DELIVERY/SALE_INVOICE/RECEIPT';
COMMENT ON COLUMN sale_statement_item.source_date IS '源单日期';
COMMENT ON COLUMN sale_statement_item.amount IS '金额';
COMMENT ON COLUMN sale_statement_item.received_amount IS '已收金额';
COMMENT ON COLUMN sale_statement_item.unpaid_amount IS '未收金额';
COMMENT ON COLUMN sale_statement_item.remark IS '备注';

CREATE INDEX IF NOT EXISTS idx_sale_statement_item_statement ON sale_statement_item(statement_id);
CREATE INDEX IF NOT EXISTS idx_sale_statement_item_source ON sale_statement_item(source_type, source_id);

-- 3. 采购对账单主表
CREATE TABLE IF NOT EXISTS purchase_statement (
    id BIGSERIAL PRIMARY KEY,
    statement_no VARCHAR(50) NOT NULL,                         -- 对账单编号
    supplier_id BIGINT,                                         -- 供应商ID
    supplier_name VARCHAR(200),                                 -- 供应商名称
    start_date DATE,                                            -- 对账开始日期
    end_date DATE,                                              -- 对账结束日期
    total_amount NUMERIC(18,2) DEFAULT 0,                       -- 对账总金额
    paid_amount NUMERIC(18,2) DEFAULT 0,                        -- 已付金额
    unpaid_amount NUMERIC(18,2) DEFAULT 0,                      -- 未付金额
    status SMALLINT DEFAULT 0,                                  -- 状态: 0草稿 1待确认 2已确认 3已关闭
    remark VARCHAR(500),                                        -- 备注
    create_by VARCHAR(50),                                      -- 创建人
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,            -- 创建时间
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP             -- 更新时间
);

COMMENT ON TABLE purchase_statement IS '采购对账单';
COMMENT ON COLUMN purchase_statement.statement_no IS '对账单编号';
COMMENT ON COLUMN purchase_statement.supplier_id IS '供应商ID';
COMMENT ON COLUMN purchase_statement.supplier_name IS '供应商名称';
COMMENT ON COLUMN purchase_statement.start_date IS '对账开始日期';
COMMENT ON COLUMN purchase_statement.end_date IS '对账结束日期';
COMMENT ON COLUMN purchase_statement.total_amount IS '对账总金额';
COMMENT ON COLUMN purchase_statement.paid_amount IS '已付金额';
COMMENT ON COLUMN purchase_statement.unpaid_amount IS '未付金额';
COMMENT ON COLUMN purchase_statement.status IS '状态: 0草稿 1待确认 2已确认 3已关闭';
COMMENT ON COLUMN purchase_statement.remark IS '备注';
COMMENT ON COLUMN purchase_statement.create_by IS '创建人';
COMMENT ON COLUMN purchase_statement.create_time IS '创建时间';
COMMENT ON COLUMN purchase_statement.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_purchase_statement_no ON purchase_statement(statement_no);
CREATE INDEX IF NOT EXISTS idx_purchase_statement_supplier ON purchase_statement(supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_statement_status ON purchase_statement(status);
CREATE INDEX IF NOT EXISTS idx_purchase_statement_date ON purchase_statement(start_date, end_date);

-- 4. 采购对账单明细表
CREATE TABLE IF NOT EXISTS purchase_statement_item (
    id BIGSERIAL PRIMARY KEY,
    statement_id BIGINT NOT NULL,                               -- 对账单ID
    source_id BIGINT,                                           -- 源单ID
    source_no VARCHAR(50),                                      -- 源单编号
    source_type VARCHAR(30),                                    -- 源单类型: PURCHASE_INBOUND/PURCHASE_INVOICE/PAYMENT
    source_date DATE,                                           -- 源单日期
    amount NUMERIC(18,2) DEFAULT 0,                             -- 金额
    paid_amount NUMERIC(18,2) DEFAULT 0,                        -- 已付金额
    unpaid_amount NUMERIC(18,2) DEFAULT 0,                      -- 未付金额
    remark VARCHAR(500)                                         -- 备注
);

COMMENT ON TABLE purchase_statement_item IS '采购对账单明细';
COMMENT ON COLUMN purchase_statement_item.statement_id IS '对账单ID';
COMMENT ON COLUMN purchase_statement_item.source_id IS '源单ID';
COMMENT ON COLUMN purchase_statement_item.source_no IS '源单编号';
COMMENT ON COLUMN purchase_statement_item.source_type IS '源单类型: PURCHASE_INBOUND/PURCHASE_INVOICE/PAYMENT';
COMMENT ON COLUMN purchase_statement_item.source_date IS '源单日期';
COMMENT ON COLUMN purchase_statement_item.amount IS '金额';
COMMENT ON COLUMN purchase_statement_item.paid_amount IS '已付金额';
COMMENT ON COLUMN purchase_statement_item.unpaid_amount IS '未付金额';
COMMENT ON COLUMN purchase_statement_item.remark IS '备注';

CREATE INDEX IF NOT EXISTS idx_purchase_statement_item_statement ON purchase_statement_item(statement_id);
CREATE INDEX IF NOT EXISTS idx_purchase_statement_item_source ON purchase_statement_item(source_type, source_id);
