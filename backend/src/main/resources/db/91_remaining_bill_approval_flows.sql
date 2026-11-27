-- 91_remaining_bill_approval_flows.sql
-- 剩余单据身份接入审批引擎的流程播种（承接 86 号试点 / 87 号生产批次）
--   采购：PURCHASE_REQUEST 采购申请单 / PURCHASE_CONTRACT 采购合同 / PURCHASE_INBOUND 采购入库单
--         PURCHASE_RETURN 采购退货单 / PURCHASE_INVOICE 采购发票
--   销售：SALE_QUOTE 销售报价单 / SALE_DELIVERY 销售出库单 / SALE_RETURN 销售退货 / SALE_INVOICE 销售发票
--   委外：SUBCONTRACT_ORDER 委外加工单 / SUBCONTRACT_DELIVERY 委外发料单 / SUBCONTRACT_RECEIPT 委外入库单
--         SUBCONTRACT_RETURN 委外退料单 / SUBCONTRACT_SUPPLEMENT 委外补料单
--   打样：SAMPLE_ORDER 打样单 / FREE_SAMPLE 免费样品订单
--   财务：PURCHASE_EXPENSE 采购费用单 / PURCHASE_PRE_PAYMENT 预付款单 / PAYMENT_RECEIPT 收款单
--         PAYMENT_VOUCHER 付款单 / OPERATION_EXPENSE 费用分摊
--   其他：BOM（BOM 清单）
--
-- 背景：这些身份在设置中心的「单据审批开关」打开后，此前只把单据状态改成「待审核」，
--       不产生审批待办 → 单据永久卡死。本脚本为它们播种「可用」的单节点审批流程。
--       仅播种流程数据，不修改任何 Service 实现与审批引擎代码。
--
-- 幂等：按 bill_type + flow_name（流程）/ flow_id + node_name（节点）/ node_id + user_id（审批人）判重，
--       可重复执行，不产生重复行，不修改任何已有流程（86/87 号已播种身份零影响）。
--
-- 审批人：admin（sys_user.user_id = 1，已验证的启用账号）。
--       approver_name 写「登录名 admin」而不是 real_name/员工姓名：口径同 87 号脚本，
--       避免日后员工档案改名导致审批人被判定「不是当前节点指定审批人」而卡死。

-- ============ 1. 采购申请单 PURCHASE_REQUEST ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PURCHASE_REQUEST_STD', '采购申请单默认审批流程', 'PURCHASE_REQUEST', 1, 99, 1, 0,
       '采购申请单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PURCHASE_REQUEST' AND flow_name = '采购申请单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PURCHASE_REQUEST' AND f.flow_name = '采购申请单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PURCHASE_REQUEST' AND f.flow_name = '采购申请单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 2. 采购合同 PURCHASE_CONTRACT ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PURCHASE_CONTRACT_STD', '采购合同默认审批流程', 'PURCHASE_CONTRACT', 1, 99, 1, 0,
       '采购合同默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PURCHASE_CONTRACT' AND flow_name = '采购合同默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PURCHASE_CONTRACT' AND f.flow_name = '采购合同默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PURCHASE_CONTRACT' AND f.flow_name = '采购合同默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 3. 采购入库单 PURCHASE_INBOUND ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PURCHASE_INBOUND_STD', '采购入库单默认审批流程', 'PURCHASE_INBOUND', 1, 99, 1, 0,
       '采购入库单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PURCHASE_INBOUND' AND flow_name = '采购入库单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PURCHASE_INBOUND' AND f.flow_name = '采购入库单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PURCHASE_INBOUND' AND f.flow_name = '采购入库单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 4. 采购退货单 PURCHASE_RETURN ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PURCHASE_RETURN_STD', '采购退货单默认审批流程', 'PURCHASE_RETURN', 1, 99, 1, 0,
       '采购退货单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PURCHASE_RETURN' AND flow_name = '采购退货单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PURCHASE_RETURN' AND f.flow_name = '采购退货单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PURCHASE_RETURN' AND f.flow_name = '采购退货单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 5. 采购发票 PURCHASE_INVOICE ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PURCHASE_INVOICE_STD', '采购发票默认审批流程', 'PURCHASE_INVOICE', 1, 99, 1, 0,
       '采购发票默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PURCHASE_INVOICE' AND flow_name = '采购发票默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PURCHASE_INVOICE' AND f.flow_name = '采购发票默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PURCHASE_INVOICE' AND f.flow_name = '采购发票默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 6. 销售报价单 SALE_QUOTE ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'SALE_QUOTE_STD', '销售报价单默认审批流程', 'SALE_QUOTE', 1, 99, 1, 0,
       '销售报价单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'SALE_QUOTE' AND flow_name = '销售报价单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'SALE_QUOTE' AND f.flow_name = '销售报价单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'SALE_QUOTE' AND f.flow_name = '销售报价单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 7. 销售出库单 SALE_DELIVERY ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'SALE_DELIVERY_STD', '销售出库单默认审批流程', 'SALE_DELIVERY', 1, 99, 1, 0,
       '销售出库单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'SALE_DELIVERY' AND flow_name = '销售出库单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'SALE_DELIVERY' AND f.flow_name = '销售出库单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'SALE_DELIVERY' AND f.flow_name = '销售出库单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 8. 销售退货 SALE_RETURN ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'SALE_RETURN_STD', '销售退货默认审批流程', 'SALE_RETURN', 1, 99, 1, 0,
       '销售退货默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'SALE_RETURN' AND flow_name = '销售退货默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'SALE_RETURN' AND f.flow_name = '销售退货默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'SALE_RETURN' AND f.flow_name = '销售退货默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 9. 销售发票 SALE_INVOICE ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'SALE_INVOICE_STD', '销售发票默认审批流程', 'SALE_INVOICE', 1, 99, 1, 0,
       '销售发票默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'SALE_INVOICE' AND flow_name = '销售发票默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'SALE_INVOICE' AND f.flow_name = '销售发票默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'SALE_INVOICE' AND f.flow_name = '销售发票默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 10. 委外加工单 SUBCONTRACT_ORDER ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'SUBCONTRACT_ORDER_STD', '委外加工单默认审批流程', 'SUBCONTRACT_ORDER', 1, 99, 1, 0,
       '委外加工单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'SUBCONTRACT_ORDER' AND flow_name = '委外加工单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'SUBCONTRACT_ORDER' AND f.flow_name = '委外加工单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'SUBCONTRACT_ORDER' AND f.flow_name = '委外加工单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 11. 委外发料单 SUBCONTRACT_DELIVERY ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'SUBCONTRACT_DELIVERY_STD', '委外发料单默认审批流程', 'SUBCONTRACT_DELIVERY', 1, 99, 1, 0,
       '委外发料单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'SUBCONTRACT_DELIVERY' AND flow_name = '委外发料单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'SUBCONTRACT_DELIVERY' AND f.flow_name = '委外发料单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'SUBCONTRACT_DELIVERY' AND f.flow_name = '委外发料单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 12. 委外入库单 SUBCONTRACT_RECEIPT ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'SUBCONTRACT_RECEIPT_STD', '委外入库单默认审批流程', 'SUBCONTRACT_RECEIPT', 1, 99, 1, 0,
       '委外入库单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'SUBCONTRACT_RECEIPT' AND flow_name = '委外入库单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'SUBCONTRACT_RECEIPT' AND f.flow_name = '委外入库单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'SUBCONTRACT_RECEIPT' AND f.flow_name = '委外入库单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 13. 委外退料单 SUBCONTRACT_RETURN ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'SUBCONTRACT_RETURN_STD', '委外退料单默认审批流程', 'SUBCONTRACT_RETURN', 1, 99, 1, 0,
       '委外退料单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'SUBCONTRACT_RETURN' AND flow_name = '委外退料单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'SUBCONTRACT_RETURN' AND f.flow_name = '委外退料单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'SUBCONTRACT_RETURN' AND f.flow_name = '委外退料单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 14. 委外补料单 SUBCONTRACT_SUPPLEMENT ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'SUBCONTRACT_SUPPLEMENT_STD', '委外补料单默认审批流程', 'SUBCONTRACT_SUPPLEMENT', 1, 99, 1, 0,
       '委外补料单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'SUBCONTRACT_SUPPLEMENT' AND flow_name = '委外补料单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'SUBCONTRACT_SUPPLEMENT' AND f.flow_name = '委外补料单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'SUBCONTRACT_SUPPLEMENT' AND f.flow_name = '委外补料单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 15. 打样单 SAMPLE_ORDER ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'SAMPLE_ORDER_STD', '打样单默认审批流程', 'SAMPLE_ORDER', 1, 99, 1, 0,
       '打样单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'SAMPLE_ORDER' AND flow_name = '打样单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'SAMPLE_ORDER' AND f.flow_name = '打样单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'SAMPLE_ORDER' AND f.flow_name = '打样单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 16. 免费样品订单 FREE_SAMPLE ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'FREE_SAMPLE_STD', '免费样品订单默认审批流程', 'FREE_SAMPLE', 1, 99, 1, 0,
       '免费样品订单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'FREE_SAMPLE' AND flow_name = '免费样品订单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'FREE_SAMPLE' AND f.flow_name = '免费样品订单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'FREE_SAMPLE' AND f.flow_name = '免费样品订单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 17. 采购费用单 PURCHASE_EXPENSE ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PURCHASE_EXPENSE_STD', '采购费用单默认审批流程', 'PURCHASE_EXPENSE', 1, 99, 1, 0,
       '采购费用单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PURCHASE_EXPENSE' AND flow_name = '采购费用单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PURCHASE_EXPENSE' AND f.flow_name = '采购费用单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PURCHASE_EXPENSE' AND f.flow_name = '采购费用单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 18. 预付款单 PURCHASE_PRE_PAYMENT ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PURCHASE_PRE_PAYMENT_STD', '预付款单默认审批流程', 'PURCHASE_PRE_PAYMENT', 1, 99, 1, 0,
       '预付款单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PURCHASE_PRE_PAYMENT' AND flow_name = '预付款单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PURCHASE_PRE_PAYMENT' AND f.flow_name = '预付款单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PURCHASE_PRE_PAYMENT' AND f.flow_name = '预付款单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 19. 收款单 PAYMENT_RECEIPT ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PAYMENT_RECEIPT_STD', '收款单默认审批流程', 'PAYMENT_RECEIPT', 1, 99, 1, 0,
       '收款单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PAYMENT_RECEIPT' AND flow_name = '收款单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PAYMENT_RECEIPT' AND f.flow_name = '收款单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PAYMENT_RECEIPT' AND f.flow_name = '收款单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 20. 付款单 PAYMENT_VOUCHER ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'PAYMENT_VOUCHER_STD', '付款单默认审批流程', 'PAYMENT_VOUCHER', 1, 99, 1, 0,
       '付款单默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'PAYMENT_VOUCHER' AND flow_name = '付款单默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'PAYMENT_VOUCHER' AND f.flow_name = '付款单默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'PAYMENT_VOUCHER' AND f.flow_name = '付款单默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 21. 费用分摊 OPERATION_EXPENSE ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'OPERATION_EXPENSE_STD', '费用分摊默认审批流程', 'OPERATION_EXPENSE', 1, 99, 1, 0,
       '费用分摊默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'OPERATION_EXPENSE' AND flow_name = '费用分摊默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'OPERATION_EXPENSE' AND f.flow_name = '费用分摊默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'OPERATION_EXPENSE' AND f.flow_name = '费用分摊默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);

-- ============ 22. BOM 清单 BOM ============

INSERT INTO approval_flow (flow_code, flow_name, bill_type, enabled, priority, is_enabled, sort_order,
                           description, remark, create_by, create_time, created_at)
SELECT 'BOM_STD', 'BOM默认审批流程', 'BOM', 1, 99, 1, 0,
       'BOM默认单节点审批流程（审批人 admin）', '91号脚本播种', 'system', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM approval_flow
                  WHERE bill_type = 'BOM' AND flow_name = 'BOM默认审批流程');

INSERT INTO approval_node (flow_id, node_name, node_order, node_type, approve_mode, approver_mode,
                           sort_order, created_by, created_at)
SELECT f.flow_id, '管理员审批', 1, 'single', 'single', 'single', 0, 'system', now()
FROM approval_flow f
WHERE f.bill_type = 'BOM' AND f.flow_name = 'BOM默认审批流程'
  AND NOT EXISTS (SELECT 1 FROM approval_node n
                  WHERE n.flow_id = f.flow_id AND n.node_name = '管理员审批');

INSERT INTO approval_node_approver (node_id, user_id, approver_name, sort_order, created_by, created_at)
SELECT n.node_id, 1, 'admin', 0, 'system', now()
FROM approval_node n
JOIN approval_flow f ON f.flow_id = n.flow_id
WHERE f.bill_type = 'BOM' AND f.flow_name = 'BOM默认审批流程'
  AND n.node_name = '管理员审批'
  AND NOT EXISTS (SELECT 1 FROM approval_node_approver a
                  WHERE a.node_id = n.node_id AND a.user_id = 1);
