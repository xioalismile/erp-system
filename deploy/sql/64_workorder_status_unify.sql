-- =====================================================
-- 64_workorder_status_unify.sql
-- 生产工单状态机对齐全站通用口径（金蝶/用友标准）：
--   status 收敛为 4 个值：草稿 / 待审核 / 已生效 / 已作废；
--   退役业务执行态「已下达 / 执行中 / 已完工」（原被写进 status，偏离通用模型）；
--   业务闭环改用独立字段 close_status（0 未结案 / 1 已结案，同步 is_closed）。
--
-- 幂等：仅按固定旧值 UPDATE，重复执行命中 0 行；无 DDL、无 DELETE / DROP。
-- 存量实测（2026-09-20，erp_db）：草稿 2 条 / 执行中 3 条，close_status 全为 NULL，work_order_operation 0 行。
-- 同内容文件另存 backend/src/main/resources/db/106_workorder_status_unify.sql（守则04 编号体系）。
-- =====================================================

-- 1) 业务执行态 → 已生效（未结案）
UPDATE production_order SET status = '已生效' WHERE status IN ('已下达', '执行中', '已完工');

-- 2) 旧「已关闭」= 已生效 + 已结案（同步 is_closed，供前端「反结案」按钮判定）
UPDATE production_order SET status = '已生效', close_status = 1, is_closed = 1 WHERE status = '已关闭';

-- 3) 结案标志兜底：NULL → 0（未结案）
UPDATE production_order SET close_status = 0 WHERE close_status IS NULL;
