-- 139：工艺路线「谁可以改负责人」（2026-10-08 · 工序操作面板第 4 版）
--   ① routing.assign_scope     谁可以改工序负责人：全部人 / 当前负责人 / 指定角色 / 工单开单人（默认「全部人」= 升级前行为）
--   ② routing.assign_role_ids  选「指定角色」时的角色编码清单（逗号分隔 sys_role.role_code；仅该档位使用）
-- 说明：全部 ADD COLUMN IF NOT EXISTS，幂等；同步 SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS
--       逐租户库启动自愈 + deploy-kit-manifest.txt 登记（守则04：新增列三库齐）。
-- 影响：存量工艺路线 assign_scope 回填「全部人」→ 谁都能改负责人，与升级前完全一致；assign_role_ids 默认空。

ALTER TABLE routing ADD COLUMN IF NOT EXISTS assign_scope VARCHAR(20) NOT NULL DEFAULT '全部人';
COMMENT ON COLUMN routing.assign_scope IS '谁可以改工序负责人：全部人/当前负责人/指定角色/工单开单人';

ALTER TABLE routing ADD COLUMN IF NOT EXISTS assign_role_ids TEXT;
COMMENT ON COLUMN routing.assign_role_ids IS '选「指定角色」时的角色编码清单（逗号分隔 sys_role.role_code）';
