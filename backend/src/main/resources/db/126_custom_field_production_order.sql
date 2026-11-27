-- ============================================================================
-- 126_custom_field_production_order.sql
-- 第 13 期：生产工单接入自定义字段（补齐 ext_data JSONB 列）
--
-- 背景：自定义字段机制是通用的，但「生产工单」此前**漏接线**（CustomFieldServiceImpl 的实体→表映射里
--       生产模块只挂了组装单/拆卸单）→ 设置中心自定义字段配置页看不到「生产工单」，
--       工单上也填不了自定义字段。本期把主档接上：
--         ① CustomFieldServiceImpl.ENTITY_TABLE_MAP 增 production_order → production_order
--         ② resolveIdColumn 增 production_order → order_id
--         ③ 本脚本补 ext_data JSONB 列（syncToExtData 会把 is_search=1 的字段同步到该列供 WHERE 过滤；
--            缺列会导致 saveFieldValues 的事务被中止 → 值写入后又被回滚）
--
-- 用途：车间排程看板「站点设置」的候选项要含「工单自定义字段」（用户 2026-09-28 口径）。
--
-- 幂等：ADD COLUMN IF NOT EXISTS（可重复执行）。无 DELETE / DROP / 无 WHERE 的全表 UPDATE。
-- 同步落盘：deploy/sql/126_custom_field_production_order.sql（部署镜像）
--           + SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动逐租户自愈（多租户物理隔离多库）
-- ============================================================================

BEGIN;

ALTER TABLE production_order ADD COLUMN IF NOT EXISTS ext_data JSONB;
COMMENT ON COLUMN production_order.ext_data IS '自定义字段快照（JSONB，仅 is_search=1 的高频字段）';

COMMIT;

-- ============================================================
-- 验证 SQL（只读，预期：1 列齐）
-- ============================================================
SELECT 'production_order_ext_data_ok' AS result,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'production_order'
           AND column_name = 'ext_data') AS ext_data_col;
