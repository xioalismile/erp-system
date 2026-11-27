-- 创建 cost_calculation 表
CREATE TABLE IF NOT EXISTS cost_calculation (
    id BIGSERIAL PRIMARY KEY,
    calc_no VARCHAR(50) NOT NULL UNIQUE,
    calc_name VARCHAR(200),
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    calc_date DATE,
    calc_method VARCHAR(50),
    quantity NUMERIC(18,4) DEFAULT 0,
    direct_material_cost NUMERIC(18,2) DEFAULT 0.00,
    direct_labor_cost NUMERIC(18,2) DEFAULT 0.00,
    manufacturing_overhead NUMERIC(18,2) DEFAULT 0.00,
    total_cost NUMERIC(18,2) DEFAULT 0.00,
    unit_cost NUMERIC(18,4) DEFAULT 0.0000,
    status VARCHAR(20) NOT NULL DEFAULT '草稿',
    remark VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

-- 创建 cost_calculation_item 表
CREATE TABLE IF NOT EXISTS cost_calculation_item (
    id BIGSERIAL PRIMARY KEY,
    calc_id BIGINT NOT NULL,
    cost_type VARCHAR(50),
    cost_name VARCHAR(100),
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    unit VARCHAR(20),
    quantity NUMERIC(18,4) DEFAULT 0,
    unit_price NUMERIC(18,4) DEFAULT 0,
    amount NUMERIC(18,2) DEFAULT 0.00,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (calc_id) REFERENCES cost_calculation(id) ON DELETE CASCADE
);

-- 授予 erp_user 权限
GRANT ALL PRIVILEGES ON TABLE cost_calculation TO erp_user;
GRANT ALL PRIVILEGES ON TABLE cost_calculation_item TO erp_user;
GRANT USAGE, SELECT ON SEQUENCE cost_calculation_id_seq TO erp_user;
GRANT USAGE, SELECT ON SEQUENCE cost_calculation_item_id_seq TO erp_user;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_cost_calc_no ON cost_calculation(calc_no);

SELECT 'cost_calculation tables created' AS result;
