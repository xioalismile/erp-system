-- ============================================================
-- 154_receipt_receive_mode.sql
-- 用途：收款单收款方式全局配置（2026-10-12）——汇总/明细二选一固定生效，
--       设置中心「财务参数 → 收款发货管控」小节，不在收款单界面随单切换。
-- 默认 summary=汇总收款（升级前行为）；幂等 NOT EXISTS。
-- 同内容文件另存 deploy/sql/152_receipt_receive_mode.sql（守则04 编号体系）。
-- ============================================================
-- 152: 收款单收款方式全局配置（2026-10-12 用户指正改版）
-- 需求：明细收款/汇总收款不能在每张收款单里随时切换（单据间口径漂移=数据处理不了），
--       必须是设置中心的全局配置，二选一固定生效。
-- 改法：新增 sys_config 键 sale.receipt.receive.mode（收款单收款方式），
--       挂设置中心 finance 组「收款发货管控」小节，select 控件二选一；
--       默认 summary（汇总收款）= 升级前行为（守则04：新增开关默认值对齐旧行为）。
--       收款单编辑页移除来源单弹窗内的模式切换，改为读取该配置。
-- 幂等：NOT EXISTS 重复执行 0 副作用。

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, config_section, remark, sort_order, created_by)
SELECT 'sale.receipt.receive.mode', 'summary', '收款单收款方式', 'finance', 'select',
       '[{"label":"汇总收款（按整单收）","value":"summary"},{"label":"明细收款（按订单明细行收）","value":"detail"}]',
       '收款发货管控',
       '收款单对销售订单收款的固定方式，全局唯一不随单变化：summary=整单一行核销（原行为）；detail=勾选订单后按明细行逐行收款并回写行级已收', 15, 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.receipt.receive.mode');

-- 已存在的行补齐小节/类型/选项（老库经 43 号脚本建过 section，防御性归位）
UPDATE sys_config
SET config_group='finance', config_section='收款发货管控', config_type='select',
    config_options='[{"label":"汇总收款（按整单收）","value":"summary"},{"label":"明细收款（按订单明细行收）","value":"detail"}]'
WHERE config_key='sale.receipt.receive.mode'
  AND (COALESCE(config_section,'')<>'收款发货管控' OR COALESCE(config_type,'')<>'select' OR COALESCE(config_options,'')='');
