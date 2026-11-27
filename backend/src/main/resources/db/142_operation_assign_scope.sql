-- ============================================================================
-- 142_operation_assign_scope.sql
-- 工序级「谁可以改负责人」（2026-10-09）
--
--   背景（用户口径）：「谁可以设置负责人现在整条路线是全部人，我要的是每个工序都可以调整」。
--   原来档位只存在工艺路线主表（routing.assign_scope / routing.assign_role_ids），一条路线只能设一次；
--   本次下沉到「工艺路线工序明细」的每一行（routing_operation），每道工序各自设。
--
--   assign_scope      全部人 / 当前负责人 / 指定角色 / 工单开单人（空 = 回落所属路线设置，再回落「全部人」）
--   assign_role_ids   选「指定角色」时的角色编码清单（逗号分隔 sys_role.role_code）
--
--   存量迁移（幂等）：把现有路线级的值平移到该路线下的每道工序 → 升级后"原来谁能改、现在还是谁能改"。
--   routing 上的两列保留过渡（守则04 2.7.2-1：废弃列至少保留 3 个大版本），仅作兜底，不再是主入口。
--
-- 幂等：ADD COLUMN IF NOT EXISTS / UPDATE ... WHERE 空值；无 DELETE / DROP / 无 WHERE 全表覆盖。
-- 同步落盘：deploy/sql/142_operation_assign_scope.sql（部署镜像）
--           + SchemaUpgradeEnsurer.BUSINESS_COLUMN_SPECS 启动逐租户自愈（多租户物理隔离多库）
-- ============================================================================

BEGIN;

ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS assign_scope VARCHAR(20);
ALTER TABLE routing_operation ADD COLUMN IF NOT EXISTS assign_role_ids TEXT;
COMMENT ON COLUMN routing_operation.assign_scope IS '谁可以改本道工序负责人：全部人/当前负责人/指定角色/工单开单人（空=回落所属工艺路线设置，再回落全部人）';
COMMENT ON COLUMN routing_operation.assign_role_ids IS '选「指定角色」时的角色编码清单（逗号分隔 sys_role.role_code）';

-- 存量迁移：路线级 → 该路线下每道工序（只填空值，幂等）
UPDATE routing_operation ro
   SET assign_scope = COALESCE(r.assign_scope, '全部人'),
       assign_role_ids = COALESCE(r.assign_role_ids, '')
  FROM routing r
 WHERE ro.routing_id = r.routing_id
   AND (ro.assign_scope IS NULL OR ro.assign_scope = '');

-- 兜底：没有任何值（路线也读不到）的工序统一按「全部人」= 升级前行为
UPDATE routing_operation SET assign_scope = '全部人' WHERE assign_scope IS NULL OR assign_scope = '';

ALTER TABLE routing_operation ALTER COLUMN assign_scope SET DEFAULT '全部人';

COMMIT;

-- 验证 SQL（只读，预期：2）
SELECT COUNT(*) AS ro_assign_cols FROM information_schema.columns
 WHERE table_name = 'routing_operation' AND column_name IN ('assign_scope', 'assign_role_ids');
