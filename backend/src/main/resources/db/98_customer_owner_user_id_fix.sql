-- 98：客户「归属人」脏数据修正 —— 把被写成员工ID的 owner_user_id 还原成用户ID（幂等）
--
-- 【背景】客户归属列 owner_user_id 的口径是「用户ID」（新建客户/私海导入/领取公海都写 sys_user.user_id），
--         但前端「分配销售员」下拉取的是员工列表、提交的是「员工ID」，历史代码 batchAssign /
--         transferByResignation 把这个员工ID直接写进了 owner_user_id，造成两类后果：
--           ① 被分配人在自己的「我的私海」里看不到这个客户（列表按用户ID匹配）；
--           ② 私海卡片与私海列表数字打架（卡片按员工ID匹配、列表按用户ID匹配）；
--           ③ 若"员工ID 恰好等于另一个用户的用户ID"，该客户会出现在别人的私海里（越权可见）。
--         代码侧已改为「员工ID → 用户ID 转换后写库」（CustomerServiceImpl.batchAssign /
--         transferByResignation / claimFromPool），本脚本负责修正 121 等已上线环境的存量脏数据。
--
-- 【判定规则（保守）】同时满足才修正：
--         ① owner_user_id = assigned_sales_id（批量分配/离职转移会同时写这两列，这是这两个入口的特征）；
--         ② 该值是存在的员工ID；
--         ③ 该值「不是」存在的用户ID（避免"员工ID 恰好等于某用户ID"的歧义 → 只统计、不修改，人工复核）；
--         ④ 该员工ID 在 sys_user 里唯一对应一个账号。
--
-- 【幂等】修正后 owner_user_id 不再等于 assigned_sales_id（或已是用户ID），重跑不产生任何变更。
-- 【范围】只 UPDATE customer.owner_user_id 一列，不碰金额、状态、归属业务字段。

DO $$
DECLARE
    ambiguous_count INT;
    fixed_count INT;
BEGIN
    -- 疑似歧义行（员工ID 与 用户ID 同号）：本次不动，留日志人工复核
    SELECT count(*) INTO ambiguous_count
    FROM customer c
    WHERE c.owner_user_id IS NOT NULL
      AND c.owner_user_id = c.assigned_sales_id
      AND EXISTS (SELECT 1 FROM employee e WHERE e.employee_id = c.owner_user_id)
      AND EXISTS (SELECT 1 FROM sys_user u WHERE u.user_id = c.owner_user_id);
    RAISE NOTICE '[98] 员工ID与用户ID同号的歧义行（本次不修改，需人工复核）: %', ambiguous_count;

    UPDATE customer c
    SET owner_user_id = u.user_id
    FROM sys_user u
    WHERE c.owner_user_id IS NOT NULL
      AND c.owner_user_id = c.assigned_sales_id
      AND u.employee_id = c.owner_user_id
      AND u.user_id <> c.owner_user_id
      AND NOT EXISTS (SELECT 1 FROM sys_user u2 WHERE u2.user_id = c.owner_user_id)
      AND (SELECT count(*) FROM sys_user u3 WHERE u3.employee_id = c.owner_user_id) = 1;
    GET DIAGNOSTICS fixed_count = ROW_COUNT;
    RAISE NOTICE '[98] 已修正 owner_user_id（员工ID → 用户ID）行数: %', fixed_count;
END $$;
