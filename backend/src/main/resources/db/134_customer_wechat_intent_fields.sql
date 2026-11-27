-- =====================================================================
-- 134：销售过程动作量化 · 「新增意向微信」口径补齐（2026-10-07）
--   立项登记表第七节第 2 条：新增意向微信 = 当天新建、或当天"微信"字段首次由空变非空的
--                              客户数（去重；同一客户 30 天内不重复计数）。
--   内容：customer 表加 2 列
--     ① wechat_set_at     TIMESTAMP  「微信」字段首次由空变非空的时间（客户保存链路维护）
--   幂等：ADD COLUMN IF NOT EXISTS；存量只对空值回填一次；只增不改不删（manifest 铁律）。
--   2 列同由 SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动时对主库 + 每个租户库兜底（守则 04 三库齐）。
--   同内容文件另存 deploy/sql/134_customer_wechat_intent_fields.sql。
-- =====================================================================

ALTER TABLE customer ADD COLUMN IF NOT EXISTS wechat_set_at TIMESTAMP;
COMMENT ON COLUMN customer.wechat_set_at IS '「微信」字段首次由空变非空的时间；用于"新增意向微信"当日口径';

ALTER TABLE customer DROP COLUMN IF EXISTS wechat_counted_at;

-- 存量回填：已有微信的客户，把首次时间回填为建档时间（只填空值，幂等；不改动其它列）
UPDATE customer SET wechat_set_at = created_at
 WHERE wechat IS NOT NULL AND wechat <> '' AND wechat_set_at IS NULL;
