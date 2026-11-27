-- 201: 替换发货指示（销售在订单行上预先指定替代品）2026-11-27
-- 幂等：CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS
-- 同步登记：SchemaUpgradeEnsurer（新库/老库/租户库自愈）

CREATE TABLE IF NOT EXISTS sale_order_substitute_plan (
    plan_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    substitute_material_id BIGINT NOT NULL,
    substitute_material_code VARCHAR(64),
    substitute_material_name VARCHAR(255),
    specification VARCHAR(255),
    base_unit_id BIGINT,
    unit_price NUMERIC(18,4),
    quantity NUMERIC(18,4) NOT NULL,
    turned_delivery_quantity NUMERIC(18,4) DEFAULT 0,
    status VARCHAR(20) DEFAULT '有效',
    remark VARCHAR(500),
    org_code VARCHAR(64),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_sosp_order ON sale_order_substitute_plan(order_id);
CREATE INDEX IF NOT EXISTS idx_sosp_item ON sale_order_substitute_plan(order_item_id);
CREATE INDEX IF NOT EXISTS idx_sosp_item_status ON sale_order_substitute_plan(order_item_id, status);

ALTER TABLE sale_delivery_item ADD COLUMN IF NOT EXISTS replace_plan_id BIGINT;
