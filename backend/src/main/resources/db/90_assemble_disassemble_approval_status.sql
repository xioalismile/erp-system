-- =====================================================================
-- 90 生产组装单 / 生产拆卸单：补 approval_status 列 + 按 status 回填历史值
-- 背景：这两张表没有 approval_status 列，驳回只能写 flow_status='rejected'，
--       与全系统「驳回写 approval_status='驳回'」口径不一致，
--       前端/报表按 approval_status 取值时取不到。
-- 幂等：ADD COLUMN IF NOT EXISTS + UPDATE 仅命中 approval_status IS NULL 的行。
-- 口径：0草稿 / 1待审核 / 2已生效 / 7已作废（与其余单据一致）
-- =====================================================================

-- 一、幂等加列
ALTER TABLE produce_assemble    ADD COLUMN IF NOT EXISTS approval_status varchar(20);
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS approval_status varchar(20);

-- 二、历史数据回填（只填空值，不覆盖已有值）
UPDATE produce_assemble    SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = 0;
UPDATE produce_assemble    SET approval_status = '待审核' WHERE approval_status IS NULL AND status = 1;
UPDATE produce_assemble    SET approval_status = '已生效' WHERE approval_status IS NULL AND status = 2;
UPDATE produce_assemble    SET approval_status = '已作废' WHERE approval_status IS NULL AND status = 7;

UPDATE produce_disassemble SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = 0;
UPDATE produce_disassemble SET approval_status = '待审核' WHERE approval_status IS NULL AND status = 1;
UPDATE produce_disassemble SET approval_status = '已生效' WHERE approval_status IS NULL AND status = 2;
UPDATE produce_disassemble SET approval_status = '已作废' WHERE approval_status IS NULL AND status = 7;

-- 三、校验：不应再有 status 与 approval_status 不匹配的历史行
select 'produce_assemble' as table_name, count(*) as mismatch
  from produce_assemble
 where approval_status is null
    or (status = 0 and approval_status <> '草稿')
    or (status = 1 and approval_status <> '待审核')
    or (status = 2 and approval_status <> '已生效')
    or (status = 7 and approval_status <> '已作废')
union all
select 'produce_disassemble', count(*)
  from produce_disassemble
 where approval_status is null
    or (status = 0 and approval_status <> '草稿')
    or (status = 1 and approval_status <> '待审核')
    or (status = 2 and approval_status <> '已生效')
    or (status = 7 and approval_status <> '已作废');
