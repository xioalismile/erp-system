-- ============================================================
-- 153_receipt_writeback.sql
-- 用途：销售订单收款情况（2026-10-12）—— 收款单两条路径回写 + 明细/汇总收款
--       ① sale_order_item 加 received_amount（订单明细行级已收金额，默认0，历史不追溯）
--       ② account_receivable 加 source_order_no（应收单来源订单号，发货收款回写订单的关联链路）
--       ③ bill_writeoff_detail 加 source_item_id/source_item_name（明细收款行落库）
--       ④ 存量应收单回填来源订单号（沿出库单追溯，幂等只填空值）
-- 幂等：ADD COLUMN IF NOT EXISTS / 只填空值，重复执行无副作用。
-- 自愈：SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 已登记（主库 + 全部租户库启动逐库补列）。
-- 同内容文件另存 deploy/sql/151_receipt_writeback_to_order.sql（守则04 编号体系）。
-- 执行：psql -h 127.0.0.1 -U erp_user -d erp_db -f 153_receipt_writeback.sql
-- ============================================================

-- ① 订单明细行级已收金额
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS received_amount NUMERIC(18,2) NOT NULL DEFAULT 0.00;
COMMENT ON COLUMN sale_order_item.received_amount IS '行级已收金额（明细收款/发货收款回写分摊，历史数据默认0不追溯）';

-- ② 应收单来源订单号
ALTER TABLE account_receivable ADD COLUMN IF NOT EXISTS source_order_no VARCHAR(50);
COMMENT ON COLUMN account_receivable.source_order_no IS '来源销售订单号（发货收款回写订单的关联链路）';

-- ③ 核销明细：来源明细行
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS source_item_id BIGINT;
ALTER TABLE bill_writeoff_detail ADD COLUMN IF NOT EXISTS source_item_name VARCHAR(200);
COMMENT ON COLUMN bill_writeoff_detail.source_item_id IS '来源明细行ID（销售订单明细行；空=汇总收款）';
COMMENT ON COLUMN bill_writeoff_detail.source_item_name IS '来源明细行货品名（明细收款展示用）';

-- ④ 存量应收单回填来源订单号（幂等：只填空值）
-- 4a. 订单直接生成的应收单：来源订单号 = 来源单号
UPDATE account_receivable
SET source_order_no = source_bill_no
WHERE source_bill_type = '销售订单'
  AND COALESCE(source_order_no, '') = '';

-- 4b. 出库生成的应收单：沿出库单追溯来源订单号
UPDATE account_receivable ar
SET source_order_no = d.source_bill_no
FROM sale_delivery d
WHERE ar.source_bill_type = '销售出库单'
  AND ar.source_bill_no = d.delivery_no
  AND d.source_bill_type IN ('销售订单', 'sale_order')
  AND COALESCE(d.source_bill_no, '') <> ''
  AND COALESCE(ar.source_order_no, '') = '';
