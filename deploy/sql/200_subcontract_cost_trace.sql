-- 2026-11-26 成本链路：委外实际材料成本留痕 + 成品成本归集
-- 幂等：可重复执行
ALTER TABLE subcontract_pick_item      ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,6);
ALTER TABLE subcontract_supplement_item ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,6);
ALTER TABLE subcontract_return_item     ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,6);
ALTER TABLE subcontract_inbound_item    ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(18,6);
ALTER TABLE subcontract_inbound_item    ADD COLUMN IF NOT EXISTS material_cost NUMERIC(18,2);
ALTER TABLE subcontract_inbound_item    ADD COLUMN IF NOT EXISTS batch_no VARCHAR(64);
ALTER TABLE subcontract_pick_item       ADD COLUMN IF NOT EXISTS batch_no VARCHAR(64);
ALTER TABLE subcontract_supplement_item ADD COLUMN IF NOT EXISTS batch_no VARCHAR(64);
ALTER TABLE subcontract_return_item     ADD COLUMN IF NOT EXISTS batch_no VARCHAR(64);
ALTER TABLE purchase_return_item        ADD COLUMN IF NOT EXISTS avg_cost NUMERIC(18,6);
