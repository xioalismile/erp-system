-- ============================================================
-- 198_material_purchase_batch.sql
-- 物料档案补采购批量政策字段 + 采购/领料两个校验开关（金蝶/用友标准口径）
-- 采购批量 = 最小订单递增量（包装量）；最小订货量 = 每次订货下限。
-- 均为 0/空 = 不启用，升级前后行为完全一致；不新增菜单与按钮权限。
-- ============================================================

ALTER TABLE material ADD COLUMN IF NOT EXISTS purchase_batch_qty NUMERIC(18,4) DEFAULT 0;
ALTER TABLE material ADD COLUMN IF NOT EXISTS min_order_qty NUMERIC(18,4) DEFAULT 0;
COMMENT ON COLUMN material.purchase_batch_qty IS '采购批量：最小订单递增量（包装量），0=不启用';
COMMENT ON COLUMN material.min_order_qty IS '最小订货量：每次采购数量下限，0=不启用';

UPDATE material SET purchase_batch_qty = 0 WHERE purchase_batch_qty IS NULL;
UPDATE material SET min_order_qty = 0 WHERE min_order_qty IS NULL;

-- 采购订单按采购批量校验（默认关：关闭时采购数量不做整数倍校验）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.order.batch.check.enabled', 'false', '采购订单按采购批量校验', 'purchase', '采购订单', 'boolean', 90, '开启后采购订单数量必须是物料采购批量的整数倍', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, config_group = EXCLUDED.config_group,
    config_section = EXCLUDED.config_section, remark = EXCLUDED.remark;

-- 领料单按采购批量校验（默认关：关闭时领料数量不做整数倍校验）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inventory.requisition.batch.check.enabled', 'false', '领料单按采购批量校验', 'inventory', '库存管理参数', 'boolean', 90, '开启后领料数量必须是物料采购批量的整数倍', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET config_name = EXCLUDED.config_name, config_group = EXCLUDED.config_group,
    config_section = EXCLUDED.config_section, remark = EXCLUDED.remark;
