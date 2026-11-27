-- ============================================================
-- 164_fix_tax_rounding_drift.sql
-- 含税价单据 1 分尾差存量修复（2026-10-18）
-- ============================================================
-- 背景：含税价单据原算法「税额 = 折后不含税金额 × 税率」单独四舍五入，
--       与「价税合计 = 不含税金额 + 税额」的舍入路径差 1 分。
--       实测 121 生产：含税 4.00×140=560.00 被算成 495.58+64.43=560.01。
-- 修复口径：含税价行「税额 = 含税合计 − 不含税金额」，价税合计 = 含税合计。
-- 保护（只修确定算错的存量）：
--   1) 仅 header.tax_calc_method='含税价'
--   2) 仅无折扣行（discount_amount=0）
--   3) 原值内部勾稽 net_amount = amount + tax_amount
--   4) 与含税合计相差 0.001~0.02（1 分档），且差额方向可纠正
--   5) 应收单只在「自动生成 + 未收款（paid_amount=0）」时同步
-- 幂等：修复后条件不再命中，重复执行 0 行。
-- 波及单据：销售订单 / 销售出库单 / 销售发票 / 应收账款（同一来源链）。
-- 同内容文件另存 deploy/sql/74_fix_tax_rounding_drift.sql

BEGIN;

CREATE TEMP TABLE _tax_drift_orders ON COMMIT DROP AS
SELECT DISTINCT o.order_id, o.order_no
FROM sale_order o
JOIN sale_order_item i ON i.order_id = o.order_id
WHERE o.tax_calc_method = '含税价'
  AND COALESCE(i.discount_amount, 0) = 0
  AND i.net_amount = i.amount + i.tax_amount
  AND ABS(i.net_amount - ROUND(i.price_with_tax * i.quantity, 2)) BETWEEN 0.001 AND 0.02
  AND ABS(i.tax_amount - (ROUND(i.price_with_tax * i.quantity, 2) - i.amount)) > 0.004;

\echo 待修复销售订单数：
SELECT COUNT(*) FROM _tax_drift_orders;

-- 1) 销售订单明细：税额/价税合计按含税合计倒算
UPDATE sale_order_item i
SET tax_amount = ROUND(i.price_with_tax * i.quantity, 2) - i.amount,
    net_amount = ROUND(i.price_with_tax * i.quantity, 2)
FROM _tax_drift_orders f
WHERE i.order_id = f.order_id
  AND COALESCE(i.discount_amount, 0) = 0
  AND i.net_amount = i.amount + i.tax_amount
  AND ABS(i.net_amount - ROUND(i.price_with_tax * i.quantity, 2)) BETWEEN 0.001 AND 0.02;

-- 2) 销售订单表头：按明细重算
UPDATE sale_order o
SET total_amount = t.total_amount,
    discount_amount = t.discount_amount,
    tax_amount = t.tax_amount,
    net_amount = t.net_amount,
    base_total_amount = ROUND(t.total_amount * COALESCE(o.exchange_rate, 1), 2),
    base_tax_amount = ROUND(t.tax_amount * COALESCE(o.exchange_rate, 1), 2),
    base_net_amount = ROUND(t.net_amount * COALESCE(o.exchange_rate, 1), 2),
    updated_at = NOW()
FROM (
  SELECT i.order_id,
         ROUND(SUM(i.amount + i.discount_amount), 2) AS total_amount,
         ROUND(SUM(i.discount_amount), 2) AS discount_amount,
         ROUND(SUM(i.tax_amount), 2) AS tax_amount,
         ROUND(SUM(i.net_amount), 2) AS net_amount
  FROM sale_order_item i
  JOIN _tax_drift_orders f ON f.order_id = i.order_id
  GROUP BY i.order_id
) t
WHERE o.order_id = t.order_id;

-- 3) 销售出库单（由受影响订单下推的）
CREATE TEMP TABLE _tax_drift_deliveries ON COMMIT DROP AS
SELECT d.delivery_id, d.delivery_no
FROM sale_delivery d
WHERE d.source_bill_no IN (SELECT order_no FROM _tax_drift_orders);

UPDATE sale_delivery_item di
SET tax_amount = ROUND(di.price_with_tax * di.quantity, 2) - di.amount,
    net_amount = ROUND(di.price_with_tax * di.quantity, 2),
    base_tax_amount = ROUND((ROUND(di.price_with_tax * di.quantity, 2) - di.amount) * COALESCE(d.exchange_rate, 1), 2),
    base_net_amount = ROUND(ROUND(di.price_with_tax * di.quantity, 2) * COALESCE(d.exchange_rate, 1), 2)
FROM _tax_drift_deliveries f
JOIN sale_delivery d ON d.delivery_id = f.delivery_id
WHERE di.delivery_id = f.delivery_id
  AND COALESCE(di.discount_amount, 0) = 0
  AND di.net_amount = di.amount + di.tax_amount
  AND ABS(di.net_amount - ROUND(di.price_with_tax * di.quantity, 2)) BETWEEN 0.001 AND 0.02;

UPDATE sale_delivery d
SET total_amount = t.total_amount,
    discount_amount = t.discount_amount,
    tax_amount = t.tax_amount,
    net_amount = t.net_amount,
    base_total_amount = ROUND(t.total_amount * COALESCE(d.exchange_rate, 1), 2),
    base_tax_amount = ROUND(t.tax_amount * COALESCE(d.exchange_rate, 1), 2),
    base_net_amount = ROUND(t.net_amount * COALESCE(d.exchange_rate, 1), 2)
FROM (
  SELECT di.delivery_id,
         ROUND(SUM(di.amount + di.discount_amount), 2) AS total_amount,
         ROUND(SUM(di.discount_amount), 2) AS discount_amount,
         ROUND(SUM(di.tax_amount), 2) AS tax_amount,
         ROUND(SUM(di.net_amount), 2) AS net_amount
  FROM sale_delivery_item di
  JOIN _tax_drift_deliveries f ON f.delivery_id = di.delivery_id
  GROUP BY di.delivery_id
) t
WHERE d.delivery_id = t.delivery_id;

-- 4) 销售发票（由受影响订单开出的）
CREATE TEMP TABLE _tax_drift_invoices ON COMMIT DROP AS
SELECT i.invoice_id, i.invoice_no
FROM sale_invoice i
WHERE i.source_bill_no IN (SELECT order_no FROM _tax_drift_orders);

UPDATE sale_invoice_item ii
SET tax_amount = ROUND(ii.price_with_tax * ii.quantity, 2) - ii.amount,
    net_amount = ROUND(ii.price_with_tax * ii.quantity, 2),
    base_tax_amount = ROUND((ROUND(ii.price_with_tax * ii.quantity, 2) - ii.amount) * COALESCE(iv.exchange_rate, 1), 2),
    base_net_amount = ROUND(ROUND(ii.price_with_tax * ii.quantity, 2) * COALESCE(iv.exchange_rate, 1), 2)
FROM _tax_drift_invoices f
JOIN sale_invoice iv ON iv.invoice_id = f.invoice_id
WHERE ii.invoice_id = f.invoice_id
  AND COALESCE(ii.discount_amount, 0) = 0
  AND ii.net_amount = ii.amount + ii.tax_amount
  AND ABS(ii.net_amount - ROUND(ii.price_with_tax * ii.quantity, 2)) BETWEEN 0.001 AND 0.02;

UPDATE sale_invoice iv
SET total_amount = t.total_amount,
    discount_amount = t.discount_amount,
    tax_amount = t.tax_amount,
    net_amount = t.net_amount
FROM (
  SELECT ii.invoice_id,
         ROUND(SUM(ii.amount + ii.discount_amount), 2) AS total_amount,
         ROUND(SUM(ii.discount_amount), 2) AS discount_amount,
         ROUND(SUM(ii.tax_amount), 2) AS tax_amount,
         ROUND(SUM(ii.net_amount), 2) AS net_amount
  FROM sale_invoice_item ii
  JOIN _tax_drift_invoices f ON f.invoice_id = ii.invoice_id
  GROUP BY ii.invoice_id
) t
WHERE iv.invoice_id = t.invoice_id;

-- 5) 应收账款：订单来源（自动生成、未收款才同步）
UPDATE account_receivable ar
SET total_amount = o.net_amount,
    balance_amount = o.net_amount - COALESCE(ar.paid_amount, 0)
FROM sale_order o
JOIN _tax_drift_orders f ON f.order_id = o.order_id
WHERE ar.source_bill_type = '销售订单'
  AND ar.source_bill_no = o.order_no
  AND COALESCE(ar.is_auto_gen, 0) = 1
  AND COALESCE(ar.paid_amount, 0) = 0
  AND ABS(ar.total_amount - o.net_amount) BETWEEN 0.001 AND 0.02;

-- 6) 应收账款：出库单来源
UPDATE account_receivable ar
SET total_amount = d.net_amount,
    balance_amount = d.net_amount - COALESCE(ar.paid_amount, 0)
FROM sale_delivery d
JOIN _tax_drift_deliveries f ON f.delivery_id = d.delivery_id
WHERE ar.source_bill_type = '销售出库单'
  AND ar.source_bill_no = d.delivery_no
  AND COALESCE(ar.is_auto_gen, 0) = 1
  AND COALESCE(ar.paid_amount, 0) = 0
  AND ABS(ar.total_amount - d.net_amount) BETWEEN 0.001 AND 0.02;

-- 7) 应收账款：发票来源
UPDATE account_receivable ar
SET total_amount = iv.net_amount,
    balance_amount = iv.net_amount - COALESCE(ar.paid_amount, 0)
FROM sale_invoice iv
JOIN _tax_drift_invoices f ON f.invoice_id = iv.invoice_id
WHERE ar.source_bill_type = '销售发票'
  AND ar.source_bill_no = iv.invoice_no
  AND COALESCE(ar.is_auto_gen, 0) = 1
  AND COALESCE(ar.paid_amount, 0) = 0
  AND ABS(ar.total_amount - iv.net_amount) BETWEEN 0.001 AND 0.02;

COMMIT;

\echo 修复后仍存在尾差的含税价销售订单行（应为 0 行）：
SELECT COUNT(*)
FROM sale_order o
JOIN sale_order_item i ON i.order_id = o.order_id
WHERE o.tax_calc_method = '含税价'
  AND COALESCE(i.discount_amount, 0) = 0
  AND ABS(i.net_amount - ROUND(i.price_with_tax * i.quantity, 2)) > 0.004;
