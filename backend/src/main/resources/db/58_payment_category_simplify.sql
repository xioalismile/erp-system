-- ============================================================
-- 58_payment_category_simplify.sql
-- 付款管控分类精简为「现结/月结」两档 + 销售合同现结免审批
-- 存量旧值迁移：现金客户/招投标客户 -> 现结；月结/账期/特批客户 -> 月结
-- 幂等：可重复执行，结果一致
-- ============================================================

-- 1. 更新字段注释（与新版两档口径一致）
COMMENT ON COLUMN customer.payment_category IS '付款管控分类：现结/月结（精简后仅两档）';

-- 2. 存量数据迁移（幂等：反复执行结果恒定）
-- 2.1 现金客户 / 招投标客户 -> 现结
UPDATE customer SET payment_category = '现结'
WHERE payment_category IN ('现金客户', '招投标客户', '现金', '招投标');

-- 2.2 月结客户 / 账期客户 / 特批客户 -> 月结
UPDATE customer SET payment_category = '月结'
WHERE payment_category IN ('月结客户', '账期客户', '特批客户', '月结30', '月结60');

-- 2.3 NULL / 空 -> 现结（默认值，与新增客户一致）
UPDATE customer SET payment_category = '现结'
WHERE payment_category IS NULL OR payment_category = '';

-- 3. 删除付款条件相关：仅移除前端入口，数据库保留 payment_term 表与数据
--    （保护历史单据 payment_term_id 引用与导出/复制链路，本脚本不做物理删除）
--    此处仅为占位说明，不执行任何删除语句