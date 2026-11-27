-- ===========================================================================
-- 33_sales_target_multi_metrics.sql  (2026-08-16)
-- 销售目标模块对齐操作手册 §16 的全面升级补丁（幂等）
-- 升级内容：
--   1. sales_target_employee 新增 4 个指标字段：新客户目标/实际、订单数目标/实际
--   2. sales_target_employee 完成率注释从"成交商机"改为"销售订单汇总（符合操作手册规范）"
-- ===========================================================================

-- 1. sales_target_employee 新增 4 列（幂等）
ALTER TABLE sales_target_employee ADD COLUMN IF NOT EXISTS new_customer_target INTEGER DEFAULT 0;
ALTER TABLE sales_target_employee ADD COLUMN IF NOT EXISTS new_customer_actual INTEGER DEFAULT 0;
ALTER TABLE sales_target_employee ADD COLUMN IF NOT EXISTS order_count_target INTEGER DEFAULT 0;
ALTER TABLE sales_target_employee ADD COLUMN IF NOT EXISTS order_count_actual INTEGER DEFAULT 0;

-- 2. 列注释（幂等覆盖，用 EXECUTE 避免解析期列校验）
DO $$
BEGIN
    EXECUTE 'COMMENT ON COLUMN sales_target_employee.new_customer_target IS ''新客户数目标（年度内新增客户数量指标）''';
    EXECUTE 'COMMENT ON COLUMN sales_target_employee.new_customer_actual IS ''新客户数实际（自动汇总：assigned_sales_id=该员工 且 created_at 在目标年度内的有效客户数）''';
    EXECUTE 'COMMENT ON COLUMN sales_target_employee.order_count_target IS ''订单数目标（年度内销售订单数量指标）''';
    EXECUTE 'COMMENT ON COLUMN sales_target_employee.order_count_actual IS ''订单数实际（自动汇总：saler_id=该员工 且 order_date 在目标年度 且 已生效/已结案未作废的订单数）''';
    -- actual_amount / completion_rate 的注释同步更新到"销售订单"口径
    EXECUTE 'COMMENT ON COLUMN sales_target_employee.actual_amount IS ''实际完成金额（自动汇总销售订单：saler_id=该员工、年度内已生效/已结案且未作废订单的 total_amount 合计）''';
    EXECUTE 'COMMENT ON COLUMN sales_target_employee.completion_rate IS ''完成率(%) = actual_amount / annual_target_amount * 100''';
END $$;

-- 3. 已有历史数据的新列默认值兜底（如果列是新添加且为空，用 0 补齐——上面 DEFAULT 0 已处理，这里再保护一次）
UPDATE sales_target_employee SET new_customer_target = 0 WHERE new_customer_target IS NULL;
UPDATE sales_target_employee SET new_customer_actual = 0 WHERE new_customer_actual IS NULL;
UPDATE sales_target_employee SET order_count_target = 0 WHERE order_count_target IS NULL;
UPDATE sales_target_employee SET order_count_actual = 0 WHERE order_count_actual IS NULL;

-- ===========================================================================
-- 验证（不影响升级）—— 确认 4 列存在
-- SELECT column_name, data_type, column_default
-- FROM information_schema.columns
-- WHERE table_name='sales_target_employee' AND column_name IN (
--   'new_customer_target','new_customer_actual','order_count_target','order_count_actual'
-- ) ORDER BY ordinal_position;
-- ===========================================================================
