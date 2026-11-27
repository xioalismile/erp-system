-- 86_pilot_bill_approval_flows.sql
-- 库存盘点单(STOCK_CHECK) / 库存调拨单(STOCK_TRANSFER) / 其他入库单(OTHER_RECEIPT) 接入审批引擎的流程播种（试点）
--
-- 背景：审批引擎此前只接了 销售订单/采购订单/销售合同 三张单据；这三张库存单据的审批开关
--       （设置中心-单据审批开关 approval.bill.{BILL_TYPE}.enabled）打开后只把单据状态改成「待审核」，
--       不产生审批待办 → 单据永久卡死。本脚本为它们播种「可用」的单节点审批流程，
--       使开关打开后「提交 → 生成待办 → 审批通过/驳回 → 回写单据状态与库存影响」闭环成立。
--
-- 幂等：按 bill_type + flow_name（流程）/ flow_id + node_name（节点）/ node_id + user_id（审批人）判重，
--       可重复执行，不产生重复行，不修改任何已有流程（销售订单/采购订单/销售合同流程零影响）。
--
-- 审批人：admin（sys_user.user_id = 1，已启用启用账号，已实查确认）。
--       approver_name 写「登录名 admin」而不是 real_name(系统管理员)/员工姓名(测试员工A改)：
--       引擎 approve() 的身份校验同时接受「登录名」与「姓名」两种身份（ApprovalRuntimeService.resolveDisplayName），
--       而 admin 账号当前绑定了员工档案(employee_id=16)，其"姓名"口径会随员工档案变动而变；
--       写登录名可避免日后该员工档案被改名/清理时审批人被判定「不是当前节点指定审批人」而卡死
--       （姓名匹配脆弱是历史已知坑，见 knowledge/2026-09-14_审批流程抢占与待办通知口径修复.md）。

-- ============ 1. 库存盘点单 STOCK_CHECK ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'STOCK_CHECK_STD', '库存盘点单默认审批流程', 'STOCK_CHECK', 1, 99, 1, 0,
       '库存盘点单默认单节点审批流程（试点接入，审批人 admin）', '86号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'STOCK_CHECK' AND flow_name = '库存盘点单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'STOCK_CHECK' AND f.flow_name = '库存盘点单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'STOCK_CHECK' AND f.flow_name = '库存盘点单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 2. 库存调拨单 STOCK_TRANSFER ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'STOCK_TRANSFER_STD', '库存调拨单默认审批流程', 'STOCK_TRANSFER', 1, 99, 1, 0,
       '库存调拨单默认单节点审批流程（试点接入，审批人 admin）', '86号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'STOCK_TRANSFER' AND flow_name = '库存调拨单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'STOCK_TRANSFER' AND f.flow_name = '库存调拨单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'STOCK_TRANSFER' AND f.flow_name = '库存调拨单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 3. 其他入库单 OTHER_RECEIPT ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'OTHER_RECEIPT_STD', '其他入库单默认审批流程', 'OTHER_RECEIPT', 1, 99, 1, 0,
       '其他入库单默认单节点审批流程（试点接入，审批人 admin）', '86号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'OTHER_RECEIPT' AND flow_name = '其他入库单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'OTHER_RECEIPT' AND f.flow_name = '其他入库单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'OTHER_RECEIPT' AND f.flow_name = '其他入库单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);
