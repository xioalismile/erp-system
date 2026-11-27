-- ============================================================================
-- 智能ERP系统 - PostgreSQL 打样管理模块建表脚本
-- 对应文档第五部分补充：打样管理模块 5.9 打样单
-- ============================================================================

-- ===========================================================================
-- 打样单表头
-- ===========================================================================
CREATE TABLE IF NOT EXISTS sample_order (
    sample_id BIGSERIAL PRIMARY KEY,
    sample_no VARCHAR(50) NOT NULL UNIQUE,
    sample_name VARCHAR(200) NOT NULL,
    sample_type VARCHAR(20) NOT NULL DEFAULT '新产品',
    sample_stage VARCHAR(20) NOT NULL DEFAULT '原样',
    customer_id BIGINT,
    customer_name VARCHAR(200),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(50),
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    specification VARCHAR(200),
    unit_id BIGINT,
    sample_qty NUMERIC(18,4) NOT NULL DEFAULT 1.0000,
    demand_date DATE NOT NULL,
    plan_finish_date DATE NOT NULL,
    actual_finish_date DATE,
    department_id BIGINT,
    engineer_id BIGINT,
    priority VARCHAR(10) NOT NULL DEFAULT '普通',
    cost_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT '草稿',
    is_bom_generated SMALLINT NOT NULL DEFAULT 0,
    generated_bom_id BIGINT,
    remark VARCHAR(1000),
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    org_code VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_sample_no ON sample_order(sample_no);
CREATE INDEX IF NOT EXISTS idx_sample_customer ON sample_order(customer_id);
CREATE INDEX IF NOT EXISTS idx_sample_status ON sample_order(status);

-- ===========================================================================
-- 打样单表体（打样子件清单/样品BOM）
-- ===========================================================================
CREATE TABLE IF NOT EXISTS sample_order_item (
    item_id BIGSERIAL PRIMARY KEY,
    sample_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    child_material_id BIGINT NOT NULL,
    child_material_code VARCHAR(50),
    child_material_name VARCHAR(200),
    specification VARCHAR(200),
    unit_id BIGINT,
    base_qty NUMERIC(18,6) NOT NULL DEFAULT 0.000000,
    qty_type VARCHAR(10) NOT NULL DEFAULT '固定',
    scrap_rate NUMERIC(8,4) DEFAULT 0.0000,
    supply_type VARCHAR(10) NOT NULL DEFAULT '领料',
    is_key_part SMALLINT NOT NULL DEFAULT 0,
    unit_price NUMERIC(18,4) DEFAULT 0.0000,
    amount NUMERIC(18,2) DEFAULT 0.00,
    remark VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (sample_id) REFERENCES sample_order(sample_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sample_item_sample ON sample_order_item(sample_id);

-- ============================================================================
-- 打样管理模块建表完成
-- ============================================================================
