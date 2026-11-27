-- ===========================================================================
-- 客户需求新增功能 - 数据库迁移脚本 (PostgreSQL)
-- 来源：客户录音访谈（2026-07-01）
-- 内容：公海池/跟进预警/客户扩展字段/免费样品订单/物料编码规则/
--       成本核算增强/运营费用/客户分群
-- ===========================================================================

-- ===========================================================================
-- 一、客户公海池机制
-- ===========================================================================

-- 客户表新增公海池相关字段
ALTER TABLE customer ADD COLUMN IF NOT EXISTS pool_type SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN customer.pool_type IS '客户池类型: 0-私海, 1-公海';

ALTER TABLE customer ADD COLUMN IF NOT EXISTS pool_enter_date TIMESTAMP;
COMMENT ON COLUMN customer.pool_enter_date IS '进入公海日期: 客户被退回公海的日期';

ALTER TABLE customer ADD COLUMN IF NOT EXISTS last_order_date DATE;
COMMENT ON COLUMN customer.last_order_date IS '最后开单日期: 客户最后一次成交订单的日期';

ALTER TABLE customer ADD COLUMN IF NOT EXISTS last_follow_up_date DATE;
COMMENT ON COLUMN customer.last_follow_up_date IS '最后跟进日期: 最后一次跟进记录的日期';

ALTER TABLE customer ADD COLUMN IF NOT EXISTS is_top SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN customer.is_top IS '是否置顶: 0-否, 1-是';

ALTER TABLE customer ADD COLUMN IF NOT EXISTS top_time TIMESTAMP;
COMMENT ON COLUMN customer.top_time IS '置顶时间';

ALTER TABLE customer ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;
COMMENT ON COLUMN customer.owner_user_id IS '归属用户ID: 私海时负责该客户的用户';

CREATE INDEX IF NOT EXISTS idx_customer_pool_type ON customer(pool_type);
CREATE INDEX IF NOT EXISTS idx_customer_last_follow_up ON customer(last_follow_up_date);
CREATE INDEX IF NOT EXISTS idx_customer_last_order ON customer(last_order_date);
CREATE INDEX IF NOT EXISTS idx_customer_is_top ON customer(is_top);

-- 客户建档扩展字段
ALTER TABLE customer ADD COLUMN IF NOT EXISTS main_business VARCHAR(500);
COMMENT ON COLUMN customer.main_business IS '主营业务: 客户主要经营业务描述';

ALTER TABLE customer ADD COLUMN IF NOT EXISTS project_type VARCHAR(100);
COMMENT ON COLUMN customer.project_type IS '项目类型: 如畜牧标签/抗金属标签等';

ALTER TABLE customer ADD COLUMN IF NOT EXISTS customer_demand TEXT;
COMMENT ON COLUMN customer.customer_demand IS '客户需求: 详细需求描述';

ALTER TABLE customer ADD COLUMN IF NOT EXISTS customer_preference TEXT;
COMMENT ON COLUMN customer.customer_preference IS '客户喜好: 非必填扩展信息';

ALTER TABLE customer ADD COLUMN IF NOT EXISTS family_background TEXT;
COMMENT ON COLUMN customer.family_background IS '家庭背景: 个人客户扩展信息';

-- 公海池配置表
CREATE TABLE IF NOT EXISTS customer_pool_config (
    config_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    no_follow_days INT NOT NULL DEFAULT 90,
    no_order_days INT NOT NULL DEFAULT 180,
    warning_days INT NOT NULL DEFAULT 7,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE customer_pool_config IS '客户公海池配置表';

-- 公海领取记录表
CREATE TABLE IF NOT EXISTS customer_pool_claim_log (
    log_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    action_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500)
);
COMMENT ON TABLE customer_pool_claim_log IS '公海池领取/释放记录表';
CREATE INDEX IF NOT EXISTS idx_pool_claim_customer ON customer_pool_claim_log(customer_id);

-- ===========================================================================
-- 二、客户跟进预警配置
-- ===========================================================================
CREATE TABLE IF NOT EXISTS customer_warning_config (
    warning_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    warning_type VARCHAR(50) NOT NULL,
    threshold_days INT NOT NULL,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    receiver_role VARCHAR(100),
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE customer_warning_config IS '客户跟进预警配置表';

-- ===========================================================================
-- 三、客户标签字典
-- ===========================================================================
CREATE TABLE IF NOT EXISTS customer_tag_dict (
    tag_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tag_name VARCHAR(100) NOT NULL,
    tag_color VARCHAR(20),
    tag_category VARCHAR(50),
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE customer_tag_dict IS '客户标签字典表';

-- ===========================================================================
-- 四、客户分群规则
-- ===========================================================================
CREATE TABLE IF NOT EXISTS customer_segment (
    segment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    segment_name VARCHAR(100) NOT NULL,
    segment_desc VARCHAR(500),
    rule_json TEXT NOT NULL,
    is_dynamic SMALLINT NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE customer_segment IS '客户分群规则表';

-- ===========================================================================
-- 五、免费样品订单
-- ===========================================================================
CREATE TABLE IF NOT EXISTS free_sample_order (
    order_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200),
    follow_up_id BIGINT,
    sales_id BIGINT,
    sales_name VARCHAR(100),
    order_date DATE NOT NULL,
    warehouse_id BIGINT,
    warehouse_name VARCHAR(100),
    express_no VARCHAR(100),
    express_company VARCHAR(100),
    total_quantity NUMERIC(18,4) DEFAULT 0,
    total_amount NUMERIC(18,2) DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT '草稿',
    delivery_date DATE,
    remark VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE free_sample_order IS '免费样品订单表';
CREATE INDEX IF NOT EXISTS idx_fso_customer ON free_sample_order(customer_id);
CREATE INDEX IF NOT EXISTS idx_fso_status ON free_sample_order(status);
CREATE INDEX IF NOT EXISTS idx_fso_order_no ON free_sample_order(order_no);

CREATE TABLE IF NOT EXISTS free_sample_order_item (
    item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT NOT NULL,
    line_no INT NOT NULL DEFAULT 1,
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    model VARCHAR(100),
    color VARCHAR(50),
    quantity NUMERIC(18,4) NOT NULL DEFAULT 1,
    unit_id BIGINT,
    unit_name VARCHAR(50),
    bin_id BIGINT,
    bin_name VARCHAR(100),
    remark VARCHAR(500)
);
COMMENT ON TABLE free_sample_order_item IS '免费样品订单明细表';
CREATE INDEX IF NOT EXISTS idx_fsoi_order ON free_sample_order_item(order_id);

-- 跟进记录关联样品订单
ALTER TABLE crm_follow_up ADD COLUMN IF NOT EXISTS related_sample_order_id BIGINT;
COMMENT ON COLUMN crm_follow_up.related_sample_order_id IS '关联样品订单ID';

-- ===========================================================================
-- 六、物料编码规则
-- ===========================================================================
CREATE TABLE IF NOT EXISTS material_code_rule (
    rule_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id BIGINT,
    category_name VARCHAR(100),
    prefix VARCHAR(20) NOT NULL,
    seq_length INT NOT NULL DEFAULT 4,
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    last_seq INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE material_code_rule IS '物料编码规则表';

-- ===========================================================================
-- 七、成本核算增强
-- ===========================================================================

-- 销售订单新增成本字段
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS labor_cost NUMERIC(18,2) DEFAULT 0.00;
COMMENT ON COLUMN sale_order.labor_cost IS '人工成本';

ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS operation_expense NUMERIC(18,2) DEFAULT 0.00;
COMMENT ON COLUMN sale_order.operation_expense IS '运营费用';

ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS business_expense NUMERIC(18,2) DEFAULT 0.00;
COMMENT ON COLUMN sale_order.business_expense IS '商务费用(回扣等)';

ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS total_cost NUMERIC(18,2) DEFAULT 0.00;
COMMENT ON COLUMN sale_order.total_cost IS '总成本';

-- 物料表新增成本核算方法字段
ALTER TABLE material ADD COLUMN IF NOT EXISTS cost_method VARCHAR(20) DEFAULT 'moving_avg';
COMMENT ON COLUMN material.cost_method IS '成本核算方法: moving_avg-移动加权平均, weighted_avg-月末加权平均, fixed-固定成本';

ALTER TABLE material ADD COLUMN IF NOT EXISTS fixed_cost NUMERIC(18,2) DEFAULT 0.00;
COMMENT ON COLUMN material.fixed_cost IS '固定成本: 当cost_method=fixed时使用';

-- 成本核算配置表
CREATE TABLE IF NOT EXISTS cost_accounting_config (
    config_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    default_method VARCHAR(20) NOT NULL DEFAULT 'moving_avg',
    is_enabled SMALLINT NOT NULL DEFAULT 1,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE cost_accounting_config IS '成本核算配置表';

-- 运营费用表
CREATE TABLE IF NOT EXISTS operation_expense (
    expense_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    expense_no VARCHAR(50) NOT NULL UNIQUE,
    expense_type VARCHAR(50) NOT NULL,
    expense_date DATE NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    related_order_type VARCHAR(50),
    related_order_id BIGINT,
    related_order_no VARCHAR(50),
    applicant_id BIGINT,
    applicant_name VARCHAR(100),
    department_id BIGINT,
    department_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT '草稿',
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE operation_expense IS '运营费用表';
CREATE INDEX IF NOT EXISTS idx_oe_expense_no ON operation_expense(expense_no);
CREATE INDEX IF NOT EXISTS idx_oe_type ON operation_expense(expense_type);
CREATE INDEX IF NOT EXISTS idx_oe_related ON operation_expense(related_order_id);

-- ===========================================================================
-- 九、初始化默认配置
-- ===========================================================================

-- 默认公海池配置
INSERT INTO customer_pool_config (config_name, no_follow_days, no_order_days, warning_days, is_enabled, remark)
SELECT '默认公海规则', 90, 180, 7, 1, '3个月未跟进或6个月未开单自动退回公海'
WHERE NOT EXISTS (SELECT 1 FROM customer_pool_config WHERE config_name = '默认公海规则');

-- 默认预警配置
INSERT INTO customer_warning_config (warning_type, threshold_days, is_enabled, receiver_role, remark)
SELECT 'no_follow_up', 90, 1, 'sales,manager', '90天未跟进预警'
WHERE NOT EXISTS (SELECT 1 FROM customer_warning_config WHERE warning_type = 'no_follow_up');

INSERT INTO customer_warning_config (warning_type, threshold_days, is_enabled, receiver_role, remark)
SELECT 'no_order', 180, 1, 'sales,manager', '180天未开单预警'
WHERE NOT EXISTS (SELECT 1 FROM customer_warning_config WHERE warning_type = 'no_order');

-- 默认成本核算配置
INSERT INTO cost_accounting_config (config_name, default_method, is_enabled, remark)
SELECT '默认成本核算', 'moving_avg', 1, '默认采用移动加权平均'
WHERE NOT EXISTS (SELECT 1 FROM cost_accounting_config);

-- 默认物料编码规则（通用）
INSERT INTO material_code_rule (category_id, category_name, prefix, seq_length, is_enabled, last_seq)
SELECT NULL, '通用物料', 'M', 6, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM material_code_rule WHERE category_name = '通用物料');

-- ===========================================================================
-- 十、客户标签字典默认数据
-- ===========================================================================
INSERT INTO customer_tag_dict (tag_name, tag_color, tag_category, is_enabled)
SELECT '政府客户', '#409EFF', '客户类型', 1
WHERE NOT EXISTS (SELECT 1 FROM customer_tag_dict WHERE tag_name = '政府客户');

INSERT INTO customer_tag_dict (tag_name, tag_color, tag_category, is_enabled)
SELECT '上市公司', '#67C23A', '客户类型', 1
WHERE NOT EXISTS (SELECT 1 FROM customer_tag_dict WHERE tag_name = '上市公司');

INSERT INTO customer_tag_dict (tag_name, tag_color, tag_category, is_enabled)
SELECT '散户', '#E6A23C', '客户类型', 1
WHERE NOT EXISTS (SELECT 1 FROM customer_tag_dict WHERE tag_name = '散户');

INSERT INTO customer_tag_dict (tag_name, tag_color, tag_category, is_enabled)
SELECT '个人客户', '#909399', '客户类型', 1
WHERE NOT EXISTS (SELECT 1 FROM customer_tag_dict WHERE tag_name = '个人客户');

INSERT INTO customer_tag_dict (tag_name, tag_color, tag_category, is_enabled)
SELECT '重点客户', '#F56C6C', '重要等级', 1
WHERE NOT EXISTS (SELECT 1 FROM customer_tag_dict WHERE tag_name = '重点客户');

INSERT INTO customer_tag_dict (tag_name, tag_color, tag_category, is_enabled)
SELECT '循环下单', '#9C27B0', '消费特征', 1
WHERE NOT EXISTS (SELECT 1 FROM customer_tag_dict WHERE tag_name = '循环下单');

-- ===========================================================================
-- 结束
-- ===========================================================================
