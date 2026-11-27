-- 132：工序操作面板重做（第 2 版）—— 新增 2 列
--   ① work_order_operation.attachments   工序附件（逗号分隔的文件 URL；面板上传/删除，复用 /api/files）
--   ② routing.auto_input_from_prev       「投入自动带出上道产出」开关（1=开，默认开；面板「本次投入」自动填上道产出）
-- 说明：全部 ADD COLUMN IF NOT EXISTS，幂等（跑 100 遍 = 跑 1 遍）；
--       同由 SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动时对主库 + 每个租户库兜底（守则 04 三库齐）。
-- 影响：存量数据不迁移（附件默认空、开关默认开）；升级后老工序面板除多出物料信息区与附件入口外与改造前一致。

ALTER TABLE work_order_operation ADD COLUMN IF NOT EXISTS attachments TEXT;
COMMENT ON COLUMN work_order_operation.attachments IS '工序附件（逗号分隔文件URL）';

ALTER TABLE routing ADD COLUMN IF NOT EXISTS auto_input_from_prev SMALLINT NOT NULL DEFAULT 1;
COMMENT ON COLUMN routing.auto_input_from_prev IS '投入自动带出上道产出（1=开 0=关）';
