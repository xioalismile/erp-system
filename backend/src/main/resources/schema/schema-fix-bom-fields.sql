-- ============================================================
-- BOM模块字段补齐脚本
-- 对齐UI截图：生产管理 → BOM
-- bom表新增10个字段，bom_item表新增4个字段
-- ============================================================

-- 1. bom 表新增字段（10个）
ALTER TABLE bom ADD COLUMN IF NOT EXISTS bom_date DATE;
ALTER TABLE bom ADD COLUMN IF NOT EXISTS bom_category VARCHAR(50);
ALTER TABLE bom ADD COLUMN IF NOT EXISTS specification VARCHAR(200);
ALTER TABLE bom ADD COLUMN IF NOT EXISTS spec_property VARCHAR(200);
ALTER TABLE bom ADD COLUMN IF NOT EXISTS color_no VARCHAR(50);
ALTER TABLE bom ADD COLUMN IF NOT EXISTS origin VARCHAR(100);
ALTER TABLE bom ADD COLUMN IF NOT EXISTS material_quality VARCHAR(100);
ALTER TABLE bom ADD COLUMN IF NOT EXISTS is_default SMALLINT DEFAULT 0;
ALTER TABLE bom ADD COLUMN IF NOT EXISTS manager_id BIGINT;
ALTER TABLE bom ADD COLUMN IF NOT EXISTS manager_name VARCHAR(50);
ALTER TABLE bom ADD COLUMN IF NOT EXISTS default_warehouse_id BIGINT;
ALTER TABLE bom ADD COLUMN IF NOT EXISTS default_warehouse_name VARCHAR(100);

-- 2. bom_item 表新增字段（4个）
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS model_no VARCHAR(100);
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS spec_property VARCHAR(200);
ALTER TABLE bom_item ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);

-- 3. 授权给erp_user
GRANT ALL PRIVILEGES ON bom TO erp_user;
GRANT ALL PRIVILEGES ON bom_item TO erp_user;
