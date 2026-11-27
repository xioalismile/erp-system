-- ============================================================
-- 163_receipt_gen_and_detail_meta.sql
-- 设置中心财务参数：「收款单生成方式」选项与默认值（设计图 v3）
-- ============================================================
-- 背景（2026-10-16 设计图 v3，2026-10-18 用户再次确认）：
--   口径是「不要应收单」——业务单据审核后直接生成**收款单草稿**，财务确认；
--   收款单只有一张核销明细列表，来源在「选单」弹窗里区分（销售订单 / 销售出库单 / 销售发票）。
--   本脚本只负责设置中心这一项参数的显示名/说明/选项/默认值：
--     「收款单生成方式」= 什么时候自动生成**收款单草稿**：
--       不生成（全手工开） / 销售订单审核 / 销售出库单审核 / 销售发票审核
--   默认值 = 不生成（升级后不改变旧账务行为，由用户在设置中心显式开启）。
--   键名保持 finance.ar.source_bill_type 不变（避免多环境键不一致）。
--   8cloud 当前已是 none，执行本脚本无行为变化。
--   发货收款管控四项参数说明末尾标注「仅作用于销售出库单」。
--   幂等：C1 为固定值 UPDATE（基线收敛）；C2 带 NOT LIKE 守卫，重复执行不会重复追加。

-- C1. 「收款单生成方式」—— 默认值 none（不生成）+ 四项选项
UPDATE sys_config
SET config_value = 'none',
    config_name = '收款单生成方式',
    remark = '什么时候自动生成收款单草稿：销售订单审核 / 销售出库单审核 / 销售发票审核 / 不生成（全手工开）；生成的是草稿，财务核对后提交才生效',
    config_options = '[{"label":"不生成","value":"none"},{"label":"销售订单审核","value":"sale_order"},{"label":"销售出库单审核","value":"sale_delivery"},{"label":"销售发票审核","value":"sale_invoice"}]',
    updated_at = NOW()
WHERE config_key = 'finance.ar.source_bill_type';

-- C2. 发货收款管控四项参数 —— 说明末尾统一标注「仅作用于销售出库单」
UPDATE sys_config
SET remark = COALESCE(remark, '') || '（仅作用于销售出库单，不影响收款单）',
    updated_at = NOW()
WHERE config_key IN (
        'sale.delivery.payment.control',
        'sale.delivery.payment.control.scope',
        'sale.delivery.payment.advance.tolerance',
        'sale.delivery.payment.check.bypass.approved'
      )
  AND COALESCE(remark, '') NOT LIKE '%（仅作用于销售出库单，不影响收款单）%';
