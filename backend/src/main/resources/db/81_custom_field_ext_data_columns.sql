-- 81_custom_field_ext_data_columns.sql
-- 2026-09-15 自定义字段「可搜索」开关适配：给全部可挂自定义字段的业务表补齐 ext_data JSONB 列
--
-- 背景：设置中心 → 自定义字段配置页对 45 个实体都开放「可搜索」开关，页面文案承诺
--       「可搜索字段会同步到业务表 ext_data JSONB 列，支持 WHERE 过滤」。
--       但存量库里除 customer 外，其余业务表都没有 ext_data 列。后果不只是"同步没生效"：
--       saveFieldValues 是 @Transactional，而 syncToExtData 是在值 upsert 完成之后才执行的，
--       PostgreSQL 事务内任何语句报错都会把整个事务置为 aborted ——
--       于是刚刚写入的自定义字段值被一起回滚：接口仍返回成功、前端弹"保存成功"，库里却一条值都没有。
--       （实证：物料档案填「单据显示测试」保存成功，但 sys_custom_field_value 无任何记录，
--         下拉里因此不显示该字段内容。）
--
-- 语义：ext_data 只是 is_search=1 字段的 JSONB 快照，供 WHERE 过滤的高性能查询使用；
--       EAV 表 sys_custom_field_value 仍是唯一 source of truth
--       （列表自定义搜索 applyCustomSearch 走值表，不依赖本列）。
--
-- 幂等：表不存在则跳过、列已存在则跳过，可重复执行零副作用；只加列，不改列、不删列、不写业务数据。
--
-- 覆盖范围：与 CustomFieldServiceImpl.resolveTableName() 的实体→表映射逐一对齐
--           （同时修正 purchase_inbound → purchase_receipt 的映射：库里真实表名是 purchase_receipt）
DO $$
DECLARE
    t text;
    tables text[] := ARRAY[
        -- 基础资料
        'customer', 'supplier', 'material', 'crm_opportunity',
        -- 销售模块
        'sale_quote', 'sale_quote_item', 'sale_contract', 'sale_contract_item',
        'sale_order', 'sale_order_item', 'sale_delivery', 'sale_delivery_item',
        'sale_return', 'sale_return_item', 'sale_invoice', 'sale_invoice_item',
        -- 采购模块（采购入库单的真实表名是 purchase_receipt，不是 purchase_inbound）
        'purchase_request', 'purchase_request_item', 'purchase_order', 'purchase_order_item',
        'purchase_receipt', 'purchase_receipt_item', 'purchase_return', 'purchase_return_item',
        'purchase_contract', 'purchase_contract_item', 'purchase_invoice', 'purchase_invoice_item',
        -- 库存模块
        'stock_transfer', 'stock_transfer_item', 'other_receipt', 'other_receipt_item',
        'other_issue', 'other_issue_item', 'material_requisition', 'material_requisition_item',
        'stock_check', 'stock_check_item',
        -- 生产模块
        'produce_assemble', 'produce_disassemble',
        -- 其他
        'free_sample_order', 'free_sample_order_item', 'bom', 'bom_item',
        -- 财务模块
        'sale_pre_receipt', 'purchase_pre_payment', 'cash_receipt', 'cash_payment'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_name = t AND table_schema = ANY(current_schemas(false)))
           AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                           WHERE table_name = t AND column_name = 'ext_data'
                             AND table_schema = ANY(current_schemas(false))) THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN ext_data JSONB', t);
            EXECUTE format('COMMENT ON COLUMN %I.ext_data IS %L', t,
                           '自定义字段快照（JSONB，仅 is_search=1 的高频字段）');
        END IF;
    END LOOP;
END $$;
