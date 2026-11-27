-- ============================================================
-- 140：清理历史遗留的「设置中心分组容器」伪按钮码（2026-10-08）
-- 背景：早期「角色权限设置 → 按钮权限」把设置中心的分组容器（系统 / 业务 / 自定义 / 日志 / AI）
--       当成页面写进了 sys_role_permission（menu_id 形如 system:sub-system / system:sub-business /
--       system:sub-custom / system:sub-log / system:sub-ai，每页写满全部动作）。
--       这些不是真实页面，却被"系统配置写权限"守卫按 system:*:save|edit|add 误判为已授权。
--       现行 UI 已不再产出这些码（子项拍平为真实页面：system:sys-param 等），此处清除历史脏数据。
-- 幂等：重复执行结果一致（DELETE ... LIKE，删不到即 0 行）。
-- ============================================================

DELETE FROM sys_role_permission WHERE menu_id LIKE 'system:sub-%';
