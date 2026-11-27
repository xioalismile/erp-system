-- 修复 purchase_receipt 表缺失字段
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(200);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS buyer_name VARCHAR(50);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS department_id BIGINT;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS stock_name VARCHAR(100);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS inspection_no VARCHAR(50);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS is_lot_control INT DEFAULT 0;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS is_closed INT DEFAULT 0;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS close_status INT DEFAULT 0;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS base_total_amount NUMERIC(18,2) DEFAULT 0.00;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS base_tax_amount NUMERIC(18,2) DEFAULT 0.00;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS base_net_amount NUMERIC(18,2) DEFAULT 0.00;

-- 创建 purchase_contract 表
CREATE TABLE IF NOT EXISTS purchase_contract (
    contract_id BIGSERIAL PRIMARY KEY,
    contract_no VARCHAR(50) NOT NULL UNIQUE,
    contract_date DATE,
    contract_type VARCHAR(50),
    validity_start DATE,
    validity_end DATE,
    supplier_id BIGINT,
    supplier_no VARCHAR(50),
    supplier_short_name VARCHAR(100),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    department_id BIGINT,
    buyer_id BIGINT,
    currency_id BIGINT,
    exchange_rate NUMERIC(18,8) DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) DEFAULT 0.00,
    tax_amount NUMERIC(18,2) DEFAULT 0.00,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    net_amount NUMERIC(18,2) DEFAULT 0.00,
    delivery_date DATE,
    payment_term_id BIGINT,
    payment_method VARCHAR(50),
    deposit_amount NUMERIC(18,2) DEFAULT 0.00,
    warehouse_id BIGINT,
    receive_address VARCHAR(500),
    transport_method VARCHAR(50),
    is_tax INT DEFAULT 0,
    tax_calc_method VARCHAR(20),
    is_invoiced INT DEFAULT 0,
    logistics_company VARCHAR(100),
    logistics_no VARCHAR(50),
    invoice_type VARCHAR(50),
    project_no VARCHAR(50),
    supplier_order_no VARCHAR(50),
    remark VARCHAR(2000),
    attachment_urls VARCHAR(1000),
    status VARCHAR(10) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    close_status INT DEFAULT 0,
    closed_by VARCHAR(50),
    closed_at TIMESTAMP,
    closed_reason VARCHAR(500),
    base_total_amount NUMERIC(18,2) DEFAULT 0.00,
    base_tax_amount NUMERIC(18,2) DEFAULT 0.00,
    base_net_amount NUMERIC(18,2) DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_purchase_contract_no ON purchase_contract(contract_no);
CREATE INDEX IF NOT EXISTS idx_purchase_contract_supplier ON purchase_contract(supplier_id);

-- 创建 purchase_contract_item 表
CREATE TABLE IF NOT EXISTS purchase_contract_item (
    item_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    specification VARCHAR(200),
    unit_id BIGINT,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0,
    unit_price NUMERIC(18,4) DEFAULT 0,
    tax_rate NUMERIC(8,4) DEFAULT 0,
    discount_rate NUMERIC(8,4) DEFAULT 0,
    discount_amount NUMERIC(18,2) DEFAULT 0,
    amount NUMERIC(18,2) DEFAULT 0,
    tax_amount NUMERIC(18,2) DEFAULT 0,
    net_amount NUMERIC(18,2) DEFAULT 0,
    delivery_date DATE,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (contract_id) REFERENCES purchase_contract(contract_id) ON DELETE CASCADE
);

SELECT 'Schema fix completed' AS result;
