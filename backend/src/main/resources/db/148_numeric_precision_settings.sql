-- 148_numeric_precision_settings.sql
-- 数值精度设置「真正生效」配套：① 对齐默认位数到当前系统主流位数 ② 归并到「小数位数设置」分区
-- 背景：设置中心的 数量/金额 小数位数此前无任何代码消费（纯摆设）；本次接入后「默认值必须=旧行为」，
--       故把默认对齐到各页当前写死的主流位数（数量4 / 单价4 / 金额2 / 汇率6 / 百分比2）。
-- 幂等：可重复执行；仅当"仍等于旧默认值"时才改写，避免覆盖管理员已改过的值。

-- ① 确保 7 个键存在（缺则补，写入对齐后的默认值）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.quantity', '4', '数量小数位数', 'sys_param', 'number', NULL, 1, '数量字段保留小数位数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.price', '4', '单价小数位数', 'sys_param', 'number', NULL, 2, '单价字段保留小数位数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.amount', '2', '金额小数位数', 'sys_param', 'number', NULL, 3, '金额字段保留小数位数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.rate', '6', '汇率小数位数', 'sys_param', 'number', NULL, 4, '汇率字段保留小数位数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.percent', '2', '百分比小数位数', 'sys_param', 'number', NULL, 5, '税率/折扣率等百分比字段保留小数位数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.rounding.mode', 'HALF_UP', '舍入方式', 'sys_param', 'select', '[{"label":"四舍五入(HALF_UP)","value":"HALF_UP"},{"label":"向下取整(FLOOR)","value":"FLOOR"},{"label":"向上取整(CEILING)","value":"CEILING"}]', 6, '数值计算舍入方式', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('precision.trim.zero', 'false', '省略小数点后多余的0', 'sys_param', 'boolean', NULL, 7, '开启后 1.50 显示为 1.5', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ② 旧默认 → 新默认（仅当仍等于"初始化时的旧默认值"时才改写，保护管理员已改过的值）
UPDATE sys_config SET config_value = '4', updated_at = NOW()
 WHERE config_key = 'precision.quantity' AND config_value = '2';
UPDATE sys_config SET config_value = '6', updated_at = NOW()
 WHERE config_key = 'precision.rate' AND config_value = '4';

-- ③ 归组 + 归分区：全部 precision.* → 系统参数 group → 「小数位数设置」分区（一处管全）
UPDATE sys_config SET config_group = 'sys_param' WHERE config_key LIKE 'precision.%';
UPDATE sys_config SET config_section = '小数位数设置'
 WHERE config_key IN ('precision.quantity','precision.amount','precision.price','precision.rate','precision.percent','precision.rounding.mode','precision.trim.zero');

-- ④ 补名称（空名才补）
UPDATE sys_config SET config_name = '数量小数位数' WHERE config_key = 'precision.quantity' AND (config_name IS NULL OR config_name = '');
UPDATE sys_config SET config_name = '金额小数位数' WHERE config_key = 'precision.amount'   AND (config_name IS NULL OR config_name = '');
UPDATE sys_config SET config_name = '单价小数位数' WHERE config_key = 'precision.price'    AND (config_name IS NULL OR config_name = '');
UPDATE sys_config SET config_name = '汇率小数位数' WHERE config_key = 'precision.rate'     AND (config_name IS NULL OR config_name = '');
UPDATE sys_config SET config_name = '百分比小数位数' WHERE config_key = 'precision.percent' AND (config_name IS NULL OR config_name = '');