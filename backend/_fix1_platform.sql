-- 修复1: 平台库删除脏租户记录 t30206330（库已物理不存在）
DELETE FROM sys_user_tenant WHERE tenant_id = 3;
DELETE FROM sys_user WHERE NOT EXISTS (SELECT 1 FROM sys_user_tenant ut WHERE ut.user_id = sys_user.user_id)
  AND username IN ('t30206330_admin');
DELETE FROM sys_tenant WHERE tenant_id = 3 AND tenant_code = 'T30206330';
