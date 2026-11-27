-- 第一阶段：核心业务闭环完善 - 数据库表结构更新
-- 任务1.1：状态机与行结案机制
-- 任务1.2：数量勾稽体系

-- ==================== 销售订单相关 ====================

-- 销售订单主表增加结案状态
ALTER TABLE sale_order ADD COLUMN `close_status` TINYINT DEFAULT 0 COMMENT '结案状态: 0-未结案, 1-已结案';

-- 销售订单明细表增加勾稽字段
ALTER TABLE sale_order_item 
  ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT '源单ID',
  ADD COLUMN `source_item_id` BIGINT DEFAULT NULL COMMENT '源明细ID',
  ADD COLUMN `source_type` VARCHAR(30) DEFAULT NULL COMMENT '源单类型',
  ADD COLUMN `detail_close_status` TINYINT DEFAULT 0 COMMENT '行结案状态: 0-未结案, 1-已结案',
  ADD COLUMN `turned_output_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '已转出库数量',
  ADD COLUMN `turned_return_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '已转退货数量',
  ADD COLUMN `turned_invoice_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '已转开票数量';

-- 销售出库单主表增加源单ID
ALTER TABLE sale_delivery ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT '源单ID';

-- 销售出库明细表增加勾稽字段
ALTER TABLE sale_delivery_item 
  ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT '源单ID',
  ADD COLUMN `source_item_id` BIGINT DEFAULT NULL COMMENT '源明细ID',
  ADD COLUMN `source_type` VARCHAR(30) DEFAULT NULL COMMENT '源单类型';

-- 销售退货明细表增加勾稽字段
ALTER TABLE sale_return_item 
  ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT '源单ID',
  ADD COLUMN `source_item_id` BIGINT DEFAULT NULL COMMENT '源明细ID',
  ADD COLUMN `source_type` VARCHAR(30) DEFAULT NULL COMMENT '源单类型';

-- 销售发票明细表增加勾稽字段
ALTER TABLE sale_invoice_item 
  ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT '源单ID',
  ADD COLUMN `source_item_id` BIGINT DEFAULT NULL COMMENT '源明细ID',
  ADD COLUMN `source_type` VARCHAR(30) DEFAULT NULL COMMENT '源单类型';

-- ==================== 采购订单相关 ====================

-- 采购订单主表增加结案状态
ALTER TABLE purchase_order ADD COLUMN `close_status` TINYINT DEFAULT 0 COMMENT '结案状态: 0-未结案, 1-已结案';

-- 采购订单明细表增加勾稽字段
ALTER TABLE purchase_order_item 
  ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT '源单ID',
  ADD COLUMN `source_item_id` BIGINT DEFAULT NULL COMMENT '源明细ID',
  ADD COLUMN `source_type` VARCHAR(30) DEFAULT NULL COMMENT '源单类型',
  ADD COLUMN `detail_close_status` TINYINT DEFAULT 0 COMMENT '行结案状态: 0-未结案, 1-已结案',
  ADD COLUMN `turned_input_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '已转入库数量',
  ADD COLUMN `turned_return_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '已转退货数量';

-- 采购入库单主表增加源单ID
ALTER TABLE purchase_inbound ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT '源单ID';

-- 采购入库明细表增加勾稽字段
ALTER TABLE purchase_inbound_item 
  ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT '源单ID',
  ADD COLUMN `source_item_id` BIGINT DEFAULT NULL COMMENT '源明细ID',
  ADD COLUMN `source_type` VARCHAR(30) DEFAULT NULL COMMENT '源单类型';

-- 采购退货明细表增加勾稽字段
ALTER TABLE purchase_return_item 
  ADD COLUMN `source_id` BIGINT DEFAULT NULL COMMENT '源单ID',
  ADD COLUMN `source_item_id` BIGINT DEFAULT NULL COMMENT '源明细ID',
  ADD COLUMN `source_type` VARCHAR(30) DEFAULT NULL COMMENT '源单类型';

-- ==================== 生产加工单相关 ====================

-- 生产加工单主表增加结案状态
ALTER TABLE work_order ADD COLUMN `close_status` TINYINT DEFAULT 0 COMMENT '结案状态: 0-未结案, 1-已结案';

-- ==================== 合同相关 ====================

-- 销售合同主表增加结案状态
ALTER TABLE sale_contract ADD COLUMN `close_status` TINYINT DEFAULT 0 COMMENT '结案状态: 0-未结案, 1-已结案';

-- 采购合同主表增加结案状态
ALTER TABLE purchase_contract ADD COLUMN `close_status` TINYINT DEFAULT 0 COMMENT '结案状态: 0-未结案, 1-已结案';

-- ==================== 金额精度字段（多币别） ====================

-- 销售订单增加本位币金额字段
ALTER TABLE sale_order 
  ADD COLUMN `base_total_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币总金额',
  ADD COLUMN `base_tax_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币税额',
  ADD COLUMN `base_net_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币净额';

-- 销售订单明细增加本位币字段
ALTER TABLE sale_order_item 
  ADD COLUMN `base_unit_price` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币单价',
  ADD COLUMN `base_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币金额',
  ADD COLUMN `base_tax_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币税额',
  ADD COLUMN `base_net_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币净额';

-- 销售出库增加本位币金额字段
ALTER TABLE sale_delivery 
  ADD COLUMN `base_total_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币总金额',
  ADD COLUMN `base_tax_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币税额',
  ADD COLUMN `base_net_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币净额';

-- 销售出库明细增加本位币字段
ALTER TABLE sale_delivery_item 
  ADD COLUMN `base_unit_price` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币单价',
  ADD COLUMN `base_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币金额',
  ADD COLUMN `base_tax_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币税额',
  ADD COLUMN `base_net_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币净额';

-- 采购订单增加本位币金额字段
ALTER TABLE purchase_order 
  ADD COLUMN `base_total_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币总金额',
  ADD COLUMN `base_tax_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币税额',
  ADD COLUMN `base_net_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币净额';

-- 采购订单明细增加本位币字段
ALTER TABLE purchase_order_item 
  ADD COLUMN `base_unit_price` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币单价',
  ADD COLUMN `base_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币金额',
  ADD COLUMN `base_tax_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币税额',
  ADD COLUMN `base_net_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币净额';

-- 采购入库增加本位币金额字段
ALTER TABLE purchase_inbound 
  ADD COLUMN `base_total_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币总金额',
  ADD COLUMN `base_tax_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币税额',
  ADD COLUMN `base_net_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币净额';

-- 采购入库明细增加本位币字段
ALTER TABLE purchase_inbound_item 
  ADD COLUMN `base_unit_price` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币单价',
  ADD COLUMN `base_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币金额',
  ADD COLUMN `base_tax_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币税额',
  ADD COLUMN `base_net_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '本位币净额';

-- ==================== 索引优化 ====================

-- 销售订单明细索引
CREATE INDEX idx_sale_order_item_source ON sale_order_item(source_id, source_item_id);
CREATE INDEX idx_sale_order_item_close ON sale_order_item(order_id, detail_close_status);

-- 销售出库明细索引
CREATE INDEX idx_sale_delivery_item_source ON sale_delivery_item(source_id, source_item_id);

-- 采购订单明细索引
CREATE INDEX idx_purchase_order_item_source ON purchase_order_item(source_id, source_item_id);
CREATE INDEX idx_purchase_order_item_close ON purchase_order_item(order_id, detail_close_status);

-- 采购入库明细索引
CREATE INDEX idx_purchase_inbound_item_source ON purchase_inbound_item(source_id, source_item_id);
