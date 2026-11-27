-- ============================================================
-- 补丁27: 修复基础数据表缺失字段
-- 问题: PriceLevel/PaymentTerm/UnitOfMeasure实体类有字段但数据库表缺少对应列
-- 影响: /api/basedata/price-level/list, /api/basedata/payment-term/list, /api/basedata/unit/list
--       全部返回400错误，导致前端Promise.all失败，客户下拉框等无法加载
-- ============================================================

-- 1. price_level 表添加 price_adjust_rate 列
ALTER TABLE price_level ADD COLUMN IF NOT EXISTS price_adjust_rate DECIMAL(10,4);

-- 2. payment_term 表添加 discount_rate, advance_rate 列
ALTER TABLE payment_term ADD COLUMN IF NOT EXISTS discount_rate DECIMAL(10,4);
ALTER TABLE payment_term ADD COLUMN IF NOT EXISTS advance_rate DECIMAL(10,4);

-- 3. unit_of_measure 表添加 conversion_rate 列
ALTER TABLE unit_of_measure ADD COLUMN IF NOT EXISTS conversion_rate DECIMAL(18,6);

-- 验证
SELECT 'price_level' as tbl, column_name FROM information_schema.columns WHERE table_name='price_level' AND column_name='price_adjust_rate'
UNION ALL
SELECT 'payment_term', column_name FROM information_schema.columns WHERE table_name='payment_term' AND column_name IN ('discount_rate','advance_rate')
UNION ALL
SELECT 'unit_of_measure', column_name FROM information_schema.columns WHERE table_name='unit_of_measure' AND column_name='conversion_rate';
