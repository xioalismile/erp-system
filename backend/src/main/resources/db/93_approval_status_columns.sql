-- =====================================================================
-- 93 补齐 10 个单据身份的 approval_status 列 + 按主状态幂等回填历史值
--
-- 背景：下列身份的所在表没有 approval_status 列（全系统统一字段，表示
--       草稿 / 待审核 / 已生效 / 驳回 / 已作废），导致审批驳回时只能回写主状态，
--       没有"驳回"标记，与其余 30+ 已接入审批的身份口径不一致。
--
-- 涉及身份（表名取自实体类 @TableName + information_schema 实测）：
--   委外 5：SUBCONTRACT_ORDER      -> subcontract_order      （列已存在，仅回填）
--           SUBCONTRACT_DELIVERY   -> subcontract_pick
--           SUBCONTRACT_RECEIPT    -> subcontract_inbound
--           SUBCONTRACT_RETURN     -> subcontract_return
--           SUBCONTRACT_SUPPLEMENT -> subcontract_supplement
--   打样 2：SAMPLE_ORDER           -> sample_order
--           FREE_SAMPLE            -> free_sample_order
--   财务 3：PURCHASE_EXPENSE       -> purchase_expense       （采购费用单）
--           PURCHASE_PRE_PAYMENT   -> purchase_pre_payment   （预付款单）
--           OPERATION_EXPENSE      -> operation_expense      （费用分摊）
--
-- 幂等：ADD COLUMN IF NOT EXISTS + UPDATE 仅命中 approval_status IS NULL 的行。
-- 口径：与 90 号脚本一致 —— 主状态已生效/已入库/已完工等后置状态一律映射为 '已生效'。
-- =====================================================================

-- 一、幂等加列
ALTER TABLE subcontract_order      ADD COLUMN IF NOT EXISTS approval_status varchar(20);
ALTER TABLE subcontract_pick       ADD COLUMN IF NOT EXISTS approval_status varchar(20);
ALTER TABLE subcontract_inbound    ADD COLUMN IF NOT EXISTS approval_status varchar(20);
ALTER TABLE subcontract_return     ADD COLUMN IF NOT EXISTS approval_status varchar(20);
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS approval_status varchar(20);
ALTER TABLE sample_order           ADD COLUMN IF NOT EXISTS approval_status varchar(20);
ALTER TABLE free_sample_order      ADD COLUMN IF NOT EXISTS approval_status varchar(20);
ALTER TABLE purchase_expense       ADD COLUMN IF NOT EXISTS approval_status varchar(20);
ALTER TABLE purchase_pre_payment   ADD COLUMN IF NOT EXISTS approval_status varchar(20);
ALTER TABLE operation_expense      ADD COLUMN IF NOT EXISTS approval_status varchar(20);

-- 二、历史数据回填（只填空值，不覆盖已有值）
-- 2.1 委外 5（status: 0草稿 1待审核 2已生效 3已发料/已入库/已处理 7已作废；加工单另有 5已完工 6已结案）
UPDATE subcontract_order      SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = 0;
UPDATE subcontract_order      SET approval_status = '待审核' WHERE approval_status IS NULL AND status = 1;
UPDATE subcontract_order      SET approval_status = '已生效' WHERE approval_status IS NULL AND status IN (2, 3, 5, 6);
UPDATE subcontract_order      SET approval_status = '已作废' WHERE approval_status IS NULL AND status = 7;

UPDATE subcontract_pick       SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = 0;
UPDATE subcontract_pick       SET approval_status = '待审核' WHERE approval_status IS NULL AND status = 1;
UPDATE subcontract_pick       SET approval_status = '已生效' WHERE approval_status IS NULL AND status IN (2, 3);
UPDATE subcontract_pick       SET approval_status = '已作废' WHERE approval_status IS NULL AND status = 7;

UPDATE subcontract_inbound    SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = 0;
UPDATE subcontract_inbound    SET approval_status = '待审核' WHERE approval_status IS NULL AND status = 1;
UPDATE subcontract_inbound    SET approval_status = '已生效' WHERE approval_status IS NULL AND status IN (2, 3);
UPDATE subcontract_inbound    SET approval_status = '已作废' WHERE approval_status IS NULL AND status = 7;

UPDATE subcontract_return     SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = 0;
UPDATE subcontract_return     SET approval_status = '待审核' WHERE approval_status IS NULL AND status = 1;
UPDATE subcontract_return     SET approval_status = '已生效' WHERE approval_status IS NULL AND status IN (2, 3);
UPDATE subcontract_return     SET approval_status = '已作废' WHERE approval_status IS NULL AND status = 7;

UPDATE subcontract_supplement SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = 0;
UPDATE subcontract_supplement SET approval_status = '待审核' WHERE approval_status IS NULL AND status = 1;
UPDATE subcontract_supplement SET approval_status = '已生效' WHERE approval_status IS NULL AND status IN (2, 3);
UPDATE subcontract_supplement SET approval_status = '已作废' WHERE approval_status IS NULL AND status = 7;

-- 2.2 打样 2（status 为中文串：草稿/待审核/已生效/打样中/已完成/已确认/已取消/已作废/已转BOM/已发货）
UPDATE sample_order       SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = '草稿';
UPDATE sample_order       SET approval_status = '待审核' WHERE approval_status IS NULL AND status = '待审核';
UPDATE sample_order       SET approval_status = '已生效' WHERE approval_status IS NULL
                              AND status IN ('已生效', '打样中', '已完成', '已确认', '已转BOM');
UPDATE sample_order       SET approval_status = '已作废' WHERE approval_status IS NULL
                              AND status IN ('已作废', '已取消');

UPDATE free_sample_order  SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = '草稿';
UPDATE free_sample_order  SET approval_status = '待审核' WHERE approval_status IS NULL AND status = '待审核';
UPDATE free_sample_order  SET approval_status = '已生效' WHERE approval_status IS NULL
                              AND status IN ('已生效', '已发货', '已完成');
UPDATE free_sample_order  SET approval_status = '已作废' WHERE approval_status IS NULL AND status = '已作废';

-- 2.3 财务 3
-- 采购费用单 status: 0草稿 1待审核 2已生效 3已分摊 4已作废
UPDATE purchase_expense     SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = 0;
UPDATE purchase_expense     SET approval_status = '待审核' WHERE approval_status IS NULL AND status = 1;
UPDATE purchase_expense     SET approval_status = '已生效' WHERE approval_status IS NULL AND status IN (2, 3);
UPDATE purchase_expense     SET approval_status = '已作废' WHERE approval_status IS NULL AND status = 4;

-- 预付款单 status: 0草稿 1待审核 2已生效 3已核销 7已作废
UPDATE purchase_pre_payment SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = 0;
UPDATE purchase_pre_payment SET approval_status = '待审核' WHERE approval_status IS NULL AND status = 1;
UPDATE purchase_pre_payment SET approval_status = '已生效' WHERE approval_status IS NULL AND status IN (2, 3);
UPDATE purchase_pre_payment SET approval_status = '已作废' WHERE approval_status IS NULL AND status = 7;

-- 费用分摊 status: 草稿/待审核/已生效/已作废
UPDATE operation_expense    SET approval_status = '草稿'   WHERE approval_status IS NULL AND status = '草稿';
UPDATE operation_expense    SET approval_status = '待审核' WHERE approval_status IS NULL AND status = '待审核';
UPDATE operation_expense    SET approval_status = '已生效' WHERE approval_status IS NULL AND status = '已生效';
UPDATE operation_expense    SET approval_status = '已作废' WHERE approval_status IS NULL AND status = '已作废';

-- 三、校验：10 张表不应再有 approval_status 为空的行
select 'subcontract_order' as table_name, count(*) as null_cnt from subcontract_order where approval_status is null
union all select 'subcontract_pick', count(*) from subcontract_pick where approval_status is null
union all select 'subcontract_inbound', count(*) from subcontract_inbound where approval_status is null
union all select 'subcontract_return', count(*) from subcontract_return where approval_status is null
union all select 'subcontract_supplement', count(*) from subcontract_supplement where approval_status is null
union all select 'sample_order', count(*) from sample_order where approval_status is null
union all select 'free_sample_order', count(*) from free_sample_order where approval_status is null
union all select 'purchase_expense', count(*) from purchase_expense where approval_status is null
union all select 'purchase_pre_payment', count(*) from purchase_pre_payment where approval_status is null
union all select 'operation_expense', count(*) from operation_expense where approval_status is null;
