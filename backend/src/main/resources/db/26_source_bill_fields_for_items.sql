-- ===========================================================================
-- 来源单据字段补齐脚本（明细表）
-- 为所有缺失 source_bill_no / source_bill_type / source_doc_id 的明细表统一补齐
-- 解决: 多源单下推时明细行需独立记录来源单号，前端明细列已展示但数据库/实体缺失字段
-- 使用 ALTER TABLE ... ADD COLUMN IF NOT EXISTS 确保可重复执行
--
-- 安全说明:
--   1. 本脚本可重复执行（所有操作均为 IF NOT EXISTS）
--   2. 本脚本自带可能缺失表的 CREATE TABLE IF NOT EXISTS，防止全新部署时表不存在报错
--   3. 执行顺序建议: init.sql → 各编号补丁 → 本脚本
-- ===========================================================================

-- ===========================================================================
-- 第一部分：确保可能缺失的表已创建（全新部署保护）
-- 以下表在历史脚本中可能缺失 CREATE TABLE，这里兜底创建
-- ===========================================================================

-- 1. sale_contract（销售合同表头）- 历史脚本中无 CREATE TABLE，这里补齐
CREATE TABLE IF NOT EXISTS sale_contract (
    contract_id BIGSERIAL PRIMARY KEY,
    contract_no VARCHAR(50),
    contract_date DATE,
    contract_type VARCHAR(50),
    validity_start DATE,
    validity_end DATE,
    customer_id BIGINT,
    customer_no VARCHAR(50),
    customer_short_name VARCHAR(100),
    contact_person VARCHAR(50),
    contact_phone VARCHAR(50),
    department_id BIGINT,
    saler_id BIGINT,
    price_level_id BIGINT,
    currency_id BIGINT,
    exchange_rate NUMERIC(18,4),
    total_amount NUMERIC(18,4) DEFAULT 0,
    tax_amount NUMERIC(18,4) DEFAULT 0,
    discount_rate NUMERIC(18,4) DEFAULT 0,
    discount_amount NUMERIC(18,4) DEFAULT 0,
    net_amount NUMERIC(18,4) DEFAULT 0,
    delivery_date DATE,
    payment_term_id BIGINT,
    payment_method VARCHAR(50),
    deposit_amount NUMERIC(18,4) DEFAULT 0,
    warehouse_id BIGINT,
    receive_address VARCHAR(200),
    transport_method VARCHAR(50),
    is_tax VARCHAR(10),
    tax_calc_method VARCHAR(50),
    is_invoiced VARCHAR(10),
    logistics_company VARCHAR(50),
    logistics_no VARCHAR(50),
    invoice_type VARCHAR(50),
    project_no VARCHAR(50),
    customer_order_no VARCHAR(50),
    opportunity_id BIGINT,
    opportunity_no VARCHAR(50),
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    remark VARCHAR(500),
    attachment_urls TEXT,
    status VARCHAR(20) DEFAULT '草稿',
    approval_status VARCHAR(20),
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    close_status INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sale_contract_no ON sale_contract(contract_no);
CREATE INDEX IF NOT EXISTS idx_sale_contract_customer ON sale_contract(customer_id);

-- 2. sale_contract_item（销售合同明细）- 历史脚本中无 CREATE TABLE，这里补齐
CREATE TABLE IF NOT EXISTS sale_contract_item (
    item_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    specification VARCHAR(100),
    unit_id BIGINT,
    unit VARCHAR(20),
    quantity NUMERIC(18,4) DEFAULT 0,
    unit_price NUMERIC(18,4) DEFAULT 0,
    tax_rate NUMERIC(18,4) DEFAULT 0,
    discount_rate NUMERIC(18,4) DEFAULT 0,
    discount_amount NUMERIC(18,4) DEFAULT 0,
    amount NUMERIC(18,4) DEFAULT 0,
    avg_cost NUMERIC(18,4) DEFAULT 0,
    tax_amount NUMERIC(18,4) DEFAULT 0,
    net_amount NUMERIC(18,4) DEFAULT 0,
    delivery_date VARCHAR(50),
    warehouse_id BIGINT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    source_doc_id BIGINT,
    remark VARCHAR(500),
    sort_no INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (contract_id) REFERENCES sale_contract(contract_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sale_contract_item_contract ON sale_contract_item(contract_id);

-- 3. purchase_contract（采购合同表头）- 兜底创建（fix_schema_pushdown.sql 中已有，但防止未执行）
CREATE TABLE IF NOT EXISTS purchase_contract (
    contract_id BIGSERIAL PRIMARY KEY,
    contract_no VARCHAR(50) NOT NULL,
    contract_date DATE,
    supplier_id BIGINT,
    supplier_name VARCHAR(100),
    currency_id BIGINT,
    exchange_rate NUMERIC(18,4) DEFAULT 1,
    total_amount NUMERIC(18,4) DEFAULT 0,
    tax_amount NUMERIC(18,4) DEFAULT 0,
    discount_rate NUMERIC(18,4) DEFAULT 0,
    discount_amount NUMERIC(18,4) DEFAULT 0,
    net_amount NUMERIC(18,4) DEFAULT 0,
    payment_term_id BIGINT,
    payment_method VARCHAR(50),
    delivery_date DATE,
    warehouse_id BIGINT,
    source_bill_type VARCHAR(50),
    source_bill_no VARCHAR(50),
    remark VARCHAR(500),
    attachment_urls TEXT,
    status VARCHAR(20) DEFAULT '草稿',
    approval_status VARCHAR(20),
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    close_status INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_purchase_contract_no ON purchase_contract(contract_no);
CREATE INDEX IF NOT EXISTS idx_purchase_contract_supplier ON purchase_contract(supplier_id);

-- 4. purchase_contract_item（采购合同明细）- 兜底创建
CREATE TABLE IF NOT EXISTS purchase_contract_item (
    item_id BIGSERIAL PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    line_no INT NOT NULL DEFAULT 10,
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    specification VARCHAR(200),
    unit_id BIGINT,
    quantity NUMERIC(18,4) NOT NULL DEFAULT 0,
    unit_price NUMERIC(18,4) DEFAULT 0,
    tax_rate NUMERIC(8,4) DEFAULT 0,
    discount_rate NUMERIC(8,4) DEFAULT 0,
    discount_amount NUMERIC(18,2) DEFAULT 0,
    amount NUMERIC(18,2) DEFAULT 0,
    tax_amount NUMERIC(18,2) DEFAULT 0,
    net_amount NUMERIC(18,2) DEFAULT 0,
    delivery_date DATE,
    remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    FOREIGN KEY (contract_id) REFERENCES purchase_contract(contract_id) ON DELETE CASCADE
);

-- 5. work_order_product（工单成品明细）- 兜底创建（work_order_product.sql 中已有，但防止未执行）
CREATE TABLE IF NOT EXISTS work_order_product (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    line_no INT DEFAULT 10,
    material_id BIGINT,
    material_code VARCHAR(50),
    material_name VARCHAR(200),
    specification VARCHAR(200),
    model VARCHAR(100),
    unit_id BIGINT,
    unit_name VARCHAR(50),
    quantity NUMERIC(18,4) DEFAULT 0,
    completed_quantity NUMERIC(18,4) DEFAULT 0,
    reported_quantity NUMERIC(18,4) DEFAULT 0,
    rejected_quantity NUMERIC(18,4) DEFAULT 0,
    source_type VARCHAR(50),
    source_no VARCHAR(50),
    source_item_id BIGINT,
    warehouse_id BIGINT,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_work_order_product_order_id ON work_order_product(order_id);


-- ===========================================================================
-- 第二部分：为明细表补齐 source_bill_no / source_bill_type / source_doc_id 字段
-- ===========================================================================

-- ===== 一、完全缺失三个字段的明细表（添加 source_bill_no + source_bill_type + source_doc_id）=====

-- 1. sale_contract_item（销售合同明细）
ALTER TABLE sale_contract_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE sale_contract_item ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE sale_contract_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 2. purchase_contract_item（采购合同明细）
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE purchase_contract_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 3. subcontract_order_item（委外加工单明细）
ALTER TABLE subcontract_order_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE subcontract_order_item ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE subcontract_order_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 4. subcontract_pick_item（委外发料单明细）
ALTER TABLE subcontract_pick_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE subcontract_pick_item ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE subcontract_pick_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 5. subcontract_inbound_item（委外入库单明细）
ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE subcontract_inbound_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 6. subcontract_supplement_item（委外补料单明细）
ALTER TABLE subcontract_supplement_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE subcontract_supplement_item ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE subcontract_supplement_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 7. subcontract_return_item（委外退料单明细）
ALTER TABLE subcontract_return_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE subcontract_return_item ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE subcontract_return_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 8. stock_transfer_item（库存调拨单明细）
ALTER TABLE stock_transfer_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE stock_transfer_item ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE stock_transfer_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 9. other_issue_item（其他出库单明细）
ALTER TABLE other_issue_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE other_issue_item ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE other_issue_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 10. work_order_product（工单成品明细）
--     注意: 该表已有 source_type / source_no / source_item_id 字段（不同命名约定）
--     此处补充标准命名的 source_bill_no / source_bill_type / source_doc_id 以统一规范
--     保留旧字段以兼容旧数据
ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS source_bill_no VARCHAR(50);
ALTER TABLE work_order_product ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;


-- ===== 二、已有 source_bill_no + source_bill_type，仅补 source_doc_id 的明细表 =====

-- 11. sale_order_item（销售订单明细）
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 12. sale_delivery_item（销售出库单明细）
ALTER TABLE sale_delivery_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 13. sale_return_item（销售退货单明细）
ALTER TABLE sale_return_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 14. sale_invoice_item（销售发票明细）
ALTER TABLE sale_invoice_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 15. purchase_request_item（采购申请单明细）
ALTER TABLE purchase_request_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 16. purchase_order_item（采购订单明细）
ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 17. purchase_receipt_item（采购入库单明细，实体类名为 PurchaseInboundItem）
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 18. purchase_return_item（采购退货单明细）
ALTER TABLE purchase_return_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 19. purchase_invoice_item（采购发票明细）
ALTER TABLE purchase_invoice_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 20. material_requisition_item（生产领料/退料/补料单明细）
ALTER TABLE material_requisition_item ADD COLUMN IF NOT EXISTS source_doc_id BIGINT;

-- 21. purchase_request_item / purchase_receipt_item 补 source_bill_type
--     （init.sql 中有 source_bill_no 但部分缺 source_bill_type）
ALTER TABLE purchase_request_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE purchase_receipt_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE purchase_return_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);
ALTER TABLE purchase_invoice_item ADD COLUMN IF NOT EXISTS source_bill_type VARCHAR(50);


-- ===== 三、数据迁移：work_order_product 旧字段 → 新字段 =====
-- 将旧的 source_type / source_no 值复制到新的 source_bill_type / source_bill_no
-- 仅在 source_bill_no 为空且 source_no 有值时执行
UPDATE work_order_product
SET source_bill_no = source_no
WHERE source_bill_no IS NULL AND source_no IS NOT NULL AND source_no != '';

UPDATE work_order_product
SET source_bill_type = source_type
WHERE source_bill_type IS NULL AND source_type IS NOT NULL AND source_type != '';

SELECT 'Source bill fields patch completed' AS result;
