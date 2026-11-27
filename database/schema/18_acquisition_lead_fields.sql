-- ============================================================================
-- 智能ERP系统 - 获客功能字段补齐 + 公海自动写入修复
-- 说明：
--   1. 给 crm_lead 表添加采集结果中的8个字段，避免信息丢失
--   2. 给 customer 表补齐公海池所需字段（如已存在则跳过）
-- 兼容：PostgreSQL
-- 日期：2026-07-21
-- ============================================================================

-- ===========================================================================
-- 模块1：crm_lead 表 - 添加采集结果字段（解决"11个字段但能用的只有2个"问题）
-- ===========================================================================

ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS credit_code VARCHAR(50);
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS registered_capital VARCHAR(100);
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS registered_address VARCHAR(500);
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS contact_address VARCHAR(500);
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS area VARCHAR(100);
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS establish_date VARCHAR(50);
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS company_status VARCHAR(50);
ALTER TABLE crm_lead ADD COLUMN IF NOT EXISTS source_url VARCHAR(500);

COMMENT ON COLUMN crm_lead.credit_code IS '统一社会信用代码';
COMMENT ON COLUMN crm_lead.registered_capital IS '注册资本';
COMMENT ON COLUMN crm_lead.registered_address IS '注册地址';
COMMENT ON COLUMN crm_lead.contact_address IS '通信地址';
COMMENT ON COLUMN crm_lead.area IS '所属地区';
COMMENT ON COLUMN crm_lead.establish_date IS '成立日期';
COMMENT ON COLUMN crm_lead.company_status IS '企业状态';
COMMENT ON COLUMN crm_lead.source_url IS '来源URL';

-- ===========================================================================
-- 模块2：customer 表 - 补齐公海池与CRM扩展字段（如已存在则跳过）
-- ===========================================================================

ALTER TABLE customer ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS source_detail VARCHAR(500);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS industry VARCHAR(100);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS company_scale VARCHAR(50);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS annual_revenue NUMERIC(18,2);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS wechat VARCHAR(100);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS website VARCHAR(200);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS crm_status VARCHAR(20);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS ext_data TEXT;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS last_contact_date DATE;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS next_contact_date DATE;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS important_level VARCHAR(20);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS potential_value NUMERIC(18,2);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS satisfaction_score INT;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS refund_amount NUMERIC(18,2);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS complaint_count INT;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS business_opportunity_count INT;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS order_count INT;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS total_order_amount NUMERIC(18,2);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS tag VARCHAR(200);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS sales_stage VARCHAR(20);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS intention_level VARCHAR(20);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS assigned_sales_id BIGINT;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS follow_up_count INT;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS is_blacklist SMALLINT DEFAULT 0;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS pool_type SMALLINT DEFAULT 0;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS pool_enter_date TIMESTAMP;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS last_order_date DATE;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS last_follow_up_date DATE;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS is_top SMALLINT DEFAULT 0;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS top_time TIMESTAMP;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS main_business VARCHAR(500);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS project_type VARCHAR(100);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS customer_demand VARCHAR(500);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS customer_preference VARCHAR(500);
ALTER TABLE customer ADD COLUMN IF NOT EXISTS family_background VARCHAR(500);

COMMENT ON COLUMN customer.source_type IS '客户来源';
COMMENT ON COLUMN customer.source_detail IS '来源详情';
COMMENT ON COLUMN customer.industry IS '所属行业';
COMMENT ON COLUMN customer.company_scale IS '公司规模';
COMMENT ON COLUMN customer.main_business IS '主营业务';
COMMENT ON COLUMN customer.pool_type IS '客户池类型: 0-私海, 1-公海';
COMMENT ON COLUMN customer.pool_enter_date IS '进入公海日期';
COMMENT ON COLUMN customer.owner_user_id IS '归属用户ID（公海时为NULL）';
COMMENT ON COLUMN customer.assigned_sales_id IS '分配销售员ID';
COMMENT ON COLUMN customer.crm_status IS 'CRM状态';
COMMENT ON COLUMN customer.sales_stage IS '销售阶段';

-- ===========================================================================
-- 模块3：acquisition_result 表 - 确认审核状态字段类型与默认值
-- ===========================================================================
-- 确保 review_status 支持 imported 状态（值类型已为 VARCHAR，无需修改）
-- 确认 imported_lead_id 字段存在
ALTER TABLE acquisition_result ADD COLUMN IF NOT EXISTS imported_lead_id BIGINT;
COMMENT ON COLUMN acquisition_result.imported_lead_id IS '已导入的CRM线索ID';

-- ============================================================================
-- 修复脚本执行完成
-- 统计：crm_lead 新增 8 字段，customer 补齐 35 字段，acquisition_result 补齐 1 字段
-- ============================================================================
