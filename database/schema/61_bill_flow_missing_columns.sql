-- 61_bill_flow_missing_columns.sql
-- 修复：单据状态流转按钮（重置草稿/驳回/作废）引用的数据库列在部分表从未创建，
-- 导致点击对应按钮必报数据库错误（UPDATE ... 字段不存在）。
-- 审计方法：提取 6 个 ServiceImpl 全部 UpdateWrapper .set() 引用列 + 实体 @TableField 列，
--           与 information_schema 全量比对（实体映射列均已存在，缺的全是流转列）。
-- 代码依据：
--   SalePreReceiptServiceImpl / PurchasePrePaymentServiceImpl
--     :296-299 reject  :320-322 reverse  :343-346 unvoid
--   OperationExpenseServiceImpl
--     :463-466 reject  :487-489 reverse  :513-516 void  :537-540 unvoid
--   SalePriceStrategyServiceImpl
--     :232-235 reject  :251-253 reverse  :272-275 void  :291-294 unvoid
--   PurchasePriceStrategyServiceImpl
--     :202-205 reject  :221-223 reverse  :242-245 void  :261-264 unvoid
--   CostCalculationServiceImpl
--     :825-828 reject  :849-851 reverse  :875-878 void  :899-902 unvoid
-- 注意：全部使用 ADD COLUMN IF NOT EXISTS，可重复执行（幂等）。

-- ============================================================
-- 一、驳回列（reject_reason / rejector / rejected_at）—— 6 张表全部缺失
-- ============================================================

-- 1. 销售预收款单
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS rejector VARCHAR(50);
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;
COMMENT ON COLUMN sale_pre_receipt.reject_reason IS '驳回原因';
COMMENT ON COLUMN sale_pre_receipt.rejector IS '驳回人';
COMMENT ON COLUMN sale_pre_receipt.rejected_at IS '驳回时间';

-- 2. 采购预付款单
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS rejector VARCHAR(50);
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;
COMMENT ON COLUMN purchase_pre_payment.reject_reason IS '驳回原因';
COMMENT ON COLUMN purchase_pre_payment.rejector IS '驳回人';
COMMENT ON COLUMN purchase_pre_payment.rejected_at IS '驳回时间';

-- 3. 经营费用
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS rejector VARCHAR(50);
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;
COMMENT ON COLUMN operation_expense.reject_reason IS '驳回原因';
COMMENT ON COLUMN operation_expense.rejector IS '驳回人';
COMMENT ON COLUMN operation_expense.rejected_at IS '驳回时间';

-- 4. 销售价格策略
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS rejector VARCHAR(50);
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;
COMMENT ON COLUMN sale_price_strategy.reject_reason IS '驳回原因';
COMMENT ON COLUMN sale_price_strategy.rejector IS '驳回人';
COMMENT ON COLUMN sale_price_strategy.rejected_at IS '驳回时间';

-- 5. 采购价格策略
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS rejector VARCHAR(50);
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;
COMMENT ON COLUMN purchase_price_strategy.reject_reason IS '驳回原因';
COMMENT ON COLUMN purchase_price_strategy.rejector IS '驳回人';
COMMENT ON COLUMN purchase_price_strategy.rejected_at IS '驳回时间';

-- 6. 成本核算单
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS rejector VARCHAR(50);
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;
COMMENT ON COLUMN cost_calculation.reject_reason IS '驳回原因';
COMMENT ON COLUMN cost_calculation.rejector IS '驳回人';
COMMENT ON COLUMN cost_calculation.rejected_at IS '驳回时间';

-- ============================================================
-- 二、作废列（void_reason / voided_by / voided_at）—— 3 张表缺失
-- （销售预收/采购预付两表已有，来自 21_bill_status_unify.sql；成本核算已有）
-- ============================================================

-- 3. 经营费用
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
COMMENT ON COLUMN operation_expense.void_reason IS '作废原因';
COMMENT ON COLUMN operation_expense.voided_by IS '作废人';
COMMENT ON COLUMN operation_expense.voided_at IS '作废时间';

-- 4. 销售价格策略
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
COMMENT ON COLUMN sale_price_strategy.void_reason IS '作废原因';
COMMENT ON COLUMN sale_price_strategy.voided_by IS '作废人';
COMMENT ON COLUMN sale_price_strategy.voided_at IS '作废时间';

-- 5. 采购价格策略
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
COMMENT ON COLUMN purchase_price_strategy.void_reason IS '作废原因';
COMMENT ON COLUMN purchase_price_strategy.voided_by IS '作废人';
COMMENT ON COLUMN purchase_price_strategy.voided_at IS '作废时间';

-- ============================================================
-- 三、重置草稿列（reverse_approver / reverse_approved_at）—— 6 张表全部缺失
-- （本部分已于本次修复先行验证：销售预收/采购预付重置草稿+删除已跑通）
-- ============================================================

-- 1. 销售预收款单
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS reverse_approver VARCHAR(50);
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS reverse_approved_at TIMESTAMP;
COMMENT ON COLUMN sale_pre_receipt.reverse_approver IS '重置草稿人';
COMMENT ON COLUMN sale_pre_receipt.reverse_approved_at IS '重置草稿时间';

-- 2. 采购预付款单
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS reverse_approver VARCHAR(50);
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS reverse_approved_at TIMESTAMP;
COMMENT ON COLUMN purchase_pre_payment.reverse_approver IS '重置草稿人';
COMMENT ON COLUMN purchase_pre_payment.reverse_approved_at IS '重置草稿时间';

-- 3. 经营费用
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS reverse_approver VARCHAR(50);
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS reverse_approved_at TIMESTAMP;
COMMENT ON COLUMN operation_expense.reverse_approver IS '重置草稿人';
COMMENT ON COLUMN operation_expense.reverse_approved_at IS '重置草稿时间';

-- 4. 销售价格策略
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS reverse_approver VARCHAR(50);
ALTER TABLE sale_price_strategy ADD COLUMN IF NOT EXISTS reverse_approved_at TIMESTAMP;
COMMENT ON COLUMN sale_price_strategy.reverse_approver IS '重置草稿人';
COMMENT ON COLUMN sale_price_strategy.reverse_approved_at IS '重置草稿时间';

-- 5. 采购价格策略
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS reverse_approver VARCHAR(50);
ALTER TABLE purchase_price_strategy ADD COLUMN IF NOT EXISTS reverse_approved_at TIMESTAMP;
COMMENT ON COLUMN purchase_price_strategy.reverse_approver IS '重置草稿人';
COMMENT ON COLUMN purchase_price_strategy.reverse_approved_at IS '重置草稿时间';

-- 6. 成本核算单（表内已有 reversed_by/reversed_at 旧列，本列为重置草稿流程新引用，两者并存不冲突）
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS reverse_approver VARCHAR(50);
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS reverse_approved_at TIMESTAMP;
COMMENT ON COLUMN cost_calculation.reverse_approver IS '重置草稿人';
COMMENT ON COLUMN cost_calculation.reverse_approved_at IS '重置草稿时间';