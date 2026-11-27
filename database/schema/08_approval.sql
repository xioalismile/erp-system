-- ============================================================
-- 审批流引擎 - 7张表
-- 支持：无限级节点、单据级开关、条件审批、多审批人选其一、审批通知
-- ============================================================

-- 1. 审批流程定义表（每个单据类型一个流程）
CREATE TABLE IF NOT EXISTS approval_flow (
    flow_id        BIGSERIAL PRIMARY KEY,
    flow_code      VARCHAR(50)  NOT NULL UNIQUE,
    flow_name      VARCHAR(100) NOT NULL,
    bill_type      VARCHAR(50)  NOT NULL,                 -- SALE_ORDER / PURCHASE_ORDER ...
    enabled        SMALLINT     NOT NULL DEFAULT 1,       -- 1启用 0停用
    description    VARCHAR(500),
    create_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP,
    create_by      VARCHAR(50),
    update_by      VARCHAR(50)
);
CREATE INDEX idx_approval_flow_bill_type ON approval_flow(bill_type);

-- 2. 审批节点表（流程下的多个节点，按 node_order 顺序流转，支持无限级）
CREATE TABLE IF NOT EXISTS approval_node (
    node_id        BIGSERIAL PRIMARY KEY,
    flow_id        BIGINT       NOT NULL REFERENCES approval_flow(flow_id) ON DELETE CASCADE,
    node_name      VARCHAR(100) NOT NULL,                 -- 如 "部门经理审批"
    node_order     INTEGER      NOT NULL,                 -- 顺序 1,2,3...
    approver_mode  VARCHAR(20)  NOT NULL DEFAULT 'single',-- single 单人 / any 任选一人 / all 全部审批 / sequence 依次审批
    position_id    BIGINT,                               -- 指定岗位（可选）
    description    VARCHAR(500),
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_approval_node_order UNIQUE (flow_id, node_order)
);
CREATE INDEX idx_approval_node_flow ON approval_node(flow_id);

-- 3. 节点审批人表（一个节点可有多个候选审批人）
CREATE TABLE IF NOT EXISTS approval_node_approver (
    id             BIGSERIAL PRIMARY KEY,
    node_id        BIGINT       NOT NULL REFERENCES approval_node(node_id) ON DELETE CASCADE,
    user_id        BIGINT       NOT NULL,                 -- 引用 sys_user.user_id
    employee_id    BIGINT,                               -- 冗余员工ID便于查询
    approver_name  VARCHAR(50),                           -- 冗余姓名便于显示
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_node_approver UNIQUE (node_id, user_id)
);
CREATE INDEX idx_approval_node_approver_node ON approval_node_approver(node_id);

-- 4. 审批条件模板表（条件审批：金额>=X 走流程A，否则走流程B 或不审批）
CREATE TABLE IF NOT EXISTS approval_condition (
    condition_id    BIGSERIAL PRIMARY KEY,
    flow_id         BIGINT       NOT NULL REFERENCES approval_flow(flow_id) ON DELETE CASCADE,
    field_key       VARCHAR(50)  NOT NULL,                -- 比较字段：amount / netAmount / totalAmount
    operator        VARCHAR(20)  NOT NULL,                -- >= / > / <= / < / == / between
    value_low       NUMERIC(18,2),                       -- 比较值低
    value_high      NUMERIC(18,2),                       -- 比较值高（between用）
    target_node_order INTEGER,                           -- 命中后跳转到的节点序号（空则走默认流程）
    description     VARCHAR(500),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_approval_condition_flow ON approval_condition(flow_id);

-- 5. 审批任务表（运行时：每个待办单据节点生成一条任务）
CREATE TABLE IF NOT EXISTS approval_task (
    task_id         BIGSERIAL PRIMARY KEY,
    flow_id         BIGINT       NOT NULL,
    node_id         BIGINT       NOT NULL,
    bill_type       VARCHAR(50)  NOT NULL,
    bill_id         BIGINT       NOT NULL,
    bill_no         VARCHAR(50),                          -- 冗余单据编号便于显示
    task_status     VARCHAR(20)  NOT NULL DEFAULT 'pending', -- pending 待办 / approved 通过 / rejected 驳回 / transferred 转交
    current_approver VARCHAR(50),                         -- 当前实际审批人（多审批人 scenario 下选定的）
    assignee_ids    VARCHAR(500),                         -- 候选审批人ID列表（JSON数组）
    comment         VARCHAR(1000),                       -- 审批意见
    submit_by       VARCHAR(50),                          -- 提交人
    submit_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finish_time     TIMESTAMP,
    node_order      INTEGER      NOT NULL,                -- 冗余节点顺序
    CONSTRAINT uk_approval_task UNIQUE (bill_type, bill_id, node_id)
);
CREATE INDEX idx_approval_task_approver ON approval_task(current_approver, task_status);
CREATE INDEX idx_approval_task_bill ON approval_task(bill_type, bill_id);

-- 6. 审批历史日志表（每个节点的处理记录，便于追溯）
CREATE TABLE IF NOT EXISTS approval_log (
    log_id          BIGSERIAL PRIMARY KEY,
    task_id         BIGINT,
    flow_id         BIGINT       NOT NULL,
    node_id         BIGINT       NOT NULL,
    bill_type       VARCHAR(50)  NOT NULL,
    bill_id         BIGINT       NOT NULL,
    bill_no         VARCHAR(50),
    node_name       VARCHAR(100),
    node_order      INTEGER,
    approver        VARCHAR(50)  NOT NULL,                -- 实际审批人
    action          VARCHAR(20)  NOT NULL,                -- approve / reject / transfer / submit
    comment         VARCHAR(1000),
    action_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_approval_log_bill ON approval_log(bill_type, bill_id);
CREATE INDEX idx_approval_log_approver ON approval_log(approver);

-- 7. 审批通知表（通知接收人审批任务到达/结果）
CREATE TABLE IF NOT EXISTS approval_notification (
    notification_id  BIGSERIAL PRIMARY KEY,
    receiver_id      BIGINT       NOT NULL,                -- 接收人user_id
    receiver_name   VARCHAR(50),
    bill_type       VARCHAR(50)  NOT NULL,
    bill_id         BIGINT       NOT NULL,
    bill_no         VARCHAR(50),
    task_id         BIGINT,
    node_name       VARCHAR(100),
    notify_type     VARCHAR(20)  NOT NULL DEFAULT 'task', -- task 待办 / result 结果 / cc 抄送 / timeout 超时
    title           VARCHAR(200) NOT NULL,
    content         VARCHAR(1000),
    is_read         SMALLINT     NOT NULL DEFAULT 0,      -- 0未读 1已读
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_time       TIMESTAMP
);
CREATE INDEX idx_approval_notification_receiver ON approval_notification(receiver_id, is_read);

-- ============ 单据级审批开关配置（写入sys_config，便于和现有体系融合）============
-- 在 sys_config 表中插入：approval.bill.<bill_type>.enabled = true/false
-- 例如 approval.bill.SALE_ORDER.enabled = true 表示销售订单需要审批
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, sort_order, created_by)
SELECT 'approval.bill.' || t.bill_type || '.enabled', 'false', t.bill_name || '需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制' || t.bill_name || '是否进入审批流程', t.sort_order, 'system'
FROM (VALUES
    -- 销售管理 (6种)
    ('SALE_QUOTE', '销售报价单', 1),
    ('SALE_ORDER', '销售订单', 2),
    ('SALE_CONTRACT', '销售合同', 3),
    ('SALE_DELIVERY', '销售出库单', 4),
    ('SALE_RETURN', '销售退货单', 5),
    ('SALE_INVOICE', '销售发票', 6),
    -- 采购管理 (6种)
    ('PURCHASE_REQUEST', '采购申请单', 7),
    ('PURCHASE_ORDER', '采购订单', 8),
    ('PURCHASE_CONTRACT', '采购合同', 9),
    ('PURCHASE_INBOUND', '采购入库单', 10),
    ('PURCHASE_RETURN', '采购退货单', 11),
    ('PURCHASE_INVOICE', '采购发票', 12),
    -- 生产管理 (3种)
    ('PRODUCTION_ORDER', '生产订单', 13),
    ('PRODUCTION_RECEIPT', '生产入库单', 14),
    ('MATERIAL_REQUISITION', '生产领料单', 15),
    -- 委外管理 (3种)
    ('SUBCONTRACT_ORDER', '委外订单', 16),
    ('SUBCONTRACT_DELIVERY', '委外发料单', 17),
    ('SUBCONTRACT_RECEIPT', '委外入库单', 18),
    -- 打样/样品 (2种)
    ('SAMPLE_ORDER', '打样单', 19),
    ('FREE_SAMPLE', '免费样品单', 20),
    -- 库存管理 (4种)
    ('STOCK_TRANSFER', '库存调拨单', 21),
    ('STOCK_CHECK', '库存盘点单', 22),
    ('OTHER_RECEIPT', '其他入库单', 23),
    ('OTHER_ISSUE', '其他出库单', 24),
    -- 财务管理 (2种)
    ('PURCHASE_EXPENSE', '采购费用单', 25),
    ('PURCHASE_PRE_PAYMENT', '预付款单', 26),
    -- 其他单据 (1种)
    ('BOM', 'BOM物料清单', 27)
) AS t(bill_type, bill_name, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config s WHERE s.config_key = 'approval.bill.' || t.bill_type || '.enabled'
);

-- ============ 默认流程：销售订单审批流程示例 ============
INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, description, create_by)
SELECT 'SALE_ORDER_DEFAULT', '销售订单默认审批流程', 'SALE_ORDER', 0, '金额>=50000元 部门经理→财务总监→总经理 三级审批', 'system'
WHERE NOT EXISTS (SELECT 1 FROM approval_flow WHERE flow_code = 'SALE_ORDER_DEFAULT');
