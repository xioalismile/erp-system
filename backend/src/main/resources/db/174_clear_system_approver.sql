-- ============================================================================
-- 174_clear_system_approver.sql
-- 审核人系统占位清理（2026-10-28，用户口径）
--
--   背景：免审批提交时旧代码把「系统(免审批)」写入 approved_by，
--         导致收款单明细报表「审核人」、采购入库/发票/退货单据信息等位置
--         出现一个假审批人。用户要求：没开审批的单据，审核人留空。
--
--   口径：只清两种免审批占位值；真实审批人一律不动。幂等，可重复执行。
--   同步：deploy/sql/174_clear_system_approver.sql
--         + 启动自愈 SchemaUpgradeEnsurer.clearSystemPlaceholderApprover
--           （主库 + 每个租户库）
-- ============================================================================

BEGIN;

DO $$
DECLARE
    r RECORD;
    v_rows INT;
    v_total INT := 0;
BEGIN
    FOR r IN
        SELECT table_schema, table_name
          FROM information_schema.columns
         WHERE column_name = 'approved_by'
           AND table_schema = current_schema()
    LOOP
        EXECUTE format(
            'UPDATE %I.%I SET approved_by = NULL WHERE approved_by IN (%L, %L)',
            r.table_schema, r.table_name,
            '系统(免审批)', '系统(免审批自动通过)');
        GET DIAGNOSTICS v_rows = ROW_COUNT;
        v_total := v_total + v_rows;
    END LOOP;
    RAISE NOTICE '审核人系统占位清理: % 行（免审批单据审核人留空）', v_total;
END $$;

COMMIT;
