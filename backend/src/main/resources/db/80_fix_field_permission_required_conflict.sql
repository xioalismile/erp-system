-- 80 修复「必填但不可见 / 只读」的字段权限矛盾记录（幂等，可重复执行）
--
-- 背景（2026-09-15）：
--   业务表单口径统一为「界面上带 * 的字段 = 一定能填写 且 点"保存并返回"一定被校验」。
--   BaseEdit 的星号与校验已收敛到同一个判定函数（isFieldRequiredDisplay），
--   对 visible=0（隐藏）或 editable=0（只读）的字段不再打星、不再校验。
--   于是历史数据里 required=1 且 visible=0 / editable=0 的"矛盾配置"会让用户配的必填静默失效。
--
-- 处置口径：必填 ⇒ 可见 + 可编辑（与 SysRoleFieldPermissionServiceImpl 的入库兜底一致）。
--   本地实测：全部租户库该条件命中 0 行，本脚本为服务器环境的保险（防脏数据导致死锁/静默失效）。
UPDATE sys_role_field_permission
SET visible  = 1,
    editable = 1
WHERE required = 1
  AND (visible = 0 OR editable = 0);
