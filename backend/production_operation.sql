-- ======================================================================
-- 生产模块改造SQL:
--   1. 工单工序实例表 work_order_operation (工序实例化, 隔离不同工单的工序状态)
--   2. production_order 新增 reported_quantity 已报工合格量字段
-- 兼容 PostgreSQL (与现有 fix_schema*.sql 风格一致)
-- ======================================================================

-- ---------- 工单工序实例表 ----------
CREATE TABLE IF NOT EXISTS work_order_operation (
    id                   BIGSERIAL PRIMARY KEY,
    order_id             BIGINT NOT NULL,
    order_no             VARCHAR(50),
    routing_id           BIGINT,
    operation_id         BIGINT,
    operation_code       VARCHAR(50),
    operation_name       VARCHAR(100),
    line_no              INTEGER,
    work_center_id       BIGINT,
    workstation_id       BIGINT,
    setup_hours          NUMERIC(10,2),
    process_hours        NUMERIC(10,2),
    plan_quantity        NUMERIC(18,4),
    reported_quantity    NUMERIC(18,4) DEFAULT 0,
    rejected_quantity    NUMERIC(18,4) DEFAULT 0,
    operation_status     VARCHAR(20) DEFAULT '待开始',
    is_outsourced        SMALLINT DEFAULT 0,
    is_qc_required       SMALLINT DEFAULT 0,
    is_critical          SMALLINT DEFAULT 0,
    plan_start_date      DATE,
    plan_end_date        DATE,
    actual_start_date    DATE,
    actual_end_date      DATE,
    prev_line_no         INTEGER,
    next_line_no         INTEGER,
    create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_work_order_operation_order_id   ON work_order_operation(order_id);
CREATE INDEX IF NOT EXISTS idx_work_order_operation_status     ON work_order_operation(operation_status);
CREATE INDEX IF NOT EXISTS idx_work_order_operation_order_op   ON work_order_operation(order_id, operation_id);

COMMENT ON TABLE  work_order_operation                  IS '工单工序实例(下达时基于工艺路线快照生成)';
COMMENT ON COLUMN work_order_operation.order_id         IS '工单ID';
COMMENT ON COLUMN work_order_operation.order_no         IS '工单号';
COMMENT ON COLUMN work_order_operation.routing_id       IS '工艺路线ID';
COMMENT ON COLUMN work_order_operation.operation_id     IS '工序ID(来源routing_operation.operation_id)';
COMMENT ON COLUMN work_order_operation.operation_code   IS '工序编码';
COMMENT ON COLUMN work_order_operation.operation_name   IS '工序名称';
COMMENT ON COLUMN work_order_operation.line_no          IS '行号';
COMMENT ON COLUMN work_order_operation.work_center_id   IS '工作中心ID';
COMMENT ON COLUMN work_order_operation.workstation_id   IS '工位ID';
COMMENT ON COLUMN work_order_operation.setup_hours      IS '准备工时';
COMMENT ON COLUMN work_order_operation.process_hours    IS '加工工时';
COMMENT ON COLUMN work_order_operation.plan_quantity    IS '计划数量';
COMMENT ON COLUMN work_order_operation.reported_quantity IS '已报工合格量';
COMMENT ON COLUMN work_order_operation.rejected_quantity IS '报废量';
COMMENT ON COLUMN work_order_operation.operation_status IS '状态: 待开始/进行中/已完成';
COMMENT ON COLUMN work_order_operation.is_outsourced    IS '是否委外: 0否 1是';
COMMENT ON COLUMN work_order_operation.is_qc_required   IS '是否质检: 0否 1是';
COMMENT ON COLUMN work_order_operation.is_critical      IS '是否关键工序: 0否 1是';
COMMENT ON COLUMN work_order_operation.plan_start_date  IS '计划开始日期';
COMMENT ON COLUMN work_order_operation.plan_end_date    IS '计划完成日期';
COMMENT ON COLUMN work_order_operation.actual_start_date IS '实际开始日期';
COMMENT ON COLUMN work_order_operation.actual_end_date  IS '实际完成日期';
COMMENT ON COLUMN work_order_operation.prev_line_no     IS '前道工序行号';
COMMENT ON COLUMN work_order_operation.next_line_no     IS '后道工序行号';
COMMENT ON COLUMN work_order_operation.create_time      IS '创建时间';
COMMENT ON COLUMN work_order_operation.update_time      IS '更新时间';

-- ---------- production_order 新增已报工合格量字段 ----------
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS reported_quantity NUMERIC(18,4) DEFAULT 0;
COMMENT ON COLUMN production_order.reported_quantity IS '已报工合格量(报工审核累计, 与入库回写的completed_quantity分离, 避免重复计算)';
