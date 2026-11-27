-- 156: sale-order-item unit price precision widen + legacy order price re-exact (2026-10-13)
-- Issue: order RHJLJ261007-001 on prod 121 shows net 9000.01 in the edit page while the
--        stored net is 9000.00. Root cause: the legacy create logic back-computed the unit
--        price as tax-inclusive 3000 / 1.13 = 2654.8672566... but the unit_price column is
--        numeric(18,2), truncating it to 2654.87; the row amount was back-computed from the
--        whole line (9000 / 1.13 = 7964.60). The two rounding paths differ by 0.01
--        (2654.87 x 3 = 7964.61), so any recalculation drifts the net by one cent.
-- Fix (ERP-conformant: business semantics untouched, amounts untouched):
--   1) widen sale_order_item.unit_price / base_unit_price to numeric(18,6) - aligns with
--      price_with_tax / price_without_tax (18,6) and the frontend price precision (4dp+);
--      widening only, stored values unchanged, idempotent.
--   2) restore the exact back-computed price on the affected order only:
--      unit_price = price_without_tax = 2654.867257, so unit price x quantity reconciles
--      exactly (2654.867257 x 3 = 7964.601771 -> 7964.60; tax 1035.40; net 9000.00).
--      Tax method (不含税价), tax_type, amount/tax/net columns: NOT touched.
-- Idempotent: ALTER TYPE numeric(18,2)->(18,6) is widening-only (no-op when already 6dp);
--      UPDATE has a WHERE guard on the specific order and distinct-values check.
-- Scope: the data UPDATE applies to tenant erp_db on 121 after explicit user authorization;
--      on other databases the order does not exist -> 0 rows -> no-op.
-- Verified locally (fixture TEST-ROUND-9000): open shows 9000.00, live edit recalcs,
--      save keeps net 9000.00, idempotent re-run = 0 rows. Regression 7/7 PASS.

ALTER TABLE sale_order_item ALTER COLUMN unit_price TYPE numeric(18,6);
ALTER TABLE sale_order_item ALTER COLUMN base_unit_price TYPE numeric(18,6);

UPDATE sale_order_item i
SET unit_price        = 2654.867257,
    price_without_tax = 2654.867257,
    base_unit_price   = ROUND(2654.867257 * COALESCE(o.exchange_rate, 1), 6)
FROM sale_order o
WHERE o.order_id = i.order_id
  AND o.order_no = 'RHJLJ261007-001'
  AND i.line_no = 1
  AND (i.unit_price        IS DISTINCT FROM 2654.867257
    OR i.price_without_tax IS DISTINCT FROM 2654.867257)
  -- guard: only touch the row while it is still in the ORIGINAL drift shape
  -- (amount/tax/net untouched). If someone saved the bill before this script ran,
  -- the amounts have already drifted to 7964.61/1035.40/9000.01 and re-anchoring
  -- the price alone would NOT reconcile -> skip and leave the row for manual review.
  AND i.amount     = 7964.60
  AND i.tax_amount = 1035.40
  AND i.net_amount = 9000.00;
