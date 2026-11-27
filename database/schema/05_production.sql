-- ============================================================================
-- 智能ERP系统 - PostgreSQL 数据库建表脚本
-- 模块：生产管理模块
-- 版本：V1.0
-- 日期：2026-06-22
-- 说明：共20张表，包含BOM、工艺路线、工单、汇报等业务单据
-- ============================================================================

-- 1. 物料清单(BOM)主表 (bom)
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

-- 2. 物料清单(BOM)明细表 (bom_item)
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

COMMENT ON TABLE bom_item IS '物料清单(BOM)明细表';

-- 3. 工艺路线主表 (routing)
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

COMMENT ON TABLE routing IS '工艺路线主表';

-- 4. 工艺路线工序表 (routing_operation)
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

COMMENT ON TABLE routing_operation IS '工艺路线工序表';

-- 5. 工作中心表 (work_center)
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

-- 6. 工序表 (workstation)
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

-- 7. MRP运算方案表 (mrp_plan)
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

COMMENT ON TABLE mrp_plan IS 'MRP运算方案';

-- 8. MRP需求明细表 (mrp_demand)
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

COMMENT ON TABLE mrp_demand IS 'MRP需求明细';

-- 9. MRP供应明细表 (mrp_supply)
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

COMMENT ON TABLE mrp_supply IS 'MRP供应明细';

-- 10. 生产工单主表 (production_order)
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

COMMENT ON TABLE production_order IS '生产工单主表';

-- 11. 生产工单明细表 (production_order_item)
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

COMMENT ON TABLE production_order_item IS '生产工单明细表';

-- 12. 工序流转卡主表 (process_card)
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

COMMENT ON TABLE process_card IS '工序流转卡主表';

-- 13. 工序流转卡明细表 (process_card_item)
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

COMMENT ON TABLE process_card_item IS '工序流转卡明细表';

-- 14. 工序汇报单主表 (operation_report)
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

COMMENT ON TABLE operation_report IS '工序汇报单主表';

-- 15. 工序汇报单明细表 (operation_report_item)
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

COMMENT ON TABLE operation_report_item IS '工序汇报单明细表';

-- 16. 生产入库单主表 (production_receipt)
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

COMMENT ON TABLE production_receipt IS '生产入库单主表';

-- 17. 生产入库单明细表 (production_receipt_item)
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

COMMENT ON TABLE production_receipt_item IS '生产入库单明细表';

-- 18. 质检单主表 (quality_check)
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

COMMENT ON TABLE quality_check IS '质检单主表';

-- 19. 质检单明细表 (quality_check_item)
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

COMMENT ON TABLE quality_check_item IS '质检单明细表';

-- 20. 班次表 (shift)
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

COMMENT ON TABLE shift IS '班次';

-- 创建索引
CREATE INDEX idx_bom_code ON bom(bom_code);
CREATE INDEX idx_bom_material ON bom(material_id);
CREATE INDEX idx_bom_item ON bom_item(bom_id);
CREATE INDEX idx_routing_code ON routing(routing_code);
CREATE INDEX idx_routing_material ON routing(material_id);
CREATE INDEX idx_routing_operation ON routing_operation(routing_id);
CREATE INDEX idx_work_center_code ON work_center(work_center_code);
CREATE INDEX idx_workstation_code ON workstation(workstation_code);
CREATE INDEX idx_mrp_plan_code ON mrp_plan(plan_code);
CREATE INDEX idx_mrp_plan_date ON mrp_plan(plan_date);
CREATE INDEX idx_mrp_demand_plan ON mrp_demand(plan_id);
CREATE INDEX idx_mrp_supply_plan ON mrp_supply(plan_id);
CREATE INDEX idx_production_order_no ON production_order(order_no);
CREATE INDEX idx_production_order_material ON production_order(material_id);
CREATE INDEX idx_production_order_date ON production_order(order_date);
CREATE INDEX idx_production_order_item ON production_order_item(order_id);
CREATE INDEX idx_process_card_no ON process_card(card_no);
CREATE INDEX idx_process_card_order ON process_card(production_order_id);
CREATE INDEX idx_process_card_item ON process_card_item(card_id);
CREATE INDEX idx_operation_report_no ON operation_report(report_no);
CREATE INDEX idx_operation_report_date ON operation_report(report_date);
CREATE INDEX idx_operation_report_item ON operation_report_item(report_id);
CREATE INDEX idx_production_receipt_no ON production_receipt(receipt_no);
CREATE INDEX idx_production_receipt_date ON production_receipt(receipt_date);
CREATE INDEX idx_production_receipt_item ON production_receipt_item(receipt_id);
CREATE INDEX idx_quality_check_no ON quality_check(check_no);
CREATE INDEX idx_quality_check_source ON quality_check(source_bill_type, source_bill_no);
CREATE INDEX idx_quality_check_item ON quality_check_item(check_id);
CREATE INDEX idx_shift_code ON shift(shift_code);

-- ============================================================================
-- 生产管理模块建表完成
-- ============================================================================
