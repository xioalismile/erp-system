-- 109_bus_status_columns.sql
-- 目的：落地「业务执行态」独立字段 bus_status（金蝶/用友三字段模型的第三个字段）。
--
-- 背景：通用单据模型是 status(审批态) + close_status(完结标志) + bus_status(业务执行态)。
-- 前两个已全站落地；bus_status 此前只在 BillStatusEnum 注释里提及、没有承载列 ——
-- 于是「已下达 / 执行中 / 已完工 / 部分入库 / 全部入库 / 已关闭」这些**业务执行态**
-- 被塞进了 status（BillStatusEnum 里它们的 code 全是 2），导致 status 值域膨胀、与审批态混淆。
--
-- 覆盖范围：当前会写「执行态」到 status 的单据表 —— 6 张 /close 写"已关闭"（调拨/盘点/其他出入库/领料/生产入库）
-- + 销售报价单/销售合同 + 采购申请单/采购订单（后两张用 UpdateWrapper 写 status="已关闭"），共 10 张。
-- 口径：bus_status 可空；存量数据留空（NULL），前端显示仍取 status —— 升级后行为与旧版完全一致。
-- 幂等：ADD COLUMN IF NOT EXISTS，重复执行无副作用；表不存在自动跳过。
-- 同内容文件另存 deploy/sql/66_bus_status_columns.sql（守则04 编号体系）。

DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY[
        'stock_transfer',        -- 库存调拨单
        'stock_check',           -- 库存盘点单
        'other_receipt',         -- 其他入库单
        'other_issue',           -- 其他出库单
        'material_requisition',  -- 生产领料单（含退料/补料，按类型区分）
        'production_receipt',    -- 生产入库单
        'sale_quote',            -- 销售报价单
        'sale_contract',         -- 销售合同
        'purchase_request',      -- 采购申请单
        'purchase_order'         -- 采购订单
    ];
    added INT := 0;
BEGIN
    FOREACH t IN ARRAY tables LOOP
        IF to_regclass(t) IS NULL THEN
            RAISE NOTICE '109: 表 % 不存在，跳过', t;
            CONTINUE;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
             WHERE table_name = t AND column_name = 'bus_status'
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN bus_status VARCHAR(20)', t);
            added := added + 1;
        END IF;
    END LOOP;
    RAISE NOTICE '109: bus_status 补列完成，本次新增 % 列（目标 10 张表）', added;
END $$;
