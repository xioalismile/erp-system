-- 167 · 小楷独立版客户资料补充（2026-10-19）
-- 幂等：ADD COLUMN IF NOT EXISTS
-- 说明：一个账号 = 一个客户/租户；手机号、邮箱、备注用于运营侧识别与跟进，不改变账号隔离口径
ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS phone  VARCHAR(32);
ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS email  VARCHAR(128);
ALTER TABLE kai_user ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
