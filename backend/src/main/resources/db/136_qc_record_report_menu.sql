-- ============================================================
-- 136_qc_record_report_menu.sql
-- 用途：生产管理 → 「工序质检报表」菜单初始化 + 角色自动授权（守则 04 2.7.1-7 / 2.8.1-6）
--
-- 背景：工序检验记录（qc_record）由工序报工/检验流程写入，但此前既没有查询接口、
--       也没有页面入口 → 用户看不到质检记录；「经营看板 → 质量」块也因为没有目标路由而无法穿透。
--       本脚本插入「工序质检报表」菜单，否则即使用户有页面路由，
--       menuRoutePaths 里没有该 route_path → 左侧菜单渲染不出来、看板质量块点进去被权限拦（守则 2.8 第 5/6/7 层）。
--
-- 口径（均已对本地实库核对，非推测）：
--   1. 父级 = 「生产管理」（取自 menu_code='production'，实库 menu_id=40）；
--      「报表」只是前端 menuConfig 的分组壳，库中并无独立「报表」菜单行，故挂生产管理下；
--   2. sort_order = 101（同组「分析报表」实库为 100，紧随其后，保证顺序 = 分析报表 → 工序质检报表）；
--   3. permission_code = production:report-qc-record（对象名 = 前端 menuConfig 的 itemKey，与设置中心按钮权限对象码一致）；
--   4. menu_id 走序列，禁止硬编码（跨库 ID 体系不同）；
--   5. 角色授权 = ADMIN / SUPER_ADMIN / PRODUCTION_MANAGER / PRODUCTION / FINANCE_MANAGER / SALES
--      （生产角色是本职使用人；FINANCE_MANAGER 是「经营看板」的授权角色，看板质量块要能穿透进来；
--        SALES 与同组「分析报表」「车间进度跟踪」口径一致，避免"原来能看现在看不到"）。
--   6. 注意：sys_role_permission.menu_id 字段实际存的是 sys_menu.route_path（VARCHAR），不是数字 ID。
--
-- 幂等：全部 INSERT 走 NOT EXISTS，重复执行 0 副作用（第二遍：菜单 0 行、授权 0 行）。
-- 执行：psql -h localhost -U erp_user -d erp_db -f 136_qc_record_report_menu.sql
--       （首次部署时 SchemaUpgradeEnsurer.ensureQcRecordReportMenu() 会执行等价逻辑；
--          本文件是独立部署/手动修复的备用入口）
-- 执行后无需重启后端，但用户需**重新登录**（menuRoutePaths 在登录时缓存）
-- 同步落盘：deploy/sql/136_qc_record_report_menu.sql（部署镜像）+ deploy-kit-manifest.txt 登记
-- ============================================================

BEGIN;

-- 1) 序列对齐（防历史上显式 ID 插入后序列落后撞主键）
SELECT setval('sys_menu_menu_id_seq', COALESCE((SELECT MAX(menu_id) FROM sys_menu), 1));

-- 2) 菜单：生产管理 → 工序质检报表
INSERT INTO sys_menu
  (menu_code, menu_name, parent_menu_id, menu_type, route_path, permission_code,
   sort_order, icon, is_visible, is_enabled, is_cache, keepalive, status, version)
SELECT 'production_qc_record_report', '工序质检报表',
       (SELECT menu_id FROM sys_menu WHERE menu_code = 'production'),
       'MENU', '/production/qc-record', 'production:report-qc-record',
       101, 'DataLine', 1, 1, 0, 1, '启用', NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'production_qc_record_report');

-- 3) 兜底修复父子关系（父菜单解析失败重跑场景）
UPDATE sys_menu
   SET parent_menu_id = (SELECT menu_id FROM sys_menu WHERE menu_code = 'production' LIMIT 1)
 WHERE menu_code = 'production_qc_record_report' AND parent_menu_id IS NULL;

-- 4) 角色授权（幂等 NOT EXISTS）
INSERT INTO sys_role_permission
  (role_id, menu_id, permission_type, permission_value, is_inherited)
SELECT DISTINCT r.role_id, m.route_path, '菜单', 1, 0
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('ADMIN', 'SUPER_ADMIN', 'PRODUCTION_MANAGER', 'PRODUCTION', 'FINANCE_MANAGER', 'SALES')
  AND m.menu_code = 'production_qc_record_report'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission p
     WHERE p.role_id = r.role_id
       AND p.menu_id = m.route_path
       AND p.permission_type = '菜单'
  );

COMMIT;

-- ============================================================
-- 验证 SQL（执行后手动核对，预期：1 行菜单 + N 行权限）
-- ============================================================
-- SELECT menu_id, menu_code, menu_name, parent_menu_id, route_path, permission_code, sort_order
--   FROM sys_menu WHERE menu_code = 'production_qc_record_report';
--
-- SELECT r.role_code, p.menu_id
--   FROM sys_role_permission p JOIN sys_role r ON r.role_id = p.role_id
--  WHERE p.menu_id = '/production/qc-record';
