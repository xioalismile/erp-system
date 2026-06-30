-- =====================================================
-- 智能ERP系统 数据库初始化脚本
-- 数据库: PostgreSQL 15+
-- =====================================================

-- 创建数据库
CREATE DATABASE erp_db ENCODING 'UTF8';
\c erp_db;

-- 创建用户
CREATE USER erp_user WITH PASSWORD 'Erp@2026';
GRANT ALL PRIVILEGES ON DATABASE erp_db TO erp_user;
GRANT ALL ON SCHEMA public TO erp_user;

-- =====================================================
-- 1. 系统用户表
-- =====================================================
CREATE TABLE sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50) UNIQUE NOT NULL,
    password    VARCHAR(200) NOT NULL,
    real_name   VARCHAR(100),
    department  VARCHAR(100),
    role        VARCHAR(50) DEFAULT 'user',
    enabled     BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted     INT DEFAULT 0
);

-- 默认管理员 (密码: admin123, 生产环境请修改)
INSERT INTO sys_user (username, password, real_name, department, role) VALUES
('admin', 'admin123', '系统管理员', '信息部', 'admin'),
('sale01', '123456', '张三', '销售一部', 'user'),
('store01', '123456', '李四', '仓库', 'user');

-- =====================================================
-- 2. 客户表
-- =====================================================
CREATE TABLE customer (
    id              BIGSERIAL PRIMARY KEY,
    customer_code   VARCHAR(50) UNIQUE NOT NULL,
    customer_name   VARCHAR(200) NOT NULL,
    contact_person  VARCHAR(100),
    contact_phone   VARCHAR(50),
    address         VARCHAR(500),
    tax_number      VARCHAR(50),
    credit_limit    DECIMAL(18,2) DEFAULT 0,
    payment_method  VARCHAR(50) DEFAULT '月结30天',
    created_at      DATE DEFAULT CURRENT_DATE,
    updated_at      DATE,
    deleted         INT DEFAULT 0
);

-- =====================================================
-- 2.1 供应商表
-- =====================================================
CREATE TABLE supplier (
    id              BIGSERIAL PRIMARY KEY,
    supplier_code   VARCHAR(50) UNIQUE NOT NULL,
    supplier_name   VARCHAR(200) NOT NULL,
    contact_person  VARCHAR(100),
    contact_phone   VARCHAR(50),
    address         VARCHAR(500),
    tax_number      VARCHAR(50),
    credit_limit    DECIMAL(18,2) DEFAULT 0,
    payment_method  VARCHAR(50) DEFAULT '月结30天',
    lead_time_days  INT DEFAULT 7,
    deleted         INT DEFAULT 0
);

-- =====================================================
-- 2.2 仓库表
-- =====================================================
CREATE TABLE warehouse (
    id              BIGSERIAL PRIMARY KEY,
    warehouse_code  VARCHAR(50) UNIQUE NOT NULL,
    warehouse_name  VARCHAR(200) NOT NULL,
    address         VARCHAR(500),
    contact_person  VARCHAR(100),
    contact_phone   VARCHAR(50),
    type            VARCHAR(20) DEFAULT '普通仓',
    status          VARCHAR(20) DEFAULT '启用',
    remark          TEXT,
    deleted         INT DEFAULT 0
);
CREATE INDEX idx_warehouse_code ON warehouse(warehouse_code);

-- =====================================================
-- 2.3 储位表
-- =====================================================
CREATE TABLE location (
    id              BIGSERIAL PRIMARY KEY,
    warehouse_code  VARCHAR(50) NOT NULL,
    location_code   VARCHAR(50) NOT NULL,
    zone_code       VARCHAR(20),
    rack_code       VARCHAR(20),
    shelf_code      VARCHAR(20),
    tier_code       VARCHAR(20),
    status          VARCHAR(20) DEFAULT '可用',
    usage_type      VARCHAR(20) DEFAULT '普通',
    remark          TEXT,
    deleted         INT DEFAULT 0
);
CREATE UNIQUE INDEX idx_location_unique ON location(warehouse_code, location_code);
CREATE INDEX idx_location_warehouse ON location(warehouse_code);

-- =====================================================
-- 3. 物料表
-- =====================================================
CREATE TABLE material (
    id              BIGSERIAL PRIMARY KEY,
    material_code   VARCHAR(50) UNIQUE NOT NULL,
    material_name   VARCHAR(200) NOT NULL,
    specification   VARCHAR(200),
    unit            VARCHAR(20) DEFAULT '个',
    category        VARCHAR(50),
    default_warehouse VARCHAR(50) DEFAULT 'WH001',
    purchase_price  DECIMAL(18,4),
    sale_price      DECIMAL(18,4),
    safety_stock    DECIMAL(18,2) DEFAULT 0,
    lead_time_days  INT DEFAULT 7,
    batch_managed   INT DEFAULT 0,
    has_expiry_date INT DEFAULT 0,
    expiry_days     INT DEFAULT 30,
    deleted         INT DEFAULT 0
);

-- =====================================================
-- 4. BOM表
-- =====================================================
CREATE TABLE bom (
    id                  BIGSERIAL PRIMARY KEY,
    parent_material_code VARCHAR(50) NOT NULL,
    child_material_code  VARCHAR(50) NOT NULL,
    usage_quantity      DECIMAL(18,6) NOT NULL,
    version             VARCHAR(20) DEFAULT '1.0',
    effective_date      DATE,
    expiry_date         DATE,
    deleted             INT DEFAULT 0
);

-- =====================================================
-- 5. 销售订单主表
-- =====================================================
CREATE TABLE sale_order (
    id                  BIGSERIAL PRIMARY KEY,
    order_no            VARCHAR(50) UNIQUE NOT NULL,
    order_date          DATE NOT NULL,
    customer_id         BIGINT,
    customer_code       VARCHAR(50),
    customer_name       VARCHAR(200),
    contact_person      VARCHAR(100),
    contact_phone       VARCHAR(50),
    delivery_address    VARCHAR(500),
    delivery_method     VARCHAR(50),
    logistics_no        VARCHAR(100),
    salesperson         VARCHAR(50),
    department          VARCHAR(100),
    currency            VARCHAR(20) DEFAULT 'CNY',
    exchange_rate       DECIMAL(18,6) DEFAULT 1.000000,
    payment_method      VARCHAR(50),
    expected_delivery_date DATE,
    actual_ship_date    DATE,
    total_amount        DECIMAL(18,2) DEFAULT 0,
    freight             DECIMAL(18,2) DEFAULT 0,
    discount_amount     DECIMAL(18,2) DEFAULT 0,
    tax_amount          DECIMAL(18,2) DEFAULT 0,
    total_with_tax      DECIMAL(18,2) DEFAULT 0,
    status              VARCHAR(20) DEFAULT '草稿',
    source_channel      VARCHAR(20) DEFAULT 'MANUAL',
    invoice_status      VARCHAR(20) DEFAULT '未开票',
    invoice_no          VARCHAR(50),
    payment_status      VARCHAR(20) DEFAULT '未收款',
    received_amount     DECIMAL(18,2) DEFAULT 0,
    auditor             VARCHAR(50),
    audit_date          DATE,
    remark              TEXT,
    created_by          VARCHAR(50),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(50),
    updated_at          TIMESTAMP,
    deleted             INT DEFAULT 0
);
CREATE INDEX idx_sale_order_no ON sale_order(order_no);
CREATE INDEX idx_sale_order_customer ON sale_order(customer_name);
CREATE INDEX idx_sale_order_status ON sale_order(status);

-- =====================================================
-- 5.1 采购订单主表
-- =====================================================
CREATE TABLE purchase_order (
    id                  BIGSERIAL PRIMARY KEY,
    order_no            VARCHAR(50) UNIQUE NOT NULL,
    order_date          DATE NOT NULL,
    supplier_id         BIGINT,
    supplier_code       VARCHAR(50),
    supplier_name       VARCHAR(200),
    contact_person      VARCHAR(100),
    contact_phone       VARCHAR(50),
    delivery_address    VARCHAR(500),
    buyer               VARCHAR(50),
    department          VARCHAR(100),
    currency            VARCHAR(20) DEFAULT 'CNY',
    exchange_rate       DECIMAL(18,6) DEFAULT 1.000000,
    payment_method      VARCHAR(50),
    expected_delivery_date DATE,
    source_type         VARCHAR(20) DEFAULT 'MANUAL',
    source_order_no     VARCHAR(50),
    total_amount        DECIMAL(18,2) DEFAULT 0,
    tax_amount          DECIMAL(18,2) DEFAULT 0,
    total_with_tax      DECIMAL(18,2) DEFAULT 0,
    status              VARCHAR(20) DEFAULT '草稿',
    inbound_status      VARCHAR(20) DEFAULT '未入库',
    auditor             VARCHAR(50),
    audit_date          DATE,
    remark              TEXT,
    created_by          VARCHAR(50),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(50),
    updated_at          TIMESTAMP,
    deleted             INT DEFAULT 0
);
CREATE INDEX idx_purchase_order_no ON purchase_order(order_no);

-- =====================================================
-- 5.2 采购订单明细表
-- =====================================================
CREATE TABLE purchase_order_item (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    line_no         INT DEFAULT 10,
    material_id     BIGINT,
    material_code   VARCHAR(50),
    material_name   VARCHAR(200),
    specification   VARCHAR(200),
    unit            VARCHAR(20),
    warehouse_code  VARCHAR(50),
    quantity        DECIMAL(18,2) NOT NULL,
    received_quantity DECIMAL(18,2) DEFAULT 0,
    unit_price      DECIMAL(18,4) NOT NULL,
    discount_rate   DECIMAL(5,2) DEFAULT 0,
    discounted_price DECIMAL(18,4),
    amount          DECIMAL(18,2),
    tax_rate        DECIMAL(5,2) DEFAULT 13,
    tax_amount      DECIMAL(18,2),
    total_amount    DECIMAL(18,2),
    delivery_date   DATE,
    suggested_order_date DATE,
    source_order_item_id BIGINT,
    remark          TEXT,
    deleted         INT DEFAULT 0
);
CREATE INDEX idx_purchase_item_order ON purchase_order_item(order_id);

-- =====================================================
-- 6. 销售订单明细表
-- =====================================================
CREATE TABLE sale_order_item (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    line_no         INT DEFAULT 10,
    material_id     BIGINT,
    material_code   VARCHAR(50),
    material_name   VARCHAR(200),
    specification   VARCHAR(200),
    unit            VARCHAR(20),
    warehouse_code  VARCHAR(50),
    location_code   VARCHAR(50),
    batch_no        VARCHAR(50),
    quantity        DECIMAL(18,2) NOT NULL,
    shipped_quantity DECIMAL(18,2) DEFAULT 0,
    unit_price      DECIMAL(18,4) NOT NULL,
    discount_rate   DECIMAL(5,2) DEFAULT 0,
    discounted_price DECIMAL(18,4),
    amount          DECIMAL(18,2),
    tax_rate        DECIMAL(5,2) DEFAULT 13,
    tax_amount      DECIMAL(18,2),
    total_amount    DECIMAL(18,2),
    is_gift         INT DEFAULT 0,
    delivery_date   DATE,
    remark          TEXT,
    deleted         INT DEFAULT 0
);
CREATE INDEX idx_sale_item_order ON sale_order_item(order_id);

-- =====================================================
-- 7. 库存表
-- =====================================================
CREATE TABLE inventory (
    id              BIGSERIAL PRIMARY KEY,
    material_code   VARCHAR(50) NOT NULL,
    material_name   VARCHAR(200),
    warehouse_code  VARCHAR(50) DEFAULT 'WH001',
    location_code   VARCHAR(50),
    quantity        DECIMAL(18,2) DEFAULT 0,
    safety_stock    DECIMAL(18,2) DEFAULT 0,
    locked_quantity DECIMAL(18,2) DEFAULT 0,
    batch_managed   INT DEFAULT 0,
    deleted         INT DEFAULT 0
);
CREATE INDEX idx_inventory_material ON inventory(material_code);
CREATE INDEX idx_inventory_warehouse ON inventory(warehouse_code);

-- =====================================================
-- 7.1 批次库存表
-- =====================================================
CREATE TABLE inventory_batch (
    id              BIGSERIAL PRIMARY KEY,
    batch_no        VARCHAR(50) UNIQUE NOT NULL,
    material_code   VARCHAR(50) NOT NULL,
    material_name   VARCHAR(200),
    warehouse_code  VARCHAR(50) DEFAULT 'WH001',
    location_code   VARCHAR(50),
    quantity        DECIMAL(18,2) DEFAULT 0,
    locked_quantity DECIMAL(18,2) DEFAULT 0,
    production_date DATE,
    expiry_date     DATE,
    supplier_code   VARCHAR(50),
    supplier_name   VARCHAR(200),
    lot_no          VARCHAR(50),
    grade           VARCHAR(20),
    remark          TEXT,
    deleted         INT DEFAULT 0
);
CREATE INDEX idx_batch_material ON inventory_batch(material_code);
CREATE INDEX idx_batch_warehouse ON inventory_batch(warehouse_code);
CREATE INDEX idx_batch_expiry ON inventory_batch(expiry_date);

-- =====================================================
-- 7.2 库存台账表
-- =====================================================
CREATE TABLE inventory_transaction (
    id              BIGSERIAL PRIMARY KEY,
    document_no     VARCHAR(50),
    document_type   VARCHAR(30),
    business_type   VARCHAR(50),
    material_code   VARCHAR(50) NOT NULL,
    material_name   VARCHAR(200),
    warehouse_code  VARCHAR(50),
    warehouse_name  VARCHAR(200),
    location_code   VARCHAR(50),
    batch_no        VARCHAR(50),
    quantity        DECIMAL(18,6) NOT NULL,
    balance_quantity DECIMAL(18,6),
    source_id       BIGINT,
    source_type     VARCHAR(50),
    operator        VARCHAR(50),
    remark          TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         INT DEFAULT 0
);
CREATE INDEX idx_tx_material ON inventory_transaction(material_code);
CREATE INDEX idx_tx_document ON inventory_transaction(document_no);

-- =====================================================
-- 9. 财务模块表
-- =====================================================
CREATE TABLE accounts_receivable (
    id              BIGSERIAL PRIMARY KEY,
    ar_no           VARCHAR(50) UNIQUE NOT NULL,
    source_type     VARCHAR(30),
    source_id       BIGINT,
    source_no       VARCHAR(50),
    customer_id     BIGINT,
    customer_code   VARCHAR(50),
    customer_name   VARCHAR(200),
    total_amount    DECIMAL(18,2),
    received_amount DECIMAL(18,2) DEFAULT 0,
    balance_amount  DECIMAL(18,2),
    currency        VARCHAR(20) DEFAULT 'CNY',
    payment_method  VARCHAR(50),
    expected_date   DATE,
    settled_date    DATE,
    status          VARCHAR(20) DEFAULT '待收款',
    write_off_status VARCHAR(20) DEFAULT '未核销',
    remark          TEXT,
    created_by      VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(50),
    updated_at      TIMESTAMP,
    deleted         INT DEFAULT 0
);

CREATE TABLE accounts_payable (
    id              BIGSERIAL PRIMARY KEY,
    ap_no           VARCHAR(50) UNIQUE NOT NULL,
    source_type     VARCHAR(30),
    source_id       BIGINT,
    source_no       VARCHAR(50),
    supplier_id     BIGINT,
    supplier_code   VARCHAR(50),
    supplier_name   VARCHAR(200),
    total_amount    DECIMAL(18,2),
    paid_amount     DECIMAL(18,2) DEFAULT 0,
    balance_amount  DECIMAL(18,2),
    currency        VARCHAR(20) DEFAULT 'CNY',
    payment_method  VARCHAR(50),
    expected_date   DATE,
    settled_date    DATE,
    status          VARCHAR(20) DEFAULT '待付款',
    write_off_status VARCHAR(20) DEFAULT '未核销',
    remark          TEXT,
    created_by      VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(50),
    updated_at      TIMESTAMP,
    deleted         INT DEFAULT 0
);

CREATE TABLE payment_receipt (
    id              BIGSERIAL PRIMARY KEY,
    receipt_no      VARCHAR(50) UNIQUE NOT NULL,
    receipt_date    DATE,
    ar_id           BIGINT,
    ar_no           VARCHAR(50),
    customer_id     BIGINT,
    customer_code   VARCHAR(50),
    customer_name   VARCHAR(200),
    sale_order_id   BIGINT,
    sale_order_no   VARCHAR(50),
    amount          DECIMAL(18,2),
    payment_method  VARCHAR(50),
    bank_account    VARCHAR(50),
    transaction_no  VARCHAR(100),
    received_by     VARCHAR(50),
    confirmed_by    VARCHAR(50),
    confirmed_date  DATE,
    status          VARCHAR(20) DEFAULT '待确认',
    remark          TEXT,
    created_by      VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(50),
    updated_at      TIMESTAMP,
    deleted         INT DEFAULT 0
);

CREATE TABLE payment_voucher (
    id              BIGSERIAL PRIMARY KEY,
    voucher_no      VARCHAR(50) UNIQUE NOT NULL,
    payment_date    DATE,
    ap_id           BIGINT,
    ap_no           VARCHAR(50),
    supplier_id     BIGINT,
    supplier_code   VARCHAR(50),
    supplier_name   VARCHAR(200),
    purchase_order_id BIGINT,
    purchase_order_no VARCHAR(50),
    amount          DECIMAL(18,2),
    payment_method  VARCHAR(50),
    bank_account    VARCHAR(50),
    transaction_no  VARCHAR(100),
    paid_by         VARCHAR(50),
    confirmed_by    VARCHAR(50),
    confirmed_date  DATE,
    status          VARCHAR(20) DEFAULT '待确认',
    remark          TEXT,
    created_by      VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(50),
    updated_at      TIMESTAMP,
    deleted         INT DEFAULT 0
);

-- =====================================================
-- 8. AI审计日志表
-- =====================================================
CREATE TABLE ai_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(64),
    channel         VARCHAR(20),
    channel_user_id VARCHAR(100),
    erp_user_id     BIGINT,
    session_id      VARCHAR(64),
    api_path        VARCHAR(200),
    input_prompt    TEXT,
    llm_raw_params  TEXT,
    llm_model       VARCHAR(50),
    intent          VARCHAR(50),
    response_status VARCHAR(20),
    before_data     TEXT,
    after_data      TEXT,
    confirm_status  VARCHAR(20) DEFAULT '无需确认',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ai_audit_trace ON ai_audit_log(trace_id);
CREATE INDEX idx_ai_audit_user ON ai_audit_log(erp_user_id);

-- =====================================================
-- 初始化演示数据
-- =====================================================

-- 仓库数据
INSERT INTO warehouse (warehouse_code, warehouse_name, address, contact_person, contact_phone, type, status) VALUES
('WH001', '主仓库', '深圳市宝安区西乡工业园A栋1楼', '李四', '13800138001', '普通仓', '启用'),
('WH002', '成品仓', '深圳市宝安区西乡工业园B栋1楼', '王五', '13800138002', '成品仓', '启用'),
('WH003', '原料仓', '深圳市宝安区西乡工业园C栋1楼', '赵六', '13800138003', '原料仓', '启用');

-- 储位数据
INSERT INTO location (warehouse_code, location_code, zone_code, rack_code, shelf_code, tier_code, status) VALUES
('WH001', 'A01-01-01', 'A', 'A01', '01', '01', '可用'),
('WH001', 'A01-01-02', 'A', 'A01', '01', '02', '可用'),
('WH001', 'A01-02-01', 'A', 'A01', '02', '01', '可用'),
('WH001', 'B01-01-01', 'B', 'B01', '01', '01', '可用'),
('WH002', 'C01-01-01', 'C', 'C01', '01', '01', '可用'),
('WH002', 'C01-01-02', 'C', 'C01', '01', '02', '可用'),
('WH003', 'D01-01-01', 'D', 'D01', '01', '01', '可用'),
('WH003', 'D01-02-01', 'D', 'D01', '02', '01', '可用');

-- 客户数据
INSERT INTO customer (customer_code, customer_name, contact_person, contact_phone, address, payment_method) VALUES
('CRM-001', '华南精密制造有限公司', '张工', '0755-88881234', '深圳市宝安区西乡街道XX工业园A栋3楼', '月结30天'),
('CRM-002', '东方机械集团', '王经理', '021-66667788', '上海市浦东新区张江高科技园区', '月结60天'),
('CRM-003', '北方重工股份', '李总', '010-88889999', '北京市昌平区科技园路8号', '款到发货');

-- 供应商数据
INSERT INTO supplier (supplier_code, supplier_name, contact_person, contact_phone, address, payment_method, lead_time_days) VALUES
('SUP-001', '深圳精密轴承有限公司', '陈经理', '0755-26551234', '深圳市南山区科技园路10号', '月结30天', 7),
('SUP-002', '上海传动设备股份', '刘总', '021-58889999', '上海市闵行区莘庄工业区', '月结60天', 14),
('SUP-003', '北京控制技术研究所', '王工', '010-82658888', '北京市海淀区中关村大街18号', '款到发货', 21);

-- 物料数据 (部分启用批次管理)
INSERT INTO material (material_code, material_name, specification, unit, category, default_warehouse, purchase_price, sale_price, safety_stock, lead_time_days, batch_managed, has_expiry_date, expiry_days) VALUES
('MAT-001', '精密轴承A', '6204-2RS', '个', '轴承', 'WH001', 18.00, 25.50, 200, 7, 0, 0, 30),
('MAT-002', '传动轴B', 'D20xL300', '根', '轴类', 'WH001', 45.00, 68.00, 50, 14, 0, 0, 30),
('MAT-003', '控制板C', 'V2.0', '片', '电子件', 'WH001', 80.00, 120.00, 30, 21, 1, 1, 90),
('MAT-004', '外壳组件', 'BOX-100', '套', '结构件', 'WH001', 50.00, 88.00, 100, 10, 0, 0, 30),
('MAT-005', '螺丝M8', 'D8x30', '个', '标准件', 'WH001', 0.50, 1.00, 2000, 3, 0, 0, 30),
('MAT-006', '密封胶', '50ml/支', '支', '化工', 'WH003', 15.00, 25.00, 100, 7, 1, 1, 180),
('MAT-007', '润滑油', '1L/瓶', '瓶', '化工', 'WH003', 35.00, 55.00, 50, 10, 1, 1, 365);

-- 库存数据
INSERT INTO inventory (material_code, material_name, warehouse_code, location_code, quantity, safety_stock, batch_managed) VALUES
('MAT-001', '精密轴承A', 'WH001', 'A01-01-01', 500, 200, 0),
('MAT-002', '传动轴B', 'WH001', 'A01-01-02', 80, 50, 0),
('MAT-004', '外壳组件', 'WH001', 'A01-02-01', 120, 100, 0),
('MAT-005', '螺丝M8', 'WH001', 'B01-01-01', 3000, 2000, 0);

-- 批次库存数据 (批次管理物料)
INSERT INTO inventory_batch (batch_no, material_code, material_name, warehouse_code, location_code, quantity, production_date, expiry_date, supplier_code, supplier_name) VALUES
('B202606010001', 'MAT-003', '控制板C', 'WH001', 'A01-01-01', 50, '2026-06-01', '2026-09-01', 'SUP-003', '北京控制技术研究所'),
('B202606100002', 'MAT-003', '控制板C', 'WH001', 'A01-01-02', 30, '2026-06-10', '2026-09-10', 'SUP-003', '北京控制技术研究所'),
('B202605010003', 'MAT-006', '密封胶', 'WH003', 'D01-01-01', 200, '2026-05-01', '2026-11-01', 'SUP-001', '深圳精密轴承有限公司'),
('B202606150004', 'MAT-006', '密封胶', 'WH003', 'D01-01-01', 150, '2026-06-15', '2026-12-15', 'SUP-001', '深圳精密轴承有限公司'),
('B202604010005', 'MAT-007', '润滑油', 'WH003', 'D01-02-01', 80, '2026-04-01', '2027-04-01', 'SUP-002', '上海传动设备股份');

-- BOM数据
INSERT INTO bom (parent_material_code, child_material_code, usage_quantity, version, effective_date) VALUES
('MAT-001', 'MAT-004', 0.5, '1.0', '2026-01-01'),
('MAT-001', 'MAT-005', 4, '1.0', '2026-01-01');

-- 授权
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO erp_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO erp_user;

-- 完成
SELECT '智能ERP数据库初始化完成!' AS message;