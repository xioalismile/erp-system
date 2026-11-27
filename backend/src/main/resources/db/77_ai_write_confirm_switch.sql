-- 77_ai_write_confirm_switch.sql
-- 2026-09-14 AI 写操作「人工确认」开关（可在 设置中心 → AI工具权限 里切换）
--
-- 背景：
--   原开关 AiConfirmService.confirmWriteEnabled 是 final 字段，只在启动时读
--   erp.ai.confirm-write-enabled（application.yml 未定义该键 → 恒为默认 true），
--   导致「写操作必须人工确认」既不能在界面切换、改配置还要重启。
--
-- 本脚本预置运行时开关（默认 true = 需要确认，与升级前行为【完全一致】）：
--   开启（默认）：AI 执行任何写操作前都弹确认卡片，由用户拍板（现状）
--   关闭        ：审批 / 提交 / 驳回 / 重置草稿 / 下推 等【可逆】动作直接执行，
--                 用户无需再回「确定」；但「删除 / 作废」等【不可逆】动作仍强制确认
--                 （该护栏在代码内写死，不随本开关放开）
--
-- 生效方式：运行时读取（AiConfirmService），设置中心改完立即生效、无需重启；
--          若本配置缺失或读取异常，回退 application.yml 的 erp.ai.confirm-write-enabled（默认 true）。
--
-- 幂等：WHERE NOT EXISTS（【铁律】禁用 ON CONFLICT：PG 环境该唯一约束可能缺失，
--       报错会中止整次部署，见 2026-09-12 事故）。

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark, created_at, updated_at, created_by, updated_by)
SELECT 'ai.confirm.write.enabled', 'true', 'AI写操作需人工确认', 'ai', 'boolean', 5,
       '开启（默认）时AI执行写操作前弹确认卡片由用户拍板；关闭后审批/提交/驳回/重置草稿/下推等可逆动作直接执行，删除/作废仍强制确认', NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ai.confirm.write.enabled');
