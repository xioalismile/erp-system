-- =====================================================================
-- 85 生产组装单 / 生产拆卸单：状态口径统一 + 单据审批开关补登记 + 单据叫法对齐菜单名
-- 背景：这两个单据内部把 6 当“生效”、3 当“作废”，与全系统统一口径（0草稿/1待审核/2已生效/7已作废，
--       结案另用 close_status=1）不一致，导致“生效单顶部按钮全空、作废单反而显示重置草稿”。
-- 幂等：重复执行结果一致（UPDATE 只命中旧值；INSERT 先判 NOT EXISTS）。
-- =====================================================================

-- 一、存量数据迁移：生效(6) → 已生效(2)、作废(3) → 已作废(7)
UPDATE produce_assemble    SET status = 2 WHERE status = 6;
UPDATE produce_assemble    SET status = 7 WHERE status = 3;
UPDATE produce_disassemble SET status = 2 WHERE status = 6;
UPDATE produce_disassemble SET status = 7 WHERE status = 3;

-- 二、新增两张单据自己的审批开关（默认 false = 升级前的免审批行为，不改动现状）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, created_by)
SELECT 'approval.bill.PRODUCE_ASSEMBLE.enabled', 'false', '生产组装单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM sys_config), '控制生产组装单是否进入审批流程', NOW(), 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PRODUCE_ASSEMBLE.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, created_by)
SELECT 'approval.bill.PRODUCE_DISASSEMBLE.enabled', 'false', '生产拆卸单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM sys_config), '控制生产拆卸单是否进入审批流程', NOW(), 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PRODUCE_DISASSEMBLE.enabled');

-- 三、费用分摊：后端一直按 OPERATION_EXPENSE 判开关，但 sys_config 里没有这一项 → 补登记（默认 false=免审批，保持现状）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, sort_order, remark, created_at, created_by)
SELECT 'approval.bill.OPERATION_EXPENSE.enabled', 'false', '费用分摊需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM sys_config), '控制费用分摊是否进入审批流程', NOW(), 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.OPERATION_EXPENSE.enabled');

-- 四、审批开关的名称统一为“系统左侧菜单名”（只改显示名，不动开关值，不影响任何判定逻辑）
UPDATE sys_config SET config_name = '生产工单需要审批'     WHERE config_key = 'approval.bill.PRODUCTION_ORDER.enabled';
UPDATE sys_config SET remark      = '控制生产工单是否进入审批流程' WHERE config_key = 'approval.bill.PRODUCTION_ORDER.enabled';
UPDATE sys_config SET config_name = '委外加工单需要审批'   WHERE config_key = 'approval.bill.SUBCONTRACT_ORDER.enabled';
UPDATE sys_config SET remark      = '控制委外加工单是否进入审批流程' WHERE config_key = 'approval.bill.SUBCONTRACT_ORDER.enabled';
UPDATE sys_config SET config_name = '免费样品订单需要审批' WHERE config_key = 'approval.bill.FREE_SAMPLE.enabled';
UPDATE sys_config SET remark      = '控制免费样品订单是否进入审批流程' WHERE config_key = 'approval.bill.FREE_SAMPLE.enabled';
UPDATE sys_config SET config_name = 'BOM需要审批'          WHERE config_key = 'approval.bill.BOM.enabled';
UPDATE sys_config SET remark      = '控制BOM是否进入审批流程' WHERE config_key = 'approval.bill.BOM.enabled';
UPDATE sys_config SET config_name = '销售退货需要审批'     WHERE config_key = 'approval.bill.SALE_RETURN.enabled';
UPDATE sys_config SET remark      = '控制销售退货是否进入审批流程' WHERE config_key = 'approval.bill.SALE_RETURN.enabled';
