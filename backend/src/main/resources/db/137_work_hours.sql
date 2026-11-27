-- 137：工时体系（2026-10-07）—— 新增 4 列
--   ① work_order_operation.actual_hours  工序实际工时（累计有效工时，由 开工/暂停/恢复/完工 自动累加）
--   ② work_order_operation.timing_since  本段计时起点（为空 = 未在计时）
--   ③ material.standard_hours            单件标准加工工时（工艺路线「单件加工工时」默认带出它）
--   ④ routing_operation.unit_hours       单件加工工时（工单下达时 × 计划数量 → 本工序总工时）
-- 口径：跨天/放假一律靠人工点「暂停」停表，不做班次自动扣减（用户 2026-10-07 拍板）。
-- 说明：全部 ADD COLUMN IF NOT EXISTS，幂等（跑 100 遍 = 跑 1 遍）；
--       同由 SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动时对主库 + 每个租户库兜底（守则 04 三库齐）。
-- 影响：存量数据不迁移（默认 0 / 空）；老工序面板工时显示 0.00 h；老路线未填单件工时时工单下达走老路径。

ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS actual_hours NUMERIC(18,4);
COMMENT ON COLUMN work_order_operation.actual_hours IS '工序实际工时（累计有效工时，小时）';

ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS timing_since TIMESTAMP;
COMMENT ON COLUMN work_order_operation.timing_since IS '本段计时起点（为空=未在计时）';

ALTER TABLE material ADD COLUMN IF NOT EXISTS standard_hours NUMERIC(18,4);
COMMENT ON COLUMN material.standard_hours IS '单件标准加工工时（小时）';

ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS unit_hours NUMERIC(18,4);
COMMENT ON COLUMN routing_operation.unit_hours IS '单件加工工时（小时），工单下达时×计划数量';
