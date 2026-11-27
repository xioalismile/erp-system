-- CRM 新增模块建表脚本 (7.7-7.10)
-- 仅创建 crm_contact, crm_quote, crm_contract, crm_payment 四张新表
-- 使用 CREATE TABLE IF NOT EXISTS 避免重复创建

-- 7.7 CRM联系人管理表
CREATE TABLE IF NOT EXISTS crm_contact (
    contact_id BIGSERIAL PRIMARY KEY,
    contact_no VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200),
    contact_name VARCHAR(50) NOT NULL,
    gender VARCHAR(10),
    position VARCHAR(50),
    department VARCHAR(50),
    mobile VARCHAR(20),
    phone VARCHAR(20),
    email VARCHAR(200),
    wechat VARCHAR(50),
    address VARCHAR(500),
    is_primary SMALLINT NOT NULL DEFAULT 0,
    birthday DATE,
    hobby VARCHAR(200),
    remark VARCHAR(500),
    creator_id BIGINT,
    create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifier_id BIGINT,
    modify_date TIMESTAMP
);
COMMENT ON TABLE crm_contact IS 'CRM联系人管理';

-- 7.8 CRM报价单表
CREATE TABLE IF NOT EXISTS crm_quote (
    quote_id BIGSERIAL PRIMARY KEY,
    quote_no VARCHAR(50) NOT NULL UNIQUE,
    opportunity_id BIGINT,
    opportunity_name VARCHAR(200),
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200),
    contact_id BIGINT,
    contact_person VARCHAR(50),
    subject VARCHAR(200) NOT NULL,
    quote_date DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_until DATE,
    total_amount NUMERIC(18,2) DEFAULT 0.00,
    discount_rate NUMERIC(5,2) DEFAULT 0.00,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    final_amount NUMERIC(18,2) DEFAULT 0.00,
    quote_content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT '草稿',
    send_date DATE,
    accept_date DATE,
    reject_reason VARCHAR(500),
    attached_file VARCHAR(500),
    assigned_sales_id BIGINT,
    remark VARCHAR(500),
    creator_id BIGINT,
    create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifier_id BIGINT,
    modify_date TIMESTAMP
);
COMMENT ON TABLE crm_quote IS 'CRM报价单(商机阶段)';

-- 7.9 CRM合同表
CREATE TABLE IF NOT EXISTS crm_contract (
    contract_id BIGSERIAL PRIMARY KEY,
    contract_no VARCHAR(50) NOT NULL UNIQUE,
    contract_name VARCHAR(200) NOT NULL,
    opportunity_id BIGINT,
    opportunity_name VARCHAR(200),
    quote_id BIGINT,
    quote_no VARCHAR(50),
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200),
    contact_id BIGINT,
    contact_person VARCHAR(50),
    contract_type VARCHAR(30),
    sign_date DATE,
    start_date DATE,
    end_date DATE,
    total_amount NUMERIC(18,2) DEFAULT 0.00,
    paid_amount NUMERIC(18,2) DEFAULT 0.00,
    unpaid_amount NUMERIC(18,2) DEFAULT 0.00,
    payment_terms VARCHAR(200),
    our_signer VARCHAR(50),
    customer_signer VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT '草稿',
    attached_file VARCHAR(500),
    assigned_sales_id BIGINT,
    remark VARCHAR(500),
    creator_id BIGINT,
    create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifier_id BIGINT,
    modify_date TIMESTAMP
);
COMMENT ON TABLE crm_contract IS 'CRM合同(商机阶段)';

-- 7.10 CRM回款表
CREATE TABLE IF NOT EXISTS crm_payment (
    payment_id BIGSERIAL PRIMARY KEY,
    payment_no VARCHAR(50) NOT NULL UNIQUE,
    contract_id BIGINT NOT NULL,
    contract_no VARCHAR(50),
    customer_id BIGINT,
    customer_name VARCHAR(200),
    opportunity_id BIGINT,
    payment_stage VARCHAR(50),
    stage_seq INT,
    plan_date DATE,
    actual_date DATE,
    plan_amount NUMERIC(18,2) DEFAULT 0.00,
    actual_amount NUMERIC(18,2) DEFAULT 0.00,
    payment_method VARCHAR(30),
    payment_account VARCHAR(100),
    invoice_no VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT '待回款',
    assigned_sales_id BIGINT,
    remark VARCHAR(500),
    creator_id BIGINT,
    create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifier_id BIGINT,
    modify_date TIMESTAMP
);
COMMENT ON TABLE crm_payment IS 'CRM回款记录';

-- 索引 (使用 IF NOT EXISTS)
CREATE INDEX IF NOT EXISTS idx_crm_contact_customer ON crm_contact(customer_id);
CREATE INDEX IF NOT EXISTS idx_crm_contact_primary ON crm_contact(is_primary);
CREATE INDEX IF NOT EXISTS idx_crm_quote_customer ON crm_quote(customer_id);
CREATE INDEX IF NOT EXISTS idx_crm_quote_opportunity ON crm_quote(opportunity_id);
CREATE INDEX IF NOT EXISTS idx_crm_quote_status ON crm_quote(status);
CREATE INDEX IF NOT EXISTS idx_crm_contract_customer ON crm_contract(customer_id);
CREATE INDEX IF NOT EXISTS idx_crm_contract_opportunity ON crm_contract(opportunity_id);
CREATE INDEX IF NOT EXISTS idx_crm_contract_status ON crm_contract(status);
CREATE INDEX IF NOT EXISTS idx_crm_payment_contract ON crm_payment(contract_id);
CREATE INDEX IF NOT EXISTS idx_crm_payment_customer ON crm_payment(customer_id);
CREATE INDEX IF NOT EXISTS idx_crm_payment_status ON crm_payment(status);
CREATE INDEX IF NOT EXISTS idx_crm_payment_plan_date ON crm_payment(plan_date);
