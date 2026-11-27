-- 99_acquisition_audit_fields.sql
-- 获客模块（采集任务 / 采集结果）审计字段补齐（幂等，只增不改不删）
--
-- 【背景】2026-09-17 客户反馈：员工账号（销售角色，数据范围=仅本人数据）新增获客任务后，
--   任务列表看不到自己刚建的任务；管理员（全部数据）却能看到。
--   实测根因：acquisition_task 的 created_by 列存在，但新增/修改时从未写入（实体未映射该列），
--   而列表查询对"仅本人数据"角色生成 WHERE created_by = 当前用户名 → NULL 永不匹配 → 列表为空。
--
-- 【修复（代码侧，本脚本只保证列存在）】
--   1. AcquisitionTask / AcquisitionResult 实体补 created_by（INSERT 填充）、updated_by（INSERT_UPDATE 填充）
--      → AuditMetaObjectHandler 自动写入当前登录用户名；
--   2. 采集结果列表由"全员可见"改为按「所属采集任务的归属」过滤（与任务列表同口径）；
--   3. 新建任务的状态由空串兜底为 pending。
--
-- 【本脚本】仅幂等补齐列（121/8cloud 已存在该列，新库/租户模板库兜底），
--   不修改任何存量数据：历史行 created_by 保持 NULL（SELF 范围用户不可见属预期，管理员不受影响）。
--   重复执行零副作用。

ALTER TABLE acquisition_task   ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);
ALTER TABLE acquisition_task   ADD COLUMN IF NOT EXISTS updated_by VARCHAR(50);
ALTER TABLE acquisition_result ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);
ALTER TABLE acquisition_result ADD COLUMN IF NOT EXISTS updated_by VARCHAR(50);

COMMENT ON COLUMN acquisition_task.created_by      IS '创建人（数据权限过滤维度，保存时自动填充当前登录用户名）';
COMMENT ON COLUMN acquisition_task.updated_by      IS '修改人（保存时自动填充当前登录用户名）';
COMMENT ON COLUMN acquisition_result.created_by    IS '创建人（保存时自动填充当前登录用户名；采集服务回调写入为 system）';
COMMENT ON COLUMN acquisition_result.updated_by    IS '修改人（保存时自动填充当前登录用户名）';
