-- ============================================================
-- 196_item_net_amount_columns.sql
-- 用途：单据明细与表头补齐「价税合计」列（net_amount），统一含税金额口径
-- 幂等：列变更 ADD COLUMN IF NOT EXISTS；回填只处理 net_amount IS NULL 的行
-- 说明：
--   前端显示价税合计：采购入库单（已有列，不改）、打样单、成本计算单
--   仅后端落库、前端不显示：生产入库单、生产工单原料、生产领料单、库存盘点单、组装单、拆卸单
--   口径：无税率列的单据 价税合计 = 含税价 × 数量；组装/拆卸 = 金额 + 税额
-- ============================================================

-- ① 明细行：新增价税合计列
ALTER TABLE sample_order_item ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE cost_calculation_item ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE production_receipt_item ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE material_requisition_item ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE stock_check_item ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE production_order_item ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE produce_assemble_detail ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE produce_assemble_bom ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE produce_disassemble_detail ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE produce_disassemble_bom ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);

-- ② 表头：新增价税合计列（生产工单表头无金额汇总，不新增）
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE material_requisition ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE stock_check ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS net_amount NUMERIC(18,2);

-- ③ 存量回填（只补空值，历史金额原值不动）

-- 打样单：含税价 × 基本用量
UPDATE sample_order_item
SET net_amount = ROUND(COALESCE(price_with_tax, 0) * COALESCE(base_qty, 0), 2)
WHERE net_amount IS NULL;

UPDATE sample_order s
SET net_amount = COALESCE((SELECT SUM(i.net_amount) FROM sample_order_item i WHERE i.sample_id = s.sample_id), s.cost_amount, 0)
WHERE s.net_amount IS NULL;

-- 成本计算单：含税价 × 数量
UPDATE cost_calculation_item
SET net_amount = ROUND(COALESCE(price_with_tax, 0) * COALESCE(quantity, 0), 2)
WHERE net_amount IS NULL;

UPDATE cost_calculation c
SET net_amount = COALESCE((SELECT SUM(i.net_amount) FROM cost_calculation_item i WHERE i.calc_id = c.id), 0)
WHERE c.net_amount IS NULL;

-- 生产入库单：含税价 = 不含税价 = 单价，价税合计 = 金额
UPDATE production_receipt_item
SET net_amount = COALESCE(amount, 0)
WHERE net_amount IS NULL;

UPDATE production_receipt r
SET net_amount = COALESCE(total_amount, 0)
WHERE r.net_amount IS NULL;

-- 生产领料单：价税合计 = 金额
UPDATE material_requisition_item
SET net_amount = COALESCE(amount, 0)
WHERE net_amount IS NULL;

UPDATE material_requisition r
SET net_amount = COALESCE(total_amount, 0)
WHERE r.net_amount IS NULL;

-- 库存盘点单：价税合计 = 盘点金额
UPDATE stock_check_item
SET net_amount = COALESCE(check_amount, 0)
WHERE net_amount IS NULL;

UPDATE stock_check c
SET net_amount = COALESCE(check_amount, 0)
WHERE c.net_amount IS NULL;

-- 生产工单原料（表名 production_order_item，实体 WorkOrderMaterial）：价税合计 = 金额（单位成本）
UPDATE production_order_item
SET net_amount = COALESCE(total_cost, 0)
WHERE net_amount IS NULL;

-- 组装单 / 拆卸单：价税合计 = 金额 + 税额
UPDATE produce_assemble_detail
SET net_amount = ROUND(COALESCE(amount, 0) + COALESCE(tax_amount, 0), 2)
WHERE net_amount IS NULL;

UPDATE produce_assemble_bom
SET net_amount = ROUND(COALESCE(amount, 0) + COALESCE(tax_amount, 0), 2)
WHERE net_amount IS NULL;

UPDATE produce_assemble
SET net_amount = ROUND(COALESCE(amount, 0) + COALESCE(tax_amount, 0), 2)
WHERE net_amount IS NULL;

UPDATE produce_disassemble_detail
SET net_amount = ROUND(COALESCE(amount, 0) + COALESCE(tax_amount, 0), 2)
WHERE net_amount IS NULL;

UPDATE produce_disassemble_bom
SET net_amount = ROUND(COALESCE(amount, 0) + COALESCE(tax_amount, 0), 2)
WHERE net_amount IS NULL;

UPDATE produce_disassemble
SET net_amount = ROUND(COALESCE(amount, 0) + COALESCE(tax_amount, 0), 2)
WHERE net_amount IS NULL;
