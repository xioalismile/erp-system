-- ============================================================
-- 176_sale_over_qty_policy.sql
-- 销售模块「超过来源单数量」三态管控
--   不控制 / 预警提示 / 不允许
-- 覆盖设置项：
--   sale.order.over.qty     超过来源单数量下单（销售订单）
--   sale.outbound.over.qty  超过来源单数量出库（销售出库单）
--   sale.return.over.qty    超过来源单数量退货（销售退货单）
-- 幂等：可重复执行
-- ============================================================

-- Step 1: 下拉固定三种选项，并补全说明
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.order.over.qty', '预警提示', '超过来源单数量下单', 'sale', '销售订单', 'select',
        '[{"label":"不控制","value":"不控制"},{"label":"预警提示","value":"预警提示"},{"label":"不允许","value":"不允许"}]',
        140, '提交时按来源明细剩余数量校验：不控制=不校验；预警提示=超量允许保存并提示；不允许=超量阻断保存', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET
    config_type = 'select',
    config_options = EXCLUDED.config_options,
    remark = EXCLUDED.remark,
    updated_at = NOW(),
    updated_by = 'system';

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.outbound.over.qty', '预警提示', '超过来源单数量出库', 'sale', '销售出库', 'select',
        '[{"label":"不控制","value":"不控制"},{"label":"预警提示","value":"预警提示"},{"label":"不允许","value":"不允许"}]',
        200, '提交/审核时按来源销售订单可发货数量校验：不控制=不校验；预警提示=超量允许保存并提示；不允许=超量阻断保存', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET
    config_type = 'select',
    config_options = EXCLUDED.config_options,
    remark = EXCLUDED.remark,
    updated_at = NOW(),
    updated_by = 'system';

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section, config_type, config_options, sort_order, remark, created_at, updated_at, created_by, updated_by)
VALUES ('sale.return.over.qty', '预警提示', '超过来源单数量退货', 'sale', '销售退货', 'select',
        '[{"label":"不控制","value":"不控制"},{"label":"预警提示","value":"预警提示"},{"label":"不允许","value":"不允许"}]',
        280, '提交/审核时按来源单据可退货数量校验：不控制=不校验；预警提示=超量允许保存并提示；不允许=超量阻断保存', NOW(), NOW(), 'system', 'system')
ON CONFLICT (config_key) DO UPDATE SET
    config_type = 'select',
    config_options = EXCLUDED.config_options,
    remark = EXCLUDED.remark,
    updated_at = NOW(),
    updated_by = 'system';

-- Step 2: 历史值归一化（不覆盖管理员已选的「不控制/预警提示/不允许」）
UPDATE sys_config
SET config_value = '预警提示', updated_at = NOW(), updated_by = 'system'
WHERE config_key IN ('sale.order.over.qty', 'sale.outbound.over.qty', 'sale.return.over.qty')
  AND config_value = '警告提示';

UPDATE sys_config
SET config_value = '不控制', updated_at = NOW(), updated_by = 'system'
WHERE config_key IN ('sale.order.over.qty', 'sale.outbound.over.qty', 'sale.return.over.qty')
  AND config_value = '不检测';

-- Step 3: 兜底清理不在三态内的其他历史值（空值/未知值按旧行为=预警提示）
UPDATE sys_config
SET config_value = '预警提示', updated_at = NOW(), updated_by = 'system'
WHERE config_key IN ('sale.order.over.qty', 'sale.outbound.over.qty', 'sale.return.over.qty')
  AND (config_value IS NULL OR config_value NOT IN ('不控制', '预警提示', '不允许'));
