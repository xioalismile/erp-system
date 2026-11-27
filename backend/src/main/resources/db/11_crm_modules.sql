-- ===========================================================================
-- CRM模块 - 5张业务表 (PostgreSQL)
-- 文档：第七部分 7.2-7.6
--   7.2 销售线索管理 crm_lead
--   7.3 商机管理     crm_opportunity
--   7.4 客户跟进记录 crm_follow_up
--   7.5 客户投诉管理 crm_complaint
--   7.6 客户满意度调查 crm_survey
-- 编码规则：L-/OPP-/FU-/C-/SUR- + yyyyMMdd + 4位流水号 (分隔符为连字符)
-- ===========================================================================

-- ===========================================================================
-- 7.2 销售线索管理表 crm_lead (30字段)
-- 状态流转：新线索 → 跟进中 → 已转化 → 已放弃
-- ===========================================================================
CREATE TABLE crm_lead (
    lead_id BIGSERIAL PRIMARY KEY,
    lead_no VARCHAR(50) NOT NULL UNIQUE,
    lead_name VARCHAR(200) NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    contact_person VARCHAR(50) NOT NULL,
    contact_phone VARCHAR(20),
    contact_email VARCHAR(200),
    contact_position VARCHAR(50),
    source_type VARCHAR(30) NOT NULL,
    source_detail VARCHAR(200),
    lead_type VARCHAR(30) NOT NULL DEFAULT '采购意向',
    industry VARCHAR(50),
    company_scale VARCHAR(30),
    demand_desc VARCHAR(1000),
    estimated_amount NUMERIC(18,2) DEFAULT 0.00,
    intention_level VARCHAR(10) NOT NULL DEFAULT 'C',
    lead_status VARCHAR(20) NOT NULL DEFAULT '新线索',
    assigned_sales_id BIGINT,
    assigned_date DATE NOT NULL DEFAULT CURRENT_DATE,
    follow_up_count INT DEFAULT 0,
    last_follow_up_date DATE,
    next_follow_up_date DATE,
    converted_customer_id BIGINT,
    converted_date DATE,
    abandon_reason VARCHAR(500),
    remark VARCHAR(500),
    creator_id BIGINT,
    create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifier_id BIGINT,
    modify_date TIMESTAMP,
    FOREIGN KEY (assigned_sales_id) REFERENCES employee(employee_id) ON DELETE SET NULL,
    FOREIGN KEY (converted_customer_id) REFERENCES customer(customer_id) ON DELETE SET NULL
);
COMMENT ON TABLE crm_lead IS '销售线索管理';

-- ===========================================================================
-- 7.3 商机管理表 crm_opportunity (31字段)
-- 状态流转：初步接触 → 需求确认 → 方案报价 → 商务谈判 → 成交 → 已关闭
-- ===========================================================================
CREATE TABLE crm_opportunity (
    opportunity_id BIGSERIAL PRIMARY KEY,
    opportunity_no VARCHAR(50) NOT NULL UNIQUE,
    opportunity_name VARCHAR(200) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    product_category VARCHAR(100),
    product_detail VARCHAR(500),
    estimated_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    probability INT NOT NULL DEFAULT 10,
    expected_close_date DATE NOT NULL,
    actual_close_date DATE,
    sales_stage VARCHAR(30) NOT NULL DEFAULT '初步接触',
    stage_update_date DATE,
    competitor VARCHAR(200),
    win_reason VARCHAR(500),
    lose_reason VARCHAR(500),
    source_lead_id BIGINT,
    assigned_sales_id BIGINT,
    assigned_dept_id BIGINT,
    follow_up_count INT DEFAULT 0,
    last_follow_up_date DATE,
    next_follow_up_date DATE,
    is_closed SMALLINT NOT NULL DEFAULT 0,
    close_date DATE,
    remark VARCHAR(500),
    creator_id BIGINT,
    create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifier_id BIGINT,
    modify_date TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE,
    FOREIGN KEY (source_lead_id) REFERENCES crm_lead(lead_id) ON DELETE SET NULL,
    FOREIGN KEY (assigned_sales_id) REFERENCES employee(employee_id) ON DELETE SET NULL
);
COMMENT ON TABLE crm_opportunity IS '商机管理';

-- ===========================================================================
-- 7.4 客户跟进记录表 crm_follow_up (19字段)
-- 来源类型：客户/线索/商机
-- ===========================================================================
CREATE TABLE crm_follow_up (
    follow_up_id BIGSERIAL PRIMARY KEY,
    follow_up_no VARCHAR(50) NOT NULL UNIQUE,
    source_type VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    source_name VARCHAR(200),
    follow_up_date DATE NOT NULL DEFAULT CURRENT_DATE,
    follow_up_type VARCHAR(20) NOT NULL,
    contact_person VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    summary VARCHAR(500),
    result VARCHAR(200),
    next_plan VARCHAR(500),
    next_follow_up_date DATE,
    important_flag SMALLINT NOT NULL DEFAULT 0,
    attachment VARCHAR(500),
    follow_up_staff_id BIGINT,
    remark VARCHAR(500),
    creator_id BIGINT,
    create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE crm_follow_up IS '客户跟进记录';

-- ===========================================================================
-- 7.5 客户投诉管理表 crm_complaint (24字段)
-- 状态流转：新建 → 处理中 → 已解决 → 已关闭
-- ===========================================================================
CREATE TABLE crm_complaint (
    complaint_id BIGSERIAL PRIMARY KEY,
    complaint_no VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200),
    contact_person VARCHAR(50) NOT NULL,
    contact_phone VARCHAR(20),
    complaint_date DATE NOT NULL DEFAULT CURRENT_DATE,
    complaint_type VARCHAR(30) NOT NULL,
    complaint_channel VARCHAR(20) NOT NULL,
    complaint_content TEXT NOT NULL,
    severity_level VARCHAR(10) NOT NULL DEFAULT '中',
    assigned_handler_id BIGINT,
    assigned_date DATE NOT NULL DEFAULT CURRENT_DATE,
    handle_status VARCHAR(20) NOT NULL DEFAULT '新建',
    handle_result VARCHAR(500),
    handle_date DATE,
    compensation_amount NUMERIC(18,2) DEFAULT 0.00,
    satisfaction_score INT DEFAULT 0,
    close_date DATE,
    remark VARCHAR(500),
    creator_id BIGINT,
    create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifier_id BIGINT,
    modify_date TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE,
    FOREIGN KEY (assigned_handler_id) REFERENCES employee(employee_id) ON DELETE SET NULL
);
COMMENT ON TABLE crm_complaint IS '客户投诉管理';

-- ===========================================================================
-- 7.6 客户满意度调查表 crm_survey (24字段)
-- 状态流转：草稿 → 进行中 → 已完成 → 已分析
-- ===========================================================================
CREATE TABLE crm_survey (
    survey_id BIGSERIAL PRIMARY KEY,
    survey_no VARCHAR(50) NOT NULL UNIQUE,
    survey_name VARCHAR(200) NOT NULL,
    survey_type VARCHAR(20) NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200),
    contact_person VARCHAR(50) NOT NULL,
    survey_date DATE NOT NULL DEFAULT CURRENT_DATE,
    survey_method VARCHAR(20) NOT NULL,
    overall_score INT NOT NULL DEFAULT 0,
    product_score INT DEFAULT 0,
    service_score INT DEFAULT 0,
    price_score INT DEFAULT 0,
    delivery_score INT DEFAULT 0,
    after_sales_score INT DEFAULT 0,
    praise_content TEXT,
    criticism_content TEXT,
    improvement_plan TEXT,
    status VARCHAR(20) NOT NULL DEFAULT '草稿',
    analyze_result VARCHAR(500),
    remark VARCHAR(500),
    handler_id BIGINT,
    creator_id BIGINT,
    create_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE,
    FOREIGN KEY (handler_id) REFERENCES employee(employee_id) ON DELETE SET NULL
);
COMMENT ON TABLE crm_survey IS '客户满意度调查';

-- ===========================================================================
-- 索引
-- ===========================================================================
CREATE INDEX idx_crm_lead_status ON crm_lead(lead_status);
CREATE INDEX idx_crm_lead_intention ON crm_lead(intention_level);
CREATE INDEX idx_crm_lead_assigned ON crm_lead(assigned_sales_id);
CREATE INDEX idx_crm_lead_next_follow ON crm_lead(next_follow_up_date);

CREATE INDEX idx_crm_opp_customer ON crm_opportunity(customer_id);
CREATE INDEX idx_crm_opp_stage ON crm_opportunity(sales_stage);
CREATE INDEX idx_crm_opp_closed ON crm_opportunity(is_closed);
CREATE INDEX idx_crm_opp_assigned ON crm_opportunity(assigned_sales_id);
CREATE INDEX idx_crm_opp_expected_close ON crm_opportunity(expected_close_date);

CREATE INDEX idx_crm_fu_source ON crm_follow_up(source_type, source_id);
CREATE INDEX idx_crm_fu_date ON crm_follow_up(follow_up_date);
CREATE INDEX idx_crm_fu_staff ON crm_follow_up(follow_up_staff_id);

CREATE INDEX idx_crm_complaint_customer ON crm_complaint(customer_id);
CREATE INDEX idx_crm_complaint_status ON crm_complaint(handle_status);
CREATE INDEX idx_crm_complaint_type ON crm_complaint(complaint_type);
CREATE INDEX idx_crm_complaint_severity ON crm_complaint(severity_level);

CREATE INDEX idx_crm_survey_customer ON crm_survey(customer_id);
CREATE INDEX idx_crm_survey_status ON crm_survey(status);
CREATE INDEX idx_crm_survey_date ON crm_survey(survey_date);

-- ===========================================================================
-- 7.7 CRM联系人管理表 crm_contact (对标钉钉天元CRM联系人模块)
-- 一个客户可拥有多个联系人，支持主联系人切换
-- 编号格式: CT-yyyyMMdd-0001
-- ===========================================================================
CREATE TABLE crm_contact (
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
    modify_date TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE
);
COMMENT ON TABLE crm_contact IS 'CRM联系人管理';

-- ===========================================================================
-- 7.8 CRM报价单表 crm_quote (商机阶段简化报价, 与销售模块 sale_quote 区分)
-- 状态流转：草稿 → 已发送 → 已接受 / 已拒绝 / 已过期
-- 编号格式: QUT-yyyyMMdd-0001
-- ===========================================================================
CREATE TABLE crm_quote (
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
    modify_date TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE,
    FOREIGN KEY (opportunity_id) REFERENCES crm_opportunity(opportunity_id) ON DELETE SET NULL,
    FOREIGN KEY (contact_id) REFERENCES crm_contact(contact_id) ON DELETE SET NULL,
    FOREIGN KEY (assigned_sales_id) REFERENCES employee(employee_id) ON DELETE SET NULL
);
COMMENT ON TABLE crm_quote IS 'CRM报价单(商机阶段)';

-- ===========================================================================
-- 7.9 CRM合同表 crm_contract (商机阶段简化合同, 与销售模块 sale_contract 区分)
-- 状态流转：草稿 → 审批中 → 已签订 → 执行中 → 已完成 / 已终止
-- 编号格式: HT-yyyyMMdd-0001 (合同=HeTong)
-- ===========================================================================
CREATE TABLE crm_contract (
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
    modify_date TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE,
    FOREIGN KEY (opportunity_id) REFERENCES crm_opportunity(opportunity_id) ON DELETE SET NULL,
    FOREIGN KEY (quote_id) REFERENCES crm_quote(quote_id) ON DELETE SET NULL,
    FOREIGN KEY (contact_id) REFERENCES crm_contact(contact_id) ON DELETE SET NULL,
    FOREIGN KEY (assigned_sales_id) REFERENCES employee(employee_id) ON DELETE SET NULL
);
COMMENT ON TABLE crm_contract IS 'CRM合同(商机阶段)';

-- ===========================================================================
-- 7.10 CRM回款表 crm_payment (关联CRM合同的回款计划/记录)
-- 状态流转：待回款 → 部分回款 / 已回款 / 已逾期
-- 编号格式: PAY-yyyyMMdd-0001
-- ===========================================================================
CREATE TABLE crm_payment (
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
    modify_date TIMESTAMP,
    FOREIGN KEY (contract_id) REFERENCES crm_contract(contract_id) ON DELETE CASCADE,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE SET NULL,
    FOREIGN KEY (opportunity_id) REFERENCES crm_opportunity(opportunity_id) ON DELETE SET NULL,
    FOREIGN KEY (assigned_sales_id) REFERENCES employee(employee_id) ON DELETE SET NULL
);
COMMENT ON TABLE crm_payment IS 'CRM回款记录';

-- ===========================================================================
-- 新增索引
-- ===========================================================================
CREATE INDEX idx_crm_contact_customer ON crm_contact(customer_id);
CREATE INDEX idx_crm_contact_primary ON crm_contact(is_primary);

CREATE INDEX idx_crm_quote_customer ON crm_quote(customer_id);
CREATE INDEX idx_crm_quote_opportunity ON crm_quote(opportunity_id);
CREATE INDEX idx_crm_quote_status ON crm_quote(status);

CREATE INDEX idx_crm_contract_customer ON crm_contract(customer_id);
CREATE INDEX idx_crm_contract_opportunity ON crm_contract(opportunity_id);
CREATE INDEX idx_crm_contract_status ON crm_contract(status);

CREATE INDEX idx_crm_payment_contract ON crm_payment(contract_id);
CREATE INDEX idx_crm_payment_customer ON crm_payment(customer_id);
CREATE INDEX idx_crm_payment_status ON crm_payment(status);
CREATE INDEX idx_crm_payment_plan_date ON crm_payment(plan_date);
