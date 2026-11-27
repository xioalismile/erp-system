-- 物流看板演示数据：5条已审核出库单（不同快递公司、不同物流单号）
-- 看板查询条件：status='已审核' AND tracking_no IS NOT NULL AND tracking_no <> ''

INSERT INTO sale_delivery (
  delivery_no, delivery_date, customer_id,
  contact_person, contact_phone, contact_address,
  warehouse_id, status, approval_status,
  carrier, tracking_no, express_company, express_no,
  total_amount, net_amount, remark,
  created_by, updated_by, created_at, updated_at, approved_at, approved_by
) VALUES
-- 1. 顺丰速运 - 在途
('DN20260814001', '2026-08-14', 2,
 '张经理', '13800138001', '上海市浦东新区张江高科技园区',
 1, '已审核', '已通过',
 '顺丰速运', 'SF1234567890', '顺丰速运', 'SF1234567890',
 12800.00, 12800.00, '物流看板演示-顺丰',
 'admin', 'admin', NOW(), NOW(), NOW(), 'admin'),

-- 2. 中通快递 - 在途
('DN20260814002', '2026-08-14', 3,
 '李总', '13900139002', '北京市海淀区中关村科技园',
 1, '已审核', '已通过',
 '中通快递', '75812345678901', '中通快递', '75812345678901',
 8650.00, 8650.00, '物流看板演示-中通',
 'admin', 'admin', NOW(), NOW(), NOW(), 'admin'),

-- 3. 圆通速递 - 在途
('DN20260814003', '2026-08-13', 4,
 '王工', '13700137003', '深圳市南山区科技园',
 1, '已审核', '已通过',
 '圆通速递', 'YT1234567890123', '圆通速递', 'YT1234567890123',
 23400.00, 23400.00, '物流看板演示-圆通',
 'admin', 'admin', NOW(), NOW(), NOW(), 'admin'),

-- 4. 韵达快递 - 在途
('DN20260814004', '2026-08-13', 2,
 '张经理', '13800138001', '上海市浦东新区张江高科技园区',
 1, '已审核', '已通过',
 '韵达快递', '43211234567890', '韵达快递', '43211234567890',
 5600.00, 5600.00, '物流看板演示-韵达',
 'admin', 'admin', NOW(), NOW(), NOW(), 'admin'),

-- 5. 京东物流 - 在途
('DN20260814005', '2026-08-12', 3,
 '李总', '13900139002', '北京市海淀区中关村科技园',
 1, '已审核', '已通过',
 '京东物流', 'JD12345678901', '京东物流', 'JD12345678901',
 18900.00, 18900.00, '物流看板演示-京东',
 'admin', 'admin', NOW(), NOW(), NOW(), 'admin')

ON CONFLICT DO NOTHING;
