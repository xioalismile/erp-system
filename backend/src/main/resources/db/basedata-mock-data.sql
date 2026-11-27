-- ===========================================================================
-- 基础资料模拟数据生成脚本 (PostgreSQL)
-- 用途: 为基础资料模块的13张表生成业务合理的模拟数据
-- 数据量: 每表 30-300 条 (按业务实际规模, 重点表 200+)
-- 执行方式: psql -U erp_user -d erp_db -f basedata-mock-data.sql
-- ===========================================================================
-- 顺序: 先无外键依赖的表 -> 有外键依赖的表
--   1. unit_of_measure    (30条)  - 计量单位 (无依赖)
--   2. currency           (30条)  - 币种 (无依赖)
--   3. settlement_mode    (20条)  - 结算方式 (无依赖)
--   4. payment_term       (50条)  - 付款条件 (无依赖)
--   5. price_level        (50条)  - 价格等级 (无依赖)
--   6. material_category  (50条)  - 物料分类 (无依赖)
--   7. account            (200条) - 会计科目 (无依赖)
--   8. department         (200条) - 部门档案 (无依赖)
--   9. employee           (200条) - 员工档案 (依赖 department)
--  10. warehouse          (50条)  - 仓库档案 (manager_id 引用 employee, 但设为 NULL 避免外键问题)
--  11. location           (200条) - 货位档案 (依赖 warehouse)
--  12. material           (300条) - 物料档案 (依赖 unit_of_measure, material_category)
--  13. customer           (250条) - 客户档案 (依赖 payment_term, currency, price_level)
--  14. supplier           (200条) - 供应商档案 (依赖 payment_term, currency)
-- ===========================================================================

-- 强制 UTF-8 客户端编码, 避免 Windows GBK 解析问题
SET client_encoding TO 'UTF8';

BEGIN; -- 事务包裹, 出错全部回滚

-- 清空已有数据(开发环境), 生产环境请删除这两行
-- 注意: TRUNCATE CASCADE 会级联到 sys_user (因 sys_user 有 FK 到 department/employee),
--       所以脚本末尾会重新插入 sys_user 和 sys_user_role 数据, 保证登录可用.
TRUNCATE TABLE unit_of_measure, currency, settlement_mode, payment_term, price_level,
              material_category, account, department, warehouse, employee, location,
              material, customer, supplier RESTART IDENTITY CASCADE;

-- =====================================================================
-- 1. 计量单位 (30条)
-- =====================================================================
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
        WHEN 1 THEN '个数'
        WHEN 2 THEN '个数'
        WHEN 3 THEN '个数'
        WHEN 4 THEN '个数'
    END,
    CASE WHEN g % 10 = 1 THEN 1 ELSE 0 END,
    CASE (g % 5)
        WHEN 0 THEN 1.0
        WHEN 1 THEN 10.0
        WHEN 2 THEN 1.0
        WHEN 3 THEN 5.0
        WHEN 4 THEN 100.0
    END,
    '启用'
FROM generate_series(1, 30) g;

-- 加几个标准单位
UPDATE unit_of_measure SET unit_code='PCS', unit_name='件', unit_group='个数', is_base_unit=1, conversion_rate=1.0 WHERE unit_id=1;
UPDATE unit_of_measure SET unit_code='KG',  unit_name='千克', unit_group='重量', is_base_unit=1, conversion_rate=1.0 WHERE unit_id=2;
UPDATE unit_of_measure SET unit_code='M',   unit_name='米', unit_group='长度', is_base_unit=1, conversion_rate=1.0 WHERE unit_id=3;
UPDATE unit_of_measure SET unit_code='L',   unit_name='升', unit_group='体积', is_base_unit=1, conversion_rate=1.0 WHERE unit_id=4;
UPDATE unit_of_measure SET unit_code='SET', unit_name='套', unit_group='个数', is_base_unit=0, conversion_rate=1.0 WHERE unit_id=5;

-- =====================================================================
-- 2. 币种 (30条)
-- =====================================================================
INSERT INTO currency (currency_code, currency_name, currency_symbol, is_base_currency, exchange_rate, status)
SELECT
    'C' || LPAD(g::TEXT, 3, '0'),
    CASE (g % 6)
        WHEN 0 THEN '人民币'
        WHEN 1 THEN '美元'
        WHEN 2 THEN '欧元'
        WHEN 3 THEN '日元'
        WHEN 4 THEN '港币'
        WHEN 5 THEN '英镑'
    END || '-' || g,
    CASE (g % 6)
        WHEN 0 THEN '¥'
        WHEN 1 THEN '$'
        WHEN 2 THEN '€'
        WHEN 3 THEN '¥'
        WHEN 4 THEN 'HK$'
        WHEN 5 THEN '£'
    END,
    CASE WHEN g = 1 THEN 1 ELSE 0 END,
    CASE (g % 6)
        WHEN 0 THEN 1.0
        WHEN 1 THEN 0.1428
        WHEN 2 THEN 0.1316
        WHEN 3 THEN 21.43
        WHEN 4 THEN 1.107
        WHEN 5 THEN 0.1148
    END,
    '启用'
FROM generate_series(1, 30) g;

-- =====================================================================
-- 3. 结算方式 (20条)
-- =====================================================================
INSERT INTO settlement_mode (settle_mode_code, settle_mode_name, settle_type, sort_order, status)
SELECT
    'S' || LPAD(g::TEXT, 3, '0'),
    CASE (g % 5)
        WHEN 0 THEN '现金'
        WHEN 1 THEN '银行转账'
        WHEN 2 THEN '支票'
        WHEN 3 THEN '汇票'
        WHEN 4 THEN '在线支付'
    END || '-' || g,
    CASE (g % 5)
        WHEN 0 THEN '收款'
        WHEN 1 THEN '通用'
        WHEN 2 THEN '收款'
        WHEN 3 THEN '通用'
        WHEN 4 THEN '通用'
    END,
    g,
    '启用'
FROM generate_series(1, 20) g;

-- =====================================================================
-- 4. 付款条件 (50条)
-- =====================================================================
INSERT INTO payment_term (payment_term_code, payment_term_name, payment_days, discount_days, discount_rate, advance_rate, status)
SELECT
    'PT' || LPAD(g::TEXT, 3, '0'),
    '付款条件-' || g,
    CASE WHEN g % 4 = 0 THEN 30 WHEN g % 4 = 1 THEN 60 WHEN g % 4 = 2 THEN 90 ELSE 15 END,
    CASE WHEN g % 3 = 0 THEN 10 WHEN g % 3 = 1 THEN 15 ELSE 30 END,
    CASE WHEN g % 4 = 0 THEN 2.0 WHEN g % 4 = 1 THEN 3.0 WHEN g % 4 = 2 THEN 5.0 ELSE 0.0 END,
    CASE WHEN g % 3 = 0 THEN 30.0 WHEN g % 3 = 1 THEN 50.0 ELSE 100.0 END,
    '启用'
FROM generate_series(1, 50) g;

-- =====================================================================
-- 5. 价格等级 (50条)
-- =====================================================================
INSERT INTO price_level (price_level_code, price_level_name, price_adjust_rate, sort_order, status)
SELECT
    'PL' || LPAD(g::TEXT, 3, '0'),
    CASE
        WHEN g = 1 THEN '标准价'
        WHEN g = 2 THEN '零售价'
        WHEN g = 3 THEN '批发价'
        WHEN g = 4 THEN 'VIP价'
        WHEN g = 5 THEN '代理价'
        ELSE '价格等级-' || g
    END,
    CASE
        WHEN g = 1 THEN 0.0
        WHEN g = 2 THEN 10.0
        WHEN g = 3 THEN -5.0
        WHEN g = 4 THEN -15.0
        WHEN g = 5 THEN -20.0
        ELSE (g - 25)::NUMERIC
    END,
    g,
    '启用'
FROM generate_series(1, 50) g;

-- =====================================================================
-- 6. 物料分类 (50条)
-- =====================================================================
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
FROM generate_series(1, 50) g;

-- =====================================================================
-- 7. 会计科目 (200条)
-- =====================================================================
INSERT INTO account (account_code, account_name, account_full_name, parent_account_id, account_type, balance_direction, account_level, is_leaf, is_cash_account, status)
SELECT
    'A' || LPAD(g::TEXT, 4, '0'),
    CASE (g % 6)
        WHEN 0 THEN '资产类科目'
        WHEN 1 THEN '负债类科目'
        WHEN 2 THEN '权益类科目'
        WHEN 3 THEN '成本类科目'
        WHEN 4 THEN '损益类科目-收入'
        WHEN 5 THEN '损益类科目-支出'
    END || g,
    '科目全称-' || g,
    CASE WHEN g <= 6 THEN NULL ELSE ((g - 1) / 10 + 1)::BIGINT END,
    CASE (g % 6)
        WHEN 0 THEN '资产'
        WHEN 1 THEN '负债'
        WHEN 2 THEN '权益'
        WHEN 3 THEN '成本'
        WHEN 4 THEN '损益'
        WHEN 5 THEN '损益'
    END,
    CASE WHEN g % 6 IN (1, 2, 5) THEN '贷' ELSE '借' END,
    CASE WHEN g <= 6 THEN 1 WHEN g <= 60 THEN 2 ELSE 3 END,
    CASE WHEN g > 60 THEN 1 ELSE 0 END,
    CASE WHEN g % 10 = 1 THEN 1 ELSE 0 END,
    '启用'
FROM generate_series(1, 200) g;

-- =====================================================================
-- 8. 部门档案 (200条)
-- =====================================================================
INSERT INTO department (dept_code, dept_name, parent_dept_id, dept_type, manager_id, status)
SELECT
    'D' || LPAD(g::TEXT, 4, '0'),
    CASE (g % 12)
        WHEN 0 THEN '总经理办公室'
        WHEN 1 THEN '财务部'
        WHEN 2 THEN '人事部'
        WHEN 3 THEN '销售部'
        WHEN 4 THEN '采购部'
        WHEN 5 THEN '生产部'
        WHEN 6 THEN '仓储部'
        WHEN 7 THEN '质量部'
        WHEN 8 THEN '研发部'
        WHEN 9 THEN '客服部'
        WHEN 10 THEN '行政部'
        WHEN 11 THEN '市场部'
    END || '-' || g,
    CASE WHEN g <= 12 THEN NULL ELSE ((g - 1) / 12 + 1)::BIGINT END,
    CASE (g % 12)
        WHEN 0 THEN '管理'
        WHEN 1 THEN '财务'
        WHEN 2 THEN '人事'
        WHEN 3 THEN '销售'
        WHEN 4 THEN '采购'
        WHEN 5 THEN '生产'
        WHEN 6 THEN '仓储'
        WHEN 7 THEN '质量'
        WHEN 8 THEN '研发'
        WHEN 9 THEN '客服'
        WHEN 10 THEN '行政'
        WHEN 11 THEN '市场'
    END,
    CASE WHEN g % 10 = 0 THEN (g / 10)::BIGINT ELSE NULL END,
    '启用'
FROM generate_series(1, 200) g;

-- =====================================================================
-- 9. 员工档案 (200条) - 依赖 department
-- =====================================================================
INSERT INTO employee (employee_code, employee_name, dept_id, position, gender, mobile_phone, email, entry_date, education, id_card, status)
SELECT
    'E' || LPAD(g::TEXT, 4, '0'),
    (ARRAY['张','李','王','刘','陈','杨','黄','赵','吴','周','徐','孙','马','朱','胡','郭','何','高','林','罗'])[1 + (g % 20)]
    || (ARRAY['伟','芳','娜','秀英','敏','静','丽','强','磊','洋','艳','勇','军','杰','娟','涛','明','超','秀兰','霞'])[1 + ((g / 20) % 20)]
    || (CASE WHEN g % 3 = 0 THEN '先生' WHEN g % 3 = 1 THEN '女士' ELSE '' END),
    (g % 200) + 1,  -- dept_id 1-200
    (ARRAY['经理','副经理','主管','专员','助理','工程师','销售员','采购员','会计','出纳','仓库管理员','质检员','操作员','研发员','客服','行政','市场专员','人事','法务','实习生'])[1 + (g % 20)],
    CASE WHEN g % 2 = 0 THEN '男' ELSE '女' END,
    '138' || LPAD((g % 100000000)::TEXT, 8, '0'),
    'employee' || g || '@example.com',
    CURRENT_DATE - (g * 7 + 30),
    (ARRAY['本科','硕士','博士','大专','高中'])[1 + (g % 5)],
    '3101' || LPAD((g * 1234567 % 100000000000)::TEXT, 14, '0'),
    '在职'
FROM generate_series(1, 200) g;

-- =====================================================================
-- 10. 仓库档案 (50条) - manager_id 设为 NULL 避免外键约束问题
-- =====================================================================
INSERT INTO warehouse (stock_code, stock_name, stock_type, address, manager_id, is_bin_control, is_mrp_available, status)
SELECT
    'W' || LPAD(g::TEXT, 3, '0'),
    CASE (g % 5)
        WHEN 0 THEN '原料仓'
        WHEN 1 THEN '成品仓'
        WHEN 2 THEN '半成品仓'
        WHEN 3 THEN '包装材料仓'
        WHEN 4 THEN '退货仓'
    END || '-' || g,
    CASE (g % 5)
        WHEN 0 THEN '原料'
        WHEN 1 THEN '成品'
        WHEN 2 THEN '半成品'
        WHEN 3 THEN '包装'
        WHEN 4 THEN '其他'
    END,
    '上海市浦东新区XX路' || g || '号',
    NULL,  -- manager_id 设为 NULL
    1,
    1,
    '启用'
FROM generate_series(1, 50) g;

-- =====================================================================
-- 11. 货位档案 (200条) - 依赖 warehouse
-- =====================================================================
INSERT INTO location (bin_code, bin_name, stock_id, status)
SELECT
    'B' || LPAD(w.stock_id::TEXT, 3, '0') || '-' || LPAD(g::TEXT, 3, '0'),
    '货架' || ((g - 1) / 10 + 1) || '层' || LPAD(((g - 1) % 10 + 1)::TEXT, 2, '0'),
    w.stock_id,
    '启用'
FROM generate_series(1, 4) g, warehouse w
WHERE (w.stock_id - 1) * 4 + g <= 200
ORDER BY w.stock_id, g
LIMIT 200;

-- =====================================================================
-- 12. 物料档案 (300条) - 依赖 unit_of_measure, material_category
-- =====================================================================
INSERT INTO material (
    material_code, material_name, full_name, model, material_type, category_id,
    base_unit_id, purchase_unit_id, sale_unit_id, stock_unit_id, production_unit_id, unit_conversion,
    is_lot_control, is_sn_control, is_bin_control, is_qc_required, is_expiry_control, expiry_days,
    sale_price, purchase_price, tax_rate, min_stock, max_stock, status
)
SELECT
    'M' || LPAD(g::TEXT, 5, '0'),
    CASE (g % 8)
        WHEN 0 THEN '轴承'
        WHEN 1 THEN '齿轮'
        WHEN 2 THEN '电机'
        WHEN 3 THEN '电缆'
        WHEN 4 THEN '钢板'
        WHEN 5 THEN '塑料件'
        WHEN 6 THEN '螺丝'
        WHEN 7 THEN '成品'
    END || '-' || LPAD(g::TEXT, 4, '0'),
    CASE (g % 8)
        WHEN 0 THEN '高精度深沟球轴承'
        WHEN 1 THEN '工业级螺旋齿轮'
        WHEN 2 THEN '伺服电机'
        WHEN 3 THEN '高压电缆'
        WHEN 4 THEN '冷轧钢板'
        WHEN 5 THEN '注塑塑料件'
        WHEN 6 THEN '不锈钢螺丝'
        WHEN 7 THEN '终端成品'
    END || '型号' || g,
    'MODEL-' || g,
    CASE (g % 4)
        WHEN 0 THEN '原料'
        WHEN 1 THEN '半成品'
        WHEN 2 THEN '成品'
        WHEN 3 THEN '包装材料'
    END,
    (g % 50) + 1,  -- category_id 1-50
    (g % 30) + 1,  -- base_unit_id 1-30
    (g % 30) + 1,
    (g % 30) + 1,
    (g % 30) + 1,
    (g % 30) + 1,
    1.0,
    CASE WHEN g % 3 = 0 THEN 1 ELSE 0 END,  -- is_lot_control
    CASE WHEN g % 5 = 0 THEN 1 ELSE 0 END,  -- is_sn_control
    1,                                        -- is_bin_control
    CASE WHEN g % 2 = 0 THEN 1 ELSE 0 END,  -- is_qc_required
    CASE WHEN g % 7 = 0 THEN 1 ELSE 0 END,  -- is_expiry_control
    CASE WHEN g % 7 = 0 THEN 365 ELSE NULL END,
    (g * 12.5)::NUMERIC(18,4),                -- sale_price
    (g * 8.3)::NUMERIC(18,4),                 -- purchase_price
    CASE WHEN g % 3 = 0 THEN 13.0 WHEN g % 3 = 1 THEN 6.0 ELSE 9.0 END,
    (g * 1.5)::NUMERIC(18,4),                 -- min_stock
    (g * 100.0)::NUMERIC(18,4),               -- max_stock
    '启用'
FROM generate_series(1, 300) g;

-- =====================================================================
-- 13. 客户档案 (250条) - 依赖 payment_term, currency, price_level
-- 注: PostgreSQL 中 customer 表暂未含 CRM 扩展字段(source_type 等),
--     仅插入基础档案字段。CRM 扩展字段后续如需可 ALTER TABLE 添加。
-- =====================================================================
INSERT INTO customer (
    customer_code, customer_name, short_name, customer_type, customer_level,
    tax_no, contact_person, contact_phone, email, address,
    bank_name, bank_account, credit_limit, payment_term_id, currency_id,
    default_saler_id, default_price_level_id, status, tax_register_no, legal_person
)
SELECT
    'C' || LPAD(g::TEXT, 5, '0'),
    (ARRAY['上海','北京','广州','深圳','杭州','南京','苏州','天津','成都','武汉','西安','重庆','青岛','大连','宁波','厦门'])[1 + (g % 16)]
    || (ARRAY['科技','电子','机械','贸易','建材','食品','服装','医药','汽车','软件','网络','自动化','金属','化工','纺织','物流'])[1 + ((g / 16) % 16)]
    || '有限公司',
    (ARRAY['科技','电子','机械','贸易','建材','食品','服装','医药','汽车','软件','网络','自动化','金属','化工','纺织','物流'])[1 + ((g / 16) % 16)] || '公司',
    CASE WHEN g % 3 = 0 THEN '经销商' WHEN g % 3 = 1 THEN '直客' ELSE '电商' END,
    CASE (g % 4)
        WHEN 0 THEN 'A级'
        WHEN 1 THEN 'B级'
        WHEN 2 THEN 'C级'
        WHEN 3 THEN 'VIP'
    END,
    '91310000' || LPAD((g * 1234567 % 10000000)::TEXT, 8, '0') || 'XX',
    (ARRAY['张','李','王','刘','陈','杨','黄','赵','吴','周'])[1 + (g % 10)] || '经理',
    '139' || LPAD((g % 100000000)::TEXT, 8, '0'),
    'customer' || g || '@example.com',
    (ARRAY['上海市','北京市','广州市','深圳市','杭州市','南京市','苏州市','天津市'])[1 + (g % 8)] || 'XX区XX路' || g || '号',
    (ARRAY['中国银行','工商银行','建设银行','农业银行','招商银行','交通银行','民生银行','光大银行'])[1 + (g % 8)],
    '6228' || LPAD((g * 1234567 % 100000000000)::TEXT, 12, '0'),
    (g * 100000)::NUMERIC(18,2),
    (g % 50) + 1,  -- payment_term_id
    (g % 30) + 1,  -- currency_id
    (g % 200) + 1, -- default_saler_id
    (g % 50) + 1,  -- default_price_level_id
    '启用',
    '91310000' || LPAD((g * 7654321 % 10000000)::TEXT, 8, '0') || 'YY',
    (ARRAY['张','李','王','刘','陈','杨','黄','赵','吴','周'])[1 + (g % 10)] || '董事长'
FROM generate_series(1, 250) g;

-- =====================================================================
-- 14. 供应商档案 (200条) - 依赖 payment_term, currency
-- =====================================================================
INSERT INTO supplier (
    supplier_code, supplier_name, short_name, supplier_type, supplier_level,
    tax_no, contact_person, contact_phone, email, address,
    bank_name, bank_account, payment_term_id, currency_id, default_buyer_id,
    delivery_days, status, legal_person
)
SELECT
    'S' || LPAD(g::TEXT, 5, '0'),
    (ARRAY['江苏','浙江','广东','山东','福建','河北','河南','湖南','湖北','安徽','江西','四川','辽宁','吉林','黑龙江'])[1 + (g % 15)]
    || (ARRAY['钢铁','塑料','电子','机械','化工','建材','包装','物流','五金','模具','元器件','自动化','液压','气动','工具'])[1 + ((g / 15) % 15)]
    || '有限公司',
    (ARRAY['钢铁','塑料','电子','机械','化工','建材','包装','物流','五金','模具','元器件','自动化','液压','气动','工具'])[1 + ((g / 15) % 15)] || '供应商',
    CASE WHEN g % 3 = 0 THEN '原厂' WHEN g % 3 = 1 THEN '代理商' ELSE '贸易商' END,
    CASE (g % 4)
        WHEN 0 THEN 'A级'
        WHEN 1 THEN 'B级'
        WHEN 2 THEN 'C级'
        WHEN 3 THEN '战略'
    END,
    '91310000' || LPAD((g * 2345678 % 10000000)::TEXT, 8, '0') || 'ZZ',
    (ARRAY['钱','孙','周','吴','郑','冯','陈','卫','朱','秦'])[1 + (g % 10)] || '经理',
    '137' || LPAD((g % 100000000)::TEXT, 8, '0'),
    'supplier' || g || '@example.com',
    (ARRAY['苏州市','杭州市','广州市','深圳市','宁波市','常州市','无锡市','东莞市'])[1 + (g % 8)] || 'XX区XX路' || g || '号',
    (ARRAY['中国银行','工商银行','建设银行','农业银行','招商银行','交通银行','民生银行','光大银行'])[1 + (g % 8)],
    '6228' || LPAD((g * 9876543 % 100000000000)::TEXT, 12, '0'),
    (g % 50) + 1,  -- payment_term_id
    (g % 30) + 1,  -- currency_id
    (g % 200) + 1, -- default_buyer_id
    CASE WHEN g % 4 = 0 THEN 7 WHEN g % 4 = 1 THEN 15 WHEN g % 4 = 2 THEN 30 ELSE 45 END,
    '启用',
    (ARRAY['钱','孙','周','吴','郑','冯','陈','卫','朱','秦'])[1 + (g % 10)] || '法人'
FROM generate_series(1, 200) g;

COMMIT;

-- ===========================================================================
-- 恢复 sys_user 和 sys_user_role (TRUNCATE CASCADE 会清空, 需重新插入)
-- 密码统一为 Erp@2026
-- ===========================================================================
BEGIN;
INSERT INTO sys_user (user_code, username, password, real_name, user_type, is_admin, is_enabled, status) VALUES
('U001', 'admin',        '$2a$10$nBs.gplHcZY6d7D9pjNYB.5UckxB3OUghqibDt7RYQh29TvoZYiOq', '系统管理员', '系统用户', 1, 1, '启用'),
('U002', 'manager',      '$2a$10$nBs.gplHcZY6d7D9pjNYB.5UckxB3OUghqibDt7RYQh29TvoZYiOq', '张总',       '普通用户', 0, 1, '启用'),
('U003', 'sale01',       '$2a$10$nBs.gplHcZY6d7D9pjNYB.5UckxB3OUghqibDt7RYQh29TvoZYiOq', '李销售',     '普通用户', 0, 1, '启用'),
('U004', 'purchase01',   '$2a$10$nBs.gplHcZY6d7D9pjNYB.5UckxB3OUghqibDt7RYQh29TvoZYiOq', '王采购',     '普通用户', 0, 1, '启用'),
('U005', 'warehouse01',  '$2a$10$nBs.gplHcZY6d7D9pjNYB.5UckxB3OUghqibDt7RYQh29TvoZYiOq', '赵仓管',     '普通用户', 0, 1, '启用'),
('U006', 'production01', '$2a$10$nBs.gplHcZY6d7D9pjNYB.5UckxB3OUghqibDt7RYQh29TvoZYiOq', '钱生产',     '普通用户', 0, 1, '启用'),
('U007', 'finance01',    '$2a$10$nBs.gplHcZY6d7D9pjNYB.5UckxB3OUghqibDt7RYQh29TvoZYiOq', '孙财务',     '普通用户', 0, 1, '启用');
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 3), (3, 4), (4, 6), (5, 8), (6, 10), (7, 12);
COMMIT;

-- ===========================================================================
-- 数据统计
-- ===========================================================================
SELECT 'unit_of_measure'    AS table_name, COUNT(*) AS row_count FROM unit_of_measure
UNION ALL SELECT 'currency',           COUNT(*) FROM currency
UNION ALL SELECT 'settlement_mode',    COUNT(*) FROM settlement_mode
UNION ALL SELECT 'payment_term',       COUNT(*) FROM payment_term
UNION ALL SELECT 'price_level',        COUNT(*) FROM price_level
UNION ALL SELECT 'material_category',  COUNT(*) FROM material_category
UNION ALL SELECT 'account',            COUNT(*) FROM account
UNION ALL SELECT 'department',         COUNT(*) FROM department
UNION ALL SELECT 'warehouse',          COUNT(*) FROM warehouse
UNION ALL SELECT 'employee',           COUNT(*) FROM employee
UNION ALL SELECT 'location',           COUNT(*) FROM location
UNION ALL SELECT 'material',           COUNT(*) FROM material
UNION ALL SELECT 'customer',           COUNT(*) FROM customer
UNION ALL SELECT 'supplier',           COUNT(*) FROM supplier
ORDER BY table_name;
