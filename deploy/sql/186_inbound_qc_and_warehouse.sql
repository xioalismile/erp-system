-- 186: 入库单质检闭环 + 仓库统一选择器 + 财务适配（2026-11-07）
-- 幂等：ADD COLUMN IF NOT EXISTS、参数/仓库按唯一键判重。
-- 复用既有质检模块表：qc_template / qc_template_item（模板与检验项）、qc_record / qc_record_item（质检记录与实测明细）。
-- 内容：
--   ① 质检模板扩展：区分「物料质检模板」与「单据质检模板」，单据模板可绑定多个单据类型
--   ② 质检记录/明细扩展：来源入库单、按物料行的合格/不合格数量、不合格处理方式、报废仓、模板自定义字段值
--   ③ 三张入库单表头/明细的质检与合格数量字段
--   ④ 采购入库明细补仓库字段（表头仓库改为统一选择器，明细仓库为准）
--   ⑤ 默认报废仓 + 库存参数
--   ⑥ 成本核算报废损失字段、质检自动退货单的库存影响标志
BEGIN;

-- ========== 一、质检模板扩展（复用 qc_template / qc_template_item） ==========
-- template_type：MATERIAL=物料质检模板（按物料/分类/工序绑定）；BILL=单据质检模板（按单据类型绑定）
ALTER TABLE qc_template ADD COLUMN IF NOT EXISTS template_type VARCHAR(20) DEFAULT 'MATERIAL';
-- bill_types：单据级模板绑定的单据类型，逗号分隔，可绑定多个（PURCHASE_INBOUND,PRODUCTION_RECEIPT,SUBCONTRACT_INBOUND）
ALTER TABLE qc_template ADD COLUMN IF NOT EXISTS bill_types VARCHAR(300);
-- is_default：默认模板（同一单据类型点质检时默认选中）
ALTER TABLE qc_template ADD COLUMN IF NOT EXISTS is_default SMALLINT DEFAULT 0;
UPDATE qc_template SET template_type = 'MATERIAL' WHERE template_type IS NULL;
UPDATE qc_template SET is_default = 0 WHERE is_default IS NULL;
CREATE INDEX IF NOT EXISTS idx_qc_template_type ON qc_template (template_type, is_enabled);

-- ========== 二、质检记录/明细扩展（复用 qc_record / qc_record_item） ==========
ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS source_bill_id BIGINT;
ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS scrap_warehouse_id BIGINT;
ALTER TABLE qc_record ADD COLUMN IF NOT EXISTS scrap_warehouse_name VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_qc_record_source ON qc_record (source_bill_type, source_bill_id);

ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS material_id BIGINT;
ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS material_code VARCHAR(50);
ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS material_name VARCHAR(200);
ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS quantity NUMERIC(18,4);
ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4);
ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4);
ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS handling_type VARCHAR(20) DEFAULT 'RELEASE'; -- RELEASE/RETURN/SCRAP
ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS scrap_warehouse_id BIGINT;
ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS scrap_warehouse_name VARCHAR(100);
ALTER TABLE qc_record_item ADD COLUMN IF NOT EXISTS ext_data TEXT;                              -- 模板自定义字段填写值（JSON 文本）
CREATE INDEX IF NOT EXISTS idx_qc_record_item_material ON qc_record_item (record_id, material_id);

-- ========== 三、采购入库单 ==========
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS qc_enabled SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS qc_template_id BIGINT;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS qc_status VARCHAR(20) DEFAULT '未质检';
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS rejected_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS payable_amount NUMERIC(18,4);

ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS stock_id BIGINT;
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS stock_name VARCHAR(100);
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4);
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4);
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS handling_type VARCHAR(20) DEFAULT 'RELEASE';
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS scrap_warehouse_id BIGINT;
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS scrap_warehouse_name VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_purchase_receipt_item_stock ON purchase_receipt_item (stock_id);

-- ========== 四、生产入库单 ==========
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS qc_enabled SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS qc_template_id BIGINT;
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS qc_status VARCHAR(20) DEFAULT '未质检';
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4);
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4);
ALTER TABLE production_receipt_item ADD COLUMN IF NOT EXISTS handling_type VARCHAR(20) DEFAULT 'RELEASE';
ALTER TABLE production_receipt_item ADD COLUMN IF NOT EXISTS scrap_warehouse_id BIGINT;
ALTER TABLE production_receipt_item ADD COLUMN IF NOT EXISTS scrap_warehouse_name VARCHAR(100);

-- ========== 五、委外入库单 ==========
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS qc_enabled SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS qc_template_id BIGINT;
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS qc_status VARCHAR(20) DEFAULT '未质检';
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4);
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4);
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS rejected_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS payable_amount NUMERIC(18,4);
ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS qualified_quantity NUMERIC(18,4);
ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS rejected_quantity NUMERIC(18,4);
ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS handling_type VARCHAR(20) DEFAULT 'RELEASE';
ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS scrap_warehouse_id BIGINT;
ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS scrap_warehouse_name VARCHAR(100);

-- ========== 六、成本核算报废损失 + 质检自动退货单库存影响 ==========
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS scrap_loss NUMERIC(18,4) DEFAULT 0;

-- 质检自动退货单：只做单据与财务冲减，不产生库存移动（不合格品从未入库）
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS stock_effect SMALLINT DEFAULT 1;
ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS stock_effect SMALLINT DEFAULT 1;
UPDATE purchase_return SET stock_effect = 1 WHERE stock_effect IS NULL;
UPDATE subcontract_return SET stock_effect = 1 WHERE stock_effect IS NULL;

-- ========== 七、默认报废仓 + 参数 ==========
INSERT INTO warehouse (stock_code, stock_name, stock_type, warehouse_type, status, is_enabled, is_mrp_available, sort_order, created_at, updated_at, created_by, updated_by, remark)
SELECT 'SCRAP', '报废仓', '报废仓', '报废仓', '启用', 1, 0, 999, NOW(), NOW(), 'system', 'system', '质检不合格默认入此仓'
WHERE NOT EXISTS (SELECT 1 FROM warehouse WHERE stock_code = 'SCRAP' OR stock_name = '报废仓');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('inv.scrap.warehouse.id',
        (SELECT CAST(stock_id AS VARCHAR) FROM warehouse WHERE stock_code = 'SCRAP' LIMIT 1),
        '默认报废仓', 'inventory', '库存管理参数', 'number', 78,
        '质检不合格选择进报废仓时默认入此仓，质检行可另选', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

COMMIT;
