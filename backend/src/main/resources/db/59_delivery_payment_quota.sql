-- ============================================================
-- 59_delivery_payment_quota.sql
-- 发货收款管控模式扩展：新增 quota（收多少款发多少货）
-- advance 语义调整为定金模式（收款达定金后不限量发货，不再依赖已下线的付款条件）
-- ============================================================

-- 1. 更新发货收款管控模式下拉选项（3档 → 4档）
UPDATE sys_config SET
  config_options = '[{"label":"不管控","value":"none"},{"label":"收多少款发多少货","value":"quota"},{"label":"全额收款才能发货","value":"full"},{"label":"定金模式（收款后允许多发）","value":"advance"}]',
  remark = 'none=不管控; quota=收多少款发多少货(累计发货净额不超过已收款); full=全额收款才能发货; advance=定金模式(收款达订单定金后不限量发货)'
WHERE config_key = 'sale.delivery.payment.control';
