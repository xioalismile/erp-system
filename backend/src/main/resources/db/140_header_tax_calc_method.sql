-- ============================================================
-- 140：单据表头「计税方式」列全站落库（2026-10-09）
--
-- 背景：全站「计税方式」铺开时，表头 tax_calc_method 仅有
--       sale_order / sale_contract / purchase_contract 三张表有列，
--       其余单据表头在实体里被临时做成 @TableField(exist = false) 的传输字段（不落库），
--       表现为「保存成功、重新打开单据后表头计税方式丢失（回落默认值）」。
-- 用途：为下列缺少该列的单据表头补列，使表头「计税方式」真正落库、详情接口能回读。
-- 说明：
--   1) 全部幂等（ADD COLUMN IF NOT EXISTS），可重复执行；只建列，不改动任何数据。
--   2) 三张已有该列的表（sale_order / sale_contract / purchase_contract）不在本脚本内。
--   3) 配套：SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 登记同样这批表，启动逐租户库自愈。
--   4) 镜像脚本：backend/src/main/resources/db/140_header_tax_calc_method.sql（内容与本文件一致）。
-- ============================================================

-- ① 销售（4 张）
ALTER TABLE sale_quote          ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN sale_quote.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE sale_return         ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN sale_return.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE sale_invoice        ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN sale_invoice.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE sale_delivery       ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN sale_delivery.tax_calc_method IS '计税方式：含税价/不含税价/不计税';

-- ② 采购（4 张）
ALTER TABLE purchase_order      ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN purchase_order.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE purchase_receipt    ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN purchase_receipt.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE purchase_return     ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN purchase_return.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE purchase_invoice    ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN purchase_invoice.tax_calc_method IS '计税方式：含税价/不含税价/不计税';

-- ③ 库存（5 张）
ALTER TABLE other_receipt       ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN other_receipt.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE other_issue         ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN other_issue.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE stock_transfer      ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN stock_transfer.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE stock_check         ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN stock_check.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE material_requisition ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN material_requisition.tax_calc_method IS '计税方式：含税价/不含税价/不计税';

-- ④ 生产（5 张）
ALTER TABLE production_order    ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN production_order.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE production_receipt  ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN production_receipt.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE routing             ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN routing.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE produce_assemble    ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN produce_assemble.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN produce_disassemble.tax_calc_method IS '计税方式：含税价/不含税价/不计税';

-- ⑤ 其他单据（3 张）
ALTER TABLE sample_order        ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN sample_order.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE cost_calculation    ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN cost_calculation.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
ALTER TABLE subcontract_order   ADD COLUMN IF NOT EXISTS tax_calc_method VARCHAR(20);
COMMENT ON COLUMN subcontract_order.tax_calc_method IS '计税方式：含税价/不含税价/不计税';
