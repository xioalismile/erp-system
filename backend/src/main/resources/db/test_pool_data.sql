-- 准备公海池测试数据
-- 1. 将10个客户设为公海（pool_type=1, owner_user_id=NULL, pool_enter_date=now）
-- 2. 将10个客户分配给sale01（user_id=3, pool_type=0, owner_user_id=3）
-- 3. 将10个客户分配给admin（user_id=1, pool_type=0, owner_user_id=1）
-- 4. 剩余220个客户保持默认（pool_type=0, owner_user_id=NULL）

-- 先清空所有公海池状态
UPDATE customer SET pool_type = 0, owner_user_id = NULL, pool_enter_date = NULL WHERE deleted = 0;

-- 1. 取前10条客户设为公海（customer_id 1-10）
UPDATE customer
SET pool_type = 1,
    owner_user_id = NULL,
    pool_enter_date = NOW(),
    last_follow_up_date = CURRENT_DATE - INTERVAL '60 day'
WHERE customer_id BETWEEN 1 AND 10 AND deleted = 0;

-- 2. 将customer_id 11-20分配给sale01（user_id=3）
UPDATE customer
SET pool_type = 0,
    owner_user_id = 3,
    assigned_sales_id = 3,
    pool_enter_date = NULL,
    last_follow_up_date = CURRENT_DATE - INTERVAL '5 day'
WHERE customer_id BETWEEN 11 AND 20 AND deleted = 0;

-- 3. 将customer_id 21-30分配给admin（user_id=1）
UPDATE customer
SET pool_type = 0,
    owner_user_id = 1,
    assigned_sales_id = 1,
    pool_enter_date = NULL,
    last_follow_up_date = CURRENT_DATE - INTERVAL '3 day'
WHERE customer_id BETWEEN 21 AND 30 AND deleted = 0;

-- 给公海客户更新客户名称便于识别
UPDATE customer SET customer_name = '【公海客户】' || customer_name WHERE customer_id BETWEEN 1 AND 10;
UPDATE customer SET customer_name = '【sale01私海】' || customer_name WHERE customer_id BETWEEN 11 AND 20;
UPDATE customer SET customer_name = '【admin私海】' || customer_name WHERE customer_id BETWEEN 21 AND 30;

-- 验证结果
SELECT
    CASE
        WHEN pool_type = 1 THEN '公海'
        WHEN owner_user_id = 1 THEN 'admin私海'
        WHEN owner_user_id = 3 THEN 'sale01私海'
        ELSE '未分配私海'
    END AS category,
    COUNT(*) AS cnt
FROM customer
WHERE deleted = 0
GROUP BY category
ORDER BY category;
