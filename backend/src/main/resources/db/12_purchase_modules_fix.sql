-- ===========================================================================
-- 采购模块4个单据(PostgreSQL生产环境)字段补齐脚本
-- 严格遵循《智能ERP系统-完整表单字段规范文档.md》第3.1-3.5节
-- 适用: 生产环境 (PostgreSQL)
-- 说明: 数据库 schema 已存在于 init.sql, 本脚本仅补齐缺失字段(用 ALTER TABLE)
-- ===========================================================================

-- ===========================================================================
-- 3.1 采购申请单 PR-
-- ===========================================================================
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS apply_dept_name VARCHAR(200);
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS applier_name VARCHAR(100);
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS apply_type VARCHAR(30);
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS demand_date DATE;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS total_qty NUMERIC(18,4) DEFAULT 0.0000;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS is_urgent SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS approver_id BIGINT;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS approve_date TIMESTAMP;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS is_closed SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS close_date TIMESTAMP;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS creator_id BIGINT;
ALTER TABLE purchase_request ADD COLUMN IF NOT EXISTS modifier_id BIGINT;

ALTER TABLE purchase_request_item ADD COLUMN IF NOT EXISTS approved_qty NUMERIC(18,4) DEFAULT 0.0000;
ALTER TABLE purchase_request_item ADD COLUMN IF NOT EXISTS suggested_supplier_id BIGINT;
ALTER TABLE purchase_request_item ADD COLUMN IF NOT EXISTS suggested_supplier_name VARCHAR(200);
ALTER TABLE purchase_request_item ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);
ALTER TABLE purchase_request_item ADD COLUMN IF NOT EXISTS source_row_no INT;
ALTER TABLE purchase_request_item ADD COLUMN IF NOT EXISTS demand_date DATE;

-- ===========================================================================
-- 3.3 采购入库单 PIN-
-- ===========================================================================
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(200);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS buyer_name VARCHAR(100);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS department_id BIGINT;
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS stock_name VARCHAR(100);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS inspection_no VARCHAR(50);
ALTER TABLE purchase_receipt ADD COLUMN IF NOT EXISTS is_lot_control SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS returned_quantity NUMERIC(18,4) DEFAULT 0.0000;
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS bin_name VARCHAR(100);
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS source_row_no INT;

-- ===========================================================================
-- 3.4 采购退货单 PRT-
-- ===========================================================================
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(200);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS buyer_name VARCHAR(100);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS department_id BIGINT;
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS dept_name VARCHAR(100);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS stock_name VARCHAR(100);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS return_type VARCHAR(30);
ALTER TABLE purchase_return ADD COLUMN IF NOT EXISTS is_lot_control SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE purchase_return_item ADD COLUMN IF NOT EXISTS bin_name VARCHAR(100);
ALTER TABLE purchase_return_item ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);
ALTER TABLE purchase_return_item ADD COLUMN IF NOT EXISTS source_row_no INT;

-- ===========================================================================
-- 3.5 采购发票 PINV-
-- ===========================================================================
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(200);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS invoice_code VARCHAR(50);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS invoice_number VARCHAR(50);
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS is_red_invoice SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_invoice ADD COLUMN IF NOT EXISTS is_match SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE purchase_invoice_item ADD COLUMN IF NOT EXISTS tax_category VARCHAR(50);
ALTER TABLE purchase_invoice_item ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);
ALTER TABLE purchase_invoice_item ADD COLUMN IF NOT EXISTS source_row_no INT;
