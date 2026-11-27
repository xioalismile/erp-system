-- 183: 批次号规则真正生效 + 批次溯源查询（2026-11-06）
-- 幂等：CREATE INDEX IF NOT EXISTS / DROP CONSTRAINT 前置存在性判断 / ON CONFLICT DO NOTHING，可重复执行。
-- 内容：① 溯源与重复入库校验所需索引；② 批次唯一口径由「批次号全局唯一」改为「物料+批次号唯一」；
--      ③ 补齐两个批次参数（启用批次管理、允许批次货品重复入库），不覆盖管理员已选值。
-- 镜像落盘：backend/src/main/resources/db/183_batch_trace_and_rules.sql
-- 启动自检：SchemaUpgradeEnsurer.ensureBatchTraceSupportOn（主库 + 每个租户库执行同份内容）。

BEGIN;

-- ========== 1. 索引：批次溯源（按批次号+物料查流水/明细） ==========
CREATE INDEX IF NOT EXISTS idx_ledger_batch_material ON inventory_ledger (batch_no, material_id);
CREATE INDEX IF NOT EXISTS idx_production_receipt_item_batch ON production_receipt_item (batch_no);
CREATE INDEX IF NOT EXISTS idx_material_requisition_item_batch ON material_requisition_item (batch_no);
CREATE INDEX IF NOT EXISTS idx_sale_delivery_item_batch ON sale_delivery_item (batch_no);
CREATE INDEX IF NOT EXISTS idx_purchase_receipt_item_batch ON purchase_receipt_item (batch_no);
CREATE INDEX IF NOT EXISTS idx_other_receipt_item_batch ON other_receipt_item (batch_no);
CREATE INDEX IF NOT EXISTS idx_batch_material ON batch (material_id);

-- ========== 2. 批次唯一口径：物料+批次号唯一 ==========
-- 旧口径 batch_no 全局唯一会把「不同物料的同一批号」判成重复；
-- 改为 (material_id, batch_no) 联合唯一后，同一物料同一批号仍然唯一，重复入库由参数控制。
DO $$
DECLARE c record;
BEGIN
  FOR c IN
    SELECT conname
      FROM pg_constraint
     WHERE conrelid = 'batch'::regclass
       AND contype = 'u'
       AND conkey = ARRAY[(SELECT attnum FROM pg_attribute
                            WHERE attrelid = 'batch'::regclass AND attname = 'batch_no')]::smallint[]
  LOOP
    EXECUTE format('ALTER TABLE batch DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uk_batch_material_batch_no ON batch (material_id, batch_no);

-- ========== 3. 参数补齐（不覆盖已有值） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.allow.batch.repeat.inbound', 'false', '允许批次货品重复入库', 'inventory', 'boolean', 77, '关闭时同一物料同一批次号只能入库一次；开启时同批次入库数量累加', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

UPDATE sys_config
   SET config_group = 'inventory',
       config_section = '库存管理参数'
 WHERE config_key IN ('inv.allow.batch.repeat.inbound')
   AND (config_group IS NULL OR config_group = 'sys_param');

COMMIT;
