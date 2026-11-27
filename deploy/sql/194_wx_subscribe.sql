-- 194_wx_subscribe.sql
-- 微信小程序订阅消息（审批提醒）：
--   sys_user 加"剩余可推次数"列：用户在小程序授权弹窗点"允许"后 +1，
--   服务器推送成功一条 -1（微信一次性订阅规则：一次授权只能发一条）。
-- 幂等：可重复执行；同内容文件另存 backend/src/main/resources/db/194_wx_subscribe.sql；
-- 启动自愈：SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 补列（含 wx_openid 补齐 171 遗漏）。

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS wx_subscribe_quota INTEGER DEFAULT 0;
COMMENT ON COLUMN sys_user.wx_subscribe_quota IS '微信订阅消息剩余可推次数：授权一次+1，推送成功-1，拒收清零';
