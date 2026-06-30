-- ============================================================================
-- 智能ERP系统 - PostgreSQL 数据库建表脚本
-- 模块：基础资料模块
-- 版本：V1.0
-- 日期：2026-06-22
-- 说明：共14张表，包含物料、客户、供应商、仓库、货位、部门、员工等基础档案
-- ============================================================================

-- 1. 计量单位表 (unit_of_measure)
CREATE TABLE unit_of_measure (
    unit_id BIGSERIAL PRIMARY KEY,
    unit_code VARCHAR(20) NOT NULL UNIQUE,
    unit_name VARCHAR(50) NOT NULL,
    unit_group VARCHAR(50),
    is_base_unit SMALLINT NOT NULL DEFAULT 0,
    conversion_rate NUMERIC(18,6) NOT NULL DEFAULT 1.000000,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

COMMENT ON TABLE unit_of_measure IS '计量单位档案';
COMMENT ON COLUMN unit_of_measure.unit_id IS '单位ID';
COMMENT ON COLUMN unit_of_measure.unit_code IS '单位编码';
COMMENT ON COLUMN unit_of_measure.unit_name IS '单位名称';
COMMENT ON COLUMN unit_of_measure.unit_group IS '单位组';
COMMENT ON COLUMN unit_of_measure.is_base_unit IS '是否基本单位';
COMMENT ON COLUMN unit_of_measure.conversion_rate IS '换算率';
COMMENT ON COLUMN unit_of_measure.status IS '状态';

-- 2. 物料分类表 (material_category)
CREATE TABLE material_category (
    category_id BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(50) NOT NULL UNIQUE,
    category_name VARCHAR(100) NOT NULL,
    parent_id BIGINT,
    category_level INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (parent_id) REFERENCES material_category(category_id)
);

COMMENT ON TABLE material_category IS '物料分类档案';
COMMENT ON COLUMN material_category.category_id IS '分类ID';
COMMENT ON COLUMN material_category.category_code IS '分类编码';
COMMENT ON COLUMN material_category.category_name IS '分类名称';
COMMENT ON COLUMN material_category.parent_id IS '上级分类';

-- 3. 物料档案表 (material)
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
COMMENT ON COLUMN material.material_id IS '物料ID';
COMMENT ON COLUMN material.material_code IS '物料编码';
COMMENT ON COLUMN material.material_name IS '物料名称';
COMMENT ON COLUMN material.material_type IS '物料类型';
COMMENT ON COLUMN material.category_id IS '物料分类';
COMMENT ON COLUMN material.is_lot_control IS '是否批次管理';
COMMENT ON COLUMN material.is_sn_control IS '是否序列号管理';
COMMENT ON COLUMN material.is_bin_control IS '是否货位管理';
COMMENT ON COLUMN material.is_qc_required IS '是否质检';
COMMENT ON COLUMN material.is_expiry_control IS '是否保质期管理';

-- 4. 部门档案表 (department)
CREATE TABLE department (
    dept_id BIGSERIAL PRIMARY KEY,
    dept_code VARCHAR(50) NOT NULL UNIQUE,
    dept_name VARCHAR(100) NOT NULL,
    parent_dept_id BIGINT,
    dept_type VARCHAR(20) NOT NULL DEFAULT '职能部门',
    manager_id BIGINT,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (parent_dept_id) REFERENCES department(dept_id)
);

COMMENT ON TABLE department IS '部门档案';
COMMENT ON COLUMN department.dept_id IS '部门ID';
COMMENT ON COLUMN department.dept_code IS '部门编码';
COMMENT ON COLUMN department.dept_name IS '部门名称';
COMMENT ON COLUMN department.parent_dept_id IS '上级部门';
COMMENT ON COLUMN department.dept_type IS '部门类型';

-- 5. 员工档案表 (employee)
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (dept_id) REFERENCES department(dept_id)
);

COMMENT ON TABLE employee IS '员工档案';
COMMENT ON COLUMN employee.employee_id IS '员工ID';
COMMENT ON COLUMN employee.employee_code IS '员工编码';
COMMENT ON COLUMN employee.employee_name IS '员工姓名';
COMMENT ON COLUMN employee.dept_id IS '所属部门';
COMMENT ON COLUMN employee.user_id IS '关联系统用户';

-- 6. 仓库档案表 (warehouse)
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (manager_id) REFERENCES employee(employee_id)
);

COMMENT ON TABLE warehouse IS '仓库档案';
COMMENT ON COLUMN warehouse.stock_id IS '仓库ID';
COMMENT ON COLUMN warehouse.stock_code IS '仓库编码';
COMMENT ON COLUMN warehouse.stock_name IS '仓库名称';
COMMENT ON COLUMN warehouse.stock_type IS '仓库类型';
COMMENT ON COLUMN warehouse.is_bin_control IS '是否货位管理';
COMMENT ON COLUMN warehouse.is_mrp_available IS '是否MRP可用';

-- 7. 货位档案表 (location)
CREATE TABLE location (
    bin_id BIGSERIAL PRIMARY KEY,
    bin_code VARCHAR(50) NOT NULL UNIQUE,
    bin_name VARCHAR(100) NOT NULL,
    stock_id BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (stock_id) REFERENCES warehouse(stock_id)
);

COMMENT ON TABLE location IS '货位档案';
COMMENT ON COLUMN location.bin_id IS '货位ID';
COMMENT ON COLUMN location.bin_code IS '货位编码';
COMMENT ON COLUMN location.bin_name IS '货位名称';
COMMENT ON COLUMN location.stock_id IS '所属仓库';

-- 8. 客户档案表 (customer)
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (payment_term_id) REFERENCES payment_term(payment_term_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (default_saler_id) REFERENCES employee(employee_id)
);

-- 9. 供应商档案表 (supplier)
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (payment_term_id) REFERENCES payment_term(payment_term_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (default_buyer_id) REFERENCES employee(employee_id)
);

COMMENT ON TABLE supplier IS '供应商档案';

-- 10. 币种表 (currency)
CREATE TABLE currency (
    currency_id BIGSERIAL PRIMARY KEY,
    currency_code VARCHAR(10) NOT NULL UNIQUE,
    currency_name VARCHAR(50) NOT NULL,
    currency_symbol VARCHAR(10),
    is_base_currency SMALLINT NOT NULL DEFAULT 0,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

COMMENT ON TABLE currency IS '币种档案';

-- 11. 会计科目表 (account)
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (parent_account_id) REFERENCES account(account_id)
);

COMMENT ON TABLE account IS '会计科目档案';

-- 12. 结算方式表 (settlement_mode)
CREATE TABLE settlement_mode (
    settle_mode_id BIGSERIAL PRIMARY KEY,
    settle_mode_code VARCHAR(20) NOT NULL UNIQUE,
    settle_mode_name VARCHAR(50) NOT NULL,
    settle_type VARCHAR(10) NOT NULL DEFAULT '通用',
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

COMMENT ON TABLE settlement_mode IS '结算方式档案';
COMMENT ON COLUMN settlement_mode.settle_type IS '结算类型';

-- 13. 付款条件表 (payment_term)
CREATE TABLE payment_term (
    payment_term_id BIGSERIAL PRIMARY KEY,
    payment_term_code VARCHAR(20) NOT NULL UNIQUE,
    payment_term_name VARCHAR(100) NOT NULL,
    payment_days INT NOT NULL DEFAULT 0,
    discount_days INT DEFAULT 0,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    advance_rate NUMERIC(8,4) DEFAULT 0.0000,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

COMMENT ON TABLE payment_term IS '付款条件档案';

-- 14. 价格等级表 (price_level)
CREATE TABLE price_level (
    price_level_id BIGSERIAL PRIMARY KEY,
    price_level_code VARCHAR(20) NOT NULL UNIQUE,
    price_level_name VARCHAR(50) NOT NULL,
    price_adjust_rate NUMERIC(8,4) NOT NULL DEFAULT 0.0000,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT '启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

COMMENT ON TABLE price_level IS '价格等级档案';

-- 创建索引
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
-- 基础资料模块建表完成
-- ============================================================================
