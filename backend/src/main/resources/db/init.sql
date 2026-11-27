-- =====================================================
-- 智能ERP系统 数据库初始化脚本（完整版）
-- 数据库: PostgreSQL 15+
-- 说明: 合并 database/schema/01-07 + 00_init.sql 初始化数据
--       可独立执行，无需 \i 命令
-- 使用方法:
--   1. 先在 PG 里手动执行:
--        CREATE USER erp_user WITH PASSWORD 'Erp@2026' CREATEDB;
--        CREATE DATABASE erp_db OWNER erp_user ENCODING 'UTF8';
--        -- 已存在 erp_user 时用这条幂等补齐: ALTER USER erp_user CREATEDB;
--        -- CREATEDB 必须带：创建租户要用 CREATE DATABASE ... TEMPLATE 从 erp_tenant_template 克隆独立库
--   2. 然后用 psql 执行本文件:
--        psql -U postgres -h localhost -d erp_db -f init.sql
--   3. 完成后用 admin / admin123 登录系统
-- =====================================================

-- 授权 erp_user 操作 public schema
GRANT ALL ON SCHEMA public TO erp_user;

-- ============================================================================
-- 模块1: 基础资料 (01_basics.sql)
-- ============================================================================

-- 1.1 计量单位表
CREATE TABLE unit_of_measure (
    unit_id BIGSERIAL PRIMARY KEY,
    unit_code VARCHAR(20) NOT NULL UNIQUE,
    unit_name VARCHAR(50) NOT NULL,
    unit_group VARCHAR(50),
    is_base_unit SMALLINT NOT NULL DEFAULT 0,
    conversion_rate NUMERIC(18,6) NOT NULL DEFAULT 1.000000,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);
COMMENT ON TABLE unit_of_measure IS '计量单位档案';

-- 1.2 物料分类表
CREATE TABLE material_category (
    category_id BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(50) NOT NULL UNIQUE,
    category_name VARCHAR(100) NOT NULL,
    parent_id BIGINT,
    category_level INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (parent_id) REFERENCES material_category(category_id)
);
COMMENT ON TABLE material_category IS '物料分类档案';

-- 1.3 物料档案表
CREATE TABLE material (
    material_id BIGSERIAL PRIMARY KEY,
    material_code VARCHAR(50) NOT NULL UNIQUE,
    material_name VARCHAR(200) NOT NULL,
    full_name VARCHAR(500),
    model VARCHAR(100),
    material_type VARCHAR(20) NOT NULL DEFAULT '自制件',
    category_id BIGINT,
    base_unit_id BIGINT NOT NULL,
    purchase_unit_id BIGINT,
    sale_unit_id BIGINT,
    stock_unit_id BIGINT,
    production_unit_id BIGINT,
    unit_conversion NUMERIC(18,6) NOT NULL DEFAULT 1.000000,
    is_lot_control SMALLINT NOT NULL DEFAULT 0,
    is_sn_control SMALLINT NOT NULL DEFAULT 0,
    is_bin_control SMALLINT NOT NULL DEFAULT 0,
    is_qc_required SMALLINT NOT NULL DEFAULT 0,
    is_expiry_control SMALLINT NOT NULL DEFAULT 0,
    expiry_days INT,
    sale_price NUMERIC(18,4) DEFAULT 0.0000,
    purchase_price NUMERIC(18,4) DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    min_stock NUMERIC(18,4) DEFAULT 0.0000,
    max_stock NUMERIC(18,4) DEFAULT 0.0000,
    status VARCHAR(10) NOT NULL DEFAULT '正常',
    image_urls VARCHAR(2000),
    main_image VARCHAR(500),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (category_id) REFERENCES material_category(category_id),
    FOREIGN KEY (base_unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (purchase_unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (sale_unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (production_unit_id) REFERENCES unit_of_measure(unit_id)
);
COMMENT ON TABLE material IS '物料档案';

-- 1.4 部门档案表
CREATE TABLE department (
    dept_id BIGSERIAL PRIMARY KEY,
    dept_code VARCHAR(50) NOT NULL UNIQUE,
    dept_name VARCHAR(100) NOT NULL,
    parent_dept_id BIGINT,
    dept_type VARCHAR(20) NOT NULL DEFAULT '职能部门',
    manager_id BIGINT,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (parent_dept_id) REFERENCES department(dept_id)
);
COMMENT ON TABLE department IS '部门档案';

-- 1.5 员工档案表
CREATE TABLE employee (
    employee_id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(50) NOT NULL UNIQUE,
    employee_name VARCHAR(100) NOT NULL,
    dept_id BIGINT NOT NULL,
    position VARCHAR(50),
    gender VARCHAR(2),
    mobile_phone VARCHAR(20),
    email VARCHAR(200),
    entry_date DATE,
    user_id BIGINT,
    status VARCHAR(10) NOT NULL DEFAULT '在职',
    leave_date DATE,
    education VARCHAR(50),
    id_card VARCHAR(20),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (dept_id) REFERENCES department(dept_id)
);
COMMENT ON TABLE employee IS '员工档案';

-- 1.6 仓库档案表
CREATE TABLE warehouse (
    stock_id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(50) NOT NULL UNIQUE,
    stock_name VARCHAR(100) NOT NULL,
    stock_type VARCHAR(20) NOT NULL DEFAULT '普通仓库',
    address VARCHAR(500),
    manager_id BIGINT,
    is_bin_control SMALLINT NOT NULL DEFAULT 0,
    is_mrp_available SMALLINT NOT NULL DEFAULT 1,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (manager_id) REFERENCES employee(employee_id)
);
COMMENT ON TABLE warehouse IS '仓库档案';

-- 1.7 货位档案表
CREATE TABLE location (
    bin_id BIGSERIAL PRIMARY KEY,
    bin_code VARCHAR(50) NOT NULL UNIQUE,
    bin_name VARCHAR(100) NOT NULL,
    stock_id BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id)
);
COMMENT ON TABLE location IS '货位档案';

-- 1.8 币种表
CREATE TABLE currency (
    currency_id BIGSERIAL PRIMARY KEY,
    currency_code VARCHAR(10) NOT NULL UNIQUE,
    currency_name VARCHAR(50) NOT NULL,
    currency_symbol VARCHAR(10),
    is_base_currency SMALLINT NOT NULL DEFAULT 0,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);
COMMENT ON TABLE currency IS '币种档案';

-- 1.9 会计科目表
CREATE TABLE account (
    account_id BIGSERIAL PRIMARY KEY,
    account_code VARCHAR(50) NOT NULL UNIQUE,
    account_name VARCHAR(200) NOT NULL,
    account_full_name VARCHAR(500),
    parent_account_id BIGINT,
    account_type VARCHAR(10) NOT NULL,
    balance_direction VARCHAR(2) NOT NULL DEFAULT '借',
    account_level INT NOT NULL DEFAULT 1,
    is_leaf SMALLINT NOT NULL DEFAULT 1,
    is_cash_account SMALLINT NOT NULL DEFAULT 0,
    is_qc_required VARCHAR(200),
    is_qc_required_quantity SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (parent_account_id) REFERENCES account(account_id)
);
COMMENT ON TABLE account IS '会计科目档案';

-- 1.10 结算方式表
CREATE TABLE settlement_mode (
    settle_mode_id BIGSERIAL PRIMARY KEY,
    settle_mode_code VARCHAR(20) NOT NULL UNIQUE,
    settle_mode_name VARCHAR(50) NOT NULL,
    settle_type VARCHAR(10) NOT NULL DEFAULT '通用',
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);
COMMENT ON TABLE settlement_mode IS '结算方式档案';

-- 1.11 付款条件表
CREATE TABLE payment_term (
    payment_term_id BIGSERIAL PRIMARY KEY,
    payment_term_code VARCHAR(20) NOT NULL UNIQUE,
    payment_term_name VARCHAR(100) NOT NULL,
    payment_days INT NOT NULL DEFAULT 0,
    discount_days INT DEFAULT 0,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    advance_rate NUMERIC(8,4) DEFAULT 0.0000,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);
COMMENT ON TABLE payment_term IS '付款条件档案';

-- 1.12 价格等级表
CREATE TABLE price_level (
    price_level_id BIGSERIAL PRIMARY KEY,
    price_level_code VARCHAR(20) NOT NULL UNIQUE,
    price_level_name VARCHAR(50) NOT NULL,
    price_adjust_rate NUMERIC(8,4) NOT NULL DEFAULT 0.0000,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);
COMMENT ON TABLE price_level IS '价格等级档案';

-- 1.13 客户档案表
CREATE TABLE customer (
    customer_id BIGSERIAL PRIMARY KEY,
    customer_code VARCHAR(50) NOT NULL UNIQUE,
    customer_name VARCHAR(200) NOT NULL,
    short_name VARCHAR(100),
    customer_type VARCHAR(20) NOT NULL DEFAULT '企业客户',
    customer_level VARCHAR(20) NOT NULL DEFAULT '普通客户',
    tax_no VARCHAR(50),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    email VARCHAR(200),
    address VARCHAR(500),
    bank_name VARCHAR(200),
    bank_account VARCHAR(50),
    credit_limit NUMERIC(18,2) DEFAULT 0.00,
    payment_term_id BIGINT,
    currency_id BIGINT,
    default_saler_id BIGINT,
    default_price_level_id BIGINT,
    status VARCHAR(10) NOT NULL DEFAULT '正常',
    tax_register_no VARCHAR(50),
    legal_person VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (payment_term_id) REFERENCES payment_term(payment_term_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (default_saler_id) REFERENCES employee(employee_id)
);
COMMENT ON TABLE customer IS '客户档案';

-- 1.14 供应商档案表
CREATE TABLE supplier (
    supplier_id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(50) NOT NULL UNIQUE,
    supplier_name VARCHAR(200) NOT NULL,
    short_name VARCHAR(100),
    supplier_type VARCHAR(20) NOT NULL DEFAULT '一般供应商',
    supplier_level VARCHAR(20) NOT NULL DEFAULT 'C级',
    tax_no VARCHAR(50),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    email VARCHAR(200),
    address VARCHAR(500),
    bank_name VARCHAR(200),
    bank_account VARCHAR(50),
    payment_term_id BIGINT,
    currency_id BIGINT,
    default_buyer_id BIGINT,
    delivery_days INT DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT '正常',
    legal_person VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (payment_term_id) REFERENCES payment_term(payment_term_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (default_buyer_id) REFERENCES employee(employee_id)
);
COMMENT ON TABLE supplier IS '供应商档案';

CREATE INDEX idx_material_code ON material(material_code);
CREATE INDEX idx_material_category ON material(category_id);
CREATE INDEX idx_department_code ON department(dept_code);
CREATE INDEX idx_employee_code ON employee(employee_code);
CREATE INDEX idx_warehouse_code ON warehouse(stock_code);
CREATE INDEX idx_location_code ON location(bin_code);
CREATE INDEX idx_location_stock ON location(stock_id);
CREATE INDEX idx_customer_code ON customer(customer_code);
CREATE INDEX idx_supplier_code ON supplier(supplier_code);
CREATE INDEX idx_currency_code ON currency(currency_code);
CREATE INDEX idx_account_code ON account(account_code);

-- ============================================================================
-- 模块2: 销售管理 (02_sales.sql)
-- ============================================================================

CREATE TABLE sale_quote (
    quote_id BIGSERIAL PRIMARY KEY,
    quote_no VARCHAR(50) NOT NULL UNIQUE,
    quote_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    saler_id BIGINT,
    price_level_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    delivery_date DATE,
    payment_term_id BIGINT,
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    source_bill_no VARCHAR(50),
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
    FOREIGN KEY (saler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (price_level_id) REFERENCES price_level(price_level_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (payment_term_id) REFERENCES payment_term(payment_term_id)
);
COMMENT ON TABLE sale_quote IS '销售报价单主表';

CREATE TABLE sale_quote_item (
    quote_item_id BIGSERIAL PRIMARY KEY,
    quote_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    delivery_date DATE,
    delivery_days INT DEFAULT 0,
    source_line_no INT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (quote_id) REFERENCES sale_quote(quote_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

CREATE TABLE sale_order (
    order_id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    order_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    saler_id BIGINT,
    price_level_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    delivery_date DATE,
    payment_term_id BIGINT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    quote_no VARCHAR(50),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    closed_by VARCHAR(50),
    closed_at TIMESTAMP,
    closed_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    FOREIGN KEY (saler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (price_level_id) REFERENCES price_level(price_level_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (payment_term_id) REFERENCES payment_term(payment_term_id)
);
COMMENT ON TABLE sale_order IS '销售订单主表';

CREATE TABLE sale_order_item (
    order_item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    delivered_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    delivery_date DATE,
    delivery_days INT DEFAULT 0,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    delivered_line_no INT,
    remark VARCHAR(500),
    avg_cost NUMERIC(18,4) DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (order_id) REFERENCES sale_order(order_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

CREATE TABLE sale_delivery (
    delivery_id BIGSERIAL PRIMARY KEY,
    delivery_no VARCHAR(50) NOT NULL UNIQUE,
    delivery_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    warehouse_id BIGINT NOT NULL,
    saler_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    source_bill_type VARCHAR(50) NOT NULL,
    source_bill_no VARCHAR(50) NOT NULL,
    order_no VARCHAR(50),
    invoice_no VARCHAR(50),
    express_company VARCHAR(100),
    express_no VARCHAR(100),
    contact_address VARCHAR(500),
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
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (saler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE sale_delivery_item (
    delivery_item_id BIGSERIAL PRIMARY KEY,
    delivery_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    batch_no VARCHAR(50),
    production_date DATE,
    expiry_date DATE,
    order_line_no INT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    remark VARCHAR(500),
    avg_cost NUMERIC(18,4) DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (delivery_id) REFERENCES sale_delivery(delivery_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id)
);

CREATE TABLE sale_return (
    return_id BIGSERIAL PRIMARY KEY,
    return_no VARCHAR(50) NOT NULL UNIQUE,
    return_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    warehouse_id BIGINT NOT NULL,
    saler_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    source_bill_type VARCHAR(50) NOT NULL,
    source_bill_no VARCHAR(50) NOT NULL,
    delivery_no VARCHAR(50),
    invoice_no VARCHAR(50),
    return_reason VARCHAR(200),
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
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (saler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE sale_return_item (
    return_item_id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    batch_no VARCHAR(50),
    delivery_line_no INT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    return_reason VARCHAR(200),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (return_id) REFERENCES sale_return(return_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id)
);

CREATE TABLE sale_invoice (
    invoice_id BIGSERIAL PRIMARY KEY,
    invoice_no VARCHAR(50) NOT NULL UNIQUE,
    invoice_date DATE NOT NULL,
    invoice_type VARCHAR(20) NOT NULL DEFAULT '增值税专用发票',
    customer_id BIGINT NOT NULL,
    tax_no VARCHAR(50),
    address VARCHAR(500),
    bank_name VARCHAR(200),
    bank_account VARCHAR(50),
    saler_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    delivery_no VARCHAR(50),
    tax_rate_avg NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
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
    FOREIGN KEY (saler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE sale_invoice_item (
    invoice_item_id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    delivery_line_no INT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (invoice_id) REFERENCES sale_invoice(invoice_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

CREATE INDEX idx_sale_quote_no ON sale_quote(quote_no);
CREATE INDEX idx_sale_quote_customer ON sale_quote(customer_id);
CREATE INDEX idx_sale_order_no ON sale_order(order_no);
CREATE INDEX idx_sale_order_customer ON sale_order(customer_id);
CREATE INDEX idx_sale_order_item ON sale_order_item(order_id);
CREATE INDEX idx_sale_delivery_no ON sale_delivery(delivery_no);
CREATE INDEX idx_sale_return_no ON sale_return(return_no);
CREATE INDEX idx_sale_invoice_no ON sale_invoice(invoice_no);

-- ============================================================================
-- 模块3: 采购管理 (03_purchase.sql)
-- ============================================================================

CREATE TABLE purchase_request (
    request_id BIGSERIAL PRIMARY KEY,
    request_no VARCHAR(50) NOT NULL UNIQUE,
    request_date DATE NOT NULL,
    department_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    buyer_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    delivery_date DATE,
    delivery_place VARCHAR(500),
    priority VARCHAR(10) NOT NULL DEFAULT '普通',
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
    FOREIGN KEY (department_id) REFERENCES department(dept_id),
    FOREIGN KEY (applicant_id) REFERENCES employee(employee_id),
    FOREIGN KEY (buyer_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE purchase_request_item (
    request_item_id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    supplier_id BIGINT,
    delivery_date DATE,
    delivery_place VARCHAR(500),
    requested_by VARCHAR(100),
    project_code VARCHAR(50),
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    purchase_quantity NUMERIC(18,4) DEFAULT 0.0000,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (request_id) REFERENCES purchase_request(request_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id)
);

CREATE TABLE purchase_order (
    order_id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    order_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL,
    supplier_name VARCHAR(200),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    department_id BIGINT,
    dept_name VARCHAR(100),
    buyer_id BIGINT,
    buyer_name VARCHAR(100),
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    prepayment_amount NUMERIC(18,2) DEFAULT 0.00,
    prepayment_ratio NUMERIC(5,2) DEFAULT 0.00,
    prepayment_date DATE,
    delivery_date DATE,
    delivery_place VARCHAR(500),
    payment_term_id BIGINT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    request_no VARCHAR(50),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(20) NOT NULL DEFAULT '待审核',
    is_closed SMALLINT NOT NULL DEFAULT 0,
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    closed_by VARCHAR(50),
    closed_at TIMESTAMP,
    closed_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id),
    FOREIGN KEY (buyer_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (payment_term_id) REFERENCES payment_term(payment_term_id)
);

CREATE TABLE purchase_order_item (
    order_item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    received_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    invoiced_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    stock_id BIGINT,
    delivery_date DATE,
    delivery_place VARCHAR(500),
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    request_line_no INT,
    remark VARCHAR(500),
    avg_cost NUMERIC(18,4) DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (order_id) REFERENCES purchase_order(order_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id)
);

CREATE TABLE purchase_receipt (
    receipt_id BIGSERIAL PRIMARY KEY,
    receipt_no VARCHAR(50) NOT NULL UNIQUE,
    receipt_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL,
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    warehouse_id BIGINT NOT NULL,
    buyer_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    invoice_no VARCHAR(50),
    order_no VARCHAR(50),
    delivery_no VARCHAR(50),
    is_return VARCHAR(2) NOT NULL DEFAULT '否',
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
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (buyer_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE purchase_receipt_item (
    receipt_item_id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    qualified_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    rejected_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    batch_no VARCHAR(50),
    production_date DATE,
    expiry_date DATE,
    order_line_no INT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    qc_status VARCHAR(10) NOT NULL DEFAULT '待检',
    remark VARCHAR(500),
    avg_cost NUMERIC(18,4) DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (receipt_id) REFERENCES purchase_receipt(receipt_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id)
);

CREATE TABLE purchase_return (
    return_id BIGSERIAL PRIMARY KEY,
    return_no VARCHAR(50) NOT NULL UNIQUE,
    return_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL,
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    warehouse_id BIGINT NOT NULL,
    buyer_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    source_bill_type VARCHAR(50) NOT NULL,
    source_bill_no VARCHAR(50) NOT NULL,
    receipt_no VARCHAR(50),
    invoice_no VARCHAR(50),
    return_reason VARCHAR(200),
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
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (buyer_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE purchase_return_item (
    return_item_id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    batch_no VARCHAR(50),
    receipt_line_no INT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    return_reason VARCHAR(200),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (return_id) REFERENCES purchase_return(return_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id)
);

CREATE TABLE purchase_invoice (
    invoice_id BIGSERIAL PRIMARY KEY,
    invoice_no VARCHAR(50) NOT NULL UNIQUE,
    invoice_date DATE NOT NULL,
    invoice_type VARCHAR(20) NOT NULL DEFAULT '增值税专用发票',
    supplier_id BIGINT NOT NULL,
    tax_no VARCHAR(50),
    address VARCHAR(500),
    bank_name VARCHAR(200),
    bank_account VARCHAR(50),
    buyer_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    receipt_no VARCHAR(50),
    tax_rate_avg NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    is_return VARCHAR(2) NOT NULL DEFAULT '否',
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
    FOREIGN KEY (buyer_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE purchase_invoice_item (
    invoice_item_id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    receipt_line_no INT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (invoice_id) REFERENCES purchase_invoice(invoice_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

CREATE INDEX idx_purchase_request_no ON purchase_request(request_no);
CREATE INDEX idx_purchase_order_no ON purchase_order(order_no);
CREATE INDEX idx_purchase_order_supplier ON purchase_order(supplier_id);
CREATE INDEX idx_purchase_order_item ON purchase_order_item(order_id);
CREATE INDEX idx_purchase_receipt_no ON purchase_receipt(receipt_no);
CREATE INDEX idx_purchase_return_no ON purchase_return(return_no);
CREATE INDEX idx_purchase_invoice_no ON purchase_invoice(invoice_no);

-- ============================================================================
-- 模块4: 库存管理 (04_inventory.sql)
-- ============================================================================

CREATE TABLE inventory_current (
    inventory_id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    material_id BIGINT NOT NULL,
    batch_no VARCHAR(50),
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    available_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    frozen_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    production_date DATE,
    expiry_date DATE,
    unit_cost NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    total_cost NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    last_in_date DATE,
    last_out_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    UNIQUE (stock_id, bin_id, material_id, batch_no),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id),
    FOREIGN KEY (material_id) REFERENCES material(material_id)
);
COMMENT ON TABLE inventory_current IS '即时库存表';

CREATE TABLE inventory_ledger (
    ledger_id BIGSERIAL PRIMARY KEY,
    trans_date DATE NOT NULL,
    trans_type VARCHAR(50) NOT NULL,
    trans_no VARCHAR(50) NOT NULL,
    line_no INT,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    material_id BIGINT NOT NULL,
    batch_no VARCHAR(50),
    in_quantity NUMERIC(18,4) DEFAULT 0.0000,
    in_amount NUMERIC(18,2) DEFAULT 0.00,
    out_quantity NUMERIC(18,4) DEFAULT 0.0000,
    out_amount NUMERIC(18,2) DEFAULT 0.00,
    balance_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    balance_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    unit_cost NUMERIC(18,4) DEFAULT 0.0000,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id),
    FOREIGN KEY (material_id) REFERENCES material(material_id)
);
COMMENT ON TABLE inventory_ledger IS '库存台账';

CREATE TABLE other_receipt (
    receipt_id BIGSERIAL PRIMARY KEY,
    receipt_no VARCHAR(50) NOT NULL UNIQUE,
    receipt_date DATE NOT NULL,
    receipt_type VARCHAR(20) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    handler_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
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
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (handler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE other_receipt_item (
    receipt_item_id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    batch_no VARCHAR(50),
    production_date DATE,
    expiry_date DATE,
    remark VARCHAR(500),
    avg_cost NUMERIC(18,4) DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (receipt_id) REFERENCES other_receipt(receipt_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id)
);

CREATE TABLE other_issue (
    issue_id BIGSERIAL PRIMARY KEY,
    issue_no VARCHAR(50) NOT NULL UNIQUE,
    issue_date DATE NOT NULL,
    issue_type VARCHAR(20) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    handler_id BIGINT,
    department_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
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
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (handler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE other_issue_item (
    issue_item_id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    batch_no VARCHAR(50),
    project_code VARCHAR(50),
    remark VARCHAR(500),
    avg_cost NUMERIC(18,4) DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (issue_id) REFERENCES other_issue(issue_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id)
);

CREATE TABLE material_requisition (
    requisition_id BIGSERIAL PRIMARY KEY,
    requisition_no VARCHAR(50) NOT NULL UNIQUE,
    bill_type VARCHAR(20) NOT NULL DEFAULT '领料',
    requisition_date DATE NOT NULL,
    work_order_no VARCHAR(50),
    department_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
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
    FOREIGN KEY (department_id) REFERENCES department(dept_id),
    FOREIGN KEY (employee_id) REFERENCES employee(employee_id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE material_requisition_item (
    requisition_item_id BIGSERIAL PRIMARY KEY,
    requisition_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    issued_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    batch_no VARCHAR(50),
    work_order_line_no INT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    remark VARCHAR(500),
    avg_cost NUMERIC(18,4) DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (requisition_id) REFERENCES material_requisition(requisition_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id)
);

CREATE TABLE stock_transfer (
    transfer_id BIGSERIAL PRIMARY KEY,
    transfer_no VARCHAR(50) NOT NULL UNIQUE,
    transfer_date DATE NOT NULL,
    from_stock_id BIGINT NOT NULL,
    to_stock_id BIGINT NOT NULL,
    handler_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    transfer_type VARCHAR(20) NOT NULL DEFAULT '同价调拨',
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
    FOREIGN KEY (from_stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (to_stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (handler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE stock_transfer_item (
    transfer_item_id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    from_stock_id BIGINT NOT NULL,
    from_bin_id BIGINT,
    to_stock_id BIGINT NOT NULL,
    to_bin_id BIGINT,
    batch_no VARCHAR(50),
    production_date DATE,
    expiry_date DATE,
    remark VARCHAR(500),
    avg_cost NUMERIC(18,4) DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (transfer_id) REFERENCES stock_transfer(transfer_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (from_stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (from_bin_id) REFERENCES location(bin_id),
    FOREIGN KEY (to_stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (to_bin_id) REFERENCES location(bin_id)
);

CREATE TABLE stock_check (
    check_id BIGSERIAL PRIMARY KEY,
    check_no VARCHAR(50) NOT NULL UNIQUE,
    check_date DATE NOT NULL,
    warehouse_id BIGINT NOT NULL,
    stock_type VARCHAR(20) NOT NULL DEFAULT '动态盘点',
    handler_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    book_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    check_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    profit_loss_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
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
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (handler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE stock_check_item (
    check_item_id BIGSERIAL PRIMARY KEY,
    check_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    batch_no VARCHAR(50),
    book_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    book_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    check_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    check_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    profit_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    profit_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    loss_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    loss_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    check_result VARCHAR(10) NOT NULL DEFAULT '盘平',
    reason VARCHAR(200),
    remark VARCHAR(500),
    profit_loss_reason VARCHAR(200),
    avg_cost NUMERIC(18,4) DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (check_id) REFERENCES stock_check(check_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id)
);

CREATE TABLE inventory_freeze (
    freeze_id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    material_id BIGINT NOT NULL,
    batch_no VARCHAR(50),
    freeze_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    freeze_type VARCHAR(20) NOT NULL,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    unfreeze_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unfreeze_date DATE,
    unfreeze_bill_no VARCHAR(50),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id),
    FOREIGN KEY (material_id) REFERENCES material(material_id)
);

CREATE TABLE batch (
    batch_id BIGSERIAL PRIMARY KEY,
    batch_no VARCHAR(50) NOT NULL UNIQUE,
    material_id BIGINT NOT NULL,
    production_date DATE,
    expiry_date DATE,
    manufacturing_no VARCHAR(100),
    supplier_id BIGINT,
    incoming_date DATE,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    available_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    frozen_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_cost NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(10) NOT NULL DEFAULT '正常',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id)
);

CREATE TABLE inventory_summary (
    summary_id BIGSERIAL PRIMARY KEY,
    period_year INT NOT NULL,
    period_month INT NOT NULL,
    stock_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    unit_id BIGINT NOT NULL,
    begin_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    begin_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    in_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    in_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    out_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    out_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    end_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    end_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (period_year, period_month, stock_id, material_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

CREATE TABLE inventory_warning (
    warning_id BIGSERIAL PRIMARY KEY,
    warning_type VARCHAR(20) NOT NULL,
    material_id BIGINT NOT NULL,
    stock_id BIGINT,
    warning_level VARCHAR(10) NOT NULL DEFAULT '一般',
    min_stock NUMERIC(18,4),
    max_stock NUMERIC(18,4),
    current_quantity NUMERIC(18,4),
    warning_quantity NUMERIC(18,4),
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    warning_content VARCHAR(500),
    handled_status VARCHAR(10) NOT NULL DEFAULT '未处理',
    handled_by VARCHAR(50),
    handled_at TIMESTAMP,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id)
);

CREATE INDEX idx_inventory_current_stock ON inventory_current(stock_id);
CREATE INDEX idx_inventory_current_material ON inventory_current(material_id);
CREATE INDEX idx_inventory_ledger_date ON inventory_ledger(trans_date);
CREATE INDEX idx_other_receipt_no ON other_receipt(receipt_no);
CREATE INDEX idx_other_issue_no ON other_issue(issue_no);
CREATE INDEX idx_material_requisition_no ON material_requisition(requisition_no);
CREATE INDEX idx_stock_transfer_no ON stock_transfer(transfer_no);
CREATE INDEX idx_stock_check_no ON stock_check(check_no);
CREATE INDEX idx_batch_no ON batch(batch_no);

-- ============================================================================
-- 模块5: 生产管理 (05_production.sql)
-- 注意: 调整了表创建顺序,先建 work_center 和 workstation,再建 routing_operation
-- ============================================================================

CREATE TABLE bom (
    bom_id BIGSERIAL PRIMARY KEY,
    bom_code VARCHAR(50) NOT NULL UNIQUE,
    bom_name VARCHAR(200) NOT NULL,
    bom_version VARCHAR(20),
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 1.0000,
    bom_type VARCHAR(20) NOT NULL DEFAULT '标准BOM',
    is_primary SMALLINT NOT NULL DEFAULT 1,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    effective_date DATE,
    expiry_date DATE,
    substitute_bom_code VARCHAR(50),
    scrap_rate NUMERIC(8,4) DEFAULT 0.0000,
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '已生效',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);
COMMENT ON TABLE bom IS '物料清单(BOM)主表';

CREATE TABLE bom_item (
    bom_item_id BIGSERIAL PRIMARY KEY,
    bom_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    scrap_rate NUMERIC(8,4) DEFAULT 0.0000,
    position_no VARCHAR(50),
    workstation_id BIGINT,
    is_optional SMALLINT NOT NULL DEFAULT 0,
    parent_line_no INT,
    substitute_line_no INT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (bom_id) REFERENCES bom(bom_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

-- 先建工作中心(被 routing_operation 引用)
CREATE TABLE work_center (
    work_center_id BIGSERIAL PRIMARY KEY,
    work_center_code VARCHAR(50) NOT NULL UNIQUE,
    work_center_name VARCHAR(100) NOT NULL,
    department_id BIGINT,
    manager_id BIGINT,
    location VARCHAR(200),
    capacity_type VARCHAR(20) NOT NULL DEFAULT '人工工时',
    capacity_per_day NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    efficiency NUMERIC(8,4) NOT NULL DEFAULT 100.0000,
    is_active SMALLINT NOT NULL DEFAULT 1,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (department_id) REFERENCES department(dept_id),
    FOREIGN KEY (manager_id) REFERENCES employee(employee_id)
);
COMMENT ON TABLE work_center IS '工作中心';

-- 再建工序(被 routing_operation 引用)
CREATE TABLE workstation (
    workstation_id BIGSERIAL PRIMARY KEY,
    workstation_code VARCHAR(50) NOT NULL UNIQUE,
    workstation_name VARCHAR(100) NOT NULL,
    work_center_id BIGINT,
    workstation_type VARCHAR(50),
    location VARCHAR(200),
    is_active SMALLINT NOT NULL DEFAULT 1,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (work_center_id) REFERENCES work_center(work_center_id)
);
COMMENT ON TABLE workstation IS '工序';

CREATE TABLE routing (
    routing_id BIGSERIAL PRIMARY KEY,
    routing_code VARCHAR(50) NOT NULL UNIQUE,
    routing_name VARCHAR(200) NOT NULL,
    routing_version VARCHAR(20),
    material_id BIGINT,
    bom_id BIGINT,
    is_primary SMALLINT NOT NULL DEFAULT 1,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    effective_date DATE,
    expiry_date DATE,
    total_work_hours NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    remark VARCHAR(2000),
    status VARCHAR(10) NOT NULL DEFAULT '已生效',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (bom_id) REFERENCES bom(bom_id)
);

CREATE TABLE routing_operation (
    operation_id BIGSERIAL PRIMARY KEY,
    routing_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    operation_code VARCHAR(50) NOT NULL,
    operation_name VARCHAR(100) NOT NULL,
    workstation_type VARCHAR(50),
    workstation_id BIGINT,
    work_center_id BIGINT,
    setup_hours NUMERIC(18,4) DEFAULT 0.0000,
    process_hours NUMERIC(18,4) DEFAULT 0.0000,
    wait_hours NUMERIC(18,4) DEFAULT 0.0000,
    move_hours NUMERIC(18,4) DEFAULT 0.0000,
    total_hours NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    batch_size INT DEFAULT 1,
    workers_required INT DEFAULT 1,
    is_qc_required SMALLINT NOT NULL DEFAULT 0,
    is_critical SMALLINT NOT NULL DEFAULT 0,
    next_operation_line_no INT,
    previous_operation_line_no INT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (routing_id) REFERENCES routing(routing_id) ON DELETE CASCADE,
    FOREIGN KEY (workstation_id) REFERENCES workstation(workstation_id),
    FOREIGN KEY (work_center_id) REFERENCES work_center(work_center_id)
);

CREATE TABLE mrp_plan (
    plan_id BIGSERIAL PRIMARY KEY,
    plan_code VARCHAR(50) NOT NULL UNIQUE,
    plan_name VARCHAR(200) NOT NULL,
    plan_date DATE NOT NULL,
    plan_type VARCHAR(20) NOT NULL DEFAULT 'MPS/MRP',
    demand_type VARCHAR(20) NOT NULL DEFAULT '综合',
    time_fence_days INT DEFAULT 0,
    lot_size_type VARCHAR(20) NOT NULL DEFAULT 'LFL',
    safety_stock_days INT DEFAULT 0,
    lead_time_days INT DEFAULT 0,
    mrp_horizontal_days INT DEFAULT 7,
    order_type VARCHAR(20) NOT NULL DEFAULT '采购申请',
    is_auto_create_order SMALLINT NOT NULL DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    plan_status VARCHAR(10) NOT NULL DEFAULT '未运行',
    run_status VARCHAR(20),
    run_by VARCHAR(50),
    run_at TIMESTAMP,
    remark VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50)
);

CREATE TABLE mrp_demand (
    demand_id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    demand_type VARCHAR(20) NOT NULL,
    demand_source VARCHAR(50),
    demand_no VARCHAR(50),
    demand_line_no INT,
    required_date DATE NOT NULL,
    demand_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    allocated_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    planned_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    order_type VARCHAR(20),
    order_no VARCHAR(50),
    order_line_no INT,
    priority INT DEFAULT 5,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (plan_id) REFERENCES mrp_plan(plan_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id)
);

CREATE TABLE mrp_supply (
    supply_id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    supply_type VARCHAR(20) NOT NULL,
    supply_source VARCHAR(50),
    supply_no VARCHAR(50),
    supply_line_no INT,
    available_date DATE NOT NULL,
    supply_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    allocated_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    order_type VARCHAR(20),
    order_no VARCHAR(50),
    order_line_no INT,
    order_status VARCHAR(10),
    supplier_id BIGINT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (plan_id) REFERENCES mrp_plan(plan_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id)
);

CREATE TABLE production_order (
    order_id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    order_date DATE NOT NULL,
    bom_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    completed_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    rejected_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    warehouse_id BIGINT,
    department_id BIGINT,
    manager_id BIGINT,
    routing_id BIGINT,
    priority VARCHAR(10) NOT NULL DEFAULT '普通',
    plan_start_date DATE,
    plan_end_date DATE,
    actual_start_date DATE,
    actual_end_date DATE,
    lead_time_days INT DEFAULT 0,
    is_urgent SMALLINT NOT NULL DEFAULT 0,
    source_type VARCHAR(20),
    source_no VARCHAR(50),
    sales_order_no VARCHAR(50),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '计划',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50),
    FOREIGN KEY (bom_id) REFERENCES bom(bom_id),
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id),
    FOREIGN KEY (manager_id) REFERENCES employee(employee_id),
    FOREIGN KEY (routing_id) REFERENCES routing(routing_id)
);

CREATE TABLE production_order_item (
    order_item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    bom_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    required_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    issued_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    returned_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    consumed_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    scrap_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_cost NUMERIC(18,4) DEFAULT 0.0000,
    total_cost NUMERIC(18,2) DEFAULT 0.00,
    stock_id BIGINT,
    bin_id BIGINT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (order_id) REFERENCES production_order(order_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id)
);

CREATE TABLE process_card (
    card_id BIGSERIAL PRIMARY KEY,
    card_no VARCHAR(50) NOT NULL UNIQUE,
    production_order_id BIGINT NOT NULL,
    production_order_no VARCHAR(50) NOT NULL,
    routing_id BIGINT,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    work_center_id BIGINT,
    workstation_id BIGINT,
    operation_id BIGINT,
    operation_name VARCHAR(100),
    priority VARCHAR(10) NOT NULL DEFAULT '普通',
    plan_start_time TIMESTAMP,
    plan_end_time TIMESTAMP,
    actual_start_time TIMESTAMP,
    actual_end_time TIMESTAMP,
    status VARCHAR(10) NOT NULL DEFAULT '等待',
    remark VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    FOREIGN KEY (production_order_id) REFERENCES production_order(order_id),
    FOREIGN KEY (routing_id) REFERENCES routing(routing_id),
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (work_center_id) REFERENCES work_center(work_center_id),
    FOREIGN KEY (workstation_id) REFERENCES workstation(workstation_id)
);

CREATE TABLE process_card_item (
    card_item_id BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    employee_id BIGINT,
    employee_code VARCHAR(50),
    employee_name VARCHAR(100),
    report_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    qualified_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    rejected_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    work_hours NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    overtime_hours NUMERIC(18,4) DEFAULT 0.0000,
    report_time TIMESTAMP NOT NULL,
    reject_reason VARCHAR(200),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (card_id) REFERENCES process_card(card_id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
);

CREATE TABLE operation_report (
    report_id BIGSERIAL PRIMARY KEY,
    report_no VARCHAR(50) NOT NULL UNIQUE,
    report_date DATE NOT NULL,
    production_order_id BIGINT,
    production_order_no VARCHAR(50),
    work_center_id BIGINT,
    workstation_id BIGINT,
    operation_id BIGINT,
    operation_name VARCHAR(100),
    employee_id BIGINT,
    employee_name VARCHAR(100),
    report_type VARCHAR(20) NOT NULL DEFAULT '工序汇报',
    total_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    qualified_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    rejected_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    work_hours NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    overtime_hours NUMERIC(18,4) DEFAULT 0.0000,
    shift_type VARCHAR(10) NOT NULL DEFAULT '白班',
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    status VARCHAR(10) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    FOREIGN KEY (production_order_id) REFERENCES production_order(order_id),
    FOREIGN KEY (work_center_id) REFERENCES work_center(work_center_id),
    FOREIGN KEY (workstation_id) REFERENCES workstation(workstation_id),
    FOREIGN KEY (operation_id) REFERENCES routing_operation(operation_id),
    FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
);

CREATE TABLE operation_report_item (
    report_item_id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    qualified_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    rejected_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    process_card_no VARCHAR(50),
    batch_no VARCHAR(50),
    reject_reason VARCHAR(200),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (report_id) REFERENCES operation_report(report_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

CREATE TABLE production_receipt (
    receipt_id BIGSERIAL PRIMARY KEY,
    receipt_no VARCHAR(50) NOT NULL UNIQUE,
    receipt_date DATE NOT NULL,
    warehouse_id BIGINT NOT NULL,
    production_order_id BIGINT,
    production_order_no VARCHAR(50),
    department_id BIGINT,
    handler_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    total_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    qualified_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    rejected_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
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
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (production_order_id) REFERENCES production_order(order_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id),
    FOREIGN KEY (handler_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id)
);

CREATE TABLE production_receipt_item (
    receipt_item_id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    qualified_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    rejected_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    stock_id BIGINT NOT NULL,
    bin_id BIGINT,
    batch_no VARCHAR(50),
    production_date DATE,
    expiry_date DATE,
    work_order_line_no INT,
    operation_report_no VARCHAR(50),
    remark VARCHAR(500),
    avg_cost NUMERIC(18,4) DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (receipt_id) REFERENCES production_receipt(receipt_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (bin_id) REFERENCES location(bin_id)
);

CREATE TABLE quality_check (
    check_id BIGSERIAL PRIMARY KEY,
    check_no VARCHAR(50) NOT NULL UNIQUE,
    check_date DATE NOT NULL,
    check_type VARCHAR(20) NOT NULL,
    source_bill_type VARCHAR(50) NOT NULL,
    source_bill_no VARCHAR(50) NOT NULL,
    supplier_id BIGINT,
    customer_id BIGINT,
    warehouse_id BIGINT,
    department_id BIGINT,
    inspector_id BIGINT,
    batch_no VARCHAR(50),
    total_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    sample_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    qualified_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    rejected_quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    check_level VARCHAR(10) NOT NULL DEFAULT 'II',
    aql_level NUMERIC(8,4) DEFAULT 1.5000,
    check_result VARCHAR(10) NOT NULL DEFAULT '待检',
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
    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(stock_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id),
    FOREIGN KEY (inspector_id) REFERENCES employee(employee_id)
);

CREATE TABLE quality_check_item (
    check_item_id BIGSERIAL PRIMARY KEY,
    check_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(500),
    unit_id BIGINT NOT NULL,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    sample_size INT NOT NULL DEFAULT 0,
    aql_accepted INT NOT NULL DEFAULT 0,
    aql_rejected INT NOT NULL DEFAULT 0,
    inspected_quantity INT NOT NULL DEFAULT 0,
    qualified_quantity INT NOT NULL DEFAULT 0,
    rejected_quantity INT NOT NULL DEFAULT 0,
    major_defects INT NOT NULL DEFAULT 0,
    minor_defects INT NOT NULL DEFAULT 0,
    critical_defects INT NOT NULL DEFAULT 0,
    check_result VARCHAR(10) NOT NULL DEFAULT '待检',
    reject_reason VARCHAR(200),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (check_id) REFERENCES quality_check(check_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

CREATE TABLE shift (
    shift_id BIGSERIAL PRIMARY KEY,
    shift_code VARCHAR(20) NOT NULL UNIQUE,
    shift_name VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    break_minutes INT DEFAULT 0,
    work_hours NUMERIC(8,4) NOT NULL DEFAULT 8.0000,
    is_night_shift SMALLINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX idx_bom_code ON bom(bom_code);
CREATE INDEX idx_bom_material ON bom(material_id);
CREATE INDEX idx_routing_code ON routing(routing_code);
CREATE INDEX idx_work_center_code ON work_center(work_center_code);
CREATE INDEX idx_workstation_code ON workstation(workstation_code);
CREATE INDEX idx_mrp_plan_code ON mrp_plan(plan_code);
CREATE INDEX idx_production_order_no ON production_order(order_no);

-- ============================================================================
-- 模块6: 财务管理 (06_finance.sql)
-- ============================================================================

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
    close_status INT NOT NULL DEFAULT 0,
    bill_before_status INT,
    closed_by VARCHAR(50),
    closed_at TIMESTAMP,
    closed_reason VARCHAR(255),
    voided_by VARCHAR(50),
    voided_at TIMESTAMP,
    void_reason VARCHAR(255),
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
    close_status INT NOT NULL DEFAULT 0,
    bill_before_status INT,
    closed_by VARCHAR(50),
    closed_at TIMESTAMP,
    closed_reason VARCHAR(255),
    voided_by VARCHAR(50),
    voided_at TIMESTAMP,
    void_reason VARCHAR(255),
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

CREATE TABLE bank_account (
    bank_account_id BIGSERIAL PRIMARY KEY,
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

CREATE INDEX idx_ar_no ON account_receivable(ar_no);
CREATE INDEX idx_ap_no ON account_payable(ap_no);
CREATE INDEX idx_cash_receipt_no ON cash_receipt(receipt_no);
CREATE INDEX idx_cash_payment_no ON cash_payment(payment_no);

-- ============================================================================
-- 模块7: 系统管理 (07_system.sql)
-- ============================================================================

CREATE TABLE sys_user (
    user_id BIGSERIAL PRIMARY KEY,
    user_code VARCHAR(50) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    mobile_phone VARCHAR(20),
    avatar_url VARCHAR(500),
    dept_id BIGINT,
    employee_id BIGINT,
    user_type VARCHAR(20) NOT NULL DEFAULT '普通用户',
    auth_type VARCHAR(20) NOT NULL DEFAULT '本地认证',
    is_admin SMALLINT NOT NULL DEFAULT 0,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    is_locked SMALLINT NOT NULL DEFAULT 0,
    lock_time TIMESTAMP,
    password_expire_time TIMESTAMP,
    password_change_time TIMESTAMP,
    last_login_time TIMESTAMP,
    last_login_ip VARCHAR(50),
    login_attempts INT DEFAULT 0,
    remark VARCHAR(500),
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (dept_id) REFERENCES department(dept_id),
    FOREIGN KEY (employee_id) REFERENCES employee(employee_id)
);
COMMENT ON TABLE sys_user IS '用户主表';

CREATE TABLE sys_role (
    role_id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL UNIQUE,
    role_name VARCHAR(100) NOT NULL,
    role_type VARCHAR(20) NOT NULL DEFAULT '业务角色',
    data_scope VARCHAR(20) NOT NULL DEFAULT '本部门',
    data_scope_value VARCHAR(500),
    is_system SMALLINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);
COMMENT ON TABLE sys_role IS '角色主表';

CREATE TABLE sys_user_role (
    user_role_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    is_primary_role SMALLINT NOT NULL DEFAULT 0,
    assign_date DATE,
    assign_by VARCHAR(50),
    data_scope_type VARCHAR(50),
    data_scope_value VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    UNIQUE (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES sys_role(role_id) ON DELETE CASCADE
);

CREATE TABLE sys_menu (
    menu_id BIGSERIAL PRIMARY KEY,
    menu_code VARCHAR(100) NOT NULL UNIQUE,
    menu_name VARCHAR(100) NOT NULL,
    parent_menu_id BIGINT,
    menu_type VARCHAR(20) NOT NULL,
    icon VARCHAR(100),
    route_path VARCHAR(200),
    component_path VARCHAR(500),
    is_visible SMALLINT NOT NULL DEFAULT 1,
    is_cache SMALLINT NOT NULL DEFAULT 0,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    permission_code VARCHAR(100),
    external_link VARCHAR(500),
    keepalive SMALLINT NOT NULL DEFAULT 1,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (parent_menu_id) REFERENCES sys_menu(menu_id)
);

-- 注意: menu_id 存前端菜单 path(如 /sale/orders) 或整组标识(如 group_basedata), 不再是 sys_menu 的数字 menu_id
CREATE TABLE sys_role_permission (
    permission_id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id VARCHAR(200) NOT NULL,
    permission_type VARCHAR(20) NOT NULL DEFAULT '菜单',
    permission_value INT NOT NULL DEFAULT 1,
    is_inherited SMALLINT NOT NULL DEFAULT 0,
    parent_role_id BIGINT,
    data_scope_type VARCHAR(20),
    data_scope_value VARCHAR(500),
    org_scope_type VARCHAR(20),
    org_scope_value VARCHAR(500),
    field_permissions TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    UNIQUE (role_id, menu_id, permission_type),
    FOREIGN KEY (role_id) REFERENCES sys_role(role_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_role_id) REFERENCES sys_role(role_id)
);

CREATE TABLE sys_dict_type (
    dict_type_id BIGSERIAL PRIMARY KEY,
    dict_type_code VARCHAR(50) NOT NULL UNIQUE,
    dict_type_name VARCHAR(100) NOT NULL,
    dict_category VARCHAR(50),
    is_system SMALLINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE TABLE sys_dict_data (
    dict_data_id BIGSERIAL PRIMARY KEY,
    dict_type_id BIGINT NOT NULL,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    dict_sort INT NOT NULL DEFAULT 0,
    is_default SMALLINT NOT NULL DEFAULT 0,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    css_class VARCHAR(100),
    list_class VARCHAR(100),
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (dict_type_id) REFERENCES sys_dict_type(dict_type_id) ON DELETE CASCADE
);

CREATE TABLE sys_operate_log (
    log_id BIGSERIAL PRIMARY KEY,
    user_code VARCHAR(50) NOT NULL,
    user_name VARCHAR(100) NOT NULL,
    real_name VARCHAR(100),
    dept_code VARCHAR(50),
    dept_name VARCHAR(100),
    operate_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    mac_address VARCHAR(50),
    host_name VARCHAR(200),
    browser_info VARCHAR(500),
    operate_type VARCHAR(20) NOT NULL,
    operate_result VARCHAR(10) NOT NULL DEFAULT '成功',
    fail_reason VARCHAR(500),
    bill_type VARCHAR(50),
    bill_no VARCHAR(50),
    operate_content TEXT,
    operate_summary VARCHAR(500),
    module_name VARCHAR(100) NOT NULL,
    request_url VARCHAR(500),
    request_method VARCHAR(10),
    request_params TEXT,
    session_id VARCHAR(100),
    duration INT,
    org_code VARCHAR(50),
    trace_id VARCHAR(100),
    archive_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sys_user_username ON sys_user(username);
CREATE INDEX idx_sys_user_role_user ON sys_user_role(user_id);
CREATE INDEX idx_sys_role_code ON sys_role(role_code);
CREATE INDEX idx_sys_menu_code ON sys_menu(menu_code);
CREATE INDEX idx_sys_dict_type_code ON sys_dict_type(dict_type_code);
CREATE INDEX idx_sys_operate_log_user ON sys_operate_log(user_code);

-- ============================================================================
-- 操作日志变更明细表（字段级前后值对比）
-- ============================================================================
CREATE TABLE IF NOT EXISTS sys_operate_log_detail (
    detail_id BIGSERIAL PRIMARY KEY,
    log_id BIGINT NOT NULL,
    field_name VARCHAR(100),
    field_desc VARCHAR(200),
    old_value TEXT,
    new_value TEXT,
    data_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sys_operate_log_detail_log_id ON sys_operate_log_detail(log_id);

-- ============================================================================
-- AI 审计日志表
-- ============================================================================
CREATE TABLE ai_audit_log (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64),
    channel VARCHAR(20),
    channel_user_id VARCHAR(100),
    erp_user_id BIGINT,
    session_id VARCHAR(64),
    api_path VARCHAR(200),
    input_prompt TEXT,
    llm_raw_params TEXT,
    llm_model VARCHAR(50),
    intent VARCHAR(50),
    response_status VARCHAR(20),
    before_data TEXT,
    after_data TEXT,
    confirm_status VARCHAR(20) DEFAULT '无需确认',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ai_audit_trace ON ai_audit_log(trace_id);
CREATE INDEX idx_ai_audit_user ON ai_audit_log(erp_user_id);

-- ============================================================================
-- 初始化数据
-- ============================================================================

-- 币种
INSERT INTO currency (currency_code, currency_name, currency_symbol, is_base_currency, exchange_rate, status)
VALUES ('CNY', '人民币', '¥', 1, 1.00000000, '启用');

-- 计量单位
INSERT INTO unit_of_measure (unit_code, unit_name, unit_group, is_base_unit, conversion_rate, status) VALUES
('PCS', '件', '个数', 1, 1.000000, '启用'),
('BOX', '盒', '个数', 0, 10.000000, '启用'),
('SET', '套', '个数', 0, 1.000000, '启用'),
('KG', '千克', '重量', 1, 1.000000, '启用'),
('G', '克', '重量', 0, 1000.000000, '启用'),
('M', '米', '长度', 1, 1.000000, '启用'),
('CM', '厘米', '长度', 0, 100.000000, '启用'),
('L', '升', '体积', 1, 1.000000, '启用'),
('ML', '毫升', '体积', 0, 1000.000000, '启用'),
('SQM', '平方米', '面积', 1, 1.000000, '启用'),
('TON', '吨', '重量', 0, 0.001000, '启用');

-- 付款条件
INSERT INTO payment_term (payment_term_code, payment_term_name, payment_days, discount_days, discount_rate, status) VALUES
('PT001', '货到付款', 0, 0, 0.0000, '启用'),
('PT002', '月结30天', 30, 0, 0.0000, '启用'),
('PT003', '月结60天', 60, 0, 0.0000, '启用'),
('PT004', '预付30%', 0, 0, 30.0000, '启用'),
('PT005', '季结90天', 90, 0, 0.0000, '启用');

-- 结算方式
INSERT INTO settlement_mode (settle_mode_code, settle_mode_name, settle_type, sort_order, status) VALUES
('CASH', '现金', '收款', 1, '启用'),
('TRANSFER', '转账', '通用', 2, '启用'),
('CHECK', '支票', '收款', 3, '启用'),
('BILL', '汇票', '通用', 4, '启用'),
('ONLINE', '在线支付', '通用', 5, '启用');

-- 价格等级
INSERT INTO price_level (price_level_code, price_level_name, price_adjust_rate, sort_order, status) VALUES
('PL01', '标准价格', 0.0000, 1, '启用'),
('PL02', '零售价', 10.0000, 2, '启用'),
('PL03', '批发价', -5.0000, 3, '启用'),
('PL04', 'VIP价', -15.0000, 4, '启用'),
('PL05', '代理价', -20.0000, 5, '启用');

-- 部门
INSERT INTO department (dept_code, dept_name, parent_dept_id, dept_type, status) VALUES
('D001', '总经理室', NULL, '决策部门', '启用'),
('D002', '销售部', NULL, '业务部门', '启用'),
('D003', '采购部', NULL, '业务部门', '启用'),
('D004', '仓库部', NULL, '业务部门', '启用'),
('D005', '生产部', NULL, '业务部门', '启用'),
('D006', '财务部', NULL, '职能部门', '启用'),
('D007', '人力资源部', NULL, '职能部门', '启用'),
('D008', '技术部', NULL, '职能部门', '启用');

-- 仓库
INSERT INTO warehouse (stock_code, stock_name, stock_type, is_bin_control, is_mrp_available, status) VALUES
('WH001', '原料仓', '原材料仓', 1, 1, '启用'),
('WH002', '成品仓', '成品仓', 1, 1, '启用'),
('WH003', '半成品仓', '半成品仓', 1, 1, '启用'),
('WH004', '包材仓', '辅助材料仓', 1, 1, '启用'),
('WH005', '不合格品仓', '废品仓', 0, 0, '启用');

-- 角色
INSERT INTO sys_role (role_code, role_name, role_type, data_scope, is_system, sort_order, status) VALUES
('SUPER_ADMIN', '超级管理员', '系统角色', '全部', 1, 1, '启用'),
('ADMIN', '系统管理员', '系统角色', '全部', 1, 2, '启用'),
('SALES_MANAGER', '销售主管', '业务角色', '本部门', 0, 3, '启用'),
('SALES', '销售人员', '业务角色', '本部门', 0, 4, '启用'),
('PURCHASE_MANAGER', '采购主管', '业务角色', '本部门', 0, 5, '启用'),
('PURCHASE', '采购人员', '业务角色', '本部门', 0, 6, '启用'),
('WAREHOUSE_MANAGER', '仓库主管', '业务角色', '本部门', 0, 7, '启用'),
('WAREHOUSE', '仓库人员', '业务角色', '本部门', 0, 8, '启用'),
('PRODUCTION_MANAGER', '生产主管', '业务角色', '本部门', 0, 9, '启用'),
('PRODUCTION', '生产人员', '业务角色', '本部门', 0, 10, '启用'),
('FINANCE_MANAGER', '财务主管', '业务角色', '本部门', 0, 11, '启用'),
('FINANCE', '财务人员', '业务角色', '本部门', 0, 12, '启用'),
('VIEWER', '查看者', '业务角色', '本部门', 0, 13, '启用');

-- 用户(密码均为 admin123,BCrypt 加密)
INSERT INTO sys_user (user_code, username, password, real_name, user_type, is_admin, is_enabled, status) VALUES
('U001', 'admin', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '系统管理员', '系统用户', 1, 1, '启用'),
('U002', 'manager', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '张总', '普通用户', 0, 1, '启用'),
('U003', 'sale01', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '李销售', '普通用户', 0, 1, '启用'),
('U004', 'purchase01', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '王采购', '普通用户', 0, 1, '启用'),
('U005', 'warehouse01', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '赵仓管', '普通用户', 0, 1, '启用'),
('U006', 'production01', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '钱生产', '普通用户', 0, 1, '启用'),
('U007', 'finance01', '$2a$10$n9rf.LQ2Yv7NFIEKjU/SfuQzDbOEvK6TRgSbrv50ugK6gwC8JcYYK', '孙财务', '普通用户', 0, 1, '启用');

-- 用户角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 3), (3, 4), (4, 6), (5, 8), (6, 10), (7, 12);

-- 数据字典
INSERT INTO sys_dict_type (dict_type_code, dict_type_name, dict_category, is_system, sort_order, status) VALUES
('bill_status', '单据状态', '业务', 1, 1, '启用'),
('approval_status', '审核状态', '业务', 1, 2, '启用'),
('material_type', '物料类型', '基础资料', 1, 3, '启用'),
('warehouse_type', '仓库类型', '基础资料', 1, 4, '启用'),
('customer_type', '客户类型', '基础资料', 1, 5, '启用'),
('supplier_type', '供应商类型', '基础资料', 1, 6, '启用'),
('gender', '性别', '系统', 1, 7, '启用'),
('yes_no', '是否', '系统', 1, 8, '启用'),
('priority', '优先级', '业务', 1, 9, '启用'),
('order_status', '工单状态', '生产', 1, 10, '启用');

INSERT INTO sys_dict_data (dict_type_id, dict_label, dict_value, dict_sort, is_default, status) VALUES
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'bill_status'), '草稿', '草稿', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'bill_status'), '已提交', '已提交', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'bill_status'), '已生效', '已生效', 3, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'bill_status'), '已关闭', '已关闭', 4, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'approval_status'), '待审核', '待审核', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'approval_status'), '审核中', '审核中', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'approval_status'), '已生效', '已生效', 3, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'approval_status'), '已驳回', '已驳回', 4, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'material_type'), '原材料', '原材料', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'material_type'), '半成品', '半成品', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'material_type'), '成品', '成品', 3, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'material_type'), '包材', '包材', 4, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'gender'), '男', '男', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'gender'), '女', '女', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'yes_no'), '是', '是', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'yes_no'), '否', '否', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'priority'), '紧急', '紧急', 1, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'priority'), '高', '高', 2, 0, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'priority'), '普通', '普通', 3, 1, '启用'),
((SELECT dict_type_id FROM sys_dict_type WHERE dict_type_code = 'priority'), '低', '低', 4, 0, '启用');

-- ============================================================================
-- 序列重置(确保从最大ID开始)
-- ============================================================================
SELECT setval('currency_currency_id_seq', (SELECT COALESCE(MAX(currency_id), 1) FROM currency));
SELECT setval('unit_of_measure_unit_id_seq', (SELECT COALESCE(MAX(unit_id), 1) FROM unit_of_measure));
SELECT setval('payment_term_payment_term_id_seq', (SELECT COALESCE(MAX(payment_term_id), 1) FROM payment_term));
SELECT setval('settlement_mode_settle_mode_id_seq', (SELECT COALESCE(MAX(settle_mode_id), 1) FROM settlement_mode));
SELECT setval('price_level_price_level_id_seq', (SELECT COALESCE(MAX(price_level_id), 1) FROM price_level));
SELECT setval('department_dept_id_seq', (SELECT COALESCE(MAX(dept_id), 1) FROM department));
SELECT setval('warehouse_stock_id_seq', (SELECT COALESCE(MAX(stock_id), 1) FROM warehouse));
SELECT setval('sys_user_user_id_seq', (SELECT COALESCE(MAX(user_id), 1) FROM sys_user));
SELECT setval('sys_role_role_id_seq', (SELECT COALESCE(MAX(role_id), 1) FROM sys_role));
SELECT setval('sys_dict_type_dict_type_id_seq', (SELECT COALESCE(MAX(dict_type_id), 1) FROM sys_dict_type));
SELECT setval('sys_dict_data_dict_data_id_seq', (SELECT COALESCE(MAX(dict_data_id), 1) FROM sys_dict_data));

-- ============================================================================
-- 授权 erp_user
-- ============================================================================
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO erp_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO erp_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO erp_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO erp_user;

-- ============================================================================
-- 完成
-- ============================================================================
SELECT '智能ERP数据库初始化完成!' AS message;
SELECT '总表数：' || (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE') AS total_tables;
