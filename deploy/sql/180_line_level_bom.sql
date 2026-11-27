-- 180: 行级 BOM 字段（生产工单成品明细 / 销售 6 单据明细 / MRP 计划明细）2026-11-06
-- 背景：BOM 清单与 BOM 版本原先只在单据表头（整单一份），用户要求明细行各记一份。
-- 幂等：ADD COLUMN IF NOT EXISTS，可重复执行；存量行为 NULL，不回填编造。
-- 镜像落盘：backend/src/main/resources/db/180_line_level_bom.sql
-- 启动自检：SchemaUpgradeEnsurer.ensureLineLevelBomColumnsOn（主库 + 逐租户库）

BEGIN;

-- ---------- 1. 生产工单 · 成品明细 ----------
ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS bom_id      BIGINT;
ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS bom_code    VARCHAR(64);
ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS bom_version VARCHAR(64);
COMMENT ON COLUMN work_order_product.bom_id IS '行级BOM ID（该成品行使用的BOM清单）';
COMMENT ON COLUMN work_order_product.bom_code IS '行级BOM清单编码';
COMMENT ON COLUMN work_order_product.bom_version IS '行级BOM版本';

-- ---------- 2. 销售 6 个单据明细（只读带出，按物料主BOM自动填） ----------
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'sale_quote_item', 'sale_order_item', 'sale_contract_item',
        'sale_delivery_item', 'sale_return_item', 'sale_invoice_item'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS bom_id BIGINT', t);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS bom_code VARCHAR(64)', t);
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS bom_version VARCHAR(64)', t);
        EXECUTE format('COMMENT ON COLUMN %I.bom_id IS ''行级BOM ID（按物料主BOM自动带出，销售不可改）''', t);
        EXECUTE format('COMMENT ON COLUMN %I.bom_code IS ''行级BOM清单编码''', t);
        EXECUTE format('COMMENT ON COLUMN %I.bom_version IS ''行级BOM版本''', t);
    END LOOP;
END $$;

-- ---------- 3. MRP 计划明细 ----------
ALTER TABLE mrp_plan_item ADD COLUMN IF NOT EXISTS bom_code    VARCHAR(64);
ALTER TABLE mrp_plan_item ADD COLUMN IF NOT EXISTS bom_version VARCHAR(64);
COMMENT ON COLUMN mrp_plan_item.bom_code IS '运算时使用的BOM清单编码';
COMMENT ON COLUMN mrp_plan_item.bom_version IS '运算时使用的BOM版本';

COMMIT;
