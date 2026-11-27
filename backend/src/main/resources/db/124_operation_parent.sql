-- ============================================================================
-- 124_operation_parent.sql
-- 第 13 期：车间工序可视化 —— 父子工序（任意层级，限同一工艺路线/同一成品行内）
--
--   1) routing_operation     加列 parent_line_no  父工序行号（指向本路线另一道工序的 line_no；空 = 顶层）
--   2) work_order_operation  加列 parent_line_no  工艺路线快照下来的父工序行号
--                            加列 parent_row_id   解析落地后的父工序行ID（work_order_operation.id；空 = 顶层）
--
-- 口径（已确认为唯一事实来源，代码不得偏离）：
--   · 父子可任意层级（多级树），但必须同一工艺路线内；工单侧必须同一成品明细行内，不允许跨成品行；
--   · 保存工艺路线时校验：父工序必须存在、不得自引用、不得成环（A→B→A）；
--   · 工单生效生成工序实例时，把 parent_line_no 解析成 parent_row_id（同一成品行内按 line_no 找）；
--   · 父工序 = 该组子工序的"汇总节点"，看板上父工序站点显示子工序汇总进度。
--
-- 幂等：ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS（可重复执行）。
--       无 DELETE / DROP / 无 WHERE 的全表 UPDATE。
-- 同步落盘：deploy/sql/124_operation_parent.sql（部署镜像）
--           + SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动逐租户自愈（多租户物理隔离多库）
-- ============================================================================

BEGIN;

ALTER TABLE routing_operation    ADD COLUMN IF NOT EXISTS parent_line_no INT;
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS parent_line_no INT;
ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS parent_row_id  BIGINT;

COMMENT ON COLUMN routing_operation.parent_line_no    IS '父工序行号（指向本工艺路线另一道工序的 line_no；为空=顶层工序）';
COMMENT ON COLUMN work_order_operation.parent_line_no IS '父工序行号（工艺路线快照）：工单生效生成时按本成品行内 line_no 解析成 parent_row_id';
COMMENT ON COLUMN work_order_operation.parent_row_id  IS '父工序行ID（work_order_operation.id；为空=顶层工序）：看板据此汇总子工序进度';

CREATE INDEX IF NOT EXISTS idx_woo_parent_row ON work_order_operation (parent_row_id);

COMMIT;

-- ============================================================
-- 验证 SQL（只读，预期：3 列齐 + 索引 1 条）
-- ============================================================
SELECT 'operation_parent_ok' AS result,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'routing_operation'
           AND column_name = 'parent_line_no') AS routing_col,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_name = 'work_order_operation'
           AND column_name IN ('parent_line_no', 'parent_row_id')) AS woo_cols,
       (SELECT COUNT(*) FROM pg_indexes
         WHERE tablename = 'work_order_operation'
           AND indexname = 'idx_woo_parent_row') AS idx_rows;
