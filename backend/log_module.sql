-- =============================================
-- 操作日志与单据追溯模块建表脚本
-- 数据库: PostgreSQL
-- =============================================

-- =============================================
-- 1. 操作日志表
-- =============================================
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGSERIAL PRIMARY KEY,
    log_no VARCHAR(64) NOT NULL,
    module VARCHAR(32),
    module_name VARCHAR(64),
    operation_type VARCHAR(32),
    operation_name VARCHAR(128),
    doc_id BIGINT,
    doc_no VARCHAR(64),
    doc_type VARCHAR(64),
    user_id BIGINT,
    username VARCHAR(64),
    operation_time TIMESTAMP,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    request_method VARCHAR(16),
    request_url VARCHAR(512),
    request_params TEXT,
    result_code INTEGER,
    result_message VARCHAR(512),
    cost_time BIGINT,
    status INTEGER DEFAULT 1,
    remark VARCHAR(512)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_operation_log_module ON operation_log(module);
CREATE INDEX IF NOT EXISTS idx_operation_log_operation_type ON operation_log(operation_type);
CREATE INDEX IF NOT EXISTS idx_operation_log_user_id ON operation_log(user_id);
CREATE INDEX IF NOT EXISTS idx_operation_log_operation_time ON operation_log(operation_time);
CREATE INDEX IF NOT EXISTS idx_operation_log_doc ON operation_log(doc_id, doc_type);
CREATE INDEX IF NOT EXISTS idx_operation_log_log_no ON operation_log(log_no);

-- 注释
COMMENT ON TABLE operation_log IS '操作日志表';
COMMENT ON COLUMN operation_log.id IS '主键ID';
COMMENT ON COLUMN operation_log.log_no IS '日志编号';
COMMENT ON COLUMN operation_log.module IS '模块: sale/purchase/inventory/finance/system';
COMMENT ON COLUMN operation_log.module_name IS '模块名称';
COMMENT ON COLUMN operation_log.operation_type IS '操作类型: CREATE/UPDATE/DELETE/SUBMIT/APPROVE/VOID/CLOSE/PRINT/EXPORT/IMPORT';
COMMENT ON COLUMN operation_log.operation_name IS '操作名称';
COMMENT ON COLUMN operation_log.doc_id IS '单据ID';
COMMENT ON COLUMN operation_log.doc_no IS '单据编号';
COMMENT ON COLUMN operation_log.doc_type IS '单据类型';
COMMENT ON COLUMN operation_log.user_id IS '操作人ID';
COMMENT ON COLUMN operation_log.username IS '操作人用户名';
COMMENT ON COLUMN operation_log.operation_time IS '操作时间';
COMMENT ON COLUMN operation_log.ip_address IS 'IP地址';
COMMENT ON COLUMN operation_log.user_agent IS '用户代理';
COMMENT ON COLUMN operation_log.request_method IS '请求方法';
COMMENT ON COLUMN operation_log.request_url IS '请求URL';
COMMENT ON COLUMN operation_log.request_params IS '请求参数(JSON)';
COMMENT ON COLUMN operation_log.result_code IS '返回结果码';
COMMENT ON COLUMN operation_log.result_message IS '返回消息';
COMMENT ON COLUMN operation_log.cost_time IS '耗时(毫秒)';
COMMENT ON COLUMN operation_log.status IS '状态: 0失败 1成功';
COMMENT ON COLUMN operation_log.remark IS '备注';


-- =============================================
-- 2. 单据追溯关系表
-- =============================================
CREATE TABLE IF NOT EXISTS doc_trace (
    id BIGSERIAL PRIMARY KEY,
    trace_no VARCHAR(64) NOT NULL,
    source_doc_id BIGINT NOT NULL,
    source_doc_no VARCHAR(64),
    source_doc_type VARCHAR(64) NOT NULL,
    target_doc_id BIGINT NOT NULL,
    target_doc_no VARCHAR(64),
    target_doc_type VARCHAR(64) NOT NULL,
    source_item_id BIGINT,
    target_item_id BIGINT,
    material_id BIGINT,
    material_name VARCHAR(255),
    quantity DECIMAL(18, 4),
    trace_direction VARCHAR(16) DEFAULT 'FORWARD',
    create_time TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_doc_trace_trace_no ON doc_trace(trace_no);
CREATE INDEX IF NOT EXISTS idx_doc_trace_source ON doc_trace(source_doc_id, source_doc_type);
CREATE INDEX IF NOT EXISTS idx_doc_trace_target ON doc_trace(target_doc_id, target_doc_type);
CREATE INDEX IF NOT EXISTS idx_doc_trace_material ON doc_trace(material_id);
CREATE INDEX IF NOT EXISTS idx_doc_trace_create_time ON doc_trace(create_time);

-- 注释
COMMENT ON TABLE doc_trace IS '单据追溯关系表';
COMMENT ON COLUMN doc_trace.id IS '主键ID';
COMMENT ON COLUMN doc_trace.trace_no IS '追溯编号';
COMMENT ON COLUMN doc_trace.source_doc_id IS '上游单据ID';
COMMENT ON COLUMN doc_trace.source_doc_no IS '上游单据编号';
COMMENT ON COLUMN doc_trace.source_doc_type IS '上游单据类型';
COMMENT ON COLUMN doc_trace.target_doc_id IS '下游单据ID';
COMMENT ON COLUMN doc_trace.target_doc_no IS '下游单据编号';
COMMENT ON COLUMN doc_trace.target_doc_type IS '下游单据类型';
COMMENT ON COLUMN doc_trace.source_item_id IS '上游明细ID';
COMMENT ON COLUMN doc_trace.target_item_id IS '下游明细ID';
COMMENT ON COLUMN doc_trace.material_id IS '商品ID';
COMMENT ON COLUMN doc_trace.material_name IS '商品名称';
COMMENT ON COLUMN doc_trace.quantity IS '追溯数量';
COMMENT ON COLUMN doc_trace.trace_direction IS '追溯方向: FORWARD正向/BACKWARD反向';
COMMENT ON COLUMN doc_trace.create_time IS '创建时间';


-- =============================================
-- 初始化数据 (可选)
-- =============================================
-- 可以在这里插入一些初始数据，如模块枚举、操作类型枚举的参考数据
