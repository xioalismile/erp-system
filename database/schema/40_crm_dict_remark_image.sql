-- ============================================================================
-- 智能ERP系统 - CRM字典初始化 + 客户备注 + 线索图片字段
-- 说明：
--   1. 初始化 CRM 相关 4 组字典类型及明细（customer_type/customer_level/
--      customer_source/industry），幂等可重复执行
--   2. customer 表增加 remark 备注字段
--   3. crm_lead 表增加 image_url 图片字段（Excel 导入内嵌图片保存后的访问URL）
-- 兼容：PostgreSQL
-- 日期：2026-08-17
-- ============================================================================

-- ===========================================================================
-- 模块1：字典类型（dict_type_code 无唯一约束，用 NOT EXISTS 防重）
-- ===========================================================================

INSERT INTO sys_dict_type (dict_type_code, dict_type_name, dict_category, is_system, sort_order, status)
SELECT v.type_code, v.type_name, '基础资料', 1, v.sort_no, '启用'
FROM (VALUES
    ('customer_type',   '客户类型', 50),
    ('customer_level',  '客户等级', 51),
    ('customer_source', '客户来源', 52),
    ('industry',        '所属行业', 53)
) AS v(type_code, type_name, sort_no)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type t WHERE t.dict_type_code = v.type_code
);

-- ===========================================================================
-- 模块2：字典明细（sys_dict_data 无唯一约束，用 NOT EXISTS 防重）
-- ===========================================================================

INSERT INTO sys_dict_data (dict_type_id, dict_label, dict_value, dict_sort, is_default, is_enabled)
SELECT t.dict_type_id, v.d_label, v.d_value, v.d_sort, 0, 1
FROM (VALUES
    -- 客户类型 customer_type
    ('customer_type', '企业客户', '企业客户', 1),
    ('customer_type', '个人客户', '个人客户', 2),
    ('customer_type', '政府机构', '政府机构', 3),
    ('customer_type', '其他',     '其他',     4),
    -- 客户等级 customer_level
    ('customer_level', 'VIP',      'VIP',      1),
    ('customer_level', '重要客户', '重要客户', 2),
    ('customer_level', '普通客户', '普通客户', 3),
    ('customer_level', '潜在客户', '潜在客户', 4),
    -- 客户来源 customer_source（合并私海+线索两组）
    ('customer_source', '线上推广',   '线上推广',   1),
    ('customer_source', '线上广告',   '线上广告',   2),
    ('customer_source', '线下展会',   '线下展会',   3),
    ('customer_source', '客户转介绍', '客户转介绍', 4),
    ('customer_source', '市场活动',   '市场活动',   5),
    ('customer_source', '网站注册',   '网站注册',   6),
    ('customer_source', '电话咨询',   '电话咨询',   7),
    ('customer_source', '其他',       '其他',       8),
    -- 所属行业 industry
    ('industry', '制造业', '制造业', 1),
    ('industry', '零售业', '零售业', 2),
    ('industry', '服务业', '服务业', 3),
    ('industry', 'IT',     'IT',     4),
    ('industry', '金融',   '金融',   5),
    ('industry', '医疗',   '医疗',   6),
    ('industry', '教育',   '教育',   7),
    ('industry', '其他',   '其他',   8)
) AS v(type_code, d_label, d_value, d_sort)
JOIN sys_dict_type t ON t.dict_type_code = v.type_code
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data d
    WHERE d.dict_type_id = t.dict_type_id
      AND d.dict_label = v.d_label
);

-- ===========================================================================
-- 模块3：customer 表 - 增加备注字段
-- ===========================================================================

ALTER TABLE customer ADD COLUMN IF NOT EXISTS remark TEXT;
COMMENT ON COLUMN customer.remark IS '备注';

-- ===========================================================================
-- 模块4：crm_lead 表 - 增加图片URL字段（多个逗号分隔）
-- ===========================================================================

ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS image_url TEXT;
COMMENT ON COLUMN crm_lead.image_url IS '图片URL（多个逗号分隔，来自Excel导入内嵌图片）';

-- ============================================================================
-- 脚本执行完成
-- 统计：字典类型 4 组（幂等），字典明细 24 条（幂等），customer 新增 1 字段，crm_lead 新增 1 字段
-- ============================================================================
