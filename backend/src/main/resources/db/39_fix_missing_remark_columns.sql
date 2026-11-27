-- ============================================================
-- 补丁39: 修复基础资料表缺失 remark 列
-- 问题: 实体类 MaterialCategory/Material/UnitOfMeasure/Department/Employee/
--       Warehouse/Location/Currency/Account/SettlementMode/PaymentTerm/PriceLevel
--       中定义了 remark 字段，但旧部署的数据库表中缺少对应列
-- 影响: 加载物料分类等基础数据时报错 "字段 'remark' 不存在"
-- 幂等: 使用 IF NOT EXISTS + ADD COLUMN IF NOT EXISTS，可重复安全执行
-- 生成时间: 2026-08-19
-- ============================================================

SET client_encoding TO 'UTF8';

DO $$
DECLARE
    col_exists BOOLEAN;
BEGIN

    -- ========== 1. material_category (物料分类表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='material_category' AND column_name='remark'
    ) THEN
        ALTER TABLE material_category ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: material_category.remark';
    END IF;

    -- ========== 2. material (物料档案表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='material' AND column_name='remark'
    ) THEN
        ALTER TABLE material ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: material.remark';
    END IF;

    -- ========== 3. unit_of_measure (计量单位表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='unit_of_measure' AND column_name='remark'
    ) THEN
        ALTER TABLE unit_of_measure ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: unit_of_measure.remark';
    END IF;

    -- ========== 4. department (部门表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='department' AND column_name='remark'
    ) THEN
        ALTER TABLE department ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: department.remark';
    END IF;

    -- ========== 5. employee (员工表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='employee' AND column_name='remark'
    ) THEN
        ALTER TABLE employee ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: employee.remark';
    END IF;

    -- ========== 6. warehouse (仓库表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='warehouse' AND column_name='remark'
    ) THEN
        ALTER TABLE warehouse ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: warehouse.remark';
    END IF;

    -- ========== 7. location (货位表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='location' AND column_name='remark'
    ) THEN
        ALTER TABLE location ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: location.remark';
    END IF;

    -- ========== 8. currency (币种表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='currency' AND column_name='remark'
    ) THEN
        ALTER TABLE currency ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: currency.remark';
    END IF;

    -- ========== 9. account (会计科目表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='account' AND column_name='remark'
    ) THEN
        ALTER TABLE account ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: account.remark';
    END IF;

    -- ========== 10. settlement_mode (结算方式表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='settlement_mode' AND column_name='remark'
    ) THEN
        ALTER TABLE settlement_mode ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: settlement_mode.remark';
    END IF;

    -- ========== 11. payment_term (付款条件表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='payment_term' AND column_name='remark'
    ) THEN
        ALTER TABLE payment_term ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: payment_term.remark';
    END IF;

    -- ========== 12. price_level (价格等级表) ==========
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='price_level' AND column_name='remark'
    ) THEN
        ALTER TABLE price_level ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
        RAISE NOTICE 'Added: price_level.remark';
    END IF;

END $$;

-- ============================================================
-- 验证: 12个基础表的 remark 列应全部存在
-- ============================================================
SELECT table_name,
       CASE WHEN EXISTS (
           SELECT 1 FROM information_schema.columns c
           WHERE c.table_name = t.table_name AND c.column_name = 'remark'
       ) THEN 'OK' ELSE 'MISSING' END as remark_column_status
FROM (VALUES
    ('material_category'),('material'),('unit_of_measure'),('department'),
    ('employee'),('warehouse'),('location'),('currency'),
    ('account'),('settlement_mode'),('payment_term'),('price_level')
) t(table_name)
ORDER BY table_name;
