-- ============================================================================
-- 智能ERP系统 - 单据状态统一升级脚本
-- 模块：全部业务单据（销售、采购、库存、生产、委外、财务、样品、免费样品）
-- 说明：为所有单据主表补齐结案状态、作废状态相关字段
--       双终态设计：已结案(close_status=1) + 已作废(status映射为7)
--       所有ALTER使用 IF NOT EXISTS，已存在的列自动跳过不报错
-- ============================================================================

-- ============================================================================
-- 注意：此脚本需要在 租户业务库（erp_tenant_xxx） 中执行
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 一、销售模块（6张）
-- ----------------------------------------------------------------------------

-- 1.1 销售报价单 sale_quote
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE sale_quote ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN sale_quote.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN sale_quote.closed_by IS '结案人';
COMMENT ON COLUMN sale_quote.closed_at IS '结案时间';
COMMENT ON COLUMN sale_quote.closed_reason IS '结案原因';
COMMENT ON COLUMN sale_quote.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN sale_quote.voided_by IS '作废人';
COMMENT ON COLUMN sale_quote.voided_at IS '作废时间';
COMMENT ON COLUMN sale_quote.void_reason IS '作废原因';

-- 1.2 销售订单 sale_order（已有 close_status/closed_*，补齐 void_*）
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE sale_order ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN sale_order.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN sale_order.closed_by IS '结案人';
COMMENT ON COLUMN sale_order.closed_at IS '结案时间';
COMMENT ON COLUMN sale_order.closed_reason IS '结案原因';
COMMENT ON COLUMN sale_order.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN sale_order.voided_by IS '作废人';
COMMENT ON COLUMN sale_order.voided_at IS '作废时间';
COMMENT ON COLUMN sale_order.void_reason IS '作废原因';

-- 1.3 销售合同 sale_contract（已有 close_status/closed_*，补齐 void_*）
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE sale_contract ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN sale_contract.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN sale_contract.closed_by IS '结案人';
COMMENT ON COLUMN sale_contract.closed_at IS '结案时间';
COMMENT ON COLUMN sale_contract.closed_reason IS '结案原因';
COMMENT ON COLUMN sale_contract.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN sale_contract.voided_by IS '作废人';
COMMENT ON COLUMN sale_contract.voided_at IS '作废时间';
COMMENT ON COLUMN sale_contract.void_reason IS '作废原因';

-- 1.4 销售出库单 sale_delivery
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE sale_delivery ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN sale_delivery.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN sale_delivery.closed_by IS '结案人';
COMMENT ON COLUMN sale_delivery.closed_at IS '结案时间';
COMMENT ON COLUMN sale_delivery.closed_reason IS '结案原因';
COMMENT ON COLUMN sale_delivery.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN sale_delivery.voided_by IS '作废人';
COMMENT ON COLUMN sale_delivery.voided_at IS '作废时间';
COMMENT ON COLUMN sale_delivery.void_reason IS '作废原因';

-- 1.5 销售退货单 sale_return
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE sale_return ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN sale_return.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN sale_return.closed_by IS '结案人';
COMMENT ON COLUMN sale_return.closed_at IS '结案时间';
COMMENT ON COLUMN sale_return.closed_reason IS '结案原因';
COMMENT ON COLUMN sale_return.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN sale_return.voided_by IS '作废人';
COMMENT ON COLUMN sale_return.voided_at IS '作废时间';
COMMENT ON COLUMN sale_return.void_reason IS '作废原因';

-- 1.6 销售发票 sale_invoice
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE sale_invoice ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN sale_invoice.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN sale_invoice.closed_by IS '结案人';
COMMENT ON COLUMN sale_invoice.closed_at IS '结案时间';
COMMENT ON COLUMN sale_invoice.closed_reason IS '结案原因';
COMMENT ON COLUMN sale_invoice.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN sale_invoice.voided_by IS '作废人';
COMMENT ON COLUMN sale_invoice.voided_at IS '作废时间';
COMMENT ON COLUMN sale_invoice.void_reason IS '作废原因';

-- ----------------------------------------------------------------------------
-- 二、采购模块（6张）
-- ----------------------------------------------------------------------------

-- 2.1 采购申请单 purchase_request
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN purchase_request.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN purchase_request.closed_by IS '结案人';
COMMENT ON COLUMN purchase_request.closed_at IS '结案时间';
COMMENT ON COLUMN purchase_request.closed_reason IS '结案原因';
COMMENT ON COLUMN purchase_request.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN purchase_request.voided_by IS '作废人';
COMMENT ON COLUMN purchase_request.voided_at IS '作废时间';
COMMENT ON COLUMN purchase_request.void_reason IS '作废原因';

-- 2.2 采购订单 purchase_order（已有 close_status/closed_*，补齐 void_*）
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN purchase_order.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN purchase_order.closed_by IS '结案人';
COMMENT ON COLUMN purchase_order.closed_at IS '结案时间';
COMMENT ON COLUMN purchase_order.closed_reason IS '结案原因';
COMMENT ON COLUMN purchase_order.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN purchase_order.voided_by IS '作废人';
COMMENT ON COLUMN purchase_order.voided_at IS '作废时间';
COMMENT ON COLUMN purchase_order.void_reason IS '作废原因';

-- 2.3 采购合同 purchase_contract（已有 close_status/closed_*，补齐 void_*）
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE purchase_contract ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN purchase_contract.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN purchase_contract.closed_by IS '结案人';
COMMENT ON COLUMN purchase_contract.closed_at IS '结案时间';
COMMENT ON COLUMN purchase_contract.closed_reason IS '结案原因';
COMMENT ON COLUMN purchase_contract.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN purchase_contract.voided_by IS '作废人';
COMMENT ON COLUMN purchase_contract.voided_at IS '作废时间';
COMMENT ON COLUMN purchase_contract.void_reason IS '作废原因';

-- 2.4 采购入库单 purchase_receipt（已有 close_status/closed_*，补齐 void_*）
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN purchase_receipt.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN purchase_receipt.closed_by IS '结案人';
COMMENT ON COLUMN purchase_receipt.closed_at IS '结案时间';
COMMENT ON COLUMN purchase_receipt.closed_reason IS '结案原因';
COMMENT ON COLUMN purchase_receipt.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN purchase_receipt.voided_by IS '作废人';
COMMENT ON COLUMN purchase_receipt.voided_at IS '作废时间';
COMMENT ON COLUMN purchase_receipt.void_reason IS '作废原因';

-- 2.5 采购退货单 purchase_return（已有 close_status/closed_*，补齐 void_*）
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN purchase_return.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN purchase_return.closed_by IS '结案人';
COMMENT ON COLUMN purchase_return.closed_at IS '结案时间';
COMMENT ON COLUMN purchase_return.closed_reason IS '结案原因';
COMMENT ON COLUMN purchase_return.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN purchase_return.voided_by IS '作废人';
COMMENT ON COLUMN purchase_return.voided_at IS '作废时间';
COMMENT ON COLUMN purchase_return.void_reason IS '作废原因';

-- 2.6 采购发票 purchase_invoice（已有 close_status/closed_*，补齐 void_*）
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN purchase_invoice.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN purchase_invoice.closed_by IS '结案人';
COMMENT ON COLUMN purchase_invoice.closed_at IS '结案时间';
COMMENT ON COLUMN purchase_invoice.closed_reason IS '结案原因';
COMMENT ON COLUMN purchase_invoice.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN purchase_invoice.voided_by IS '作废人';
COMMENT ON COLUMN purchase_invoice.voided_at IS '作废时间';
COMMENT ON COLUMN purchase_invoice.void_reason IS '作废原因';

-- ----------------------------------------------------------------------------
-- 三、库存模块（5张）
-- ----------------------------------------------------------------------------

-- 3.1 库存调拨单 stock_transfer
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN stock_transfer.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN stock_transfer.closed_by IS '结案人';
COMMENT ON COLUMN stock_transfer.closed_at IS '结案时间';
COMMENT ON COLUMN stock_transfer.closed_reason IS '结案原因';
COMMENT ON COLUMN stock_transfer.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN stock_transfer.voided_by IS '作废人';
COMMENT ON COLUMN stock_transfer.voided_at IS '作废时间';
COMMENT ON COLUMN stock_transfer.void_reason IS '作废原因';

-- 3.2 库存盘点单 stock_check
ALTER TABLE stock_check ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE stock_check ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE stock_check ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE stock_check ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE stock_check ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE stock_check ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE stock_check ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE stock_check ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN stock_check.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN stock_check.closed_by IS '结案人';
COMMENT ON COLUMN stock_check.closed_at IS '结案时间';
COMMENT ON COLUMN stock_check.closed_reason IS '结案原因';
COMMENT ON COLUMN stock_check.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN stock_check.voided_by IS '作废人';
COMMENT ON COLUMN stock_check.voided_at IS '作废时间';
COMMENT ON COLUMN stock_check.void_reason IS '作废原因';

-- 3.3 领料出库单 material_requisition
ALTER TABLE material_requisition ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE material_requisition ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE material_requisition ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE material_requisition ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE material_requisition ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE material_requisition ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE material_requisition ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE material_requisition ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN material_requisition.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN material_requisition.closed_by IS '结案人';
COMMENT ON COLUMN material_requisition.closed_at IS '结案时间';
COMMENT ON COLUMN material_requisition.closed_reason IS '结案原因';
COMMENT ON COLUMN material_requisition.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN material_requisition.voided_by IS '作废人';
COMMENT ON COLUMN material_requisition.voided_at IS '作废时间';
COMMENT ON COLUMN material_requisition.void_reason IS '作废原因';

-- 3.4 其他入库单 other_receipt
ALTER TABLE other_receipt ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE other_receipt ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE other_receipt ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE other_receipt ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE other_receipt ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE other_receipt ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE other_receipt ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE other_receipt ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN other_receipt.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN other_receipt.closed_by IS '结案人';
COMMENT ON COLUMN other_receipt.closed_at IS '结案时间';
COMMENT ON COLUMN other_receipt.closed_reason IS '结案原因';
COMMENT ON COLUMN other_receipt.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN other_receipt.voided_by IS '作废人';
COMMENT ON COLUMN other_receipt.voided_at IS '作废时间';
COMMENT ON COLUMN other_receipt.void_reason IS '作废原因';

-- 3.5 其他出库单 other_issue
ALTER TABLE other_issue ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE other_issue ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE other_issue ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE other_issue ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE other_issue ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE other_issue ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE other_issue ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE other_issue ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN other_issue.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN other_issue.closed_by IS '结案人';
COMMENT ON COLUMN other_issue.closed_at IS '结案时间';
COMMENT ON COLUMN other_issue.closed_reason IS '结案原因';
COMMENT ON COLUMN other_issue.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN other_issue.voided_by IS '作废人';
COMMENT ON COLUMN other_issue.voided_at IS '作废时间';
COMMENT ON COLUMN other_issue.void_reason IS '作废原因';

-- ----------------------------------------------------------------------------
-- 四、生产模块（6张）
-- ----------------------------------------------------------------------------

-- 4.1 生产工单 production_order（已有 close_status/closed_*，补齐 void_*）
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE production_order ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN production_order.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN production_order.closed_by IS '结案人';
COMMENT ON COLUMN production_order.closed_at IS '结案时间';
COMMENT ON COLUMN production_order.closed_reason IS '结案原因';
COMMENT ON COLUMN production_order.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN production_order.voided_by IS '作废人';
COMMENT ON COLUMN production_order.voided_at IS '作废时间';
COMMENT ON COLUMN production_order.void_reason IS '作废原因';

-- 4.2 生产入库单 production_receipt
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE production_receipt ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN production_receipt.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN production_receipt.closed_by IS '结案人';
COMMENT ON COLUMN production_receipt.closed_at IS '结案时间';
COMMENT ON COLUMN production_receipt.closed_reason IS '结案原因';
COMMENT ON COLUMN production_receipt.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN production_receipt.voided_by IS '作废人';
COMMENT ON COLUMN production_receipt.voided_at IS '作废时间';
COMMENT ON COLUMN production_receipt.void_reason IS '作废原因';

-- 4.3 工序移交单 process_transfer
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE process_transfer ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN process_transfer.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN process_transfer.closed_by IS '结案人';
COMMENT ON COLUMN process_transfer.closed_at IS '结案时间';
COMMENT ON COLUMN process_transfer.closed_reason IS '结案原因';
COMMENT ON COLUMN process_transfer.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN process_transfer.voided_by IS '作废人';
COMMENT ON COLUMN process_transfer.voided_at IS '作废时间';
COMMENT ON COLUMN process_transfer.void_reason IS '作废原因';

-- 4.4 工序汇报单 operation_report
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN operation_report.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN operation_report.closed_by IS '结案人';
COMMENT ON COLUMN operation_report.closed_at IS '结案时间';
COMMENT ON COLUMN operation_report.closed_reason IS '结案原因';
COMMENT ON COLUMN operation_report.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN operation_report.voided_by IS '作废人';
COMMENT ON COLUMN operation_report.voided_at IS '作废时间';
COMMENT ON COLUMN operation_report.void_reason IS '作废原因';

-- 4.5 产品组装单 produce_assemble
ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE produce_assemble ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN produce_assemble.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN produce_assemble.closed_by IS '结案人';
COMMENT ON COLUMN produce_assemble.closed_at IS '结案时间';
COMMENT ON COLUMN produce_assemble.closed_reason IS '结案原因';
COMMENT ON COLUMN produce_assemble.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN produce_assemble.voided_by IS '作废人';
COMMENT ON COLUMN produce_assemble.voided_at IS '作废时间';
COMMENT ON COLUMN produce_assemble.void_reason IS '作废原因';

-- 4.6 产品拆卸单 produce_disassemble
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE produce_disassemble ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN produce_disassemble.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN produce_disassemble.closed_by IS '结案人';
COMMENT ON COLUMN produce_disassemble.closed_at IS '结案时间';
COMMENT ON COLUMN produce_disassemble.closed_reason IS '结案原因';
COMMENT ON COLUMN produce_disassemble.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN produce_disassemble.voided_by IS '作废人';
COMMENT ON COLUMN produce_disassemble.voided_at IS '作废时间';
COMMENT ON COLUMN produce_disassemble.void_reason IS '作废原因';

-- ----------------------------------------------------------------------------
-- 五、委外模块（5张）
-- ----------------------------------------------------------------------------

-- 5.1 委外订单 subcontract_order（已有 close_status/closed_*，补齐 void_*）
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE subcontract_order ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN subcontract_order.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN subcontract_order.closed_by IS '结案人';
COMMENT ON COLUMN subcontract_order.closed_at IS '结案时间';
COMMENT ON COLUMN subcontract_order.closed_reason IS '结案原因';
COMMENT ON COLUMN subcontract_order.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN subcontract_order.voided_by IS '作废人';
COMMENT ON COLUMN subcontract_order.voided_at IS '作废时间';
COMMENT ON COLUMN subcontract_order.void_reason IS '作废原因';

-- 5.2 委外领料单 subcontract_pick
ALTER TABLE subcontract_pick ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE subcontract_pick ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE subcontract_pick ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE subcontract_pick ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE subcontract_pick ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE subcontract_pick ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE subcontract_pick ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE subcontract_pick ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN subcontract_pick.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN subcontract_pick.closed_by IS '结案人';
COMMENT ON COLUMN subcontract_pick.closed_at IS '结案时间';
COMMENT ON COLUMN subcontract_pick.closed_reason IS '结案原因';
COMMENT ON COLUMN subcontract_pick.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN subcontract_pick.voided_by IS '作废人';
COMMENT ON COLUMN subcontract_pick.voided_at IS '作废时间';
COMMENT ON COLUMN subcontract_pick.void_reason IS '作废原因';

-- 5.3 委外补料单 subcontract_supplement
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN subcontract_supplement.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN subcontract_supplement.closed_by IS '结案人';
COMMENT ON COLUMN subcontract_supplement.closed_at IS '结案时间';
COMMENT ON COLUMN subcontract_supplement.closed_reason IS '结案原因';
COMMENT ON COLUMN subcontract_supplement.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN subcontract_supplement.voided_by IS '作废人';
COMMENT ON COLUMN subcontract_supplement.voided_at IS '作废时间';
COMMENT ON COLUMN subcontract_supplement.void_reason IS '作废原因';

-- 5.4 委外入库单 subcontract_inbound
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE subcontract_inbound ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN subcontract_inbound.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN subcontract_inbound.closed_by IS '结案人';
COMMENT ON COLUMN subcontract_inbound.closed_at IS '结案时间';
COMMENT ON COLUMN subcontract_inbound.closed_reason IS '结案原因';
COMMENT ON COLUMN subcontract_inbound.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN subcontract_inbound.voided_by IS '作废人';
COMMENT ON COLUMN subcontract_inbound.voided_at IS '作废时间';
COMMENT ON COLUMN subcontract_inbound.void_reason IS '作废原因';

-- 5.5 委外退货单 subcontract_return
ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE subcontract_return ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN subcontract_return.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN subcontract_return.closed_by IS '结案人';
COMMENT ON COLUMN subcontract_return.closed_at IS '结案时间';
COMMENT ON COLUMN subcontract_return.closed_reason IS '结案原因';
COMMENT ON COLUMN subcontract_return.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN subcontract_return.voided_by IS '作废人';
COMMENT ON COLUMN subcontract_return.voided_at IS '作废时间';
COMMENT ON COLUMN subcontract_return.void_reason IS '作废原因';

-- ----------------------------------------------------------------------------
-- 六、财务模块（11张）
-- ----------------------------------------------------------------------------

-- 6.1 收款单 cash_receipt
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE cash_receipt ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN cash_receipt.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN cash_receipt.closed_by IS '结案人';
COMMENT ON COLUMN cash_receipt.closed_at IS '结案时间';
COMMENT ON COLUMN cash_receipt.closed_reason IS '结案原因';
COMMENT ON COLUMN cash_receipt.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN cash_receipt.voided_by IS '作废人';
COMMENT ON COLUMN cash_receipt.voided_at IS '作废时间';
COMMENT ON COLUMN cash_receipt.void_reason IS '作废原因';

-- 6.2 付款单 cash_payment
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE cash_payment ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN cash_payment.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN cash_payment.closed_by IS '结案人';
COMMENT ON COLUMN cash_payment.closed_at IS '结案时间';
COMMENT ON COLUMN cash_payment.closed_reason IS '结案原因';
COMMENT ON COLUMN cash_payment.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN cash_payment.voided_by IS '作废人';
COMMENT ON COLUMN cash_payment.voided_at IS '作废时间';
COMMENT ON COLUMN cash_payment.void_reason IS '作废原因';

-- 6.3 资金调拨单 fund_transfer
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN fund_transfer.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN fund_transfer.closed_by IS '结案人';
COMMENT ON COLUMN fund_transfer.closed_at IS '结案时间';
COMMENT ON COLUMN fund_transfer.closed_reason IS '结案原因';
COMMENT ON COLUMN fund_transfer.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN fund_transfer.voided_by IS '作废人';
COMMENT ON COLUMN fund_transfer.voided_at IS '作废时间';
COMMENT ON COLUMN fund_transfer.void_reason IS '作废原因';

-- 6.4 预算单 budget
ALTER TABLE budget ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE budget ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE budget ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE budget ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE budget ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE budget ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE budget ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE budget ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN budget.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN budget.closed_by IS '结案人';
COMMENT ON COLUMN budget.closed_at IS '结案时间';
COMMENT ON COLUMN budget.closed_reason IS '结案原因';
COMMENT ON COLUMN budget.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN budget.voided_by IS '作废人';
COMMENT ON COLUMN budget.voided_at IS '作废时间';
COMMENT ON COLUMN budget.void_reason IS '作废原因';

-- 6.5 采购预付款单 purchase_pre_payment
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE purchase_pre_payment ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN purchase_pre_payment.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN purchase_pre_payment.closed_by IS '结案人';
COMMENT ON COLUMN purchase_pre_payment.closed_at IS '结案时间';
COMMENT ON COLUMN purchase_pre_payment.closed_reason IS '结案原因';
COMMENT ON COLUMN purchase_pre_payment.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN purchase_pre_payment.voided_by IS '作废人';
COMMENT ON COLUMN purchase_pre_payment.voided_at IS '作废时间';
COMMENT ON COLUMN purchase_pre_payment.void_reason IS '作废原因';

-- 6.6 销售预收款单 sale_pre_receipt
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE sale_pre_receipt ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN sale_pre_receipt.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN sale_pre_receipt.closed_by IS '结案人';
COMMENT ON COLUMN sale_pre_receipt.closed_at IS '结案时间';
COMMENT ON COLUMN sale_pre_receipt.closed_reason IS '结案原因';
COMMENT ON COLUMN sale_pre_receipt.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN sale_pre_receipt.voided_by IS '作废人';
COMMENT ON COLUMN sale_pre_receipt.voided_at IS '作废时间';
COMMENT ON COLUMN sale_pre_receipt.void_reason IS '作废原因';

-- 6.7 采购对账单 purchase_statement
ALTER TABLE purchase_statement ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_statement ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_statement ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_statement ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_statement ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE purchase_statement ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE purchase_statement ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE purchase_statement ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN purchase_statement.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN purchase_statement.closed_by IS '结案人';
COMMENT ON COLUMN purchase_statement.closed_at IS '结案时间';
COMMENT ON COLUMN purchase_statement.closed_reason IS '结案原因';
COMMENT ON COLUMN purchase_statement.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN purchase_statement.voided_by IS '作废人';
COMMENT ON COLUMN purchase_statement.voided_at IS '作废时间';
COMMENT ON COLUMN purchase_statement.void_reason IS '作废原因';

-- 6.8 销售对账单 sale_statement
ALTER TABLE sale_statement ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sale_statement ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_statement ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_statement ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sale_statement ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE sale_statement ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sale_statement ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE sale_statement ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN sale_statement.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN sale_statement.closed_by IS '结案人';
COMMENT ON COLUMN sale_statement.closed_at IS '结案时间';
COMMENT ON COLUMN sale_statement.closed_reason IS '结案原因';
COMMENT ON COLUMN sale_statement.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN sale_statement.voided_by IS '作废人';
COMMENT ON COLUMN sale_statement.voided_at IS '作废时间';
COMMENT ON COLUMN sale_statement.void_reason IS '作废原因';

-- 6.9 采购费用单 purchase_expense
ALTER TABLE purchase_expense ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_expense ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE purchase_expense ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE purchase_expense ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE purchase_expense ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE purchase_expense ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE purchase_expense ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE purchase_expense ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN purchase_expense.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN purchase_expense.closed_by IS '结案人';
COMMENT ON COLUMN purchase_expense.closed_at IS '结案时间';
COMMENT ON COLUMN purchase_expense.closed_reason IS '结案原因';
COMMENT ON COLUMN purchase_expense.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN purchase_expense.voided_by IS '作废人';
COMMENT ON COLUMN purchase_expense.voided_at IS '作废时间';
COMMENT ON COLUMN purchase_expense.void_reason IS '作废原因';

-- 6.10 销售费用单 sale_expense
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE sale_expense ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN sale_expense.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN sale_expense.closed_by IS '结案人';
COMMENT ON COLUMN sale_expense.closed_at IS '结案时间';
COMMENT ON COLUMN sale_expense.closed_reason IS '结案原因';
COMMENT ON COLUMN sale_expense.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN sale_expense.voided_by IS '作废人';
COMMENT ON COLUMN sale_expense.voided_at IS '作废时间';
COMMENT ON COLUMN sale_expense.void_reason IS '作废原因';

-- 6.11 成本计算单 cost_calculation
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE cost_calculation ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN cost_calculation.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN cost_calculation.closed_by IS '结案人';
COMMENT ON COLUMN cost_calculation.closed_at IS '结案时间';
COMMENT ON COLUMN cost_calculation.closed_reason IS '结案原因';
COMMENT ON COLUMN cost_calculation.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN cost_calculation.voided_by IS '作废人';
COMMENT ON COLUMN cost_calculation.voided_at IS '作废时间';
COMMENT ON COLUMN cost_calculation.void_reason IS '作废原因';

-- ----------------------------------------------------------------------------
-- 七、样品与免费样品模块（2张）
-- ----------------------------------------------------------------------------

-- 7.1 样品订单 sample_order
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE sample_order ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN sample_order.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN sample_order.closed_by IS '结案人';
COMMENT ON COLUMN sample_order.closed_at IS '结案时间';
COMMENT ON COLUMN sample_order.closed_reason IS '结案原因';
COMMENT ON COLUMN sample_order.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN sample_order.voided_by IS '作废人';
COMMENT ON COLUMN sample_order.voided_at IS '作废时间';
COMMENT ON COLUMN sample_order.void_reason IS '作废原因';

-- 7.2 免费样品订单 free_sample_order
ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE free_sample_order ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN free_sample_order.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN free_sample_order.closed_by IS '结案人';
COMMENT ON COLUMN free_sample_order.closed_at IS '结案时间';
COMMENT ON COLUMN free_sample_order.closed_reason IS '结案原因';
COMMENT ON COLUMN free_sample_order.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN free_sample_order.voided_by IS '作废人';
COMMENT ON COLUMN free_sample_order.voided_at IS '作废时间';
COMMENT ON COLUMN free_sample_order.void_reason IS '作废原因';

-- ----------------------------------------------------------------------------
-- 八、期初与MRP（4张）
-- ----------------------------------------------------------------------------

-- 8.1 期初应收账款 account_receivable
ALTER TABLE account_receivable ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE account_receivable ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE account_receivable ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE account_receivable ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE account_receivable ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE account_receivable ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE account_receivable ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE account_receivable ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN account_receivable.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN account_receivable.closed_by IS '结案人';
COMMENT ON COLUMN account_receivable.closed_at IS '结案时间';
COMMENT ON COLUMN account_receivable.closed_reason IS '结案原因';
COMMENT ON COLUMN account_receivable.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN account_receivable.voided_by IS '作废人';
COMMENT ON COLUMN account_receivable.voided_at IS '作废时间';
COMMENT ON COLUMN account_receivable.void_reason IS '作废原因';

-- 8.2 期初应付账款 account_payable
ALTER TABLE account_payable ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE account_payable ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE account_payable ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE account_payable ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE account_payable ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE account_payable ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE account_payable ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE account_payable ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN account_payable.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN account_payable.closed_by IS '结案人';
COMMENT ON COLUMN account_payable.closed_at IS '结案时间';
COMMENT ON COLUMN account_payable.closed_reason IS '结案原因';
COMMENT ON COLUMN account_payable.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN account_payable.voided_by IS '作废人';
COMMENT ON COLUMN account_payable.voided_at IS '作废时间';
COMMENT ON COLUMN account_payable.void_reason IS '作废原因';

-- 8.3 MRP计划 mrp_plan
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN mrp_plan.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN mrp_plan.closed_by IS '结案人';
COMMENT ON COLUMN mrp_plan.closed_at IS '结案时间';
COMMENT ON COLUMN mrp_plan.closed_reason IS '结案原因';
COMMENT ON COLUMN mrp_plan.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN mrp_plan.voided_by IS '作废人';
COMMENT ON COLUMN mrp_plan.voided_at IS '作废时间';
COMMENT ON COLUMN mrp_plan.void_reason IS '作废原因';

-- 8.4 固定资产 fixed_asset
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS close_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS closed_by VARCHAR(50);
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS closed_reason VARCHAR(500);
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS bill_before_status SMALLINT;
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS voided_by VARCHAR(50);
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS voided_at TIMESTAMP;
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500);
COMMENT ON COLUMN fixed_asset.close_status IS '结案状态 0=未结案 1=已结案';
COMMENT ON COLUMN fixed_asset.closed_by IS '结案人';
COMMENT ON COLUMN fixed_asset.closed_at IS '结案时间';
COMMENT ON COLUMN fixed_asset.closed_reason IS '结案原因';
COMMENT ON COLUMN fixed_asset.bill_before_status IS '作废前状态（反作废恢复用）';
COMMENT ON COLUMN fixed_asset.voided_by IS '作废人';
COMMENT ON COLUMN fixed_asset.voided_at IS '作废时间';
COMMENT ON COLUMN fixed_asset.void_reason IS '作废原因';

-- ============================================================================
-- 升级完成标记
-- ============================================================================
-- 执行完毕后，可通过以下SQL验证升级结果：
-- SELECT table_name, column_name, data_type
--   FROM information_schema.columns
--  WHERE table_name IN (
--    'sale_quote','sale_order','sale_contract','sale_delivery','sale_return','sale_invoice',
--    'purchase_request','purchase_order','purchase_contract','purchase_inbound','purchase_return','purchase_invoice',
--    'stock_transfer','stock_check','material_requisition','other_receipt','other_issue',
--    'work_order','production_receipt','process_transfer','operation_report','produce_assemble','produce_disassemble',
--    'subcontract_order','subcontract_pick','subcontract_supplement','subcontract_inbound','subcontract_return',
--    'payment_receipt','payment_voucher','fund_transfer','budget','purchase_pre_payment','sale_pre_receipt',
--    'purchase_statement','sale_statement','purchase_expense','sale_expense','cost_calculation',
--    'sample_order','free_sample_order',
--    'accounts_receivable','accounts_payable','mrp_plan','fixed_asset'
--  ) AND column_name IN ('close_status','closed_by','closed_at','closed_reason',
--                        'bill_before_status','voided_by','voided_at','void_reason')
--  ORDER BY table_name, column_name;
