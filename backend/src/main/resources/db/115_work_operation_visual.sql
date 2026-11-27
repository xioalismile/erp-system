-- ============================================================================
-- 115_work_operation_visual.sql
-- 车间工序可视化 · 第 1 期：工艺路线主档化 + 工序类型/计件单价 + 工单工序扩展字段
--                            + 工序编号规则 + 默认费率参数
--
-- 幂等：本文件可重复执行（守则 04 2.7.1-2）。
-- 同步落盘：deploy/sql/115_work_operation_visual.sql（部署镜像）
--           + SchemaUpgradeEnsurer 启动逐租户自愈（多租户物理隔离多库，一次性脚本只覆盖执行到的那个库）
-- ============================================================================

-- ---------- 1. 工艺路线工序明细：工序类型 + 计件单价 ----------
ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS operation_type VARCHAR(20) DEFAULT '状态类型';
ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS piece_rate NUMERIC(18,4) DEFAULT 0;
COMMENT ON COLUMN routing_operation.operation_type IS '工序类型：状态类型/数量类型/入库类型/金额类型';
COMMENT ON COLUMN routing_operation.piece_rate IS '计件单价（单件工资，用于工序工资核算）';

-- ---------- 2. 工单工序实例：类型/负责人/投入产出/计件单价 ----------
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS operation_type VARCHAR(20) DEFAULT '状态类型';
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS assignee_id BIGINT;
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS assignee_name VARCHAR(50);
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS input_quantity NUMERIC(18,4) DEFAULT 0;
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS output_quantity NUMERIC(18,4) DEFAULT 0;
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS piece_rate NUMERIC(18,4) DEFAULT 0;
COMMENT ON COLUMN work_order_operation.operation_type IS '工序类型（工艺路线快照）：状态类型/数量类型/入库类型/金额类型';
COMMENT ON COLUMN work_order_operation.assignee_id IS '工序负责人（员工ID，派工写入）';
COMMENT ON COLUMN work_order_operation.input_quantity IS '投入数量（上道合格数；首道=工单数量）';
COMMENT ON COLUMN work_order_operation.output_quantity IS '产出数量（本道合格数）';
COMMENT ON COLUMN work_order_operation.piece_rate IS '计件单价（工艺路线快照）';

-- ---------- 3. 工艺路线：从"单据"降为"主档"，状态只有 启用/停用 ----------
-- 旧口径：草稿 / 已生效 / 已停用（+ 审核人 / 审核时间 / 审批状态）
-- 新口径：启用 / 停用（is_enabled 与 status 同步；approval_status/approved_by 列保留不删，守则 04 保留 3 个大版本）
-- 迁移：草稿/已驳回 → 停用（未审核不自动启用，由用户显式启用）；已生效 → 启用
UPDATE routing SET status = '停用' WHERE status IS NULL OR status IN ('草稿', '已驳回', 'DRAFT', 'REJECTED', '待审核', 'PENDING');
UPDATE routing SET status = '启用' WHERE status IN ('已生效', 'APPROVED');
UPDATE routing SET is_enabled = CASE WHEN status = '启用' THEN 1 ELSE 0 END
 WHERE is_enabled IS NULL OR is_enabled <> (CASE WHEN status = '启用' THEN 1 ELSE 0 END);

-- ---------- 4. 自动编号设置：新增「工序」单据类型 ----------
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator,
                                  current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'ROUTING_OP', '工序', 'OP', 'yyyyMMdd', 4, 'DAILY', '-',
       0, NULL, 83, 1, 1, '工序编号：OP-日期-流水（工艺路线内每道工序自动生成）'
 WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'ROUTING_OP');

-- ---------- 5. 默认费率参数已移除（2026-11-09） ----------
-- 费率只认工作中心档案，不再提供全局默认费率参数；历史库中的两条配置由 190 号脚本清除。

-- ---------- 验证 ----------
SELECT 'work_operation_visual_ok' AS result,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'routing_operation' AND column_name IN ('operation_type', 'piece_rate')) AS routing_op_cols,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'work_order_operation'
           AND column_name IN ('operation_type', 'assignee_id', 'assignee_name', 'input_quantity', 'output_quantity', 'piece_rate')) AS woo_cols;
