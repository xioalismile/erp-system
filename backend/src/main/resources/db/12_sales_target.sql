-- ===========================================================================
-- 销售目标管理模块 - PostgreSQL 生产环境脚本
-- 包含两张表：
--   1. sales_target_company  公司年度销售目标（含自动增长配置）
--   2. sales_target_employee  销售人员年度目标分配（含完成情况）
-- 业务规则：
--   - 公司年度目标设置后，下一年度目标自动按增长率(默认30%)递增
--   - 销售目标需在年度开始前（如今年7-8月）分配到每位销售
--   - 完成情况由成交商机(actual_close_date 在目标年度内)自动汇总
--   - 未分配目标 = 公司目标 - 已分配目标，提示需要招聘新销售
-- ===========================================================================

-- 1. 公司年度销售目标表
CREATE TABLE IF NOT EXISTS sales_target_company (
    id BIGSERIAL PRIMARY KEY,
    target_year INTEGER NOT NULL,
    annual_amount NUMERIC(18,2) NOT NULL,                 -- 年度销售目标金额
    growth_rate NUMERIC(5,2) DEFAULT 30.00,                -- 自动增长率(%)，默认30%
    auto_generate_next_year SMALLINT DEFAULT 1,            -- 是否自动生成下年度目标: 1=是 0=否
    description VARCHAR(500),                              -- 备注
    status VARCHAR(20) DEFAULT '启用',                     -- 启用/停用
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(50) DEFAULT 'admin',
    update_by VARCHAR(50) DEFAULT 'admin',
    CONSTRAINT uk_company_year UNIQUE (target_year)
);

COMMENT ON TABLE sales_target_company IS '公司年度销售目标表';
COMMENT ON COLUMN sales_target_company.target_year IS '目标年度';
COMMENT ON COLUMN sales_target_company.annual_amount IS '年度销售目标金额';
COMMENT ON COLUMN sales_target_company.growth_rate IS '下年度自动增长率(%)，默认30%';
COMMENT ON COLUMN sales_target_company.auto_generate_next_year IS '是否自动生成下年度目标:1=是,0=否';

-- 2. 销售人员年度目标分配表
CREATE TABLE IF NOT EXISTS sales_target_employee (
    id BIGSERIAL PRIMARY KEY,
    target_year INTEGER NOT NULL,                          -- 目标年度
    employee_id BIGINT NOT NULL,                           -- 销售员ID (employee表)
    employee_name VARCHAR(100),                            -- 销售员姓名（冗余字段，便于查询）
    dept_id BIGINT,                                        -- 部门ID
    dept_name VARCHAR(100),                                -- 部门名称
    annual_target_amount NUMERIC(18,2) NOT NULL,           -- 年度目标金额
    q1_target NUMERIC(18,2) DEFAULT 0,                     -- Q1目标
    q2_target NUMERIC(18,2) DEFAULT 0,                     -- Q2目标
    q3_target NUMERIC(18,2) DEFAULT 0,                     -- Q3目标
    q4_target NUMERIC(18,2) DEFAULT 0,                     -- Q4目标
    actual_amount NUMERIC(18,2) DEFAULT 0,                 -- 实际完成金额（由成交商机汇总）
    completion_rate NUMERIC(5,2) DEFAULT 0,                -- 完成率(%) = 实际/目标*100
    allocation_status VARCHAR(20) DEFAULT '已分配',        -- 已分配/待分配
    remark VARCHAR(500),                                   -- 备注
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(50) DEFAULT 'admin',
    update_by VARCHAR(50) DEFAULT 'admin',
    CONSTRAINT uk_emp_year UNIQUE (target_year, employee_id)
);

COMMENT ON TABLE sales_target_employee IS '销售人员年度目标分配表';
COMMENT ON COLUMN sales_target_employee.target_year IS '目标年度';
COMMENT ON COLUMN sales_target_employee.employee_id IS '销售员ID';
COMMENT ON COLUMN sales_target_employee.annual_target_amount IS '销售员年度目标金额';
COMMENT ON COLUMN sales_target_employee.q1_target IS 'Q1季度目标';
COMMENT ON COLUMN sales_target_employee.q2_target IS 'Q2季度目标';
COMMENT ON COLUMN sales_target_employee.q3_target IS 'Q3季度目标';
COMMENT ON COLUMN sales_target_employee.q4_target IS 'Q4季度目标';
COMMENT ON COLUMN sales_target_employee.actual_amount IS '实际完成金额（成交商机汇总）';
COMMENT ON COLUMN sales_target_employee.completion_rate IS '完成率(%)';
COMMENT ON COLUMN sales_target_employee.allocation_status IS '分配状态:已分配/待分配';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_ste_year ON sales_target_employee(target_year);
CREATE INDEX IF NOT EXISTS idx_ste_employee ON sales_target_employee(employee_id);
CREATE INDEX IF NOT EXISTS idx_stc_year ON sales_target_company(target_year);
