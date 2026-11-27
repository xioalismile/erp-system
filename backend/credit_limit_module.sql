-- =============================================
-- 信誉额度管理模块建表脚本
-- 数据库: PostgreSQL
-- =============================================

-- =============================================
-- 1. 客户信誉额度表
-- =============================================
CREATE TABLE IF NOT EXISTS credit_limit (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(128),
    credit_limit DECIMAL(18, 2) NOT NULL DEFAULT 0,
    used_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    available_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    warning_ratio DECIMAL(10, 4) DEFAULT 0.8,
    status INTEGER DEFAULT 1,
    effective_date DATE,
    expiry_date DATE,
    currency_code VARCHAR(16),
    base_credit_limit DECIMAL(18, 2) DEFAULT 0,
    base_used_amount DECIMAL(18, 2) DEFAULT 0,
    base_available_amount DECIMAL(18, 2) DEFAULT 0,
    remark VARCHAR(512),
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

-- 索引
CREATE UNIQUE INDEX IF NOT EXISTS idx_credit_limit_customer_id ON credit_limit(customer_id);
CREATE INDEX IF NOT EXISTS idx_credit_limit_status ON credit_limit(status);
CREATE INDEX IF NOT EXISTS idx_credit_limit_currency_code ON credit_limit(currency_code);
CREATE INDEX IF NOT EXISTS idx_credit_limit_create_time ON credit_limit(create_time);

-- 注释
COMMENT ON TABLE credit_limit IS '客户信誉额度表';
COMMENT ON COLUMN credit_limit.id IS '主键ID';
COMMENT ON COLUMN credit_limit.customer_id IS '客户ID';
COMMENT ON COLUMN credit_limit.customer_name IS '客户名称';
COMMENT ON COLUMN credit_limit.credit_limit IS '信誉额度';
COMMENT ON COLUMN credit_limit.used_amount IS '已用金额';
COMMENT ON COLUMN credit_limit.available_amount IS '可用额度';
COMMENT ON COLUMN credit_limit.warning_ratio IS '预警比例(如0.8表示用到80%预警)';
COMMENT ON COLUMN credit_limit.status IS '状态: 0冻结 1正常';
COMMENT ON COLUMN credit_limit.effective_date IS '生效日期';
COMMENT ON COLUMN credit_limit.expiry_date IS '失效日期';
COMMENT ON COLUMN credit_limit.currency_code IS '币种';
COMMENT ON COLUMN credit_limit.base_credit_limit IS '本位币额度';
COMMENT ON COLUMN credit_limit.base_used_amount IS '本位币已用';
COMMENT ON COLUMN credit_limit.base_available_amount IS '本位币可用';
COMMENT ON COLUMN credit_limit.remark IS '备注';
COMMENT ON COLUMN credit_limit.create_by IS '创建人';
COMMENT ON COLUMN credit_limit.create_time IS '创建时间';
COMMENT ON COLUMN credit_limit.update_time IS '更新时间';


-- =============================================
-- 2. 额度变更记录表
-- =============================================
CREATE TABLE IF NOT EXISTS credit_limit_log (
    id BIGSERIAL PRIMARY KEY,
    credit_limit_id BIGINT NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    before_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    change_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    after_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    related_doc_id BIGINT,
    related_doc_no VARCHAR(64),
    related_doc_type VARCHAR(64),
    reason VARCHAR(512),
    operator VARCHAR(64),
    operate_time TIMESTAMP,
    remark VARCHAR(512)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_credit_limit_log_credit_limit_id ON credit_limit_log(credit_limit_id);
CREATE INDEX IF NOT EXISTS idx_credit_limit_log_change_type ON credit_limit_log(change_type);
CREATE INDEX IF NOT EXISTS idx_credit_limit_log_related_doc ON credit_limit_log(related_doc_id, related_doc_type);
CREATE INDEX IF NOT EXISTS idx_credit_limit_log_operate_time ON credit_limit_log(operate_time);

-- 注释
COMMENT ON TABLE credit_limit_log IS '额度变更记录表';
COMMENT ON COLUMN credit_limit_log.id IS '主键ID';
COMMENT ON COLUMN credit_limit_log.credit_limit_id IS '信誉额度ID';
COMMENT ON COLUMN credit_limit_log.change_type IS '变更类型: INIT初始化/INCREASE增加/DECREASE减少/ADJUST调整/ORDER_OCCUPY订单占用/ORDER_RELEASE订单释放/PAYMENT_RECOVERY付款恢复';
COMMENT ON COLUMN credit_limit_log.before_amount IS '变更前金额';
COMMENT ON COLUMN credit_limit_log.change_amount IS '变更金额';
COMMENT ON COLUMN credit_limit_log.after_amount IS '变更后金额';
COMMENT ON COLUMN credit_limit_log.related_doc_id IS '关联单据ID';
COMMENT ON COLUMN credit_limit_log.related_doc_no IS '关联单据编号';
COMMENT ON COLUMN credit_limit_log.related_doc_type IS '关联单据类型';
COMMENT ON COLUMN credit_limit_log.reason IS '变更原因';
COMMENT ON COLUMN credit_limit_log.operator IS '操作人';
COMMENT ON COLUMN credit_limit_log.operate_time IS '操作时间';
COMMENT ON COLUMN credit_limit_log.remark IS '备注';


-- =============================================
-- 初始化数据 (示例数据)
-- =============================================

-- 插入示例客户信誉额度数据 (假设客户ID 1, 2, 3 已存在)
-- 实际使用时请根据真实客户数据调整
INSERT INTO credit_limit (customer_id, customer_name, credit_limit, used_amount, available_amount, warning_ratio, status, currency_code, base_credit_limit, base_used_amount, base_available_amount, remark, create_by, create_time, update_time)
VALUES
(1, '北京科技有限公司', 100000.00, 35000.00, 65000.00, 0.80, 1, 'CNY', 100000.00, 35000.00, 65000.00, '优质客户，初始额度10万', 'admin', NOW(), NOW()),
(2, '上海贸易有限公司', 50000.00, 42000.00, 8000.00, 0.80, 1, 'CNY', 50000.00, 42000.00, 8000.00, '普通客户，额度5万，已预警', 'admin', NOW(), NOW()),
(3, '广州电子科技有限公司', 200000.00, 0.00, 200000.00, 0.75, 0, 'CNY', 200000.00, 0.00, 200000.00, 'VIP客户，额度20万，已冻结', 'admin', NOW(), NOW())
ON CONFLICT (customer_id) DO NOTHING;

-- 插入示例额度变更记录
INSERT INTO credit_limit_log (credit_limit_id, change_type, before_amount, change_amount, after_amount, related_doc_id, related_doc_no, related_doc_type, reason, operator, operate_time, remark)
VALUES
(1, 'INIT', 0.00, 100000.00, 100000.00, NULL, NULL, NULL, '初始化额度', 'admin', NOW(), '首次设置额度'),
(1, 'ORDER_OCCUPY', 0.00, 20000.00, 20000.00, 1001, 'SO202501001', 'SALE_ORDER', '订单占用', 'admin', NOW(), '销售订单占用'),
(1, 'ORDER_OCCUPY', 20000.00, 15000.00, 35000.00, 1002, 'SO202501002', 'SALE_ORDER', '订单占用', 'admin', NOW(), '销售订单占用'),
(2, 'INIT', 0.00, 50000.00, 50000.00, NULL, NULL, NULL, '初始化额度', 'admin', NOW(), '首次设置额度'),
(2, 'ORDER_OCCUPY', 0.00, 42000.00, 42000.00, 1003, 'SO202501003', 'SALE_ORDER', '订单占用', 'admin', NOW(), '销售订单占用'),
(3, 'INIT', 0.00, 200000.00, 200000.00, NULL, NULL, NULL, '初始化额度', 'admin', NOW(), '首次设置额度');
