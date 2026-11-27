-- ===========================================================================
-- ERP系统数据完善与修复脚本 (PostgreSQL)
-- 用途: 1. 修复 sys_user_role 表缺失字段
--       2. 完善物料数据(类型对齐字典)
--       3. 补充基础资料数据
-- ===========================================================================
SET client_encoding TO 'UTF8';

BEGIN;

-- ===========================================================================
-- 1. 修复 sys_user_role 表结构 - 添加缺失字段
-- ===========================================================================
ALTER TABLE IF EXISTS sys_user_role 
ADD COLUMN IF NOT EXISTS is_primary_role SMALLINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS assign_date DATE,
ADD COLUMN IF NOT EXISTS assign_by VARCHAR(50),
ADD COLUMN IF NOT EXISTS data_scope_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS data_scope_value VARCHAR(500);

-- ===========================================================================
-- 2. 更新 sys_user_role 已有数据 - 填充新增字段
-- ===========================================================================
UPDATE sys_user_role 
SET is_primary_role = CASE 
    WHEN role_id = 1 THEN 1  
    WHEN role_id = 3 THEN 1  
    WHEN role_id = 4 THEN 1  
    WHEN role_id = 6 THEN 1  
    WHEN role_id = 8 THEN 1  
    WHEN role_id = 10 THEN 1 
    WHEN role_id = 12 THEN 1 
    ELSE 0 
END,
assign_date = CURRENT_DATE,
assign_by = 'admin',
data_scope_type = CASE 
    WHEN role_id = 1 THEN '全部'
    ELSE '本部门' 
END,
data_scope_value = NULL;

-- ===========================================================================
-- 3. 修复物料类型值 - 对齐字典数据
-- 字典值: 原材料, 半成品, 成品, 包材
-- 当前数据: 原料, 半成品, 成品, 包装材料
-- ===========================================================================
UPDATE material 
SET material_type = '原材料' 
WHERE material_type = '原料';

UPDATE material 
SET material_type = '包材' 
WHERE material_type = '包装材料';

-- ===========================================================================
-- 4. 确保物料分类数据完整
-- ===========================================================================
INSERT INTO material_category (category_code, category_name, parent_id, category_level, sort_order, status)
SELECT 
    'MC' || LPAD(g::TEXT, 3, '0'),
    CASE (g % 10)
        WHEN 0 THEN '轴承类'
        WHEN 1 THEN '轴类'
        WHEN 2 THEN '电子件'
        WHEN 3 THEN '结构件'
        WHEN 4 THEN '标准件'
        WHEN 5 THEN '原材料'
        WHEN 6 THEN '包装材料'
        WHEN 7 THEN '辅助材料'
        WHEN 8 THEN '半成品'
        WHEN 9 THEN '成品'
    END || '-' || g,
    CASE WHEN g <= 10 THEN NULL ELSE ((g - 1) / 5)::BIGINT + 1 END,
    CASE WHEN g <= 10 THEN 1 ELSE 2 END,
    g,
    '启用'
FROM generate_series(1, 50) g
ON CONFLICT (category_code) DO NOTHING;

-- ===========================================================================
-- 5. 确保计量单位数据完整
-- ===========================================================================
INSERT INTO unit_of_measure (unit_code, unit_name, unit_group, is_base_unit, conversion_rate, status)
SELECT
    'U' || LPAD(g::TEXT, 3, '0'),
    CASE (g % 5)
        WHEN 0 THEN '件' || g
        WHEN 1 THEN '盒' || g
        WHEN 2 THEN '套' || g
        WHEN 3 THEN '包' || g
        WHEN 4 THEN '箱' || g
    END,
    CASE (g % 5)
        WHEN 0 THEN '个数'
        WHEN 1 THEN '包装'
        WHEN 2 THEN '成套'
        WHEN 3 THEN '包装'
        WHEN 4 THEN '运输'
    END,
    CASE WHEN g % 3 = 0 THEN 1 ELSE 0 END,
    1.0,
    '启用'
FROM generate_series(1, 30) g
ON CONFLICT (unit_code) DO NOTHING;

-- ===========================================================================
-- 6. 确保货币数据完整 (字段: currency_code, currency_name, currency_symbol, is_base_currency, exchange_rate, status)
-- ===========================================================================
INSERT INTO currency (currency_code, currency_name, currency_symbol, is_base_currency, exchange_rate, status)
VALUES
('CNY', '人民币', '¥', 1, 1.0, '启用'),
('USD', '美元', '$', 0, 7.24, '启用'),
('EUR', '欧元', '€', 0, 7.86, '启用'),
('GBP', '英镑', '£', 0, 9.12, '启用'),
('JPY', '日元', '¥', 0, 0.048, '启用')
ON CONFLICT (currency_code) DO UPDATE SET 
    currency_name = EXCLUDED.currency_name,
    currency_symbol = EXCLUDED.currency_symbol,
    is_base_currency = EXCLUDED.is_base_currency,
    exchange_rate = EXCLUDED.exchange_rate;

-- ===========================================================================
-- 7. 确保付款条件数据完整 (字段: payment_term_code, payment_term_name, payment_days, discount_days, discount_rate, advance_rate, status)
-- ===========================================================================
INSERT INTO payment_term (payment_term_code, payment_term_name, payment_days, discount_days, discount_rate, advance_rate, status)
VALUES
('NET7', '7天内付款', 7, 0, 0.0000, 0.0000, '启用'),
('NET15', '15天内付款', 15, 0, 0.0000, 0.0000, '启用'),
('NET30', '30天内付款', 30, 0, 0.0000, 0.0000, '启用'),
('NET45', '45天内付款', 45, 0, 0.0000, 0.0000, '启用'),
('NET60', '60天内付款', 60, 0, 0.0000, 0.0000, '启用'),
('30D5P', '30天付款/5%折扣', 30, 10, 5.0000, 0.0000, '启用'),
('30D2P', '30天付款/2%折扣', 30, 15, 2.0000, 0.0000, '启用'),
('COD', '货到付款', 0, 0, 0.0000, 0.0000, '启用')
ON CONFLICT (payment_term_code) DO NOTHING;

-- ===========================================================================
-- 8. 确保结算方式数据完整
-- ===========================================================================
INSERT INTO settlement_mode (settle_mode_code, settle_mode_name, status)
VALUES
('BANK_TRANSFER', '银行转账', '启用'),
('CASH', '现金', '启用'),
('CHECK', '支票', '启用'),
('CREDIT_CARD', '信用卡', '启用'),
('ONLINE_PAY', '在线支付', '启用'),
('BANK_DRAFT', '银行汇票', '启用'),
('PROMISSORY_NOTE', '承兑汇票', '启用')
ON CONFLICT (settle_mode_code) DO NOTHING;

-- ===========================================================================
-- 9. 确保价格等级数据完整
-- ===========================================================================
INSERT INTO price_level (price_level_code, price_level_name, price_adjust_rate, sort_order, status)
VALUES
('PL001', '标准价', 0.0, 1, '启用'),
('PL002', '零售价', 10.0, 2, '启用'),
('PL003', '批发价', -5.0, 3, '启用'),
('PL004', 'VIP价', -15.0, 4, '启用'),
('PL005', '代理价', -20.0, 5, '启用')
ON CONFLICT (price_level_code) DO NOTHING;

-- ===========================================================================
-- 10. 重置序列
-- ===========================================================================
SELECT setval('sys_user_role_user_role_id_seq', (SELECT COALESCE(MAX(user_role_id), 1) FROM sys_user_role));
SELECT setval('material_material_id_seq', (SELECT COALESCE(MAX(material_id), 1) FROM material));
SELECT setval('material_category_category_id_seq', (SELECT COALESCE(MAX(category_id), 1) FROM material_category));
SELECT setval('unit_of_measure_unit_id_seq', (SELECT COALESCE(MAX(unit_id), 1) FROM unit_of_measure));
SELECT setval('currency_currency_id_seq', (SELECT COALESCE(MAX(currency_id), 1) FROM currency));
SELECT setval('payment_term_payment_term_id_seq', (SELECT COALESCE(MAX(payment_term_id), 1) FROM payment_term));
SELECT setval('settlement_mode_settle_mode_id_seq', (SELECT COALESCE(MAX(settle_mode_id), 1) FROM settlement_mode));
SELECT setval('price_level_price_level_id_seq', (SELECT COALESCE(MAX(price_level_id), 1) FROM price_level));

-- ===========================================================================
-- 验证数据
-- ===========================================================================
SELECT 'sys_user_role字段检查' AS check_item, 
       CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sys_user_role' AND column_name = 'is_primary_role') THEN 'OK' ELSE '缺失' END AS status;

SELECT '物料类型统计' AS check_item, 
       material_type, 
       COUNT(*) AS count 
FROM material 
GROUP BY material_type 
ORDER BY material_type;

COMMIT;

-- ===========================================================================
-- 执行完成
-- ===========================================================================
SELECT '数据完善与修复完成' AS result;
