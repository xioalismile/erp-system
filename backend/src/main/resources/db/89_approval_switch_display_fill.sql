-- 给 3 个「裸行」审批开关补齐展示字段（应用侧自动建行时只写了 key+value）
-- 原则：只补 NULL 的展示字段，绝不改 config_value（用户可能已配置过开关值）
UPDATE sys_config
SET config_name = '生产退料单需要审批',
    config_group = 'approval',
    config_type = 'boolean',
    config_options = '[{"label":"启用","value":true},{"label":"停用","value":false}]',
    remark = '控制生产退料单是否进入审批流程'
WHERE config_key = 'approval.bill.MATERIAL_RETURN.enabled' AND config_name IS NULL;

UPDATE sys_config
SET config_name = '生产补料单需要审批',
    config_group = 'approval',
    config_type = 'boolean',
    config_options = '[{"label":"启用","value":true},{"label":"停用","value":false}]',
    remark = '控制生产补料单是否进入审批流程'
WHERE config_key = 'approval.bill.MATERIAL_SUPPLEMENT.enabled' AND config_name IS NULL;

UPDATE sys_config
SET config_name = '委外退料单需要审批',
    config_group = 'approval',
    config_type = 'boolean',
    config_options = '[{"label":"启用","value":true},{"label":"停用","value":false}]',
    remark = '控制委外退料单是否进入审批流程'
WHERE config_key = 'approval.bill.SUBCONTRACT_RETURN.enabled' AND config_name IS NULL;

UPDATE sys_config
SET config_name = '委外补料单需要审批',
    config_group = 'approval',
    config_type = 'boolean',
    config_options = '[{"label":"启用","value":true},{"label":"停用","value":false}]',
    remark = '控制委外补料单是否进入审批流程'
WHERE config_key = 'approval.bill.SUBCONTRACT_SUPPLEMENT.enabled' AND config_name IS NULL;

-- 校验：不应再有缺名的开关；已为 true 的开关值不得被改动
select count(*) as still_no_name from sys_config where config_key like 'approval.bill.%.enabled' and config_name is null;
select config_key, config_value, config_name from sys_config where config_key in
 ('approval.bill.MATERIAL_RETURN.enabled','approval.bill.MATERIAL_SUPPLEMENT.enabled')
 order by config_key;
select count(*) as true_switches from sys_config where config_key like 'approval.bill.%.enabled' and config_value = 'true';
