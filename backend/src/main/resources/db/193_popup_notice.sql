-- 193: 设置中心「弹窗设置」种子（2026-11-19）
-- 功能：有新提醒时桌面右下角弹卡片（与后台提醒模块 sys_notification 互通）
-- 口径：所有开关默认关闭（旧行为=不弹窗，守则 2.7.2-6 默认值对齐旧行为）
-- 幂等：全部 NOT EXISTS 守卫，可重复执行

BEGIN;

-- ========== 一、配置种子（存 notification 组，与消息通知设置同一组便于同接口读取） ==========
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, sort_order, remark)
SELECT v.k, v.va, v.nm, 'notification', 'text', v.so, v.rm
  FROM (VALUES
        ('notification.popup.enabled',                                  'false', '启用弹窗提醒', 201, '总开关。开=有新提醒时右下角弹卡片；关=只在铃铛显示红点'),
        ('notification.popup.type.approval.enabled',                    'false', '待审批提醒弹窗', 202, '有新的待审批提醒时弹窗'),
        ('notification.popup.type.approval.template',                   '您有 {数量} 张待审批单据，请及时处理。', '待审批提醒弹窗内容', 203, '占位符：{数量}=该类未读条数 {单据类型}=单据中文名 {单号}=最新一条单据编号'),
        ('notification.popup.type.approval_finish.enabled',             'false', '审批结果提醒弹窗', 204, '审批通过或驳回时弹窗'),
        ('notification.popup.type.approval_finish.template',            '您的单据已完成审批：{单据类型} {单号}', '审批结果提醒弹窗内容', 205, '占位符同上'),
        ('notification.popup.type.operation_mention.enabled',           'false', '工序通知弹窗', 206, '收到工序通知或被@时弹窗'),
        ('notification.popup.type.operation_mention.template',          '工序通知：{单号} {内容}', '工序通知弹窗内容', 207, '占位符：{内容}=通知正文摘要，其余同上')
       ) AS v(k, va, nm, so, rm)
 WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = v.k);

-- ========== 二、设置中心菜单行（父级=设置中心，紧随消息通知设置 sort_order=170） ==========
INSERT INTO sys_menu (menu_code, menu_name, parent_menu_id, menu_type, route_path,
                      permission_code, component_path, sort_order, icon, is_visible, is_enabled,
                      is_cache, keepalive, status)
SELECT 'settings_popup_notice', '弹窗设置', c.menu_id, 'MENU', '/system/settings?item=popup-setting',
       NULL, NULL, 171, NULL, 1, 1, 0, 1, '启用'
  FROM sys_menu c
 WHERE c.menu_code = 'system_settings_center'
   AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.route_path = '/system/settings?item=popup-setting')
 LIMIT 1;

-- ========== 三、角色自动授权（守则 2.7.2-3：超级管理员+系统管理员自动可见） ==========
-- 口径同 133：sys_role_permission.menu_id 存 route_path 字符串
INSERT INTO sys_role_permission (role_id, menu_id, permission_type, created_at)
SELECT r.role_id, '/system/settings?item=popup-setting', '菜单', NOW()
  FROM sys_role r
 WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN')
   AND NOT EXISTS (SELECT 1 FROM sys_role_permission p
                    WHERE p.role_id = r.role_id AND p.permission_type = '菜单'
                      AND p.menu_id = '/system/settings?item=popup-setting');

COMMIT;

-- 复核（人工核对用）：
-- SELECT config_key, config_value FROM sys_config WHERE config_key LIKE 'notification.popup.%' ORDER BY sort_order;
-- SELECT menu_id, menu_name, route_path FROM sys_menu WHERE route_path = '/system/settings?item=popup-setting';
-- SELECT r.role_code, p.menu_id FROM sys_role_permission p JOIN sys_role r ON r.role_id = p.role_id WHERE p.menu_id = '/system/settings?item=popup-setting';
