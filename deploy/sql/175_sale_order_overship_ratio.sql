-- ============================================================================
-- 175_sale_order_overship_ratio.sql
-- 销售订单「超量发货比例」字段（2026-10-31，用户口径）
--
--   口径：比例按百分比输入，10 = 10%（=0.1）；
--        最高发货数量 = 订单数量 + 订单数量 × 比例/100；
--        比例为 0 / 空 = 最高发货数量等于订单数量（不允许超量）。
--   校验：销售出库单提交/审核时按“已下推（生效+草稿）+ 本次发货 - 已生效退货”逐行校验。
--   同步：backend/src/main/resources/db/175_sale_order_overship_ratio.sql
--         + 启动自愈 SchemaUpgradeEnsurer.ensureSaleOrderOvershipRatio（主库 + 每个租户库）
--   幂等：可重复执行。
-- ============================================================================

BEGIN;

ALTER TABLE sale_order
    ADD COLUMN IF NOT EXISTS overship_ratio NUMERIC(10,4) NOT NULL DEFAULT 0;

COMMENT ON COLUMN sale_order.overship_ratio IS
    '超量发货比例(%)：最高发货=订单数量×(1+比例/100)；0=不允许超量';

COMMIT;
