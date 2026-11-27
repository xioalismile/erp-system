-- 199 工艺路线：多物料/多分类绑定 + 工序自定义字段定义
-- 说明：全部幂等，可重复执行；存量单物料自动迁入关联表；工序补稳定标识 operation_key

-- 1. 工艺路线 ↔ 适用物料（多选）
CREATE TABLE IF NOT EXISTS routing_material (
    id BIGSERIAL PRIMARY KEY,
    routing_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_routing_material ON routing_material (routing_id, material_id);
CREATE INDEX IF NOT EXISTS idx_routing_material_mid ON routing_material (material_id);

-- 2. 工艺路线 ↔ 适用分类（多选，按分类绑定时该分类及下级分类的物料都适用）
CREATE TABLE IF NOT EXISTS routing_material_category (
    id BIGSERIAL PRIMARY KEY,
    routing_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_routing_material_category ON routing_material_category (routing_id, category_id);
CREATE INDEX IF NOT EXISTS idx_routing_material_category_cid ON routing_material_category (category_id);

-- 3. 工序稳定标识：工艺路线保存会全删全插工序行，字段定义不能绑自增ID，改绑 operation_key
ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS operation_key VARCHAR(64);
UPDATE routing_operation SET operation_key = 'ro_' || operation_id WHERE operation_key IS NULL OR operation_key = '';
CREATE INDEX IF NOT EXISTS idx_routing_operation_key ON routing_operation (routing_id, operation_key);

-- 4. 工单工序快照带出路线工序标识（打印/操作面板据此取该工序自己的字段）
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS routing_operation_key VARCHAR(64);

-- 5. 工序自定义字段定义（只属于某条工艺路线的某道工序）
CREATE TABLE IF NOT EXISTS routing_operation_field (
    id BIGSERIAL PRIMARY KEY,
    routing_id BIGINT NOT NULL,
    operation_key VARCHAR(64) NOT NULL,
    field_label VARCHAR(100) NOT NULL,
    field_type VARCHAR(20) NOT NULL DEFAULT 'text',
    field_options TEXT,
    is_required SMALLINT NOT NULL DEFAULT 0,
    sort_no INT NOT NULL DEFAULT 0,
    custom_field_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_routing_operation_field ON routing_operation_field (routing_id, operation_key, sort_no);

-- 6. 存量迁移：工艺路线主表单物料 → 多物料关联表（只补缺失行）
INSERT INTO routing_material (routing_id, material_id, created_at)
SELECT r.routing_id, r.material_id, NOW()
  FROM routing r
 WHERE r.material_id IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM routing_material m
        WHERE m.routing_id = r.routing_id AND m.material_id = r.material_id);
