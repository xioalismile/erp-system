-- ============================================================
-- 170_invoice_entry_mode.sql
-- 用途：发票金额录入方式（2026-10-23）——设置中心「系统参数 → 发票录入口径」下拉，
--       qty_price=按数量单价（默认，升级前行为：金额=数量×单价）；
--       amount   =按发票金额（直接录入本次开票金额，数量单价只读并自动反算，允许分次开票）。
-- 依据：金蝶「发票只校验金额」「单价×数量与不含税金额不一致时自动反算单价」「价税合计=数量×含税单价（默认不勾选）」。
-- 幂等：NOT EXISTS 重复执行 0 副作用；不改表结构。
-- 同内容文件另存 deploy/sql/170_invoice_entry_mode.sql（守则 04 编号体系）。
-- ============================================================

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, config_section, remark, sort_order, created_by)
SELECT 'sys.invoice.entry.mode', 'qty_price', '发票金额录入方式', 'sys_param', 'select',
       '[{"label":"按数量单价","value":"qty_price"},{"label":"按发票金额","value":"amount"}]',
       '发票录入口径',
       '按数量单价=开票金额由数量×单价自动计算，与升级前一致；按发票金额=直接录入本次开票金额，数量与单价只读并由金额自动反算，开多少冲多少，超额不允许保存',
       90, 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.invoice.entry.mode');

-- 已存在的行补齐分组/小节/类型/选项（老库防御性归位）
UPDATE sys_config
SET config_group = 'sys_param',
    config_section = '发票录入口径',
    config_type = 'select',
    config_options = '[{"label":"按数量单价","value":"qty_price"},{"label":"按发票金额","value":"amount"}]'
WHERE config_key = 'sys.invoice.entry.mode'
  AND (COALESCE(config_section, '') <> '发票录入口径'
       OR COALESCE(config_type, '') <> 'select'
       OR COALESCE(config_options, '') = '');
