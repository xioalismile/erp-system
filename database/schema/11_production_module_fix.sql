-- ============================================================================
-- 智能ERP系统 - PostgreSQL 生产管理模块字段补齐脚本
-- 对应文档第五部分生产管理模块 5.1-5.8
-- 说明：补齐缺失字段、创建缺失表、修正默认值
-- ============================================================================

-- ===========================================================================
-- 5.1 BOM物料清单 - 补齐缺失字段
-- ===========================================================================
ALTER TABLE bom ALTER COLUMN status SET DEFAULT '草稿';
ALTER TABLE bom ALTER COLUMN bom_version SET DEFAULT 'V1.0';

ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS qty_type VARCHAR(10) NOT NULL DEFAULT '固定';
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS effective_date DATE;
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS expiry_date DATE;
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS process_no VARCHAR(10);
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS is_key_part SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS supply_type VARCHAR(10) NOT NULL DEFAULT '领料';
ALTER TABLE bom_item ALTER COLUMN quantity TYPE NUMERIC(18,6);

-- ===========================================================================
-- 5.2 工艺路线 - 补齐缺失字段
-- ===========================================================================
ALTER TABLE routing ALTER COLUMN status SET DEFAULT '草稿';
ALTER TABLE routing ALTER COLUMN routing_version SET DEFAULT 'V1.0';
ALTER TABLE routing ADD COLUMN IF NOT EXISTS material_code VARCHAR(50);
ALTER TABLE routing ADD COLUMN IF NOT EXISTS material_name VARCHAR(200);
ALTER TABLE routing ADD COLUMN IF NOT EXISTS total_process_count INT DEFAULT 0;

ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS work_center_name VARCHAR(100);
ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS is_outsourced SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS prev_process_no VARCHAR(10);
ALTER TABLE routing_operation ALTER COLUMN operation_code TYPE VARCHAR(10);
ALTER TABLE routing_operation ALTER COLUMN process_hours TYPE NUMERIC(18,6);

-- ===========================================================================
-- 5.3 工作中心/车间 - 补齐缺失字段
-- ===========================================================================
ALTER TABLE work_center ADD COLUMN IF NOT EXISTS work_center_type VARCHAR(20) NOT NULL DEFAULT '生产线';
ALTER TABLE work_center ADD COLUMN IF NOT EXISTS capacity_unit VARCHAR(10) NOT NULL DEFAULT '小时';
ALTER TABLE work_center ADD COLUMN IF NOT EXISTS labor_rate NUMERIC(18,4) NOT NULL DEFAULT 0.0000;
ALTER TABLE work_center ADD COLUMN IF NOT EXISTS overhead_rate NUMERIC(18,4) NOT NULL DEFAULT 0.0000;
ALTER TABLE work_center ADD COLUMN IF NOT EXISTS max_workers INT DEFAULT 0;
ALTER TABLE work_center ADD COLUMN IF NOT EXISTS is_outsourced SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE work_center ADD COLUMN IF NOT EXISTS supplier_id BIGINT;
ALTER TABLE work_center ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
ALTER TABLE work_center ALTER COLUMN capacity_per_day TYPE NUMERIC(18,2);
ALTER TABLE work_center ALTER COLUMN capacity_per_day SET DEFAULT 8.00;

-- ===========================================================================
-- 5.4 生产工单 - 补齐缺失字段
-- ===========================================================================
ALTER TABLE production_order ALTER COLUMN status SET DEFAULT '草稿';
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS model VARCHAR(100);
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS bom_version VARCHAR(20);
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS routing_version VARCHAR(20);
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS work_center_id BIGINT;
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS is_closed SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE production_order ALTER COLUMN actual_start_date TYPE TIMESTAMP;
ALTER TABLE production_order ALTER COLUMN actual_end_date TYPE TIMESTAMP;

ALTER TABLE production_order_item ADD COLUMN IF NOT EXISTS supply_type VARCHAR(10) NOT NULL DEFAULT '领料';
ALTER TABLE production_order_item ADD COLUMN IF NOT EXISTS process_no VARCHAR(10);
ALTER TABLE production_order_item ALTER COLUMN bom_quantity TYPE NUMERIC(18,6);
ALTER TABLE production_order_item ALTER COLUMN issued_quantity TYPE NUMERIC(18,6);
ALTER TABLE production_order_item ALTER COLUMN specification TYPE VARCHAR(100);

-- ===========================================================================
-- 5.5 工序转移单 - 补齐缺失字段和明细表
-- ===========================================================================
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS material_id BIGINT;
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS material_name VARCHAR(200);
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS work_center_id BIGINT;
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS rework_qty NUMERIC(18,4) DEFAULT 0.0000;
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE process_transfer ALTER COLUMN transfer_type SET DEFAULT '正常转移';

-- 创建工序转移单明细表
CREATE TABLE IF NOT EXISTS process_transfer_item (
    item_id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    from_process_no VARCHAR(10) NOT NULL,
    to_process_no VARCHAR(10) NOT NULL,
    transfer_qty NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    scrap_qty NUMERIC(18,4) DEFAULT 0.0000,
    rework_qty NUMERIC(18,4) DEFAULT 0.0000,
    transfer_type VARCHAR(10) NOT NULL DEFAULT '正常转移',
    remark VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_pti_transfer ON process_transfer_item(transfer_id);

-- ===========================================================================
-- 5.6 工序汇报单 - 补齐缺失字段
-- ===========================================================================
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS process_no VARCHAR(10);
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS equipment_id BIGINT;
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);

-- ===========================================================================
-- 5.7 委外加工单 - 创建缺失表
-- ===========================================================================
CREATE TABLE IF NOT EXISTS subcontract_order (
    subcontract_id BIGSERIAL PRIMARY KEY,
    bill_no VARCHAR(50) NOT NULL UNIQUE,
    order_date DATE NOT NULL,
    material_id BIGINT NOT NULL,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    unit_id BIGINT,
    qty NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    supplier_id BIGINT NOT NULL,
    supplier_name VARCHAR(200),
    process_no VARCHAR(10),
    unit_price NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    total_price NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    plan_start_date DATE,
    plan_finish_date DATE,
    actual_start_date TIMESTAMP,
    actual_finish_date TIMESTAMP,
    completed_qty NUMERIC(18,4) NOT NULL DEFAULT 0.0000,
    remark VARCHAR(500),
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT '草稿',
    approval_status VARCHAR(10) NOT NULL DEFAULT '待审核',
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50),
    org_code VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_subcontract_no ON subcontract_order(bill_no);
CREATE INDEX IF NOT EXISTS idx_subcontract_supplier ON subcontract_order(supplier_id);

CREATE TABLE IF NOT EXISTS subcontract_order_item (
    item_id BIGSERIAL PRIMARY KEY,
    subcontract_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    material_name VARCHAR(200),
    unit_id BIGINT,
    base_qty NUMERIC(18,6) NOT NULL DEFAULT 0.000000,
    actual_qty NUMERIC(18,6) NOT NULL DEFAULT 0.000000,
    supply_type VARCHAR(10) NOT NULL DEFAULT '我方供料',
    remark VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (subcontract_id) REFERENCES subcontract_order(subcontract_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_subcontract_item ON subcontract_order_item(subcontract_id);

-- ===========================================================================
-- 5.8 MRP运算方案 - 补齐缺失字段
-- ===========================================================================
ALTER TABLE mrp_plan ALTER COLUMN plan_status SET DEFAULT '草稿';
ALTER TABLE mrp_plan ALTER COLUMN time_fence_days SET DEFAULT 7;
ALTER TABLE mrp_plan ALTER COLUMN lot_size_type SET DEFAULT '逐批法';
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS plan_horizon INT NOT NULL DEFAULT 90;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS demand_source VARCHAR(100);
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_so SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_forecast SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_safety_stock SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_open_po SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_open_mo SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_onhand_stock SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS lot_size NUMERIC(18,4) DEFAULT 0.0000;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS min_order_qty NUMERIC(18,4) DEFAULT 0.0000;

-- ===========================================================================
-- 生产入库单 - 补齐缺失字段（实体引用了work_order_no、process_card_no）
-- ===========================================================================
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS work_order_no VARCHAR(50);
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS process_card_no VARCHAR(50);

-- ============================================================================
-- 生产管理模块字段补齐完成
-- ============================================================================
