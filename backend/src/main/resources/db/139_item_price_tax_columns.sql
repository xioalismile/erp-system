-- ============================================================
-- 139：单据「计税方式」与含税/不含税价 —— 明细列全站铺开（2026-10-09）
--
-- 用途：在全部带明细的单据表体（共 40 张）上补齐两列 ——
--   price_with_tax     含税价  （按表头「计税方式」由「单价」换算的含税单价）
--   price_without_tax  不含税价（按表头「计税方式」由「单价」换算的不含税单价）
-- 说明：
--   1) 全部幂等（ADD COLUMN IF NOT EXISTS），可重复执行；只建列，不改动任何数据。
--   2) sale_order_item 为 2026-10-08 销售订单试点已建列，此处一并列出（IF NOT EXISTS 天然幂等）。
--   3) 配套：SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 登记同样 40 张表的两列，启动逐租户库自愈。
--   4) 镜像脚本：backend/src/main/resources/db/139_item_price_tax_columns.sql（内容与本文件一致）。
-- ============================================================

-- ① 销售（6 张）
ALTER TABLE sale_order_item     ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sale_order_item     ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sale_order_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sale_order_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE sale_quote_item     ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sale_quote_item     ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sale_quote_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sale_quote_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE sale_contract_item  ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sale_contract_item  ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sale_contract_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sale_contract_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE sale_delivery_item  ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sale_delivery_item  ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sale_delivery_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sale_delivery_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE sale_invoice_item   ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sale_invoice_item   ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sale_invoice_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sale_invoice_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE sale_return_item    ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sale_return_item    ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sale_return_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sale_return_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

-- ② 采购（5 张）
ALTER TABLE purchase_order_item    ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE purchase_order_item    ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN purchase_order_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN purchase_order_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN purchase_contract_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN purchase_contract_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE purchase_receipt_item  ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE purchase_receipt_item  ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN purchase_receipt_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN purchase_receipt_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE purchase_return_item   ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE purchase_return_item   ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN purchase_return_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN purchase_return_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE purchase_invoice_item  ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE purchase_invoice_item  ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN purchase_invoice_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN purchase_invoice_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

-- ③ 库存（5 张）
ALTER TABLE stock_transfer_item      ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE stock_transfer_item      ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN stock_transfer_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN stock_transfer_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE stock_check_item         ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE stock_check_item         ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN stock_check_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN stock_check_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE other_receipt_item       ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE other_receipt_item       ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN other_receipt_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN other_receipt_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE other_issue_item         ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE other_issue_item         ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN other_issue_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN other_issue_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE material_requisition_item ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE material_requisition_item ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN material_requisition_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN material_requisition_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

-- ④ 生产（4 张）
ALTER TABLE production_order_item   ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE production_order_item   ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN production_order_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN production_order_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE production_receipt_item ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE production_receipt_item ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN production_receipt_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN production_receipt_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE work_order_operation    ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE work_order_operation    ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN work_order_operation.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN work_order_operation.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE routing_operation       ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE routing_operation       ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN routing_operation.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN routing_operation.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

-- ⑤ 生产组装/拆卸（4 张）
ALTER TABLE produce_assemble_detail     ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE produce_assemble_detail     ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN produce_assemble_detail.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN produce_assemble_detail.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE produce_assemble_bom        ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE produce_assemble_bom        ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN produce_assemble_bom.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN produce_assemble_bom.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE produce_disassemble_detail  ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE produce_disassemble_detail  ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN produce_disassemble_detail.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN produce_disassemble_detail.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE produce_disassemble_bom     ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE produce_disassemble_bom     ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN produce_disassemble_bom.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN produce_disassemble_bom.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

-- ⑥ 委外（2 张）
ALTER TABLE subcontract_order_item   ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE subcontract_order_item   ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN subcontract_order_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN subcontract_order_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN subcontract_inbound_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN subcontract_inbound_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

-- ⑦ 打样（1 张）
ALTER TABLE sample_order_item ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sample_order_item ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sample_order_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sample_order_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

-- ⑧ 财务（13 张：收付款 / 预收预付 / 资金调拨 / 成本核算 / 费用 / 核销 / 费用分摊 / 对账 / 工资）
ALTER TABLE cash_receipt_item              ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE cash_receipt_item              ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN cash_receipt_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN cash_receipt_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE cash_payment_item              ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE cash_payment_item              ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN cash_payment_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN cash_payment_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE sale_pre_receipt_item          ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sale_pre_receipt_item          ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sale_pre_receipt_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sale_pre_receipt_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE purchase_pre_payment_item      ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE purchase_pre_payment_item      ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN purchase_pre_payment_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN purchase_pre_payment_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE fund_transfer_item             ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE fund_transfer_item             ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN fund_transfer_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN fund_transfer_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE cost_calculation_item          ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE cost_calculation_item          ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN cost_calculation_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN cost_calculation_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE sale_expense_item              ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sale_expense_item              ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sale_expense_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sale_expense_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE purchase_expense_item          ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE purchase_expense_item          ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN purchase_expense_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN purchase_expense_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE bill_writeoff_detail           ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE bill_writeoff_detail           ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN bill_writeoff_detail.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN bill_writeoff_detail.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE operation_expense_allocation   ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE operation_expense_allocation   ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN operation_expense_allocation.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN operation_expense_allocation.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE sale_statement_item            ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE sale_statement_item            ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN sale_statement_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN sale_statement_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE purchase_statement_item        ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE purchase_statement_item        ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN purchase_statement_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN purchase_statement_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';

ALTER TABLE wage_settlement_item           ADD COLUMN IF NOT EXISTS price_with_tax NUMERIC(18,6);
ALTER TABLE wage_settlement_item           ADD COLUMN IF NOT EXISTS price_without_tax NUMERIC(18,6);
COMMENT ON COLUMN wage_settlement_item.price_with_tax IS '含税价（按表头计税方式由单价换算）';
COMMENT ON COLUMN wage_settlement_item.price_without_tax IS '不含税价（按表头计税方式由单价换算）';
