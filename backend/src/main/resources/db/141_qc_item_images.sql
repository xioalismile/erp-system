-- ============================================================================
-- 141_qc_item_images.sql
-- 第 19 期：质检逐项留证 —— 检验记录明细 qc_record_item 加「留证图片」列
--
--   images：逗号分隔的图片 URL（复用全站上传通道 /api/files/upload），一个检验项可多张。
--           为空 = 未留证（用户口径：留证不强制）。
--
-- 幂等：ADD COLUMN IF NOT EXISTS（可重复执行）；无 DELETE / DROP / 无 WHERE 全表 UPDATE。
-- 同步落盘：deploy/sql/141_qc_item_images.sql（部署镜像）
--           + SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动逐租户自愈（多租户物理隔离多库）
-- ============================================================================

BEGIN;

ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS images VARCHAR(1000);
COMMENT ON COLUMN qc_record_item.images IS '留证图片：逗号分隔 URL（一个检验项可多张；空=未留证）';

COMMIT;

-- 验证 SQL（只读，预期：1）
SELECT COUNT(*) AS qc_item_images_col FROM information_schema.columns
 WHERE table_name = 'qc_record_item' AND column_name = 'images';
