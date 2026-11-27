-- ============================================================
-- 28_fix_duplicate_check.sql
-- 修复查重功能默认值为true
-- ============================================================

UPDATE sys_config 
SET config_value = 'true', updated_at = NOW()
WHERE config_key IN (
    'sys.customer.duplicate.check',
    'sys.supplier.duplicate.check', 
    'sys.material.duplicate.check'
);
