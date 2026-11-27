-- 50_employee_account_cleanup.sql
-- 员工系统账号清理 + username 唯一约束（2026-09-02 员工档案"用户名已被占用"修复配套）。
--
-- 背景：删除员工历史从不清理 sys_user，导致孤儿账号永久占用用户名；
--       演示数据重复插入导致 manager/sale01 等 username 各存在 3 条，
--       查重接口 selectOne 撞多条直接 500。
-- 本脚本与代码修复（deleteWithUser 联动清理 / save 回传 employeeId / 防双击）配套。
--
-- 幂等：全部为条件 DELETE，重复执行影响 0 行；索引 IF NOT EXISTS。
-- 注意：本脚本含受控 DELETE（经用户批准的清理操作），范围严格限定：
--       孤儿账号（employee_id 指向已删员工）/ 重复 username 收敛（每组保留 user_id 最小）/
--       tst_ 前缀测试账号 / testfix3_admin 残留账号，禁止扩大。

-- 1) 孤儿账号：employee_id 指向不存在的员工（含已删员工的残留账号）
DELETE FROM sys_user_role
WHERE user_id IN (
    SELECT u.user_id FROM sys_user u
    WHERE u.employee_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM employee e WHERE e.employee_id = u.employee_id)
);
DELETE FROM sys_user u
WHERE u.employee_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM employee e WHERE e.employee_id = u.employee_id);

-- 2) 重复 username 收敛：每组只保留 user_id 最小的一条（admin=1 天然保留）
DELETE FROM sys_user_role
WHERE user_id IN (
    SELECT u.user_id FROM sys_user u
    WHERE u.user_id > (SELECT MIN(u2.user_id) FROM sys_user u2 WHERE u2.username = u.username)
);
DELETE FROM sys_user u
WHERE u.user_id > (SELECT MIN(u2.user_id) FROM sys_user u2 WHERE u2.username = u.username);

-- 3) tst_ 前缀测试账号（user_id 54-57，employee_id 为空的独立测试账号）
DELETE FROM sys_user_role
WHERE user_id IN (SELECT user_id FROM sys_user WHERE username LIKE 'tst\_%' ESCAPE '\');
DELETE FROM sys_user WHERE username LIKE 'tst\_%' ESCAPE '\';

-- 4) 历史测试残留账号（乱码 real_name，无员工关联）
DELETE FROM sys_user_role
WHERE user_id IN (SELECT user_id FROM sys_user WHERE username = 'testfix3_admin');
DELETE FROM sys_user WHERE username = 'testfix3_admin';

-- 5) username 唯一索引（上述清理后建，防重复账号再次产生；
--    若目标库存在本脚本未覆盖的重复数据，建索引失败会中止部署，属有意防护）
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_username ON sys_user (username);
