-- ============================================================
-- ERP系统第一阶段完善 - 数据库表结构更新脚本
-- 更新日期: 2025-07-15
-- ============================================================

-- ==================== 销售订单相关 ====================

-- sale_order 表新增字段
ALTER TABLE sale_order
ADD COLUMN close_status INT DEFAULT 0 COMMENT '结案状态: 0-未结案, 1-已结案',
ADD COLUMN base_total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币金额(折前)',
ADD COLUMN base_tax_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币税额',
ADD COLUMN base_net_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币价税合计';

-- sale_order_item 表新增字段
ALTER TABLE sale_order_item
ADD COLUMN source_id BIGINT COMMENT '源单ID',
ADD COLUMN source_item_id BIGINT COMMENT '源单明细ID',
ADD COLUMN source_type VARCHAR(20) COMMENT '源单类型: sell/buy',
ADD COLUMN detail_close_status INT DEFAULT 0 COMMENT '行结案状态: 0-未结案, 1-已结案',
ADD COLUMN turned_output_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '已转出库数量',
ADD COLUMN turned_return_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '已转退货数量',
ADD COLUMN turned_invoice_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '已转开票数量',
ADD COLUMN tax_type INT DEFAULT 2 COMMENT '计税方式: 1-价内税, 2-价外税';

-- ==================== 销售出库单相关 ====================

-- sale_delivery 表新增字段
ALTER TABLE sale_delivery
ADD COLUMN close_status INT DEFAULT 0 COMMENT '结案状态: 0-未结案, 1-已结案',
ADD COLUMN base_total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币金额(折前)',
ADD COLUMN base_tax_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币税额',
ADD COLUMN base_net_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币价税合计';

-- sale_delivery_item 表新增字段
ALTER TABLE sale_delivery_item
ADD COLUMN source_id BIGINT COMMENT '源单ID',
ADD COLUMN source_item_id BIGINT COMMENT '源单明细ID',
ADD COLUMN source_type VARCHAR(20) COMMENT '源单类型: sell/buy',
ADD COLUMN tax_type INT DEFAULT 2 COMMENT '计税方式: 1-价内税, 2-价外税';

-- ==================== 销售退货单相关 ====================

-- sale_return 表新增字段
ALTER TABLE sale_return
ADD COLUMN close_status INT DEFAULT 0 COMMENT '结案状态: 0-未结案, 1-已结案',
ADD COLUMN base_total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币金额(折前)',
ADD COLUMN base_tax_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币税额',
ADD COLUMN base_net_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币价税合计';

-- sale_return_item 表新增字段
ALTER TABLE sale_return_item
ADD COLUMN source_id BIGINT COMMENT '源单ID',
ADD COLUMN source_item_id BIGINT COMMENT '源单明细ID',
ADD COLUMN source_type VARCHAR(20) COMMENT '源单类型: sell/buy',
ADD COLUMN tax_type INT DEFAULT 2 COMMENT '计税方式: 1-价内税, 2-价外税';

-- ==================== 销售发票相关 ====================

-- sale_invoice 表新增字段
ALTER TABLE sale_invoice
ADD COLUMN close_status INT DEFAULT 0 COMMENT '结案状态: 0-未结案, 1-已结案',
ADD COLUMN base_total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币金额(折前)',
ADD COLUMN base_tax_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币税额',
ADD COLUMN base_net_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币价税合计';

-- sale_invoice_item 表新增字段
ALTER TABLE sale_invoice_item
ADD COLUMN source_id BIGINT COMMENT '源单ID',
ADD COLUMN source_item_id BIGINT COMMENT '源单明细ID',
ADD COLUMN source_type VARCHAR(20) COMMENT '源单类型: sell/buy',
ADD COLUMN tax_type INT DEFAULT 2 COMMENT '计税方式: 1-价内税, 2-价外税';

-- ==================== 采购订单相关 ====================

-- purchase_order 表新增字段
ALTER TABLE purchase_order
ADD COLUMN close_status INT DEFAULT 0 COMMENT '结案状态: 0-未结案, 1-已结案',
ADD COLUMN base_total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币金额(折前)',
ADD COLUMN base_tax_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币税额',
ADD COLUMN base_net_amount DECIMAL(18,2) DEFAULT 0 COMMENT '本位币价税合计';

-- purchase_order_item 表新增字段
ALTER TABLE purchase_order_item
ADD COLUMN source_id BIGINT COMMENT '源单ID',
ADD COLUMN source_item_id BIGINT COMMENT '源单明细ID',
ADD COLUMN source_type VARCHAR(20) COMMENT '源单类型: sell/buy',
ADD COLUMN detail_close_status INT DEFAULT 0 COMMENT '行结案状态: 0-未结案, 1-已结案',
ADD COLUMN turned_input_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '已转入库数量',
ADD COLUMN turned_return_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '已转退货数量',
ADD COLUMN tax_type INT DEFAULT 2 COMMENT '计税方式: 1-价内税, 2-价外税';

-- ==================== 采购入库单相关 ====================

-- purchase_inbound_item 表新增字段
ALTER TABLE purchase_inbound_item
ADD COLUMN source_id BIGINT COMMENT '源单ID',
ADD COLUMN source_item_id BIGINT COMMENT '源单明细ID',
ADD COLUMN source_type VARCHAR(20) COMMENT '源单类型: sell/buy';

-- ==================== 索引优化 ====================

-- 销售订单明细勾稽索引
CREATE INDEX idx_sale_order_item_source ON sale_order_item(source_id, source_item_id);

-- 销售出库单明细勾稽索引
CREATE INDEX idx_sale_delivery_item_source ON sale_delivery_item(source_id, source_item_id);

-- 销售退货单明细勾稽索引
CREATE INDEX idx_sale_return_item_source ON sale_return_item(source_id, source_item_id);

-- 销售发票明细勾稽索引
CREATE INDEX idx_sale_invoice_item_source ON sale_invoice_item(source_id, source_item_id);

-- 采购订单明细勾稽索引
CREATE INDEX idx_purchase_order_item_source ON purchase_order_item(source_id, source_item_id);

-- 采购入库单明细勾稽索引
CREATE INDEX idx_purchase_inbound_item_source ON purchase_inbound_item(source_id, source_item_id);

-- ==================== 初始数据更新 ====================

-- 更新现有数据的计税方式为默认值(价外税)
UPDATE sale_order_item SET tax_type = 2 WHERE tax_type IS NULL;
UPDATE sale_delivery_item SET tax_type = 2 WHERE tax_type IS NULL;
UPDATE sale_return_item SET tax_type = 2 WHERE tax_type IS NULL;
UPDATE sale_invoice_item SET tax_type = 2 WHERE tax_type IS NULL;
UPDATE purchase_order_item SET tax_type = 2 WHERE tax_type IS NULL;

-- 更新现有数据的结案状态为默认值(未结案)
UPDATE sale_order SET close_status = 0 WHERE close_status IS NULL;
UPDATE sale_delivery SET close_status = 0 WHERE close_status IS NULL;
UPDATE sale_return SET close_status = 0 WHERE close_status IS NULL;
UPDATE sale_invoice SET close_status = 0 WHERE close_status IS NULL;
UPDATE purchase_order SET close_status = 0 WHERE close_status IS NULL;

-- 更新现有数据的行结案状态为默认值(未结案)
UPDATE sale_order_item SET detail_close_status = 0 WHERE detail_close_status IS NULL;
UPDATE purchase_order_item SET detail_close_status = 0 WHERE detail_close_status IS NULL;

-- 更新现有数据的勾稽数量为默认值(0)
UPDATE sale_order_item SET turned_output_quantity = 0 WHERE turned_output_quantity IS NULL;
UPDATE sale_order_item SET turned_return_quantity = 0 WHERE turned_return_quantity IS NULL;
UPDATE sale_order_item SET turned_invoice_quantity = 0 WHERE turned_invoice_quantity IS NULL;
UPDATE purchase_order_item SET turned_input_quantity = 0 WHERE turned_input_quantity IS NULL;
UPDATE purchase_order_item SET turned_return_quantity = 0 WHERE turned_return_quantity IS NULL;

-- 更新现有数据的本位币金额(默认等于原金额)
UPDATE sale_order SET 
    base_total_amount = COALESCE(total_amount, 0),
    base_tax_amount = COALESCE(tax_amount, 0),
    base_net_amount = COALESCE(net_amount, 0)
WHERE base_total_amount IS NULL;

UPDATE sale_delivery SET 
    base_total_amount = COALESCE(total_amount, 0),
    base_tax_amount = COALESCE(tax_amount, 0),
    base_net_amount = COALESCE(net_amount, 0)
WHERE base_total_amount IS NULL;

UPDATE sale_return SET 
    base_total_amount = COALESCE(total_amount, 0),
    base_tax_amount = COALESCE(tax_amount, 0),
    base_net_amount = COALESCE(net_amount, 0)
WHERE base_total_amount IS NULL;

UPDATE sale_invoice SET 
    base_total_amount = COALESCE(total_amount, 0),
    base_tax_amount = COALESCE(tax_amount, 0),
    base_net_amount = COALESCE(net_amount, 0)
WHERE base_total_amount IS NULL;

UPDATE purchase_order SET 
    base_total_amount = COALESCE(total_amount, 0),
    base_tax_amount = COALESCE(tax_amount, 0),
    base_net_amount = COALESCE(net_amount, 0)
WHERE base_total_amount IS NULL;

-- ============================================================
-- 更新完成
-- ============================================================