-- 64_logistics_17track.sql
-- 17TRACK 国际物流查询配置项（幂等执行：不存在才插入）
--
-- 背景：系统原仅接入快递100（国内）。本脚本新增 17TRACK（国际）配置项。
-- 说明：config_group 使用独立的 'logistics_17track'，
--       目的是不与既有 'logistics' 分组混在一起（'logistics' 分组会被
--       KgwLogisticsConfig.vue / SysConfig.vue 的"看板参数"动态表单整组渲染，
--       混入会导致重复表单项）。
--
-- 密钥类信息 config_type='password'：仅后端内存使用，接口只返回脱敏掩码，绝不返回明文。

-- 1) 17TRACK 国际物流查询开关（默认关闭 = 保持升级前旧行为，所有单仍走快递100）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'logistics.17track.enabled', 'false', '17TRACK国际物流开关', 'logistics_17track', 'boolean', NULL, 1,
        '是否启用17TRACK国际物流查询。关闭后所有单号仍走快递100（保持升级前行为）',
        NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'logistics.17track.enabled');

-- 2) 17TRACK 密钥（17token）：登录 api.17track.net → 设置页 获取
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'logistics.17track.token', '', '17TRACK密钥(17token)', 'logistics_17track', 'password', NULL, 2,
        '在 https://api.17track.net 注册后，登录后台「设置页」获取的 API 密钥(17token)。留空表示未配置',
        NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'logistics.17track.token');

-- 3) 17TRACK 查询模式：Standard=标准模式(每次扣1额度)，Instant=即时模式(每次扣10额度)
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'logistics.17track.cache_level', 'Standard', '17TRACK查询模式', 'logistics_17track', 'select', 'Standard,Instant', 3,
        'Standard=标准模式(取最近3小时数据，每次扣1个额度，推荐)；Instant=即时模式(立即抓取，每次扣10个额度)',
        NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'logistics.17track.cache_level');

-- 4) 国际承运商关键字清单（逗号分隔）：命中则走 17TRACK 国际通道
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'logistics.17track.international_carriers',
        'DHL,UPS,FedEx,Fedex,USPS,TNT,Aramex,Royal Mail,La Poste,PostNL,Japan Post,Singapore Post,e邮宝,China Post,燕文,4PX,云途,YunExpress,递四方,顺丰国际',
        '国际承运商关键字', 'logistics_17track', 'text', NULL, 4,
        '逗号分隔。承运商名称包含其中任一关键字即判定为国际件，走17TRACK；否则走快递100',
        NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'logistics.17track.international_carriers');
