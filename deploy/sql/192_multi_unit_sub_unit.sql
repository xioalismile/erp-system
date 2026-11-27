-- =====================================================================
-- 多单位换算：单据明细「副单位」可手选（2026-11-10）
--
-- 背景：副单位此前固定取物料档案第一个「固定」换算行，用户无法在单据上选择；
--       新增 sub_unit_id 记录本行所选的副单位，空值 = 仍按第一个固定换算行显示。
--
-- 幂等：ADD COLUMN IF NOT EXISTS，可重复执行。
-- 与启动自愈 SchemaUpgradeEnsurer#ensureMultiUnitSupportOn 同口径（主库 + 逐租户库）。
-- =====================================================================

DO $$
DECLARE
    t text;
    item_tables text[] := ARRAY[
        'sale_quote_item', 'sale_order_item', 'sale_contract_item',
        'sale_delivery_item', 'sale_return_item', 'sale_invoice_item',
        'purchase_contract_item', 'purchase_order_item', 'purchase_receipt_item',
        'purchase_return_item', 'purchase_invoice_item', 'purchase_request_item',
        'other_receipt_item', 'other_issue_item', 'stock_transfer_item',
        'stock_check_item', 'material_requisition_item',
        'production_receipt_item', 'work_order_product', 'production_order_item',
        'sample_order_item', 'free_sample_order_item',
        'produce_assemble_detail', 'produce_assemble_bom',
        'produce_disassemble_detail', 'produce_disassemble_bom',
        'subcontract_order_item', 'subcontract_pick_item',
        'subcontract_inbound_item', 'subcontract_return_item', 'subcontract_supplement_item'
    ];
BEGIN
    FOREACH t IN ARRAY item_tables LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'public' AND table_name = t) THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS unit_id BIGINT', t);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS sub_unit_id BIGINT', t);
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS float_conversion_rate NUMERIC(18,8)', t);
        END IF;
    END LOOP;
END $$;

COMMENT ON COLUMN sale_order_item.sub_unit_id IS '副单位（单据行手选；空=按物料档案第一个固定换算行）';
