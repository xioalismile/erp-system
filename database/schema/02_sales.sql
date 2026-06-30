-- ============================================================================
-- 智能ERP系统 - PostgreSQL 数据库建表脚本
-- 模块：销售管理模块
-- 版本：V1.0
-- 日期：2026-06-22
-- 说明：共10张表，包含报价单、订单、出库单、退货单、发票的主表和明细表
-- ============================================================================

-- 1. 销售报价单主表 (sale_quote)
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
COMMENT ON COLUMN sale_quote.quote_id IS '报价单ID';
COMMENT ON COLUMN sale_quote.quote_no IS '报价单号';
COMMENT ON COLUMN sale_quote.quote_date IS '报价日期';
COMMENT ON COLUMN sale_quote.customer_id IS '客户ID';
COMMENT ON COLUMN sale_quote.saler_id IS '业务员';
COMMENT ON COLUMN sale_quote.total_amount IS '单据金额';
COMMENT ON COLUMN sale_quote.tax_amount IS '税额';
COMMENT ON COLUMN sale_quote.net_amount IS '净金额';

-- 2. 销售报价单明细表 (sale_quote_item)
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

COMMENT ON TABLE sale_quote_item IS '销售报价单明细表';

-- 3. 销售订单主表 (sale_order)
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

-- 4. 销售订单明细表 (sale_order_item)
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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (order_id) REFERENCES sale_order(order_id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    FOREIGN KEY (unit_id) REFERENCES unit_of_measure(unit_id)
);

COMMENT ON TABLE sale_order_item IS '销售订单明细表';

-- 5. 销售出库单主表 (sale_delivery)
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

COMMENT ON TABLE sale_delivery IS '销售出库单主表';

-- 6. 销售出库单明细表 (sale_delivery_item)
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

COMMENT ON TABLE sale_delivery_item IS '销售出库单明细表';

-- 7. 销售退货单主表 (sale_return)
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

COMMENT ON TABLE sale_return IS '销售退货单主表';

-- 8. 销售退货单明细表 (sale_return_item)
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

COMMENT ON TABLE sale_return_item IS '销售退货单明细表';

-- 9. 销售发票主表 (sale_invoice)
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

COMMENT ON TABLE sale_invoice IS '销售发票主表';

-- 10. 销售发票明细表 (sale_invoice_item)
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

COMMENT ON TABLE sale_invoice_item IS '销售发票明细表';

-- 创建索引
CREATE INDEX idx_sale_quote_no ON sale_quote(quote_no);
CREATE INDEX idx_sale_quote_customer ON sale_quote(customer_id);
CREATE INDEX idx_sale_quote_date ON sale_quote(quote_date);
CREATE INDEX idx_sale_quote_item ON sale_quote_item(quote_id);
CREATE INDEX idx_sale_order_no ON sale_order(order_no);
CREATE INDEX idx_sale_order_customer ON sale_order(customer_id);
CREATE INDEX idx_sale_order_date ON sale_order(order_date);
CREATE INDEX idx_sale_order_item ON sale_order_item(order_id);
CREATE INDEX idx_sale_delivery_no ON sale_delivery(delivery_no);
CREATE INDEX idx_sale_delivery_customer ON sale_delivery(customer_id);
CREATE INDEX idx_sale_delivery_date ON sale_delivery(delivery_date);
CREATE INDEX idx_sale_delivery_item ON sale_delivery_item(delivery_id);
CREATE INDEX idx_sale_return_no ON sale_return(return_no);
CREATE INDEX idx_sale_return_customer ON sale_return(customer_id);
CREATE INDEX idx_sale_return_date ON sale_return(return_date);
CREATE INDEX idx_sale_return_item ON sale_return_item(return_id);
CREATE INDEX idx_sale_invoice_no ON sale_invoice(invoice_no);
CREATE INDEX idx_sale_invoice_customer ON sale_invoice(customer_id);
CREATE INDEX idx_sale_invoice_date ON sale_invoice(invoice_date);
CREATE INDEX idx_sale_invoice_item ON sale_invoice_item(invoice_id);

-- ============================================================================
-- 销售管理模块建表完成
-- ============================================================================
