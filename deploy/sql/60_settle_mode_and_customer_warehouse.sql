-- ============================================================
-- 60_settle_mode_and_customer_warehouse.sql
-- 目的：消除「前端字段存在但保存不落库」问题（用户 2026-09-20 明确要求）
--
-- 背景：以下字段在前端编辑页有真实可选的控件，但后端表/实体没有对应列，
--       Spring/Jackson 反序列化时静默忽略 → 用户选了、保存成功、刷新后丢失。
--
-- 幂等：全部 ADD COLUMN IF NOT EXISTS，重复执行零副作用；只增不删、不改类型。
-- 多租户：本文件只覆盖执行时那一个库；每个租户库由启动时
--       SchemaUpgradeEnsurer.ensureColumnIfMissing 逐库补齐（同一份列清单）。
-- ============================================================

-- ---------- 1) 结算方式（基础资料-结算方式 主档 ID）----------
-- 原先只有 finance 的付款单/收款单有 settle_mode_id；
-- 下列 6 张单据编辑页都有「结算方式」下拉，但表里无列 → 选了不落库。
ALTER TABLE sale_quote       ADD COLUMN IF NOT EXISTS settle_mode_id BIGINT;
ALTER TABLE sale_return      ADD COLUMN IF NOT EXISTS settle_mode_id BIGINT;
ALTER TABLE sale_invoice     ADD COLUMN IF NOT EXISTS settle_mode_id BIGINT;
ALTER TABLE sale_delivery    ADD COLUMN IF NOT EXISTS settle_mode_id BIGINT;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS settle_mode_id BIGINT;
ALTER TABLE purchase_order   ADD COLUMN IF NOT EXISTS settle_mode_id BIGINT;

COMMENT ON COLUMN sale_quote.settle_mode_id       IS '结算方式ID（基础资料-结算方式 settlement_mode）';
COMMENT ON COLUMN sale_return.settle_mode_id      IS '结算方式ID（基础资料-结算方式 settlement_mode）';
COMMENT ON COLUMN sale_invoice.settle_mode_id     IS '结算方式ID（基础资料-结算方式 settlement_mode）';
COMMENT ON COLUMN sale_delivery.settle_mode_id    IS '结算方式ID（基础资料-结算方式 settlement_mode）';
COMMENT ON COLUMN purchase_request.settle_mode_id IS '结算方式ID（基础资料-结算方式 settlement_mode）';
COMMENT ON COLUMN purchase_order.settle_mode_id   IS '结算方式ID（基础资料-结算方式 settlement_mode）';

-- ---------- 2) 客户档案「默认发货仓」----------
-- 客户档案编辑页有该下拉；销售订单「选客户自动带默认发货仓」也依赖它，
-- 实体原先无此列 → 选了不保存、自动带仓恒失效。
ALTER TABLE customer ADD COLUMN IF NOT EXISTS default_delivery_warehouse_id BIGINT;
COMMENT ON COLUMN customer.default_delivery_warehouse_id IS '默认发货仓库ID（库存-仓库 stock_id）';
