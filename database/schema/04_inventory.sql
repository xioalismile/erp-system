-- ============================================================================
-- 智能ERP系统 - PostgreSQL 数据库建表脚本
-- 模块：库存管理模块
-- 版本：V1.0
-- 日期：2026-06-22
-- 说明：共16张表，包含入库、出库、调拨、盘点等业务单据
-- ============================================================================

-- 1. 即时库存表 (inventory_current)
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
COMMENT ON COLUMN inventory_current.stock_id IS '仓库ID';
COMMENT ON COLUMN inventory_current.bin_id IS '货位ID';
COMMENT ON COLUMN inventory_current.material_id IS '物料ID';
COMMENT ON COLUMN inventory_current.batch_no IS '批次号';
COMMENT ON COLUMN inventory_current.quantity IS '库存数量';
COMMENT ON COLUMN inventory_current.available_quantity IS '可用数量';
COMMENT ON COLUMN inventory_current.frozen_quantity IS '冻结数量';
COMMENT ON COLUMN inventory_current.unit_cost IS '单位成本';

-- 2. 库存台账表 (inventory_ledger)
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

-- 3. 其他入库单主表 (other_receipt)
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

COMMENT ON TABLE other_receipt IS '其他入库单主表';

-- 4. 其他入库单明细表 (other_receipt_item)
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

COMMENT ON TABLE other_receipt_item IS '其他入库单明细表';

-- 5. 其他出库单主表 (other_issue)
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

COMMENT ON TABLE other_issue IS '其他出库单主表';

-- 6. 其他出库单明细表 (other_issue_item)
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

COMMENT ON TABLE other_issue_item IS '其他出库单明细表';

-- 7. 生产领料单主表 (material_requisition)
CREATE TABLE material_requisition (
    requisition_id BIGSERIAL PRIMARY KEY,
    requisition_no VARCHAR(50) NOT NULL UNIQUE,
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

COMMENT ON TABLE material_requisition IS '生产领料单主表';

-- 8. 生产领料单明细表 (material_requisition_item)
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

COMMENT ON TABLE material_requisition_item IS '生产领料单明细表';

-- 9. 调拨单主表 (stock_transfer)
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

COMMENT ON TABLE stock_transfer IS '调拨单主表';

-- 10. 调拨单明细表 (stock_transfer_item)
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

COMMENT ON TABLE stock_transfer_item IS '调拨单明细表';

-- 11. 盘点单主表 (stock_check)
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

COMMENT ON TABLE stock_check IS '盘点单主表';

-- 12. 盘点单明细表 (stock_check_item)
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

COMMENT ON TABLE stock_check_item IS '盘点单明细表';

-- 13. 库存冻结记录表 (inventory_freeze)
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

COMMENT ON TABLE inventory_freeze IS '库存冻结记录表';

-- 14. 批次表 (batch)
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

COMMENT ON TABLE batch IS '批次表';

-- 15. 收发存汇总表 (inventory_summary)
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

COMMENT ON TABLE inventory_summary IS '收发存汇总表';

-- 16. 库存预警表 (inventory_warning)
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

COMMENT ON TABLE inventory_warning IS '库存预警表';

-- 创建索引
CREATE INDEX idx_inventory_current_stock ON inventory_current(stock_id);
CREATE INDEX idx_inventory_current_material ON inventory_current(material_id);
CREATE INDEX idx_inventory_current_batch ON inventory_current(batch_no);
CREATE INDEX idx_inventory_ledger_date ON inventory_ledger(trans_date);
CREATE INDEX idx_inventory_ledger_material ON inventory_ledger(material_id);
CREATE INDEX idx_inventory_ledger_type ON inventory_ledger(trans_type);
CREATE INDEX idx_other_receipt_no ON other_receipt(receipt_no);
CREATE INDEX idx_other_receipt_date ON other_receipt(receipt_date);
CREATE INDEX idx_other_receipt_item ON other_receipt_item(receipt_id);
CREATE INDEX idx_other_issue_no ON other_issue(issue_no);
CREATE INDEX idx_other_issue_date ON other_issue(issue_date);
CREATE INDEX idx_other_issue_item ON other_issue_item(issue_id);
CREATE INDEX idx_material_requisition_no ON material_requisition(requisition_no);
CREATE INDEX idx_material_requisition_date ON material_requisition(requisition_date);
CREATE INDEX idx_material_requisition_item ON material_requisition_item(requisition_id);
CREATE INDEX idx_stock_transfer_no ON stock_transfer(transfer_no);
CREATE INDEX idx_stock_transfer_date ON stock_transfer(transfer_date);
CREATE INDEX idx_stock_transfer_item ON stock_transfer_item(transfer_id);
CREATE INDEX idx_stock_check_no ON stock_check(check_no);
CREATE INDEX idx_stock_check_date ON stock_check(check_date);
CREATE INDEX idx_stock_check_item ON stock_check_item(check_id);
CREATE INDEX idx_inventory_freeze ON inventory_freeze(stock_id, material_id);
CREATE INDEX idx_batch_no ON batch(batch_no);
CREATE INDEX idx_batch_material ON batch(material_id);
CREATE INDEX idx_inventory_summary_period ON inventory_summary(period_year, period_month);
CREATE INDEX idx_inventory_warning_material ON inventory_warning(material_id);
CREATE INDEX idx_inventory_warning_type ON inventory_warning(warning_type);

-- ============================================================================
-- 库存管理模块建表完成
-- ============================================================================
