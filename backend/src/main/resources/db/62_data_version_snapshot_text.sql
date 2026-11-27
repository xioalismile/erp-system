-- 62_data_version_snapshot_text.sql
-- 数据版本快照列扩容：before_snapshot / after_snapshot varchar(255) → TEXT
-- 背景：DataVersionRecordServiceImpl.recordVersion 把整行实体 JSON 快照写入这两列，
--       客户等宽表序列化后超过 255 字符 → INSERT 超长报错；若发生在业务事务内会把
--       PG 事务污染(aborted)致保存静默回滚（表现为"编辑客户资料保存失败"）。
-- 幂等：可重复执行，零副作用。
ALTER TABLE data_version_record ALTER COLUMN before_snapshot TYPE TEXT;
ALTER TABLE data_version_record ALTER COLUMN after_snapshot TYPE TEXT;
