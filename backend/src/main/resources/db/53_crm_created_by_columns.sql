-- 53_crm_created_by_columns.sql
-- 数据权限体系（2026-09-02）两部分 schema 兜底：
--
-- 一、CRM 6 张业务表补 created_by 列（数据范围过滤维度）。
-- 实体侧已加 @TableField(value = "created_by", fill = FieldFill.INSERT)，
-- AuditMetaObjectHandler 在 INSERT 时自动填充当前用户名。
-- 本地库该列已存在（开发期手工补过），8cloud/121 等服务器库缺此列时由本脚本幂等补齐，
-- 否则新 jar 部署后 CRM 新增单据会直接报"column created_by does not exist"。
-- 存量数据不回填（历史行 created_by 保持 NULL：SELF 范围用户不可见属预期，
-- 管理员/ALL 范围不受影响）。
--
-- 二、operation_expense 补结案字段（44 号状态机脚本漏补该表，实体
-- OperationExpense 引用 close_status/closed_by/closed_at/closed_reason 四列，
-- 缺列时运营费用任何 selectList/selectPage 直接报错——数据看板财务接口实测暴露）。
--
-- 幂等：ADD COLUMN IF NOT EXISTS + UPDATE ... WHERE IS NULL 兜底，重复执行零副作用。

-- ========== 一、CRM created_by ==========
ALTER TABLE crm_lead        ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);
ALTER TABLE crm_opportunity ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);
ALTER TABLE crm_contact     ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);
ALTER TABLE crm_follow_up   ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);
ALTER TABLE crm_complaint   ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);
ALTER TABLE crm_survey      ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);

-- ========== 二、operation_expense 结案字段（对齐 cash_receipt/cash_payment 口径） ==========
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS close_status INT DEFAULT 0;
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE operation_expense ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(255);

COMMENT ON COLUMN operation_expense.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN operation_expense.closed_by IS '结案人';
COMMENT ON COLUMN operation_expense.closed_at IS '结案时间';
COMMENT ON COLUMN operation_expense.closed_reason IS '结案原因';

UPDATE operation_expense SET close_status = 0 WHERE close_status IS NULL;
