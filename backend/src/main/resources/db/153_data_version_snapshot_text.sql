-- 153: 数据版本记录快照列放宽 VARCHAR(255) → TEXT
-- 根因（2026-10-12 本地实锤）：before_snapshot/after_snapshot 存整行 JSON 快照，
-- 超 255 字符时 INSERT 报"值太长" → PG 事务被中止(25P02) → 同事务内后续语句全部失败、
-- 最终 COMMIT 实际执行 ROLLBACK → 业务操作静默回滚但接口返回成功
-- （基础资料→员工档案 删除实测：员工行/账号行/角色绑定全部回滚，仅平台库副作用已提交）。
-- 改法：快照列放宽为 TEXT（幂等：对已为 TEXT 的列重复执行无副作用）。
ALTER TABLE data_version_record ALTER COLUMN before_snapshot TYPE TEXT;
ALTER TABLE data_version_record ALTER COLUMN after_snapshot TYPE TEXT;