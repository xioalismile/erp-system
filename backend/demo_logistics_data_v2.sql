SET client_encoding = 'UTF8';

-- ============================================================
-- 物流看板 + 可编辑 演示数据
-- 关键点：
--   1. 状态为「草稿」，允许前端编辑
--   2. 填充所有必填关联字段(customer_id/warehouse_id/currency_id/saler_id/org_code)
--   3. 写入 1 条 sale_delivery_item 明细行(有真实物料/单位)
--   4. 有 tracking_no/carrier 可展示在物流看板
-- ============================================================

-- 取一条现有物料和单位作演示
-- (material_id 最小的可用物料)
\set MATERIAL_ID (SELECT material_id FROM material WHERE is_active = 1 OR TRUE ORDER BY material_id LIMIT 1 OFFSET 0)
\set UNIT_ID     (SELECT unit_id FROM unit WHERE unit_id IS NOT NULL ORDER BY unit_id LIMIT 1)

-- ============ 1. 删除上次插入的残留演示单(如果存在，使用DELETE非级联避免依赖明细表结构) ============
DELETE FROM sale_delivery_item WHERE delivery_id IN (
  SELECT delivery_id FROM sale_delivery WHERE delivery_no LIKE 'DEMO%'
);
DELETE FROM sale_delivery WHERE delivery_no LIKE 'DEMO%';

-- ============ 2. 插入 5 条出库单头(草稿状态，允许编辑) ============
INSERT INTO sale_delivery (
  delivery_no, delivery_date, customer_id,
  contact_person, contact_phone, contact_address,
  warehouse_id, currency_id, saler_id, org_code,
  status, approval_status,
  carrier, tracking_no, express_company, express_no,
  remark, created_by, updated_by, created_at, updated_at
) VALUES
('DEMO000001', CURRENT_DATE, 2,
 '张经理', '13800138001', '上海市浦东新区张江高科技园区',
 1, 1, 1, 'DEMO_ORG',
 '草稿', '待审核',
 'SF', 'SF1234567890', '顺丰速运', 'SF1234567890',
 '物流看板演示数据 - 草稿可编辑 - 顺丰',
 'admin', 'admin', NOW(), NOW()),

('DEMO000002', CURRENT_DATE, 3,
 '李总', '13900139002', '北京市海淀区中关村科技园',
 1, 1, 2, 'DEMO_ORG',
 '草稿', '待审核',
 'ZTO', '75812345678901', '中通快递', '75812345678901',
 '物流看板演示数据 - 草稿可编辑 - 中通',
 'admin', 'admin', NOW(), NOW()),

('DEMO000003', CURRENT_DATE - 1, 4,
 '王工', '13700137003', '深圳市南山区科技园',
 1, 1, 3, 'DEMO_ORG',
 '草稿', '待审核',
 'YTO', 'YT1234567890123', '圆通速递', 'YT1234567890123',
 '物流看板演示数据 - 草稿可编辑 - 圆通',
 'admin', 'admin', NOW(), NOW()),

('DEMO000004', CURRENT_DATE - 1, 2,
 '张经理', '13800138001', '上海市浦东新区张江高科技园区',
 1, 1, 4, 'DEMO_ORG',
 '草稿', '待审核',
 'YD', '43211234567890', '韵达快递', '43211234567890',
 '物流看板演示数据 - 草稿可编辑 - 韵达',
 'admin', 'admin', NOW(), NOW()),

('DEMO000005', CURRENT_DATE - 2, 3,
 '李总', '13900139002', '北京市海淀区中关村科技园',
 1, 1, 5, 'DEMO_ORG',
 '草稿', '待审核',
 'JD', 'JD12345678901', '京东物流', 'JD12345678901',
 '物流看板演示数据 - 草稿可编辑 - 京东',
 'admin', 'admin', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- ============ 3. 为每条出库单写入 1 条明细行(简单 1行物料) ============
INSERT INTO sale_delivery_item (
  delivery_id, line_no, material_id, material_code, material_name,
  unit_id, quantity, unit_price, tax_rate,
  amount, tax_amount, net_amount,
  discount_rate, discount_amount, tax_type, stock_id,
  remark, created_by, updated_by, created_at, updated_at
)
SELECT
  d.delivery_id,
  1 as line_no,
  (SELECT material_id FROM material ORDER BY material_id LIMIT 1) as material_id,
  (SELECT material_code FROM material ORDER BY material_id LIMIT 1) as material_code,
  (SELECT material_name FROM material ORDER BY material_id LIMIT 1) as material_name,
  (SELECT unit_id FROM unit ORDER BY unit_id LIMIT 1) as unit_id,
  10.0000 as quantity,
  100.0000 as unit_price,
  13.0000 as tax_rate,
  1130.00 as amount,
  130.00 as tax_amount,
  1000.00 as net_amount,
  0.0000 as discount_rate,
  0.00 as discount_amount,
  2 as tax_type,
  1 as stock_id,
  '演示明细行（可删除/修改/新增）' as remark,
  'admin' as created_by,
  'admin' as updated_by,
  NOW() as created_at,
  NOW() as updated_at
FROM sale_delivery d
WHERE d.delivery_no BETWEEN 'DEMO000001' AND 'DEMO000005'
ON CONFLICT DO NOTHING;

-- ============ 4. 回写头表的合计金额(与明细1条一致) ============
UPDATE sale_delivery
SET total_amount  = 1130.00,
    tax_amount    = 130.00,
    net_amount    = 1000.00,
    discount_rate = 0.0000,
    discount_amount = 0.00,
    updated_at    = NOW()
WHERE delivery_no BETWEEN 'DEMO000001' AND 'DEMO000005';
