-- 190_bill_status_unify.sql
-- 单据统一状态机接入：为尚未接入的 10 张单据补齐状态机必需列（2026-11-09）
--
-- 背景：统一单据状态机（AbstractBillService）要求单据具备「审批状态 / 审核人 / 审核时间 /
--       结案标志 / 结案三件套 / 作废四件套」等列。以下 10 张单据此前各自实现状态流转，
--       缺部分列，无法接入统一基类。
--
-- 口径：
--   1) 全部 ADD COLUMN IF NOT EXISTS，幂等；重复执行 0 副作用；
--   2) 只加列、不删列、不改类型、不动任何存量数据；
--   3) 新增列一律允许 NULL（除 close_status 给默认 0 = 未结案），存量单据行为不变；
--   4) 三库同口径由启动自愈保证：主库 + 全部租户独立库 + 租户模板库。
--
-- 影响表：operation_expense / purchase_expense / sale_expense / purchase_pre_payment /
--         sale_pre_receipt / cost_calculation / wage_settlement / bom /
--         sale_price_strategy / purchase_price_strategy

-- ============ 经营费用分摊：补「作废前状态」 ============
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS bill_before_status INTEGER;

-- ============ 采购费用：补审核人/审核时间 ============
ALTER TABLE purchase_expense ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE purchase_expense ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

-- ============ 销售费用：补审批状态/审核人/审核时间 ============
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

-- ============ 采购预付款：补审核人/审核时间 ============
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

-- ============ 销售预收款：补审批状态/审核人/审核时间 ============
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

-- ============ 成本核算：补审批状态/审核人/审核时间 ============
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

-- ============ 工序工资核算：补作废前状态 + 结案四件套 ============
ALTER TABLE wage_settlement ADD COLUMN IF NOT EXISTS bill_before_status INTEGER;
ALTER TABLE wage_settlement ADD COLUMN IF NOT EXISTS close_status INTEGER DEFAULT 0;
ALTER TABLE wage_settlement ADD COLUMN IF NOT EXISTS closed_by VARCHAR(64);
ALTER TABLE wage_settlement ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE wage_settlement ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(255);

-- ============ BOM：补结案四件套 + 作废四件套 + 作废前状态 ============
ALTER TABLE bom ADD COLUMN IF NOT EXISTS close_status INTEGER DEFAULT 0;
ALTER TABLE bom ADD COLUMN IF NOT EXISTS closed_by VARCHAR(64);
ALTER TABLE bom ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE bom ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(255);
ALTER TABLE bom ADD COLUMN IF NOT EXISTS bill_before_status INTEGER;
ALTER TABLE bom ADD COLUMN IF NOT EXISTS voided_by VARCHAR(64);
ALTER TABLE bom ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE bom ADD COLUMN IF NOT EXISTS void_reason VARCHAR(255);

-- ============ 销售价格策略：补审批/结案/作废前状态 ============
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS close_status INTEGER DEFAULT 0;
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS closed_by VARCHAR(64);
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(255);
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS bill_before_status INTEGER;

-- ============ 采购价格策略：同上 ============
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS close_status INTEGER DEFAULT 0;
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS closed_by VARCHAR(64);
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(255);
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS bill_before_status INTEGER;

-- ============ 2026-11-09 补充：9 张单据缺「审核人 / 审核时间」 ============
-- 根因：首批补列核对时按推测表名查询，采购入库与生产工单的真实表名分别是
--       purchase_receipt / production_order（数据已齐，无缺口）；
--       而打样、组装拆卸、委外 5 张的真实缺口是 approved_by / approved_at。
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

ALTER TABLE subcontract_pick ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE subcontract_pick ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS approved_by VARCHAR(64);
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
