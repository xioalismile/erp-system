-- ================================================================
-- QA 审批脏数据清理 + 重建演示审批流程（本地一次性脚本，不进 deploy-kit manifest）
-- 背景：2026-09-01 QA 会话残留 4 个测试流程（sale_order_flow_qa / FLOW0018-0020）、
--       20 条测试审批任务、假审批人（QA审批人/测试财务/测试老板/测试经理）、
--       以及销售订单 31-50 共 20 张 100元/100万元测试单
-- 注意：本脚本会清空全部审批流程定义后重建，仅在 QA 残留清理场景一次性执行
-- ================================================================
BEGIN;

-- 1. 清理审批运行时数据（当前全部为 QA 残留）
DELETE FROM approval_notification;
DELETE FROM approval_log;
DELETE FROM approval_task;

-- 2. 清理审批流程定义（先子后父）
DELETE FROM approval_node_approver;
DELETE FROM approval_node;
DELETE FROM approval_condition;
DELETE FROM approval_flow;

-- 3. 清理 QA 测试销售订单（2026-09-01/02 QA 会话产物：order_id 31-50）
DELETE FROM sale_order_item WHERE order_id BETWEEN 31 AND 50;
DELETE FROM sale_order WHERE order_id BETWEEN 31 AND 50;

-- 4. 重建演示流程 A：销售订单标准审批（兜底流程，优先级 99）
--    审批人用真实在职员工：测试员工A改（管理员，user_id=1）、销售（user_id=37）
INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, description, create_by)
VALUES ('SALE_ORDER_STD', '销售订单标准审批流程', 'SALE_ORDER', 1, 99, '销售订单默认审批：销售经理 → 财务', 'system');

INSERT INTO approval_node (flow_id, node_name, node_order, approver_mode)
SELECT f.flow_id, '销售经理审批', 1, 'single' FROM approval_flow f WHERE f.flow_code = 'SALE_ORDER_STD';

INSERT INTO approval_node (flow_id, node_name, node_order, approver_mode)
SELECT f.flow_id, '财务审批', 2, 'single' FROM approval_flow f WHERE f.flow_code = 'SALE_ORDER_STD';

INSERT INTO approval_node_approver (node_id, user_id, employee_id, approver_name)
SELECT n.node_id, 1, 16, '测试员工A改' FROM approval_node n JOIN approval_flow f ON n.flow_id = f.flow_id
WHERE f.flow_code = 'SALE_ORDER_STD' AND n.node_order = 1;

INSERT INTO approval_node_approver (node_id, user_id, employee_id, approver_name)
SELECT n.node_id, 37, 15, '销售' FROM approval_node n JOIN approval_flow f ON n.flow_id = f.flow_id
WHERE f.flow_code = 'SALE_ORDER_STD' AND n.node_order = 2;

-- 5. 重建演示流程 B：大额销售订单审批（条件路由，优先级 1）
--    金额≥50000 元直接走总经理审批
INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, condition_expr, description, create_by)
VALUES ('SALE_ORDER_LARGE', '销售订单大额审批流程', 'SALE_ORDER', 1, 1,
        '{"rules":[{"field":"totalAmount","op":"ge","value":50000}]}',
        '金额≥50000元的销售订单：总经理审批', 'system');

INSERT INTO approval_node (flow_id, node_name, node_order, approver_mode)
SELECT f.flow_id, '总经理审批', 1, 'single' FROM approval_flow f WHERE f.flow_code = 'SALE_ORDER_LARGE';

INSERT INTO approval_node_approver (node_id, user_id, employee_id, approver_name)
SELECT n.node_id, 1, 16, '测试员工A改' FROM approval_node n JOIN approval_flow f ON n.flow_id = f.flow_id
WHERE f.flow_code = 'SALE_ORDER_LARGE' AND n.node_order = 1;

COMMIT;

-- 验证
SELECT flow_id, flow_code, flow_name, bill_type, enabled, priority, condition_expr FROM approval_flow ORDER BY priority;
SELECT n.flow_id, n.node_name, n.node_order, n.approver_mode, a.approver_name, a.user_id
FROM approval_node n JOIN approval_node_approver a ON n.node_id = a.node_id ORDER BY n.flow_id, n.node_order;
SELECT COUNT(*) AS remaining_tasks FROM approval_task;
SELECT COUNT(*) AS remaining_logs FROM approval_log;
SELECT COUNT(*) AS qa_orders_left FROM sale_order WHERE order_id BETWEEN 31 AND 50;
