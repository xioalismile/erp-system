-- 51_notification_route_fix.sql
-- 修正存量通知(sys_notification.route_path)的坏跳转路径，对齐前端真实路由表
-- 根因：MyDashboardHelper 历史版本生成过以下前端不存在的路径，点击后空白页/403页
--   /inventory/transfers/{id}   -> 前端真实路由 /inventory/stock-transfers/edit/{id}
--   /inventory/checks/{id}      -> 前端真实路由 /inventory/stock-checks/edit/{id}
--   /finance/ar/{id}            -> 前端无详情页，改跳 /finance/report/ar-summary
--   /finance/ap/{id}            -> 前端无详情页，改跳 /finance/report/ap-summary
--   /sample/{id}                -> 前端真实路由 /sample/edit/{id}
--   /freesample/{id}            -> 前端真实路由 /freesample/edit/{id}
--   /production/routings/{id}   -> 前端只有列表页 /production/routings
-- 幂等：所有语句带 WHERE 条件，第二遍执行零命中零变更（固定值基线收敛，允许 UPDATE）

UPDATE sys_notification
SET route_path = REPLACE(route_path, '/inventory/transfers/', '/inventory/stock-transfers/edit/')
WHERE route_path LIKE '%/inventory/transfers/%';

UPDATE sys_notification
SET route_path = REPLACE(route_path, '/inventory/checks/', '/inventory/stock-checks/edit/')
WHERE route_path LIKE '%/inventory/checks/%';

UPDATE sys_notification
SET route_path = '/finance/report/ar-summary'
WHERE route_path LIKE '%/finance/ar/%';

UPDATE sys_notification
SET route_path = '/finance/report/ap-summary'
WHERE route_path LIKE '%/finance/ap/%';

UPDATE sys_notification
SET route_path = REPLACE(route_path, '/sample/', '/sample/edit/')
WHERE route_path LIKE '/sample/%' AND route_path NOT LIKE '/sample/edit/%';

UPDATE sys_notification
SET route_path = REPLACE(route_path, '/freesample/', '/freesample/edit/')
WHERE route_path LIKE '/freesample/%' AND route_path NOT LIKE '/freesample/edit/%';

UPDATE sys_notification
SET route_path = '/production/routings'
WHERE route_path LIKE '%/production/routings/%';
