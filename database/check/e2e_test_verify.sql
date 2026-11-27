-- ============================================================================
-- 端到端测试【数据准确性复核】脚本（SQL 侧 ground truth）
-- 用法：psql -h localhost -p 5432 -U erp_user -d erp_db -f e2e_test_verify.sql
-- 说明：文件含中文，必须 psql -f 执行。
-- 把下面 5 个变量替换成 tests/e2e-chain-test.mjs 汇总输出的实际 ID 后执行。
-- ============================================================================
\set receipt_id 25
\set ar_no 'AR-20260901-0003'
\set mrp_plan_id 5
\set wo_id 7
\set pt_id 7

\echo '===== 1) 收款单 / 收款明细 / 核销明细 ====='
SELECT receipt_id, receipt_no, status, amount, received_amount FROM cash_receipt WHERE receipt_id = :receipt_id;
SELECT count(*) AS 明细行数, sum(amount) AS 明细金额合计 FROM cash_receipt_item WHERE receipt_id = :receipt_id;
SELECT bill_type, source_type, source_no, writeoff_amount, settled_qty
  FROM bill_writeoff_detail WHERE bill_id = :receipt_id;

\echo '===== 2) 多收自动转预收款单：主表金额 + 【明细必须非空】 ====='
SELECT id, receipt_no, amount, used_amount, remain_amount, status, source_type, source_bill_id
  FROM sale_pre_receipt WHERE source_bill_id = :receipt_id;
SELECT count(*) AS 预收款明细行数, sum(i.amount) AS 预收款明细金额
  FROM sale_pre_receipt_item i
  JOIN sale_pre_receipt p ON p.id = i.pre_receipt_id
 WHERE p.source_bill_id = :receipt_id;

\echo '===== 3) 应收单余额是否已结清 ====='
SELECT ar_no, total_amount, paid_amount, balance_amount, status, is_closed
  FROM account_receivable WHERE ar_no = :'ar_no';

\echo '===== 4) MRP 生成的采购订单：张数/明细行数/数量与计划是否一致 ====='
SELECT o.order_id, o.order_no, o.supplier_id, o.status, count(i.order_item_id) AS 明细行数, sum(i.quantity) AS 数量合计
  FROM purchase_order o LEFT JOIN purchase_order_item i ON i.order_id = o.order_id
 WHERE o.source_bill_type = 'MRP计划'
   AND o.source_bill_no = (SELECT plan_no FROM mrp_plan WHERE id = :mrp_plan_id)
 GROUP BY o.order_id, o.order_no, o.supplier_id, o.status ORDER BY o.order_id;
SELECT material_code, suggest_type, plan_quantity FROM mrp_plan_item
 WHERE plan_id = :mrp_plan_id AND suggest_type = 'PURCHASE' ORDER BY material_code;

\echo '===== 5) 生产工单：成品明细 + 原料明细(应发/已发) ====='
SELECT order_id, order_no, status, quantity, completed_quantity FROM production_order WHERE order_id = :wo_id;
SELECT count(*) AS 成品明细行数 FROM work_order_product WHERE order_id = :wo_id;
SELECT line_no, material_code, required_quantity, issued_quantity
  FROM production_order_item WHERE order_id = :wo_id ORDER BY line_no;

\echo '===== 6) 工序转移单：主表数量 是否等于 明细合计 ====='
SELECT transfer_id, transfer_no, status, transfer_qty FROM process_transfer WHERE transfer_id = :pt_id;
SELECT count(*) AS 明细行数, sum(transfer_qty) AS 明细数量合计 FROM process_transfer_item WHERE transfer_id = :pt_id;

\echo '===== 7) 库存侧结果（生产入库 / 领料 / 销售出库 / 采购入库） ====='
SELECT stock_id, material_id, quantity, available_quantity FROM inventory_current
 WHERE (material_id = 87 AND stock_id = 7)     -- 生产入库 +10 组装 +10
    OR (material_id = 88 AND stock_id = 7)     -- 领料 -20
    OR (material_id = 89 AND stock_id = 7)     -- 领料 -5.05
    OR (material_id = 90 AND stock_id = 7)     -- 领料 -30
    OR (material_id = 49 AND stock_id = 4)     -- 销售出库 -10
    OR (material_id = 2  AND stock_id = 6)     -- 采购入库 +100
 ORDER BY material_id, stock_id;
