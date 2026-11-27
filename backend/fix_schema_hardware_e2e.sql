-- 五金行业端到端测试数据库修复脚本
-- 修复NOT NULL约束导致下推/创建失败的问题

-- 1. material_requisition_item.stock_id 改为可空
--    原因: service 已处理 null stockId 回退到 warehouseId
ALTER TABLE material_requisition_item ALTER COLUMN stock_id DROP NOT NULL;

-- 2. purchase_invoice.currency_id 改为可空
--    原因: PurchaseInvoice 实体无 currencyId 字段, push-to-invoice 无法设置
ALTER TABLE purchase_invoice ALTER COLUMN currency_id DROP NOT NULL;

-- 3. purchase_invoice.exchange_rate 改为可空
--    原因: PurchaseInvoice 实体无 exchangeRate 字段
ALTER TABLE purchase_invoice ALTER COLUMN exchange_rate DROP NOT NULL;

SELECT 'Schema fixes applied successfully' AS result;
