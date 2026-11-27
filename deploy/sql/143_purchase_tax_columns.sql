-- ============================================================
-- 143：采购三单（入库单 / 退货单 / 发票）税额·净额·折扣列补齐（2026-10-09）
--
-- 用途：采购入库单 / 采购退货单 / 采购发票 的表头与明细实体已按下列列映射并参与保存，
--   但租户库是从模板库克隆出来的历史快照，且这些列此前没有登记进启动自愈清单
--   → 租户库永久缺列：保存时 PostgreSQL 先报「字段 ... 不存在」，事务随即被中止，
--   界面只能看到「当前事务被终止」（8cloud 测试租户实测：三张单据全部无法保存）。
--
-- 说明：
--   1) 全部幂等（ADD COLUMN IF NOT EXISTS），可重复执行；只建列，不改动任何数据。
--   2) 列类型与主库一致（均 NUMERIC(18,2)）。
--   3) 配套：SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 登记同样 14 列，启动逐租户库自愈。
--   4) 镜像脚本：backend/src/main/resources/db/143_purchase_tax_columns.sql（内容与本文件一致）。
-- ============================================================

-- ① 采购入库单
ALTER TABLE purchase_receipt       ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(18,2);
ALTER TABLE purchase_receipt       ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,2);
ALTER TABLE purchase_receipt_item  ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(18,2);
ALTER TABLE purchase_receipt_item  ADD COLUMN IF NOT EXISTS tax_rate        NUMERIC(18,2);
ALTER TABLE purchase_receipt_item  ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,2);

-- ② 采购退货单
ALTER TABLE purchase_return        ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(18,2);
ALTER TABLE purchase_return        ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,2);
ALTER TABLE purchase_return        ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,2);
ALTER TABLE purchase_return_item   ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(18,2);
ALTER TABLE purchase_return_item   ADD COLUMN IF NOT EXISTS tax_rate        NUMERIC(18,2);
ALTER TABLE purchase_return_item   ADD COLUMN IF NOT EXISTS discount_rate   NUMERIC(18,2);
ALTER TABLE purchase_return_item   ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,2);
ALTER TABLE purchase_return_item   ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,2);

-- ③ 采购发票
ALTER TABLE purchase_invoice_item  ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,2);
