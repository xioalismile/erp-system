-- 83_custom_field_ext_data_basedata_archives.sql
-- 2026-09-15 自定义字段开放「基础资料·档案」11 个实体：补齐其 ext_data JSONB 列
--
-- 背景：设置中心 → 自定义字段配置页此前只开放 45 个实体，仓库档案 / 计量单位 / 员工档案 /
--       部门档案 / 币种 / 会计科目 / 货位档案 / 结算方式 / 价格等级 / 物料分类 / 银行账户
--       这 11 个档案页面前端早就接了字段能力（custom-field-entity），但配置页没开放这些实体、
--       后端也没覆写实体类型 —— 属"白挂"。本次按用户口径「全部开放」把这 11 个补齐。
--
-- 为什么必须有 ext_data 列：CustomFieldServiceImpl.syncToExtData 会把 is_search=1 的字段值
--   同步到业务表 ext_data；若业务表没有该列，saveFieldValues 的 @Transactional 事务会被
--   PostgreSQL 置为 aborted → 刚写入的自定义字段值被整体静默回滚（详见 81 号补丁说明）。
--   81 号已跳过对不存在列的报错，但"可搜索字段的快照同步"仍需要该列才能真正落地。
--
-- 幂等：表不存在则跳过、列已存在则跳过，可重复执行零副作用；只加列，不改列、不删列、不写业务数据。
--
-- 表名/主键以实体 @TableName / @TableId 与真实库为准（注意几个反直觉的：
--   仓库=warehouse(stock_id) / 部门=department(dept_id) / 货位=location(bin_id) /
--   结算方式=settlement_mode(settle_mode_id) / 计量单位=unit_of_measure(unit_id)）
DO $$
DECLARE
    t text;
    tables text[] := ARRAY[
        'material_category',  -- 物料分类 (category_id)
        'unit_of_measure',    -- 计量单位 (unit_id)
        'warehouse',          -- 仓库档案 (stock_id)
        'location',           -- 货位档案 (bin_id)
        'department',         -- 部门档案 (dept_id)
        'employee',           -- 员工档案 (employee_id)
        'currency',           -- 币种 (currency_id)
        'settlement_mode',    -- 结算方式 (settle_mode_id)
        'price_level',        -- 价格等级 (price_level_id)
        'bank_account'        -- 银行账户 (account_id)
        -- 'account'          -- 会计科目：按用户要求不接入自定义字段，已注释（勿恢复）
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
