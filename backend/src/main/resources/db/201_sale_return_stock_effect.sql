-- 201_sale_return_stock_effect.sql
-- 销售退货单：区分「回库 / 不回库」，回库时按原出库成本增加库存
-- 2026-11-28

-- 表头：1=回库（审核增加库存）；0=不回库（客户不寄回，仅登记与财务冲减）
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS stock_effect SMALLINT DEFAULT 1;
UPDATE sale_return SET stock_effect = 1 WHERE stock_effect IS NULL;
COMMENT ON COLUMN sale_return.stock_effect IS '退货处理：1=回库增加库存；0=不回库仅登记';

-- 明细：审核时冻结的原出库成本，反审/作废按原成本冲回
ALTER TABLE sale_return_item ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,6);
COMMENT ON COLUMN sale_return_item.avg_cost IS '回库成本：审核时冻结的原出库成本';
