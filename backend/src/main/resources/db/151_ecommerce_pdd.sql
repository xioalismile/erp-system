-- ============================================================
-- 73_ecommerce_pdd.sql
-- 用途：电商平台接入拼多多（国内站）—— 加列 + 存量回填 + 菜单改名（幂等）
--       （守则 04 适配 2.7.2-1 三兼容、2.7.2-6 默认值=旧行为）
--
-- 内容：
--   1. 店铺档案加「访问令牌」「令牌到期时间」两列（拼多多令牌直接落库，加密）
--   2. 平台订单加「平台」一列，存量数据回填「亚马逊」
--   3. 菜单「亚马逊订单」改名「平台订单」（权限码/路径不变，授权不受影响）
--
-- 幂等：加列走 ADD COLUMN IF NOT EXISTS；回填/改名带条件；重复执行结果一致。
-- 同口径列已登记 SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS（启动自愈逐租户库补齐）。
-- 执行后需重新登录（菜单名在登录时缓存）。
-- ============================================================

BEGIN;

-- ---------- 1. 店铺档案加访问令牌两列 ----------
ALTER TABLE ecom_shop ADD COLUMN IF NOT EXISTS access_token VARCHAR(1000);
ALTER TABLE ecom_shop ADD COLUMN IF NOT EXISTS token_expire_at TIMESTAMP;

-- ---------- 2. 平台订单加平台列 + 存量回填 ----------
ALTER TABLE ecom_platform_order ADD COLUMN IF NOT EXISTS platform VARCHAR(20);
UPDATE ecom_platform_order SET platform = 'AMAZON' WHERE platform IS NULL;
CREATE INDEX IF NOT EXISTS idx_ecom_order_platform ON ecom_platform_order (platform);

-- 防御性：拼多多店铺（如有）区域/市场编号按国内口径归一
UPDATE ecom_shop SET region = 'CN', marketplace_id = 'PDD' WHERE platform = 'PDD' AND (region IS DISTINCT FROM 'CN' OR marketplace_id IS DISTINCT FROM 'PDD');

-- ---------- 3. 菜单改名（亚马逊订单 → 平台订单；权限码/路径不变） ----------
UPDATE sys_menu SET menu_name = '平台订单' WHERE menu_code = 'ecommerce_orders' AND menu_name <> '平台订单';

COMMIT;

-- ============================================================
-- 验证 SQL
-- ============================================================
-- SELECT column_name, data_type FROM information_schema.columns
--  WHERE table_name = 'ecom_shop' AND column_name IN ('access_token','token_expire_at');
-- SELECT count(*) FROM ecom_platform_order WHERE platform IS NULL;   -- 预期 0
-- SELECT menu_name FROM sys_menu WHERE menu_code = 'ecommerce_orders'; -- 预期：平台订单
