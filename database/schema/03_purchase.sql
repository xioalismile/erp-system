-- ============================================================================
-- 智能ERP系统 - PostgreSQL 数据库建表脚本
-- 模块：采购管理模块
-- 版本：V1.0
-- 日期：2026-06-22
-- 说明：共10张表，包含申请单、订单、入库单、退货单、发票的主表和明细表
-- ============================================================================

-- 1. 采购申请单主表 (purchase_request)
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

COMMENT ON TABLE purchase_request IS '采购申请单主表';
COMMENT ON COLUMN purchase_request.request_id IS '申请单ID';
COMMENT ON COLUMN purchase_request.request_no IS '申请单号';
COMMENT ON COLUMN purchase_request.request_date IS '申请日期';
COMMENT ON COLUMN purchase_request.department_id IS '申请部门';
COMMENT ON COLUMN purchase_request.applicant_id IS '申请人';
COMMENT ON COLUMN purchase_request.buyer_id IS '采购员';
COMMENT ON COLUMN purchase_request.priority IS '优先级';

-- 2. 采购申请单明细表 (purchase_request_item)
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

COMMENT ON TABLE purchase_request_item IS '采购申请单明细表';

-- 3. 采购订单主表 (purchase_order)
CREATE TABLE purchase_order (
    order_id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    order_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL,
    contact_person VARCHAR(50),
    contact_phone VARCHAR(20),
    department_id BIGINT,
    buyer_id BIGINT,
    currency_id BIGINT NOT NULL,
    exchange_rate NUMERIC(18,8) NOT NULL DEFAULT 1.00000000,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    prepayment_amount NUMERIC(18,2) DEFAULT 0.00,
    delivery_date DATE,
    delivery_place VARCHAR(500),
    payment_term_id BIGINT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    request_no VARCHAR(50),
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
    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id),
    FOREIGN KEY (department_id) REFERENCES department(dept_id),
    FOREIGN KEY (buyer_id) REFERENCES employee(employee_id),
    FOREIGN KEY (currency_id) REFERENCES currency(currency_id),
    FOREIGN KEY (payment_term_id) REFERENCES payment_term(payment_term_id)
);

COMMENT ON TABLE purchase_order IS '采购订单主表';

-- 4. 采购订单明细表 (purchase_order_item)
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
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    tax_rate NUMERIC(8,4) NOT NULL DEFAULT 13.0000,
    discount_rate NUMERIC(8,4) DEFAULT 0.0000,
    discount_amount NUMERIC(18,2) DEFAULT 0.00,
    amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    delivery_date DATE,
    delivery_place VARCHAR(500),
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_line_no INT,
    request_line_no INT,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (order_id) REFERENCES purchase_order(order_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

COMMENT ON TABLE purchase_order_item IS '采购订单明细表';

-- 5. 采购入库单主表 (purchase_receipt)
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

COMMENT ON TABLE purchase_receipt IS '采购入库单主表';

-- 6. 采购入库单明细表 (purchase_receipt_item)
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

COMMENT ON TABLE purchase_receipt_item IS '采购入库单明细表';

-- 7. 采购退货单主表 (purchase_return)
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

COMMENT ON TABLE purchase_return IS '采购退货单主表';

-- 8. 采购退货单明细表 (purchase_return_item)
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

COMMENT ON TABLE purchase_return_item IS '采购退货单明细表';

-- 9. 采购发票主表 (purchase_invoice)
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

COMMENT ON TABLE purchase_invoice IS '采购发票主表';

-- 10. 采购发票明细表 (purchase_invoice_item)
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

COMMENT ON TABLE purchase_invoice_item IS '采购发票明细表';

-- 创建索引
CREATE INDEX idx_purchase_request_no ON purchase_request(request_no);
CREATE INDEX idx_purchase_request_dept ON purchase_request(department_id);
CREATE INDEX idx_purchase_request_date ON purchase_request(request_date);
CREATE INDEX idx_purchase_request_item ON purchase_request_item(request_id);
CREATE INDEX idx_purchase_order_no ON purchase_order(order_no);
CREATE INDEX idx_purchase_order_supplier ON purchase_order(supplier_id);
CREATE INDEX idx_purchase_order_date ON purchase_order(order_date);
CREATE INDEX idx_purchase_order_item ON purchase_order_item(order_id);
CREATE INDEX idx_purchase_receipt_no ON purchase_receipt(receipt_no);
CREATE INDEX idx_purchase_receipt_supplier ON purchase_receipt(supplier_id);
CREATE INDEX idx_purchase_receipt_date ON purchase_receipt(receipt_date);
CREATE INDEX idx_purchase_receipt_item ON purchase_receipt_item(receipt_id);
CREATE INDEX idx_purchase_return_no ON purchase_return(return_no);
CREATE INDEX idx_purchase_return_supplier ON purchase_return(supplier_id);
CREATE INDEX idx_purchase_return_date ON purchase_return(return_date);
CREATE INDEX idx_purchase_return_item ON purchase_return_item(return_id);
CREATE INDEX idx_purchase_invoice_no ON purchase_invoice(invoice_no);
CREATE INDEX idx_purchase_invoice_supplier ON purchase_invoice(supplier_id);
CREATE INDEX idx_purchase_invoice_date ON purchase_invoice(invoice_date);
CREATE INDEX idx_purchase_invoice_item ON purchase_invoice_item(invoice_id);

-- ============================================================================
-- 采购管理模块建表完成
-- ============================================================================
