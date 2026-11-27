-- =============================================================
-- 127_routing_notify_switch.sql
-- 工序操作面板改造 + 工序通知（2026-09-28 第13期补充）
-- 内容：工艺路线主档新增两个开关，均默认 0（关）＝ 与升级前行为完全一致
-- 幂等：ADD COLUMN IF NOT EXISTS，跑 100 遍 = 跑 1 遍
-- 兜底：SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 已登记，启动时主库 + 每个租户库逐库补齐
-- =============================================================

-- 1) 自动通知下道工序负责人（默认关）
ALTER TABLE routing ADD COLUMN IF NOT EXISTS auto_notify_next SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN routing.auto_notify_next IS '自动通知下道工序负责人：1=本路线工序完工/检验通过时自动给下道工序负责人发系统通知；0=不发（默认关）';

-- 2) 按顺序执行（默认关）
ALTER TABLE routing ADD COLUMN IF NOT EXISTS enforce_sequence SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN routing.enforce_sequence IS '按顺序执行：1=前道工序未完工时后道工序不能开工；0=不限制（默认关）';
