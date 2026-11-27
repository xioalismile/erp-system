-- 76_approval_dirty_flow_cleanup.sql
-- 修复现象：设置中心配好销售订单审批（审批人=李工）后，
--   1) 小铃铛有红点，但「我的待办」/工作台「待我审批」看不到刚提交的单；
--   2) 单据提交后被派到历史脏流程，待办无人可见，永久卡在「待审核」。
-- 根因（本地 erp_db 实测）：
--   ① SALE_ORDER 下存在 3 条历史脏流程 TESTFLOW-VERIFY*（flow_id 19/20/21，enabled=1，
--      flow_name/node_name/审批人姓名全部为 '?' 乱码），与正规流程 flow 16 的 priority 同为 99；
--      ApprovalFlowService.getEnabledFlowByBillType 兜底取「第一个无条件表达式流程」，
--      PG 在 priority 相同时返回顺序不稳定（实测 17,19,20,21,16）→ 单据落到脏流程 19。
--   ② 脏流程节点审批人 approver_name='???'，approval_task.current_approver='???'，
--      任何真实账号都匹配不到 → 待办对所有人不可见。
-- 处理：作废「审批人无法解析到有效账号」的在途任务 + 单据回退草稿 + 清理脏流程及其假通知。
-- 幂等：可重复执行；备份快照表 bak_20260914_* 每次重建为当前最新状态。
-- 关联代码修复：ApprovalFlowService（排序确定性 + 兜底流程唯一性校验）、
--   ApprovalRuntimeService（审批人有效性校验）、NotificationScheduler（真实角色 + 引擎真实待办）。

BEGIN;

-- ===== 0. 备份快照（bak_ 前缀，仅本次修复使用；重跑覆盖为最新状态）=====
DROP TABLE IF EXISTS bak_20260914_approval_flow;
CREATE TABLE bak_20260914_approval_flow AS SELECT * FROM approval_flow;
DROP TABLE IF EXISTS bak_20260914_approval_node;
CREATE TABLE bak_20260914_approval_node AS SELECT * FROM approval_node;
DROP TABLE IF EXISTS bak_20260914_approval_node_approver;
CREATE TABLE bak_20260914_approval_node_approver AS SELECT * FROM approval_node_approver;
DROP TABLE IF EXISTS bak_20260914_approval_task;
CREATE TABLE bak_20260914_approval_task AS SELECT * FROM approval_task;
DROP TABLE IF EXISTS bak_20260914_sys_notification;
CREATE TABLE bak_20260914_sys_notification AS SELECT * FROM sys_notification;
DROP TABLE IF EXISTS bak_20260914_sale_order;
CREATE TABLE bak_20260914_sale_order AS SELECT * FROM sale_order;

-- ===== 1. 快照「审批人无法解析到有效账号」的在途任务（作废前留档，供后续步骤关联）=====
-- 判定口径：current_approver 既不是任何启用账号的 real_name，也不是任何员工姓名
DROP TABLE IF EXISTS bak_20260914_bad_pending_tasks;
CREATE TABLE bak_20260914_bad_pending_tasks AS
SELECT t.*
FROM approval_task t
WHERE t.task_status = 'pending'
  AND NOT EXISTS (SELECT 1 FROM sys_user u
                  WHERE u.real_name = t.current_approver AND u.is_enabled = 1)
  AND NOT EXISTS (SELECT 1 FROM employee e
                  WHERE e.employee_name = t.current_approver);

-- ===== 2. 作废这些任务（置 cancelled，保留原始记录可追溯）=====
UPDATE approval_task t
SET task_status = 'cancelled',
    comment     = COALESCE(t.comment, '') || '【系统修复】原指派审批人无法解析到有效账号，任务作废，请重新提交单据',
    finish_time = NOW(),
    updated_at  = NOW()
WHERE t.task_id IN (SELECT task_id FROM bak_20260914_bad_pending_tasks);

-- ===== 3. 对应单据回退「草稿」，允许重新提交走正确流程 =====
UPDATE sale_order so
SET status = '草稿', approval_status = '草稿', updated_at = NOW()
WHERE so.approval_status = '待审核'
  AND EXISTS (SELECT 1 FROM bak_20260914_bad_pending_tasks b
              WHERE b.bill_type = 'SALE_ORDER' AND b.bill_id = so.order_id);

UPDATE sale_contract sc
SET status = '草稿', approval_status = '草稿', updated_at = NOW()
WHERE sc.approval_status = '待审核'
  AND EXISTS (SELECT 1 FROM bak_20260914_bad_pending_tasks b
              WHERE b.bill_type = 'SALE_CONTRACT' AND b.bill_id = sc.contract_id);

-- ===== 4. 清理历史脏流程（含节点、审批人）=====
-- 口径：流程编码 TESTFLOW-VERIFY*（admin 创建、中文全为乱码的验证残留）
DELETE FROM approval_node_approver a
WHERE a.node_id IN (SELECT n.node_id
                    FROM approval_node n
                    JOIN approval_flow f ON f.flow_id = n.flow_id
                    WHERE f.flow_code LIKE 'TESTFLOW-VERIFY%');

DELETE FROM approval_node n
WHERE n.flow_id IN (SELECT f.flow_id FROM approval_flow f
                    WHERE f.flow_code LIKE 'TESTFLOW-VERIFY%');

DELETE FROM approval_flow f
WHERE f.flow_code LIKE 'TESTFLOW-VERIFY%';

-- ===== 5. 清理被作废单据的未读审批通知（避免铃铛红点点进去却无待办）=====
DELETE FROM sys_notification
WHERE is_read = 0
  AND type = 'approval'
  AND source_no IN (SELECT bill_no FROM bak_20260914_bad_pending_tasks);

DELETE FROM approval_notification
WHERE is_read = 0
  AND bill_no IN (SELECT bill_no FROM bak_20260914_bad_pending_tasks);

COMMIT;
