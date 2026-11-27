-- 194: 工作台待审批列表脏数据修复（2026-11-19）
-- 现象：历史版本免审批提交/部分生效路径只改了单据状态（status）未同步审批状态（approval_status），
--       导致 status='已生效' 的单据 approval_status 仍为 '待审核'，混进工作台「待我审批」列表。
-- 修复：单据状态与审批状态冲突的存量行，按单据状态回写审批状态；幂等可重复执行。
-- 代码侧：MyDashboardHelper 12 处待办查询已加双条件（status 与 approval_status 同为待审核），
--       即使存量未刷，脏数据也不会再进待办列表。

BEGIN;

-- 一、销售/采购/库存/生产 11 张单据头表（状态冲突行按 status 映射回写 approval_status）
UPDATE sale_order         SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';
UPDATE sale_delivery      SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';
UPDATE sale_return        SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';
UPDATE sale_invoice       SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';
UPDATE purchase_order     SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';
UPDATE purchase_return    SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';
UPDATE purchase_invoice   SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';
UPDATE stock_transfer     SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';
UPDATE stock_check        SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';
UPDATE production_receipt SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';
UPDATE operation_report   SET approval_status = CASE status WHEN '草稿' THEN '草稿' WHEN '已作废' THEN '已作废' ELSE '已生效' END WHERE approval_status = '待审核' AND status <> '待审核';

COMMIT;

-- 复核（人工核对用）：
-- SELECT 'sale_order' t, count(*) FROM sale_order WHERE status='已生效' AND approval_status='待审核'
-- UNION ALL SELECT 'sale_delivery', count(*) FROM sale_delivery WHERE status='已生效' AND approval_status='待审核'
-- UNION ALL SELECT 'purchase_order', count(*) FROM purchase_order WHERE status='已生效' AND approval_status='待审核'
-- UNION ALL SELECT 'stock_transfer', count(*) FROM stock_transfer WHERE status='已生效' AND approval_status='待审核'
-- UNION ALL SELECT 'production_receipt', count(*) FROM production_receipt WHERE status='已生效' AND approval_status='待审核';
