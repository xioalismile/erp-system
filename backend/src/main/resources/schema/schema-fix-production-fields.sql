-- ============================================================
-- 生产模块字段补齐脚本
-- 对齐实体类与 PostgreSQL 表结构（31 个缺失字段）
-- ============================================================

-- 1. bom_item 表（缺 6 字段）
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS qty_type VARCHAR(20);
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS effective_date DATE;
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS expiry_date DATE;
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS process_no VARCHAR(50);
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS is_key_part SMALLINT DEFAULT 0;
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS supply_type VARCHAR(20);

-- 2. routing 表（缺 3 字段）
ALTER TABLE routing ADD COLUMN IF NOT EXISTS material_code VARCHAR(50);
ALTER TABLE routing ADD COLUMN IF NOT EXISTS material_name VARCHAR(200);
ALTER TABLE routing ADD COLUMN IF NOT EXISTS total_process_count INTEGER DEFAULT 0;

-- 3. routing_operation 表（缺 3 字段）
ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS work_center_name VARCHAR(200);
ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS is_outsourced SMALLINT DEFAULT 0;
ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS prev_process_no VARCHAR(50);

-- 4. production_order 表（缺 5 字段）
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS model VARCHAR(100);
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS bom_version VARCHAR(20);
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS routing_version VARCHAR(20);
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS work_center_id BIGINT;
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS is_closed SMALLINT DEFAULT 0;

-- 5. production_receipt 表（缺 2 字段）
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS work_order_no VARCHAR(50);
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS process_card_no VARCHAR(50);

-- 6. operation_report 表（缺 2 字段）
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS process_no VARCHAR(50);
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS equipment_id BIGINT;
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);

-- 6.2 subcontract_order 表 - 补充来源单据字段
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);

-- 7. mrp_plan 表（缺 10 字段）
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS plan_horizon INTEGER;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS demand_source VARCHAR(50);
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_so SMALLINT DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_forecast SMALLINT DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_safety_stock SMALLINT DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_open_po SMALLINT DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_open_mo SMALLINT DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_onhand_stock SMALLINT DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS lot_size NUMERIC(18,4) DEFAULT 1.0000;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS min_order_qty NUMERIC(18,4) DEFAULT 1.0000;
