-- ============================================================================
-- 96_acquisition_result_text_columns.sql
-- 获客采集：放宽「采集结果 / 线索 / 客户」的长文本列长度
--
-- 【现象】风鸟采集任务显示「已完成，已采集 10 条」，但采集结果页是空的
-- 【根因】acquisition_result.business_scope（经营范围）等列被建成 varchar(255)，
--         而风鸟/企查查返回的经营范围是整段数百字 → 入库报
--         "对于可变字符类型来说，值太长了(255)" → batchSave 事务整批回滚 → 0 条落库
--         （任务计数是采集服务自己上报的，与是否落库无关，所以出现"有 10 条但结果空"）
-- 【跨模块】审核通过/批量导入CRM 会把 经营范围 → crm_lead.demand_desc → customer.main_business，
--           这两张表的对应列同样是 255，必须一起放宽，否则导入 CRM 会再炸一次
--
-- 【只放宽，不缩不删】全部用 information_schema 判断，幂等，可重复执行
-- 日期：2026-09-16
-- ============================================================================

DO $$
DECLARE
    -- 需要放宽为 TEXT 的列（整段长文本：经营范围、地址、URL、JSON）
    targets text[][] := ARRAY[
        ['acquisition_result', 'business_scope'],
        ['acquisition_result', 'registered_address'],
        ['acquisition_result', 'contact_address'],
        ['acquisition_result', 'source_url'],
        ['acquisition_result', 'screenshot_path'],
        ['acquisition_result', 'ai_raw_result'],
        ['crm_lead',           'demand_desc'],
        ['customer',           'main_business']
    ];
    item text[];
    tbl  text;
    col  text;
    cur  int;
BEGIN
    FOREACH item SLICE 1 IN ARRAY targets LOOP
        tbl := item[1];
        col := item[2];
        -- 取当前列长度：NULL = 已是 text/无长度限制，跳过
        SELECT character_maximum_length INTO cur
          FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = tbl AND column_name = col;

        IF FOUND AND cur IS NOT NULL THEN
            EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I TYPE TEXT', tbl, col);
            RAISE NOTICE '96: %.% 由 varchar(%) 放宽为 TEXT', tbl, col, cur;
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    -- 需要放宽为 VARCHAR(1000) 的列（中等长度：行业、地区、状态、驳回原因等）
    targets text[][] := ARRAY[
        ['acquisition_result', 'industry'],
        ['acquisition_result', 'area'],
        ['acquisition_result', 'company_status'],
        ['acquisition_result', 'establish_date'],
        ['acquisition_result', 'reject_reason'],
        ['crm_lead',           'registered_address'],
        ['crm_lead',           'contact_address'],
        ['crm_lead',           'source_url'],
        ['customer',           'source_detail'],
        ['customer',           'address'],
        ['customer',           'industry']
    ];
    item text[];
    tbl  text;
    col  text;
    cur  int;
BEGIN
    FOREACH item SLICE 1 IN ARRAY targets LOOP
        tbl := item[1];
        col := item[2];
        SELECT character_maximum_length INTO cur
          FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = tbl AND column_name = col;

        -- 只在"有长度限制且小于 1000"时放宽，已放宽过（1000/TEXT）则跳过 → 幂等
        IF FOUND AND cur IS NOT NULL AND cur < 1000 THEN
            EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I TYPE VARCHAR(1000)', tbl, col);
            RAISE NOTICE '96: %.% 由 varchar(%) 放宽为 varchar(1000)', tbl, col, cur;
        END IF;
    END LOOP;
END $$;

-- ============================================================================
-- 执行完毕（重复执行零副作用）
-- ============================================================================
