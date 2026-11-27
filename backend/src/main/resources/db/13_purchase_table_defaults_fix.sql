-- ===========================================================================
-- 采购模块表字段默认值补齐脚本
-- 解决: 新实体类与旧表结构不兼容问题, 为所有NOT NULL且无默认值的字段添加默认值
-- ===========================================================================

-- ===== 采购申请单 purchase_request =====
ALTER TABLE purchase_request ALTER COLUMN currency_id SET DEFAULT 1;
ALTER TABLE purchase_request ALTER COLUMN created_by SET DEFAULT 'admin';

-- ===== 采购订单 purchase_order =====
ALTER TABLE purchase_order ALTER COLUMN currency_id SET DEFAULT 1;
ALTER TABLE purchase_order ALTER COLUMN created_by SET DEFAULT 'admin';

-- ===== 采购入库单 purchase_receipt =====
ALTER TABLE purchase_receipt ALTER COLUMN supplier_id DROP NOT NULL;
ALTER TABLE purchase_receipt ALTER COLUMN created_by SET DEFAULT 'admin';

-- ===== 采购退货单 purchase_return =====
ALTER TABLE purchase_return ALTER COLUMN supplier_id DROP NOT NULL;
ALTER TABLE purchase_return ALTER COLUMN created_by SET DEFAULT 'admin';

-- ===== 采购发票 purchase_invoice =====
ALTER TABLE purchase_invoice ALTER COLUMN supplier_id DROP NOT NULL;
ALTER TABLE purchase_invoice ALTER COLUMN created_by SET DEFAULT 'admin';
