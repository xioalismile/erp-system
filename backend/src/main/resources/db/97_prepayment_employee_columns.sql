-- 97_prepayment_employee_columns.sql
-- 目的：给「销售预收款单 sale_pre_receipt」「采购预付款单 purchase_pre_payment」补「收款人 / 付款人」员工字段。
--       依据《列表页使用说明书》自检表第 1 条：单据页至少需要 单据编号 / 状态 / 日期 + 该页往来单位或经手人；
--       这两张表原先只有 customer_id / supplier_id，没有任何员工列，导致列表页无法声明员工筛选维度。
-- 幂等：全部使用 ADD COLUMN IF NOT EXISTS，可重复执行；旧数据保持 NULL（行为与升级前一致，不改变任何可见数据范围）。
-- 运行时兜底：com.erp.config.SchemaUpgradeEnsurer.ensureBusinessDbColumns() 同步声明了这 4 列，老库启动时自动补齐。

ALTER TABLE sale_pre_receipt     ADD COLUMN IF NOT EXISTS cashier_id   BIGINT;
ALTER TABLE sale_pre_receipt     ADD COLUMN IF NOT EXISTS cashier_name VARCHAR(100);
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS cashier_id   BIGINT;
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS cashier_name VARCHAR(100);
