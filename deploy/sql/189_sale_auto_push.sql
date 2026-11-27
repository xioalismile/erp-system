-- 189: 自动下推单据（设置中心 → 业务 → 自动下推设置）
-- 背景（2026-11-08 用户需求）：
--   ① 销售合同审核生效后，自动下推一份「已生效」的销售订单；
--   ② 销售订单审核生效后，自动生成一份「已生效」的销售合同；
--   ③ 目标单记录来源单据类型与来源单号，来源/去向页签可查。
-- 两个开关默认关闭，开启前行为与升级前完全一致。本脚本幂等可重跑。
-- 代码侧同口径实现：SaleAutoPushServiceImpl + SchemaUpgradeEnsurer.ensureSaleAutoPushSettings。

-- 1) 设置项：销售合同 → 销售订单
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, config_options, sort_order, remark,
                        created_at, updated_at, created_by, updated_by)
SELECT 'sale.auto.push.contract.to.order', 'false', '销售合同自动生成销售订单', 'sale', '自动下推单据',
       'boolean', NULL, 70,
       '开启后，销售合同审核生效时自动生成一份状态为已生效的销售订单，并记录来源单号；自动生成的单据不会再次触发反向生成。',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.auto.push.contract.to.order');

-- 2) 设置项：销售订单 → 销售合同
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, config_options, sort_order, remark,
                        created_at, updated_at, created_by, updated_by)
SELECT 'sale.auto.push.order.to.contract', 'false', '销售订单自动生成销售合同', 'sale', '自动下推单据',
       'boolean', NULL, 71,
       '开启后，销售订单审核生效时自动生成一份状态为已生效的销售合同，并记录来源单号；自动生成的单据不会再次触发反向生成。',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.auto.push.order.to.contract');

-- 3) 设置中心菜单：挂在「设置中心」下，route_path 与前端 item key 一致
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, route_path,
                      permission_code, component_path, sort_order, icon, is_visible,
                      is_enabled, is_cache, keepalive, status)
SELECT 'system_auto_push_settings', '自动下推设置', p.menu_id, 'MENU', '/system/settings?item=auto-push',
       NULL, NULL, 24, 'Connection', 1, 1, 0, 1, '启用'
FROM (SELECT menu_id FROM sys_menu
      WHERE menu_code = 'system_settings_center' OR route_path = '/system/settings'
      ORDER BY menu_id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'system_auto_push_settings');

-- 4) 角色授权：超级管理员 + 系统管理员自动可见
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, created_at)
SELECT r.role_id, '/system/settings?item=auto-push', '菜单', NOW()
FROM sys_role r
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission p
                  WHERE p.role_id = r.role_id
                    AND p.permission_type = '菜单'
                    AND p.menu_id = '/system/settings?item=auto-push');
