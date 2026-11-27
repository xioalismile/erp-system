-- 95_other_issue_flow.sql
-- 其他出库单（OTHER_ISSUE）接入审批引擎的流程播种（承接 91 号剩余单据身份脚本）
--
-- 背景：其他出库单此前未播种任何可用审批流程，设置中心的「单据审批开关」打开后
--       提交只把单据状态改成「待审核」，不产生审批待办 → 单据永久卡死。
--       本脚本为它播种「可用」的单节点审批流程；仅播种流程数据，不修改任何 Service 与审批引擎代码。
--
-- 幂等：按 bill_type + flow_name（流程）/ flow_id + node_name（节点）/ node_id + user_id（审批人）判重，
--       可重复执行，不产生重复行，不修改任何已有流程（86/87/91 号已播种身份零影响）。
--
-- 审批人：admin（sys_user.user_id = 1，已验证的启用账号）。
--       approver_name 写「登录名 admin」而不是 real_name/员工姓名：口径同 87/91 号脚本，
--       避免日后员工档案改名导致审批人被判定「不是当前节点指定审批人」而卡死。

-- ============ 其他出库单 OTHER_ISSUE ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'OTHER_ISSUE_STD', '其他出库单默认审批流程', 'OTHER_ISSUE', 1, 99, 1, 0,
       '其他出库单默认单节点审批流程（审批人 admin）', '95号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'OTHER_ISSUE' AND flow_name = '其他出库单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'OTHER_ISSUE' AND f.flow_name = '其他出库单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'OTHER_ISSUE' AND f.flow_name = '其他出库单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);
