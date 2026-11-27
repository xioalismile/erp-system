-- =====================================================================
-- 78_repair_item_warehouse_from_header.sql
-- 历史导入单据「明细仓库为空」一次性修复（2026-11-14）
--
-- 背景：旧版导入模板只把仓库写到表头，明细行没有仓库；其它入库单、采购入库单
--       提交/审核时逐行校验仓库，会被拦住。
-- 口径：
--   1. 只修复 stock_id/warehouse_id 为空的明细行，按表头仓库回填；
--   2. 已有明细仓库的行一律不动（不覆盖用户后来手工选择的仓库）；
--   3. 幂等：重复执行不会改变结果，不会造成数据混乱；
--   4. 本脚本只作为一次性数据修复，由部署/运维手工执行一次，不挂启动自愈、不随服务启动反复跑。
-- 执行建议：先备份或在测试库验证；执行后可用文末核对 SQL 检查剩余空值行数。
-- =====================================================================

BEGIN;

-- 1. 其它入库单
UPDATE other_receipt_item i
SET stock_id = o.warehouse_id
FROM other_receipt o
WHERE i.receipt_id = o.receipt_id
  AND i.stock_id IS NULL
  AND o.warehouse_id IS NOT NULL;

-- 2. 其它出库单
UPDATE other_issue_item i
SET stock_id = o.warehouse_id
FROM other_issue o
WHERE i.issue_id = o.issue_id
  AND i.stock_id IS NULL
  AND o.warehouse_id IS NOT NULL;

-- 3. 库存盘点单
UPDATE stock_check_item i
SET stock_id = o.warehouse_id
FROM stock_check o
WHERE i.check_id = o.check_id
  AND i.stock_id IS NULL
  AND o.warehouse_id IS NOT NULL;

-- 4. 生产领料/退料/补料单（共用 material_requisition）
UPDATE material_requisition_item i
SET stock_id = o.warehouse_id
FROM material_requisition o
WHERE i.requisition_id = o.requisition_id
  AND i.stock_id IS NULL
  AND o.warehouse_id IS NOT NULL;

-- 5. 采购入库单（表名 purchase_receipt）
UPDATE purchase_receipt_item i
SET stock_id = o.warehouse_id
FROM purchase_receipt o
WHERE i.receipt_id = o.receipt_id
  AND i.stock_id IS NULL
  AND o.warehouse_id IS NOT NULL;

-- 6. 销售出库单
UPDATE sale_delivery_item i
SET stock_id = o.warehouse_id
FROM sale_delivery o
WHERE i.delivery_id = o.delivery_id
  AND i.stock_id IS NULL
  AND o.warehouse_id IS NOT NULL;

-- 7. 销售退货单
UPDATE sale_return_item i
SET stock_id = o.warehouse_id
FROM sale_return o
WHERE i.return_id = o.return_id
  AND i.stock_id IS NULL
  AND o.warehouse_id IS NOT NULL;

-- 8. 生产入库单
UPDATE production_receipt_item i
SET stock_id = o.warehouse_id
FROM production_receipt o
WHERE i.receipt_id = o.receipt_id
  AND i.stock_id IS NULL
  AND o.warehouse_id IS NOT NULL;

-- 9. 销售订单明细的仓库列名是 warehouse_id（不是 stock_id）
UPDATE sale_order_item i
SET warehouse_id = o.warehouse_id
FROM sale_order o
WHERE i.order_id = o.order_id
  AND i.warehouse_id IS NULL
  AND o.warehouse_id IS NOT NULL;

COMMIT;

-- =====================================================================
-- 核对（只读）：逐表查看仍为空的明细行数；全部为 0 表示已补齐
-- =====================================================================
-- SELECT 'other_receipt_item' AS tbl, COUNT(*) FROM other_receipt_item WHERE stock_id IS NULL
-- UNION ALL SELECT 'other_issue_item', COUNT(*) FROM other_issue_item WHERE stock_id IS NULL
-- UNION ALL SELECT 'stock_check_item', COUNT(*) FROM stock_check_item WHERE stock_id IS NULL
-- UNION ALL SELECT 'material_requisition_item', COUNT(*) FROM material_requisition_item WHERE stock_id IS NULL
-- UNION ALL SELECT 'purchase_receipt_item', COUNT(*) FROM purchase_receipt_item WHERE stock_id IS NULL
-- UNION ALL SELECT 'sale_delivery_item', COUNT(*) FROM sale_delivery_item WHERE stock_id IS NULL
-- UNION ALL SELECT 'sale_return_item', COUNT(*) FROM sale_return_item WHERE stock_id IS NULL
-- UNION ALL SELECT 'production_receipt_item', COUNT(*) FROM production_receipt_item WHERE stock_id IS NULL
-- UNION ALL SELECT 'sale_order_item', COUNT(*) FROM sale_order_item WHERE warehouse_id IS NULL;
