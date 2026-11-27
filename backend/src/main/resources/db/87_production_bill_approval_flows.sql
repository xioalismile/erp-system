-- 87_production_bill_approval_flows.sql
-- 生产管理 7 个单据身份接入审批引擎的流程播种（承接 86 号试点脚本）
--   PRODUCTION_ORDER     生产工单（口径：提交=下达，审批通过=执行下达）
--   PRODUCE_ASSEMBLE     生产组装单
--   PRODUCE_DISASSEMBLE  生产拆卸单
--   PRODUCTION_RECEIPT   生产入库单
--   MATERIAL_REQUISITION 生产领料单（material_requisition 表 bill_type=PICK）
--   MATERIAL_RETURN      生产退料单（同表 bill_type=RETURN，新身份）
--   MATERIAL_SUPPLEMENT  生产补料单（同表 bill_type=SUPPLEMENT，新身份）
--
-- 背景：这 7 个身份在设置中心的「单据审批开关」打开后，此前只把单据状态改成「待审核」，
--       不产生审批待办 → 单据永久卡死。本脚本为它们播种「可用」的单节点审批流程，
--       使开关打开后「提交 → 生成待办 → 审批通过/驳回 → 回写单据状态与库存影响」闭环成立。
--
-- 幂等：按 bill_type + flow_name（流程）/ flow_id + node_name（节点）/ node_id + user_id（审批人）判重，
--       可重复执行，不产生重复行，不修改任何已有流程（销售订单/采购订单/销售合同/86号三张流程零影响）。
--
-- 审批人：admin（sys_user.user_id = 1，已验证的启用账号）。
--       approver_name 写「登录名 admin」而不是 real_name(系统管理员)/员工姓名：
--       引擎 approve() 的身份校验同时接受「登录名」与「姓名」两种身份（ApprovalRuntimeService.resolveDisplayName），
--       而 admin 账号当前绑定了员工档案，其"姓名"口径会随员工档案变动而变；
--       写登录名可避免日后该员工档案被改名/清理时审批人被判定「不是当前节点指定审批人」而卡死
--       （姓名匹配脆弱是历史已知坑，见 knowledge/2026-09-14_审批流程抢占与待办通知口径修复.md）。

-- ============ 1. 生产工单 PRODUCTION_ORDER ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PRODUCTION_ORDER_STD', '生产工单默认审批流程', 'PRODUCTION_ORDER', 1, 99, 1, 0,
       '生产工单默认单节点审批流程（提交=下达，审批通过=执行下达，审批人 admin）', '87号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PRODUCTION_ORDER' AND flow_name = '生产工单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PRODUCTION_ORDER' AND f.flow_name = '生产工单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PRODUCTION_ORDER' AND f.flow_name = '生产工单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 2. 生产组装单 PRODUCE_ASSEMBLE ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PRODUCE_ASSEMBLE_STD', '生产组装单默认审批流程', 'PRODUCE_ASSEMBLE', 1, 99, 1, 0,
       '生产组装单默认单节点审批流程（审批人 admin）', '87号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PRODUCE_ASSEMBLE' AND flow_name = '生产组装单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PRODUCE_ASSEMBLE' AND f.flow_name = '生产组装单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PRODUCE_ASSEMBLE' AND f.flow_name = '生产组装单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 3. 生产拆卸单 PRODUCE_DISASSEMBLE ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PRODUCE_DISASSEMBLE_STD', '生产拆卸单默认审批流程', 'PRODUCE_DISASSEMBLE', 1, 99, 1, 0,
       '生产拆卸单默认单节点审批流程（审批人 admin）', '87号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PRODUCE_DISASSEMBLE' AND flow_name = '生产拆卸单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PRODUCE_DISASSEMBLE' AND f.flow_name = '生产拆卸单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PRODUCE_DISASSEMBLE' AND f.flow_name = '生产拆卸单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 4. 生产入库单 PRODUCTION_RECEIPT ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PRODUCTION_RECEIPT_STD', '生产入库单默认审批流程', 'PRODUCTION_RECEIPT', 1, 99, 1, 0,
       '生产入库单默认单节点审批流程（审批人 admin）', '87号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PRODUCTION_RECEIPT' AND flow_name = '生产入库单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PRODUCTION_RECEIPT' AND f.flow_name = '生产入库单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PRODUCTION_RECEIPT' AND f.flow_name = '生产入库单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 5. 生产领料单 MATERIAL_REQUISITION ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'MATERIAL_REQUISITION_STD', '生产领料单默认审批流程', 'MATERIAL_REQUISITION', 1, 99, 1, 0,
       '生产领料单默认单节点审批流程（审批人 admin）', '87号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'MATERIAL_REQUISITION' AND flow_name = '生产领料单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'MATERIAL_REQUISITION' AND f.flow_name = '生产领料单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'MATERIAL_REQUISITION' AND f.flow_name = '生产领料单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 6. 生产退料单 MATERIAL_RETURN（material_requisition 表 bill_type=RETURN） ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'MATERIAL_RETURN_STD', '生产退料单默认审批流程', 'MATERIAL_RETURN', 1, 99, 1, 0,
       '生产退料单默认单节点审批流程（同表 bill_type=RETURN，审批人 admin）', '87号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'MATERIAL_RETURN' AND flow_name = '生产退料单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'MATERIAL_RETURN' AND f.flow_name = '生产退料单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'MATERIAL_RETURN' AND f.flow_name = '生产退料单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 7. 生产补料单 MATERIAL_SUPPLEMENT（material_requisition 表 bill_type=SUPPLEMENT） ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'MATERIAL_SUPPLEMENT_STD', '生产补料单默认审批流程', 'MATERIAL_SUPPLEMENT', 1, 99, 1, 0,
       '生产补料单默认单节点审批流程（同表 bill_type=SUPPLEMENT，审批人 admin）', '87号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'MATERIAL_SUPPLEMENT' AND flow_name = '生产补料单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'MATERIAL_SUPPLEMENT' AND f.flow_name = '生产补料单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'MATERIAL_SUPPLEMENT' AND f.flow_name = '生产补料单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);
