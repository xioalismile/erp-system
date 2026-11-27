-- 171_wx_openid.sql
-- 背景：微信小程序「一键登录 / 绑定微信」需要把微信 openid 落到账号上。
--       2026-10-23 排查发现 sys_user 一直没有 wx_openid 列，导致微信登录必然报
--       「服务器未启用微信登录，请联系管理员执行数据库升级」。
-- 口径：纯新增列 + 索引，幂等；不改任何既有列与数据。
--       多租户环境下每个租户独立库都要执行（ADD COLUMN IF NOT EXISTS 天然幂等）。
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS wx_openid VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_sys_user_wx_openid ON sys_user(wx_openid);
COMMENT ON COLUMN sys_user.wx_openid IS '微信小程序openid，用于微信一键登录与绑定';
