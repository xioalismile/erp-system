-- ============================================================
-- 61_field_persistence_columns.sql
-- 目的：彻底消除「前端字段存在但后端不持久化」（用户 2026-09-20 口径：字段可以多，但不能少）
--
-- 背景：全站 55 个编辑页 × 187 个实体核对，剩余 107 个"前端可填可选、后端无列"的字段
--       → 统一加列持久化（不删前端控件）。含用户点名的「库存单据表头 折扣/税额/净额」。
--
-- 幂等：全部 ADD COLUMN IF NOT EXISTS；只增不删、不改类型、不动已有列。
-- 多租户：本文件只覆盖执行时那一个库；每个租户库由启动时
--       SchemaUpgradeEnsurer.ensureBusinessDbColumns() 的同名清单逐库补齐。
-- 注意：PurchaseInbound 的物理表名是 purchase_receipt（不是 purchase_inbound）。
-- ============================================================

-- ---------- 1) 附件（12 张表原先无 attachment_urls）----------
ALTER TABLE sale_pre_receipt        ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE purchase_pre_payment    ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE operation_expense       ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE cost_calculation        ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE operation_report        ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE mrp_plan                ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE subcontract_order       ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE subcontract_return      ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE subcontract_inbound     ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE subcontract_supplement  ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE subcontract_pick        ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);
ALTER TABLE production_order        ADD COLUMN IF NOT EXISTS attachment_urls VARCHAR(1000);

-- ---------- 2) 收货地址（5 张表无任何地址列）----------
ALTER TABLE purchase_receipt  ADD COLUMN IF NOT EXISTS delivery_address VARCHAR(255);
ALTER TABLE purchase_return   ADD COLUMN IF NOT EXISTS delivery_address VARCHAR(255);
ALTER TABLE purchase_invoice  ADD COLUMN IF NOT EXISTS delivery_address VARCHAR(255);
ALTER TABLE sale_quote        ADD COLUMN IF NOT EXISTS delivery_address VARCHAR(255);
ALTER TABLE sale_return       ADD COLUMN IF NOT EXISTS delivery_address VARCHAR(255);

-- ---------- 3) 交货日期 ----------
ALTER TABLE purchase_receipt  ADD COLUMN IF NOT EXISTS expected_delivery_date DATE;

-- ---------- 4) 预付款比例/金额（主表）----------
ALTER TABLE sale_quote     ADD COLUMN IF NOT EXISTS prepaid_rate   NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sale_quote     ADD COLUMN IF NOT EXISTS prepaid_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_return    ADD COLUMN IF NOT EXISTS prepaid_rate   NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sale_return    ADD COLUMN IF NOT EXISTS prepaid_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_invoice   ADD COLUMN IF NOT EXISTS prepaid_rate   NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sale_invoice   ADD COLUMN IF NOT EXISTS prepaid_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_delivery  ADD COLUMN IF NOT EXISTS prepaid_rate   NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sale_delivery  ADD COLUMN IF NOT EXISTS prepaid_amount NUMERIC(18,2) DEFAULT 0;
-- 采购订单预付金额复用既有 prepayment_amount，此处只补比例
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS prepaid_rate   NUMERIC(18,4) DEFAULT 0;

-- ---------- 5) 库存单据表头：折扣/税额/净额（用户点名必须保留）----------
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(18,2) DEFAULT 0;
ALTER TABLE stock_transfer ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,2) DEFAULT 0;
ALTER TABLE other_receipt  ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE other_receipt  ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(18,2) DEFAULT 0;
ALTER TABLE other_receipt  ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,2) DEFAULT 0;
ALTER TABLE other_issue    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE other_issue    ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(18,2) DEFAULT 0;
ALTER TABLE other_issue    ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,2) DEFAULT 0;

-- ---------- 6) 库存单据明细：税率/折扣/税/净额 ----------
ALTER TABLE stock_transfer_item ADD COLUMN IF NOT EXISTS tax_rate        NUMERIC(18,4) DEFAULT 0;
ALTER TABLE stock_transfer_item ADD COLUMN IF NOT EXISTS discount_rate   NUMERIC(18,4) DEFAULT 0;
ALTER TABLE stock_transfer_item ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE stock_transfer_item ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(18,4) DEFAULT 0;
ALTER TABLE stock_transfer_item ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,4) DEFAULT 0;
ALTER TABLE other_receipt_item  ADD COLUMN IF NOT EXISTS tax_rate        NUMERIC(18,4) DEFAULT 0;
ALTER TABLE other_receipt_item  ADD COLUMN IF NOT EXISTS discount_rate   NUMERIC(18,4) DEFAULT 0;
ALTER TABLE other_receipt_item  ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE other_receipt_item  ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(18,4) DEFAULT 0;
ALTER TABLE other_receipt_item  ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,4) DEFAULT 0;
ALTER TABLE other_issue_item    ADD COLUMN IF NOT EXISTS tax_rate        NUMERIC(18,4) DEFAULT 0;
ALTER TABLE other_issue_item    ADD COLUMN IF NOT EXISTS discount_rate   NUMERIC(18,4) DEFAULT 0;
ALTER TABLE other_issue_item    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE other_issue_item    ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(18,4) DEFAULT 0;
ALTER TABLE other_issue_item    ADD COLUMN IF NOT EXISTS net_amount      NUMERIC(18,4) DEFAULT 0;

-- ---------- 7) 预收/预付明细（核销与折算字段）----------
ALTER TABLE sale_pre_receipt_item     ADD COLUMN IF NOT EXISTS return_amount      NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_pre_receipt_item     ADD COLUMN IF NOT EXISTS base_amount        NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_pre_receipt_item     ADD COLUMN IF NOT EXISTS used_amount        NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_pre_receipt_item     ADD COLUMN IF NOT EXISTS used_base_amount   NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_pre_receipt_item     ADD COLUMN IF NOT EXISTS return_base_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE sale_pre_receipt_item     ADD COLUMN IF NOT EXISTS remark             VARCHAR(500);
ALTER TABLE purchase_pre_payment_item ADD COLUMN IF NOT EXISTS return_amount      NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_pre_payment_item ADD COLUMN IF NOT EXISTS base_amount        NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_pre_payment_item ADD COLUMN IF NOT EXISTS used_amount        NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_pre_payment_item ADD COLUMN IF NOT EXISTS used_base_amount   NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_pre_payment_item ADD COLUMN IF NOT EXISTS return_base_amount NUMERIC(18,2) DEFAULT 0;
ALTER TABLE purchase_pre_payment_item ADD COLUMN IF NOT EXISTS remark             VARCHAR(500);

-- ---------- 8) MRP 运算参数 ----------
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS plan_horizon         INTEGER;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS time_fence_days      INTEGER;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_so           INTEGER DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_forecast     INTEGER DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_safety_stock INTEGER DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_open_po      INTEGER DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_open_mo      INTEGER DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS include_onhand_stock INTEGER DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS lot_size_type        VARCHAR(50);
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS lot_size             NUMERIC(18,4) DEFAULT 0;
ALTER TABLE mrp_plan ADD COLUMN IF NOT EXISTS min_order_qty        NUMERIC(18,4) DEFAULT 0;

-- ---------- 9) 物料默认仓库 ----------
ALTER TABLE material ADD COLUMN IF NOT EXISTS default_warehouse_id BIGINT;

-- ---------- 10) 生产报工：设备 / 工作中心名称 ----------
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS equipment_id     VARCHAR(100);
ALTER TABLE operation_report ADD COLUMN IF NOT EXISTS work_center_name VARCHAR(100);

-- ---------- 11) 采购明细：批次/生产日期/有效期 ----------
ALTER TABLE purchase_invoice_item   ADD COLUMN IF NOT EXISTS batch_no        VARCHAR(50);
ALTER TABLE purchase_invoice_item   ADD COLUMN IF NOT EXISTS production_date DATE;
ALTER TABLE purchase_invoice_item   ADD COLUMN IF NOT EXISTS expiry_date     DATE;
ALTER TABLE purchase_order_item     ADD COLUMN IF NOT EXISTS batch_no        VARCHAR(50);
ALTER TABLE purchase_order_item     ADD COLUMN IF NOT EXISTS production_date DATE;
ALTER TABLE purchase_order_item     ADD COLUMN IF NOT EXISTS expiry_date     DATE;
ALTER TABLE purchase_request_item   ADD COLUMN IF NOT EXISTS batch_no        VARCHAR(50);
ALTER TABLE purchase_request_item   ADD COLUMN IF NOT EXISTS production_date DATE;
ALTER TABLE purchase_request_item   ADD COLUMN IF NOT EXISTS expiry_date     DATE;
-- purchase_return_item.batch_no 已存在，只补日期
ALTER TABLE purchase_return_item    ADD COLUMN IF NOT EXISTS production_date DATE;
ALTER TABLE purchase_return_item    ADD COLUMN IF NOT EXISTS expiry_date     DATE;
ALTER TABLE purchase_contract_item  ADD COLUMN IF NOT EXISTS batch_no        VARCHAR(50);

-- ---------- 12) 委外订单：表头物料/单位 + 明细单位/供应类型 ----------
ALTER TABLE subcontract_order      ADD COLUMN IF NOT EXISTS material_id   BIGINT;
ALTER TABLE subcontract_order      ADD COLUMN IF NOT EXISTS material_code VARCHAR(50);
ALTER TABLE subcontract_order      ADD COLUMN IF NOT EXISTS material_name VARCHAR(200);
ALTER TABLE subcontract_order      ADD COLUMN IF NOT EXISTS unit_id       BIGINT;
ALTER TABLE subcontract_order      ADD COLUMN IF NOT EXISTS unit_name     VARCHAR(50);
ALTER TABLE subcontract_order_item ADD COLUMN IF NOT EXISTS unit_id       BIGINT;
ALTER TABLE subcontract_order_item ADD COLUMN IF NOT EXISTS supply_type   VARCHAR(50);

-- ---------- 13) 委外退货/入库/补料：领料单 + 来源单据 ----------
ALTER TABLE subcontract_return     ADD COLUMN IF NOT EXISTS pick_id          BIGINT;
ALTER TABLE subcontract_return     ADD COLUMN IF NOT EXISTS pick_no          VARCHAR(50);
ALTER TABLE subcontract_return     ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE subcontract_return     ADD COLUMN IF NOT EXISTS source_bill_no   VARCHAR(50);
ALTER TABLE subcontract_inbound    ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE subcontract_inbound    ADD COLUMN IF NOT EXISTS source_bill_no   VARCHAR(50);
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS pick_id          BIGINT;
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS pick_no          VARCHAR(50);
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE subcontract_supplement ADD COLUMN IF NOT EXISTS source_bill_no   VARCHAR(50);
