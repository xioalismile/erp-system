-- ============================================================================
-- 智能ERP系统 - 租户版本管理升级脚本
-- 说明：为sys_tenant表添加版本、功能模块、试用期等字段
-- ============================================================================

ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS version VARCHAR(30) DEFAULT 'STANDARD';
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS features TEXT DEFAULT '';
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS trial_days INT DEFAULT 0;

COMMENT ON COLUMN sys_tenant.version IS '版本：STANDARD-标准版, CRM-CRM获客版, ULTIMATE-旗舰版';
COMMENT ON COLUMN sys_tenant.features IS '启用的功能模块（JSON数组格式）';
COMMENT ON COLUMN sys_tenant.trial_days IS '试用天数（0=正式版）';

-- 更新现有租户的默认版本设置
UPDATE sys_tenant SET version = 'STANDARD' WHERE version IS NULL;

-- 更新默认租户的features（标准版功能）
UPDATE sys_tenant 
SET features = '["basic","sale","purchase","inventory","finance","assemble","disassemble"]' 
WHERE tenant_code = 'DEFAULT' OR features = '' OR features IS NULL;

-- ============================================================================
-- 版本功能定义（供前端使用）
-- STANDARD: basic,sale,purchase,inventory,finance,assemble,disassemble
-- CRM: STANDARD + crm,acquisition,sample
-- ULTIMATE: CRM + production,subcontract,cost,price_strategy,system
-- ============================================================================

-- 初始化默认数据的features字段
UPDATE sys_tenant 
SET features = '["basic","sale","purchase","inventory","finance"]' 
WHERE features = '' OR features IS NULL;
