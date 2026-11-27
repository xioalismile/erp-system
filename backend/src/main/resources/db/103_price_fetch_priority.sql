-- ============================================================
-- 103_price_fetch_priority.sql
-- 价格策略联动取价：补齐【采购侧】取价开关与取价优先级（销售侧 Key 早已存在，本次修的是"代码读错 Key"）
-- 背景：
--   1) 销售侧 sale.price.strategy.enabled / sale.price.fetch.priority 早已存在，
--      但后端判断"是否启用价格策略"一直读的是 kgw_sale_price_strategy（界面上没有该项、默认 false）
--      → 界面上把「价格策略启用」打开也没用；本次代码侧改为读正确 Key（不在本脚本）。
--   2) 采购侧此前没有对应的取价配置，本次补齐，与销售侧同口径。
-- 幂等：ADD COLUMN IF NOT EXISTS + ON CONFLICT (config_key) DO NOTHING —— 跑 100 遍结果一致，不覆盖既有值。
-- 只增不改不删：无 DELETE / TRUNCATE / DROP；不新增表 / 不新增业务列。
-- ============================================================

-- 兼容：全新库若未跑过 43_sys_config_dynamic_settings.sql，先确保小节字段存在（幂等）
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_section VARCHAR(100);

-- ========== 1. 采购侧：价格策略启用（默认"是"，与销售侧口径一致；关闭即回到"按物料采购价取价"的旧行为） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, config_section, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.price.strategy.enabled', 'true', '价格策略启用', 'purchase', 'boolean', NULL, 4, '是否启用采购价格策略匹配（关闭后按物料采购价取价）', '公用参数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 2. 采购侧：取价优先级（默认"价格策略优先"） ==========
-- 说明：采购侧没有"价格等级"档案，等级档不适用 → 只提供 策略 / 物料采购价 两档
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, config_section, created_at, updated_at, created_by, updated_by)
VALUES ('purchase.price.fetch.priority', 'strategy,material', '取价优先级', 'purchase', 'select', '价格策略优先=strategy,material|物料采购价优先=material,strategy', 5, '采购取价方式优先级，逗号分隔', '公用参数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

-- ========== 3. 销售侧：兜底补齐（某环境缺失才插入，已有值不会被覆盖） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, config_section, created_at, updated_at, created_by, updated_by)
VALUES ('sale.price.strategy.enabled', 'true', '价格策略启用', 'sale', 'boolean', NULL, 3, '是否启用销售价格策略匹配', '公用参数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, config_section, created_at, updated_at, created_by, updated_by)
VALUES ('sale.price.fetch.priority', 'strategy,level,material', '取价优先级', 'sale', 'select', '价格策略优先=strategy,level,material|价格等级优先=level,strategy,material|物料默认价优先=material,strategy,level', 4, '销售取价方式优先级，逗号分隔', '公用参数', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO NOTHING;
