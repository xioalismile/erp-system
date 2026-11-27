-- ============================================================================
-- 智能ERP系统 - PostgreSQL 数据库建表脚本
-- 模块：财务管理模块
-- 版本：V1.0
-- 日期：2026-06-22
-- 说明：共24张表，包含应收应付、收付款、凭证、固定资产等业务单据
-- ============================================================================

-- 1. 应收单主表 (account_receivable)
CREATE TABLE account_receivable (
    ar_id BIGSERIAL PRIMARY KEY,
    ar_no VARCHAR(50) NOT NULL UNIQUE,
    ar_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    paid_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    balance_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    due_date DATE,
    payment_term_id BIGINT,
    source_bill_type VARCHAR(50) NOT NULL,
    source_bill_no VARCHAR(50) NOT NULL,
    invoice_no VARCHAR(50),
    saler_id BIGINT,
    department_id BIGINT,
    is_auto_gen SMALLINT NOT NULL DEFAULT 0,
    is_closed SMALLINT NOT NULL DEFAULT 0,
    closed_by VARCHAR(50),
    closed_at TIMESTAMP,
    closed_reason VARCHAR(500),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '未核销',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (payment_term_id) REFERENCES payment_term(payment_term_id),
    FOREIGN KEY (saler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id)
);

COMMENT ON TABLE account_receivable IS '应收单主表';

-- 2. 应收单明细表 (account_receivable_item)
CREATE TABLE account_receivable_item (
    ar_item_id BIGSERIAL PRIMARY KEY,
    ar_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    unit_id BIGINT,
    quantity NUMERIC(18,4) DEFAULT 0.0000,
    unit_price NUMERIC(18,4) DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (ar_id) REFERENCES account_receivable(ar_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

COMMENT ON TABLE account_receivable_item IS '应收单明细表';

-- 3. 应付单主表 (account_payable)
CREATE TABLE account_payable (
    ap_id BIGSERIAL PRIMARY KEY,
    ap_no VARCHAR(50) NOT NULL UNIQUE,
    ap_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL,
    supplier_name VARCHAR(200) NOT NULL,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    paid_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    balance_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    due_date DATE,
    payment_term_id BIGINT,
    source_bill_type VARCHAR(50) NOT NULL,
    source_bill_no VARCHAR(50) NOT NULL,
    invoice_no VARCHAR(50),
    buyer_id BIGINT,
    department_id BIGINT,
    is_auto_gen SMALLINT NOT NULL DEFAULT 0,
    is_closed SMALLINT NOT NULL DEFAULT 0,
    closed_by VARCHAR(50),
    closed_at TIMESTAMP,
    closed_reason VARCHAR(500),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '未核销',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (payment_term_id) REFERENCES payment_term(payment_term_id),
    FOREIGN KEY (buyer_id) REFERENCES employee(employee_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id)
);

COMMENT ON TABLE account_payable IS '应付单主表';

-- 4. 应付单明细表 (account_payable_item)
CREATE TABLE account_payable_item (
    ap_item_id BIGSERIAL PRIMARY KEY,
    ap_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    unit_id BIGINT,
    quantity NUMERIC(18,4) DEFAULT 0.0000,
    unit_price NUMERIC(18,4) DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (ap_id) REFERENCES account_payable(ap_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

COMMENT ON TABLE account_payable_item IS '应付单明细表';

-- 5. 收款单主表 (cash_receipt)
CREATE TABLE cash_receipt (
    receipt_id BIGSERIAL PRIMARY KEY,
    receipt_no VARCHAR(50) NOT NULL UNIQUE,
    receipt_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    settle_mode_id BIGINT NOT NULL,
    bank_account VARCHAR(50),
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    received_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    document_no VARCHAR(50),
    cashier_id BIGINT,
    department_id BIGINT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    FOREIGN KEY (settle_mode_id) REFERENCES settlement_mode(settle_mode_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (cashier_id) REFERENCES employee(employee_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id)
);

COMMENT ON TABLE cash_receipt IS '收款单主表';

-- 6. 收款单核销明细表 (cash_receipt_writeoff)
CREATE TABLE cash_receipt_writeoff (
    writeoff_id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    receipt_no VARCHAR(50) NOT NULL,
    ar_id BIGINT NOT NULL,
    ar_no VARCHAR(50) NOT NULL,
    ar_date DATE NOT NULL,
    writeoff_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    writeoff_date DATE NOT NULL,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    FOREIGN KEY (receipt_id) REFERENCES cash_receipt(receipt_id) ON DELETE CASCADE,
    FOREIGN KEY (ar_id) REFERENCES account_receivable(ar_id)
);

COMMENT ON TABLE cash_receipt_writeoff IS '收款单核销明细表';

-- 7. 付款单主表 (cash_payment)
CREATE TABLE cash_payment (
    payment_id BIGSERIAL PRIMARY KEY,
    payment_no VARCHAR(50) NOT NULL UNIQUE,
    payment_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL,
    supplier_name VARCHAR(200) NOT NULL,
    settle_mode_id BIGINT NOT NULL,
    bank_account VARCHAR(50),
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    paid_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    document_no VARCHAR(50),
    cashier_id BIGINT,
    department_id BIGINT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id),
    FOREIGN KEY (settle_mode_id) REFERENCES settlement_mode(settle_mode_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (cashier_id) REFERENCES employee(employee_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id)
);

COMMENT ON TABLE cash_payment IS '付款单主表';

-- 8. 付款单核销明细表 (cash_payment_writeoff)
CREATE TABLE cash_payment_writeoff (
    writeoff_id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    payment_no VARCHAR(50) NOT NULL,
    ap_id BIGINT NOT NULL,
    ap_no VARCHAR(50) NOT NULL,
    ap_date DATE NOT NULL,
    writeoff_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    writeoff_date DATE NOT NULL,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    FOREIGN KEY (payment_id) REFERENCES cash_payment(payment_id) ON DELETE CASCADE,
    FOREIGN KEY (ap_id) REFERENCES account_payable(ap_id)
);

COMMENT ON TABLE cash_payment_writeoff IS '付款单核销明细表';

-- 9. 收款退款单主表 (cash_receipt_refund)
CREATE TABLE cash_receipt_refund (
    refund_id BIGSERIAL PRIMARY KEY,
    refund_no VARCHAR(50) NOT NULL UNIQUE,
    refund_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    settle_mode_id BIGINT NOT NULL,
    bank_account VARCHAR(50),
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    cash_receipt_id BIGINT,
    cash_receipt_no VARCHAR(50),
    cashier_id BIGINT,
    department_id BIGINT,
    reason VARCHAR(500),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    FOREIGN KEY (settle_mode_id) REFERENCES settlement_mode(settle_mode_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (cash_receipt_id) REFERENCES cash_receipt(receipt_id),
    FOREIGN KEY (cashier_id) REFERENCES employee(employee_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id)
);

COMMENT ON TABLE cash_receipt_refund IS '收款退款单主表';

-- 10. 付款退款单主表 (cash_payment_refund)
CREATE TABLE cash_payment_refund (
    refund_id BIGSERIAL PRIMARY KEY,
    refund_no VARCHAR(50) NOT NULL UNIQUE,
    refund_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL,
    supplier_name VARCHAR(200) NOT NULL,
    settle_mode_id BIGINT NOT NULL,
    bank_account VARCHAR(50),
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    cash_payment_id BIGINT,
    cash_payment_no VARCHAR(50),
    cashier_id BIGINT,
    department_id BIGINT,
    reason VARCHAR(500),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id),
    FOREIGN KEY (settle_mode_id) REFERENCES settlement_mode(settle_mode_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (cash_payment_id) REFERENCES cash_payment(payment_id),
    FOREIGN KEY (cashier_id) REFERENCES employee(employee_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id)
);

COMMENT ON TABLE cash_payment_refund IS '付款退款单主表';

-- 14. 成本计算单主表 (cost_calculation)
CREATE TABLE cost_calculation (
    calc_id BIGSERIAL PRIMARY KEY,
    calc_no VARCHAR(50) NOT NULL UNIQUE,
    calc_date DATE NOT NULL,
    calc_type VARCHAR(20) NOT NULL DEFAULT '月结成本',
    period_year INT NOT NULL,
    period_month INT NOT NULL,
    total_cost NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    material_cost NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    labor_cost NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    overhead_cost NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50)
);

COMMENT ON TABLE cost_calculation IS '成本计算单主表';

-- 15. 成本计算单明细表 (cost_calculation_item)
CREATE TABLE cost_calculation_item (
    calc_item_id BIGSERIAL PRIMARY KEY,
    calc_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    cost_object_type VARCHAR(20) NOT NULL,
    cost_object_id BIGINT NOT NULL,
    cost_object_code VARCHAR(50) NOT NULL,
    cost_object_name VARCHAR(200) NOT NULL,
    material_cost NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    labor_cost NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    overhead_cost NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    total_cost NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    output_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_cost NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (calc_id) REFERENCES cost_calculation(calc_id) ON DELETE CASCADE
);

COMMENT ON TABLE cost_calculation_item IS '成本计算单明细表';

-- 16. 成本期间表 (cost_period)
CREATE TABLE cost_period (
    period_id BIGSERIAL PRIMARY KEY,
    period_year INT NOT NULL,
    period_month INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT '打开',
    is_closed SMALLINT NOT NULL DEFAULT 0,
    closed_by VARCHAR(50),
    closed_at TIMESTAMP,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    UNIQUE (period_year, period_month)
);

COMMENT ON TABLE cost_period IS '成本期间';

-- 17. 固定资产主表 (fixed_asset)
CREATE TABLE fixed_asset (
    asset_id BIGSERIAL PRIMARY KEY,
    asset_code VARCHAR(50) NOT NULL UNIQUE,
    asset_name VARCHAR(200) NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    specification VARCHAR(200),
    brand VARCHAR(100),
    serial_no VARCHAR(100),
    purchase_date DATE,
    use_start_date DATE,
    supplier_id BIGINT,
    purchase_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_value NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    depreciation_method VARCHAR(20) NOT NULL DEFAULT '年限平均法',
    service_life_years INT NOT NULL DEFAULT 5,
    residual_rate NUMERIC(8,4) NOT NULL DEFAULT 5.0000,
    residual_value NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    depreciation_per_month NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    accumulated_depreciation NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    current_depreciation NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    department_id BIGINT,
    location VARCHAR(200),
    custodian VARCHAR(100),
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    card_no VARCHAR(50),
    is_tag_print SMALLINT NOT NULL DEFAULT 0,
    photo_urls VARCHAR(1000),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '使用中',
    disposal_status VARCHAR(10),
    disposal_date DATE,
    disposal_amount NUMERIC(18,2),
    disposal_reason VARCHAR(500),
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id)
);

COMMENT ON TABLE fixed_asset IS '固定资产主表';

-- 18. 固定资产折旧明细表 (asset_depreciation)
CREATE TABLE asset_depreciation (
    depreciation_id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    asset_code VARCHAR(50) NOT NULL,
    period_year INT NOT NULL,
    period_month INT NOT NULL,
    depreciation_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    accumulated_depreciation NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_value NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    is_posted SMALLINT NOT NULL DEFAULT 0,
    voucher_id BIGINT,
    voucher_no VARCHAR(50),
    posted_by VARCHAR(50),
    posted_at TIMESTAMP,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    FOREIGN KEY (asset_id) REFERENCES fixed_asset(asset_id),
    FOREIGN KEY (voucher_id) REFERENCES voucher(voucher_id)
);

COMMENT ON TABLE asset_depreciation IS '固定资产折旧明细表';

-- 19. 预算单主表 (budget)
CREATE TABLE budget (
    budget_id BIGSERIAL PRIMARY KEY,
    budget_code VARCHAR(50) NOT NULL UNIQUE,
    budget_name VARCHAR(200) NOT NULL,
    budget_type VARCHAR(20) NOT NULL,
    period_year INT NOT NULL,
    period_month INT,
    department_id BIGINT,
    project_code VARCHAR(50),
    budget_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    used_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    available_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    control_level VARCHAR(10) NOT NULL DEFAULT '严格控制',
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    effective_date DATE,
    expiry_date DATE,
    remark VARCHAR(2000),
    status VARCHAR(10) NOT NULL DEFAULT '已生效',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (department_id) REFERENCES department(dept_id)
);

COMMENT ON TABLE budget IS '预算单主表';

-- 20. 预算明细表 (budget_item)
CREATE TABLE budget_item (
    budget_item_id BIGSERIAL PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    account_id BIGINT NOT NULL,
    account_code VARCHAR(50) NOT NULL,
    account_name VARCHAR(200) NOT NULL,
    budget_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    used_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    available_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (budget_id) REFERENCES budget(budget_id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES account(account_id)
);

COMMENT ON TABLE budget_item IS '预算明细表';

-- 21. 资金调拨单主表 (fund_transfer)
CREATE TABLE fund_transfer (
    transfer_id BIGSERIAL PRIMARY KEY,
    transfer_no VARCHAR(50) NOT NULL UNIQUE,
    transfer_date DATE NOT NULL,
    from_bank_account VARCHAR(50) NOT NULL,
    from_account_name VARCHAR(200),
    to_bank_account VARCHAR(50) NOT NULL,
    to_account_name VARCHAR(200),
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    transfer_fee NUMERIC(18,2) DEFAULT 0.00,
    reason VARCHAR(500),
    handler_id BIGINT,
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (handler_id) REFERENCES employee(employee_id)
);

COMMENT ON TABLE fund_transfer IS '资金调拨单主表';

-- 22. 往来核销记录表 (writeoff_record)
CREATE TABLE writeoff_record (
    record_id BIGSERIAL PRIMARY KEY,
    writeoff_date DATE NOT NULL,
    writeoff_type VARCHAR(20) NOT NULL,
    bill_type VARCHAR(20) NOT NULL,
    bill_id BIGINT NOT NULL,
    bill_no VARCHAR(50) NOT NULL,
    bill_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    writeoff_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    counterparty_type VARCHAR(20) NOT NULL,
    counterparty_id BIGINT NOT NULL,
    counterparty_name VARCHAR(200) NOT NULL,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50)
);

COMMENT ON TABLE writeoff_record IS '往来核销记录表';

-- 24. 银行账户表 (bank_account)
CREATE TABLE bank_account (
    account_id BIGSERIAL PRIMARY KEY,
    account_no VARCHAR(50) NOT NULL UNIQUE,
    account_name VARCHAR(200) NOT NULL,
    bank_name VARCHAR(200) NOT NULL,
    account_type VARCHAR(20) NOT NULL DEFAULT '基本户',
    currency_id BIGINT NOT NULL,
    opening_balance NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    current_balance NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    is_default SMALLINT NOT NULL DEFAULT 0,
    is_active SMALLINT NOT NULL DEFAULT 1,
    remark VARCHAR(500),
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

COMMENT ON TABLE bank_account IS '银行账户';

-- 创建索引
CREATE INDEX idx_ar_no ON account_receivable(ar_no);
CREATE INDEX idx_ar_date ON account_receivable(ar_date);
CREATE INDEX idx_ar_customer ON account_receivable(customer_id);
CREATE INDEX idx_ar_item ON account_receivable_item(ar_id);
CREATE INDEX idx_ap_no ON account_payable(ap_no);
CREATE INDEX idx_ap_date ON account_payable(ap_date);
CREATE INDEX idx_ap_supplier ON account_payable(supplier_id);
CREATE INDEX idx_ap_item ON account_payable_item(ap_id);
CREATE INDEX idx_cash_receipt_no ON cash_receipt(receipt_no);
CREATE INDEX idx_cash_receipt_date ON cash_receipt(receipt_date);
CREATE INDEX idx_cash_receipt_customer ON cash_receipt(customer_id);
CREATE INDEX idx_cash_receipt_writeoff ON cash_receipt_writeoff(receipt_id);
CREATE INDEX idx_cash_payment_no ON cash_payment(payment_no);
CREATE INDEX idx_cash_payment_date ON cash_payment(payment_date);
CREATE INDEX idx_cash_payment_supplier ON cash_payment(supplier_id);
CREATE INDEX idx_cash_payment_writeoff ON cash_payment_writeoff(payment_id);
CREATE INDEX idx_cash_receipt_refund_no ON cash_receipt_refund(refund_no);
CREATE INDEX idx_cash_payment_refund_no ON cash_payment_refund(refund_no);
CREATE INDEX idx_cost_calculation_no ON cost_calculation(calc_no);
CREATE INDEX idx_cost_calculation_date ON cost_calculation(calc_date);
CREATE INDEX idx_cost_calculation_item ON cost_calculation_item(calc_id);
CREATE INDEX idx_cost_period ON cost_period(period_year, period_month);
CREATE INDEX idx_fixed_asset_code ON fixed_asset(asset_code);
CREATE INDEX idx_fixed_asset_type ON fixed_asset(asset_type);
CREATE INDEX idx_asset_depreciation ON asset_depreciation(asset_id);
CREATE INDEX idx_budget_code ON budget(budget_code);
CREATE INDEX idx_budget_period ON budget(period_year, period_month);
CREATE INDEX idx_budget_item ON budget_item(budget_id);
CREATE INDEX idx_fund_transfer_no ON fund_transfer(transfer_no);
CREATE INDEX idx_fund_transfer_date ON fund_transfer(transfer_date);
CREATE INDEX idx_writeoff_record_date ON writeoff_record(writeoff_date);
CREATE INDEX idx_bank_account_no ON bank_account(account_no);

-- ============================================================================
-- 财务管理模块建表完成
-- ============================================================================
