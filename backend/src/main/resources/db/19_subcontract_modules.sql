-- ===========================================================================
-- 委外模块 - PostgreSQL 建表脚本
-- 包含8张表：
--   1. subcontract_order               委外加工单主表
--   2. subcontract_order_item          委外加工单明细表
--   3. subcontract_pick                委外领料单主表
--   4. subcontract_pick_item           委外领料单明细表
--   5. subcontract_supplement          委外补料单主表
--   6. subcontract_supplement_item     委外补料单明细表
--   7. subcontract_inbound             委外入库单主表
--   8. subcontract_inbound_item        委外入库单明细表
--   9. subcontract_return              委外退料单主表
--  10. subcontract_return_item         委外退料单明细表
-- ===========================================================================

-- 1. 委外加工单主表
CREATE TABLE IF NOT EXISTS subcontract_order (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    supplier_id BIGINT,
    supplier_name VARCHAR(128),
    process_item VARCHAR(256),
    order_date DATE,
    expect_date DATE,
    actual_date DATE,
    total_quantity NUMERIC(18,4) DEFAULT 0,
    finished_quantity NUMERIC(18,4) DEFAULT 0,
    unit_price NUMERIC(18,4) DEFAULT 0,
    total_amount NUMERIC(18,4) DEFAULT 0,
    tax_rate NUMERIC(10,4) DEFAULT 0,
    tax_amount NUMERIC(18,4) DEFAULT 0,
    net_amount NUMERIC(18,4) DEFAULT 0,
    currency_code VARCHAR(16),
    exchange_rate NUMERIC(18,6) DEFAULT 1,
    base_total_amount NUMERIC(18,4) DEFAULT 0,
    base_tax_amount NUMERIC(18,4) DEFAULT 0,
    base_net_amount NUMERIC(18,4) DEFAULT 0,
    source_bill_type VARCHAR(32),
    source_bill_no VARCHAR(64),
    status SMALLINT DEFAULT 0,
    close_status SMALLINT DEFAULT 0,
    remark VARCHAR(512),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_subcontract_order_no UNIQUE (order_no)
);

CREATE INDEX IF NOT EXISTS idx_subcontract_order_supplier ON subcontract_order(supplier_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_order_status ON subcontract_order(status);
CREATE INDEX IF NOT EXISTS idx_subcontract_order_create_time ON subcontract_order(create_time);

COMMENT ON TABLE subcontract_order IS '委外加工单主表';
COMMENT ON COLUMN subcontract_order.order_no IS '委外加工单号';
COMMENT ON COLUMN subcontract_order.supplier_id IS '供应商ID';
COMMENT ON COLUMN subcontract_order.supplier_name IS '供应商名称';
COMMENT ON COLUMN subcontract_order.process_item IS '加工项目';
COMMENT ON COLUMN subcontract_order.order_date IS '加工日期';
COMMENT ON COLUMN subcontract_order.expect_date IS '预计完工日期';
COMMENT ON COLUMN subcontract_order.actual_date IS '实际完工日期';
COMMENT ON COLUMN subcontract_order.total_quantity IS '总数量';
COMMENT ON COLUMN subcontract_order.finished_quantity IS '已完成数量';
COMMENT ON COLUMN subcontract_order.unit_price IS '单价';
COMMENT ON COLUMN subcontract_order.total_amount IS '总金额';
COMMENT ON COLUMN subcontract_order.tax_rate IS '税率';
COMMENT ON COLUMN subcontract_order.tax_amount IS '税额';
COMMENT ON COLUMN subcontract_order.net_amount IS '净额';
COMMENT ON COLUMN subcontract_order.currency_code IS '币种';
COMMENT ON COLUMN subcontract_order.exchange_rate IS '汇率';
COMMENT ON COLUMN subcontract_order.base_total_amount IS '本位币总金额';
COMMENT ON COLUMN subcontract_order.base_tax_amount IS '本位币税额';
COMMENT ON COLUMN subcontract_order.base_net_amount IS '本位币净额';
COMMENT ON COLUMN subcontract_order.source_bill_type IS '来源单据类型';
COMMENT ON COLUMN subcontract_order.source_bill_no IS '来源单据号';
COMMENT ON COLUMN subcontract_order.status IS '状态:0草稿 1待审核 2已生效 3执行中 4已完成';
COMMENT ON COLUMN subcontract_order.close_status IS '关闭状态:0未关闭 1已关闭';
COMMENT ON COLUMN subcontract_order.remark IS '备注';
COMMENT ON COLUMN subcontract_order.create_by IS '创建人';
COMMENT ON COLUMN subcontract_order.create_time IS '创建时间';
COMMENT ON COLUMN subcontract_order.update_time IS '更新时间';

-- 2. 委外加工单明细表
CREATE TABLE IF NOT EXISTS subcontract_order_item (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    material_id BIGINT,
    material_code VARCHAR(64),
    material_name VARCHAR(256),
    specification VARCHAR(256),
    unit VARCHAR(32),
    quantity NUMERIC(18,4) DEFAULT 0,
    finished_qty NUMERIC(18,4) DEFAULT 0,
    actual_qty NUMERIC(18,4) DEFAULT 0,
    unit_price NUMERIC(18,4) DEFAULT 0,
    amount NUMERIC(18,4) DEFAULT 0,
    tax_rate NUMERIC(10,4) DEFAULT 0,
    tax_amount NUMERIC(18,4) DEFAULT 0,
    net_amount NUMERIC(18,4) DEFAULT 0,
    bom_id BIGINT,
    remark VARCHAR(512),
    detail_close_status SMALLINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subcontract_order_item_order ON subcontract_order_item(order_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_order_item_material ON subcontract_order_item(material_id);

COMMENT ON TABLE subcontract_order_item IS '委外加工单明细表';
COMMENT ON COLUMN subcontract_order_item.order_id IS '委外加工单ID';
COMMENT ON COLUMN subcontract_order_item.material_id IS '物料ID';
COMMENT ON COLUMN subcontract_order_item.material_code IS '物料编码';
COMMENT ON COLUMN subcontract_order_item.material_name IS '物料名称';
COMMENT ON COLUMN subcontract_order_item.specification IS '规格型号';
COMMENT ON COLUMN subcontract_order_item.unit IS '单位';
COMMENT ON COLUMN subcontract_order_item.quantity IS '数量';
COMMENT ON COLUMN subcontract_order_item.finished_qty IS '已完成数量';
COMMENT ON COLUMN subcontract_order_item.actual_qty IS '实际数量';
COMMENT ON COLUMN subcontract_order_item.unit_price IS '单价';
COMMENT ON COLUMN subcontract_order_item.amount IS '金额';
COMMENT ON COLUMN subcontract_order_item.tax_rate IS '税率';
COMMENT ON COLUMN subcontract_order_item.tax_amount IS '税额';
COMMENT ON COLUMN subcontract_order_item.net_amount IS '净额';
COMMENT ON COLUMN subcontract_order_item.bom_id IS 'BOM ID';
COMMENT ON COLUMN subcontract_order_item.remark IS '备注';
COMMENT ON COLUMN subcontract_order_item.detail_close_status IS '明细关闭状态:0未关闭 1已关闭';
COMMENT ON COLUMN subcontract_order_item.create_time IS '创建时间';
COMMENT ON COLUMN subcontract_order_item.update_time IS '更新时间';

-- 3. 委外领料单主表
CREATE TABLE IF NOT EXISTS subcontract_pick (
    id BIGSERIAL PRIMARY KEY,
    pick_no VARCHAR(64) NOT NULL,
    order_id BIGINT,
    order_no VARCHAR(64),
    supplier_id BIGINT,
    supplier_name VARCHAR(128),
    pick_date DATE,
    status SMALLINT DEFAULT 0,
    total_quantity NUMERIC(18,4) DEFAULT 0,
    remark VARCHAR(512),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_subcontract_pick_no UNIQUE (pick_no)
);

CREATE INDEX IF NOT EXISTS idx_subcontract_pick_order ON subcontract_pick(order_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_pick_supplier ON subcontract_pick(supplier_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_pick_status ON subcontract_pick(status);
CREATE INDEX IF NOT EXISTS idx_subcontract_pick_create_time ON subcontract_pick(create_time);

COMMENT ON TABLE subcontract_pick IS '委外领料单主表';
COMMENT ON COLUMN subcontract_pick.pick_no IS '领料单号';
COMMENT ON COLUMN subcontract_pick.order_id IS '关联委外加工单ID';
COMMENT ON COLUMN subcontract_pick.order_no IS '关联委外加工单号';
COMMENT ON COLUMN subcontract_pick.supplier_id IS '供应商ID';
COMMENT ON COLUMN subcontract_pick.supplier_name IS '供应商名称';
COMMENT ON COLUMN subcontract_pick.pick_date IS '领料日期';
COMMENT ON COLUMN subcontract_pick.status IS '状态:0草稿 1待审核 2已生效 3已出库';
COMMENT ON COLUMN subcontract_pick.total_quantity IS '总数量';
COMMENT ON COLUMN subcontract_pick.remark IS '备注';
COMMENT ON COLUMN subcontract_pick.create_by IS '创建人';
COMMENT ON COLUMN subcontract_pick.create_time IS '创建时间';
COMMENT ON COLUMN subcontract_pick.update_time IS '更新时间';

-- 4. 委外领料单明细表
CREATE TABLE IF NOT EXISTS subcontract_pick_item (
    id BIGSERIAL PRIMARY KEY,
    pick_id BIGINT NOT NULL,
    order_item_id BIGINT,
    material_id BIGINT,
    material_code VARCHAR(64),
    material_name VARCHAR(256),
    specification VARCHAR(256),
    unit VARCHAR(32),
    quantity NUMERIC(18,4) DEFAULT 0,
    warehouse_id BIGINT,
    warehouse_name VARCHAR(128),
    remark VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subcontract_pick_item_pick ON subcontract_pick_item(pick_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_pick_item_material ON subcontract_pick_item(material_id);

COMMENT ON TABLE subcontract_pick_item IS '委外领料单明细表';
COMMENT ON COLUMN subcontract_pick_item.pick_id IS '领料单ID';
COMMENT ON COLUMN subcontract_pick_item.order_item_id IS '加工单明细ID';
COMMENT ON COLUMN subcontract_pick_item.material_id IS '物料ID';
COMMENT ON COLUMN subcontract_pick_item.material_code IS '物料编码';
COMMENT ON COLUMN subcontract_pick_item.material_name IS '物料名称';
COMMENT ON COLUMN subcontract_pick_item.specification IS '规格型号';
COMMENT ON COLUMN subcontract_pick_item.unit IS '单位';
COMMENT ON COLUMN subcontract_pick_item.quantity IS '数量';
COMMENT ON COLUMN subcontract_pick_item.warehouse_id IS '仓库ID';
COMMENT ON COLUMN subcontract_pick_item.warehouse_name IS '仓库名称';
COMMENT ON COLUMN subcontract_pick_item.remark IS '备注';
COMMENT ON COLUMN subcontract_pick_item.create_time IS '创建时间';
COMMENT ON COLUMN subcontract_pick_item.update_time IS '更新时间';

-- 5. 委外补料单主表
CREATE TABLE IF NOT EXISTS subcontract_supplement (
    id BIGSERIAL PRIMARY KEY,
    supplement_no VARCHAR(64) NOT NULL,
    order_id BIGINT,
    order_no VARCHAR(64),
    supplier_id BIGINT,
    supplier_name VARCHAR(128),
    supplement_date DATE,
    status SMALLINT DEFAULT 0,
    total_quantity NUMERIC(18,4) DEFAULT 0,
    reason VARCHAR(512),
    remark VARCHAR(512),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_subcontract_supplement_no UNIQUE (supplement_no)
);

CREATE INDEX IF NOT EXISTS idx_subcontract_supplement_order ON subcontract_supplement(order_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_supplement_supplier ON subcontract_supplement(supplier_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_supplement_status ON subcontract_supplement(status);
CREATE INDEX IF NOT EXISTS idx_subcontract_supplement_create_time ON subcontract_supplement(create_time);

COMMENT ON TABLE subcontract_supplement IS '委外补料单主表';
COMMENT ON COLUMN subcontract_supplement.supplement_no IS '补料单号';
COMMENT ON COLUMN subcontract_supplement.order_id IS '关联委外加工单ID';
COMMENT ON COLUMN subcontract_supplement.order_no IS '关联委外加工单号';
COMMENT ON COLUMN subcontract_supplement.supplier_id IS '供应商ID';
COMMENT ON COLUMN subcontract_supplement.supplier_name IS '供应商名称';
COMMENT ON COLUMN subcontract_supplement.supplement_date IS '补料日期';
COMMENT ON COLUMN subcontract_supplement.status IS '状态:0草稿 1待审核 2已生效 3已出库';
COMMENT ON COLUMN subcontract_supplement.total_quantity IS '总数量';
COMMENT ON COLUMN subcontract_supplement.reason IS '补料原因';
COMMENT ON COLUMN subcontract_supplement.remark IS '备注';
COMMENT ON COLUMN subcontract_supplement.create_by IS '创建人';
COMMENT ON COLUMN subcontract_supplement.create_time IS '创建时间';
COMMENT ON COLUMN subcontract_supplement.update_time IS '更新时间';

-- 6. 委外补料单明细表
CREATE TABLE IF NOT EXISTS subcontract_supplement_item (
    id BIGSERIAL PRIMARY KEY,
    supplement_id BIGINT NOT NULL,
    order_item_id BIGINT,
    material_id BIGINT,
    material_code VARCHAR(64),
    material_name VARCHAR(256),
    specification VARCHAR(256),
    unit VARCHAR(32),
    quantity NUMERIC(18,4) DEFAULT 0,
    warehouse_id BIGINT,
    warehouse_name VARCHAR(128),
    remark VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subcontract_supplement_item_supp ON subcontract_supplement_item(supplement_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_supplement_item_material ON subcontract_supplement_item(material_id);

COMMENT ON TABLE subcontract_supplement_item IS '委外补料单明细表';
COMMENT ON COLUMN subcontract_supplement_item.supplement_id IS '补料单ID';
COMMENT ON COLUMN subcontract_supplement_item.order_item_id IS '加工单明细ID';
COMMENT ON COLUMN subcontract_supplement_item.material_id IS '物料ID';
COMMENT ON COLUMN subcontract_supplement_item.material_code IS '物料编码';
COMMENT ON COLUMN subcontract_supplement_item.material_name IS '物料名称';
COMMENT ON COLUMN subcontract_supplement_item.specification IS '规格型号';
COMMENT ON COLUMN subcontract_supplement_item.unit IS '单位';
COMMENT ON COLUMN subcontract_supplement_item.quantity IS '数量';
COMMENT ON COLUMN subcontract_supplement_item.warehouse_id IS '仓库ID';
COMMENT ON COLUMN subcontract_supplement_item.warehouse_name IS '仓库名称';
COMMENT ON COLUMN subcontract_supplement_item.remark IS '备注';
COMMENT ON COLUMN subcontract_supplement_item.create_time IS '创建时间';
COMMENT ON COLUMN subcontract_supplement_item.update_time IS '更新时间';

-- 7. 委外入库单主表
CREATE TABLE IF NOT EXISTS subcontract_inbound (
    id BIGSERIAL PRIMARY KEY,
    inbound_no VARCHAR(64) NOT NULL,
    order_id BIGINT,
    order_no VARCHAR(64),
    supplier_id BIGINT,
    supplier_name VARCHAR(128),
    inbound_date DATE,
    status SMALLINT DEFAULT 0,
    total_quantity NUMERIC(18,4) DEFAULT 0,
    processing_fee NUMERIC(18,4) DEFAULT 0,
    remark VARCHAR(512),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_subcontract_inbound_no UNIQUE (inbound_no)
);

CREATE INDEX IF NOT EXISTS idx_subcontract_inbound_order ON subcontract_inbound(order_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_inbound_supplier ON subcontract_inbound(supplier_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_inbound_status ON subcontract_inbound(status);
CREATE INDEX IF NOT EXISTS idx_subcontract_inbound_create_time ON subcontract_inbound(create_time);

COMMENT ON TABLE subcontract_inbound IS '委外入库单主表';
COMMENT ON COLUMN subcontract_inbound.inbound_no IS '入库单号';
COMMENT ON COLUMN subcontract_inbound.order_id IS '关联委外加工单ID';
COMMENT ON COLUMN subcontract_inbound.order_no IS '关联委外加工单号';
COMMENT ON COLUMN subcontract_inbound.supplier_id IS '供应商ID';
COMMENT ON COLUMN subcontract_inbound.supplier_name IS '供应商名称';
COMMENT ON COLUMN subcontract_inbound.inbound_date IS '入库日期';
COMMENT ON COLUMN subcontract_inbound.status IS '状态:0草稿 1待审核 2已生效 3已入库';
COMMENT ON COLUMN subcontract_inbound.total_quantity IS '总数量';
COMMENT ON COLUMN subcontract_inbound.processing_fee IS '加工费';
COMMENT ON COLUMN subcontract_inbound.remark IS '备注';
COMMENT ON COLUMN subcontract_inbound.create_by IS '创建人';
COMMENT ON COLUMN subcontract_inbound.create_time IS '创建时间';
COMMENT ON COLUMN subcontract_inbound.update_time IS '更新时间';

-- 8. 委外入库单明细表
CREATE TABLE IF NOT EXISTS subcontract_inbound_item (
    id BIGSERIAL PRIMARY KEY,
    inbound_id BIGINT NOT NULL,
    order_item_id BIGINT,
    material_id BIGINT,
    material_code VARCHAR(64),
    material_name VARCHAR(256),
    specification VARCHAR(256),
    unit VARCHAR(32),
    quantity NUMERIC(18,4) DEFAULT 0,
    warehouse_id BIGINT,
    warehouse_name VARCHAR(128),
    processing_fee NUMERIC(18,4) DEFAULT 0,
    remark VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subcontract_inbound_item_inbound ON subcontract_inbound_item(inbound_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_inbound_item_material ON subcontract_inbound_item(material_id);

COMMENT ON TABLE subcontract_inbound_item IS '委外入库单明细表';
COMMENT ON COLUMN subcontract_inbound_item.inbound_id IS '入库单ID';
COMMENT ON COLUMN subcontract_inbound_item.order_item_id IS '加工单明细ID';
COMMENT ON COLUMN subcontract_inbound_item.material_id IS '物料ID';
COMMENT ON COLUMN subcontract_inbound_item.material_code IS '物料编码';
COMMENT ON COLUMN subcontract_inbound_item.material_name IS '物料名称';
COMMENT ON COLUMN subcontract_inbound_item.specification IS '规格型号';
COMMENT ON COLUMN subcontract_inbound_item.unit IS '单位';
COMMENT ON COLUMN subcontract_inbound_item.quantity IS '数量';
COMMENT ON COLUMN subcontract_inbound_item.warehouse_id IS '仓库ID';
COMMENT ON COLUMN subcontract_inbound_item.warehouse_name IS '仓库名称';
COMMENT ON COLUMN subcontract_inbound_item.processing_fee IS '加工费';
COMMENT ON COLUMN subcontract_inbound_item.remark IS '备注';
COMMENT ON COLUMN subcontract_inbound_item.create_time IS '创建时间';
COMMENT ON COLUMN subcontract_inbound_item.update_time IS '更新时间';

-- 9. 委外退料单主表
CREATE TABLE IF NOT EXISTS subcontract_return (
    id BIGSERIAL PRIMARY KEY,
    return_no VARCHAR(64) NOT NULL,
    order_id BIGINT,
    order_no VARCHAR(64),
    supplier_id BIGINT,
    supplier_name VARCHAR(128),
    return_date DATE,
    status SMALLINT DEFAULT 0,
    total_quantity NUMERIC(18,4) DEFAULT 0,
    remark VARCHAR(512),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_subcontract_return_no UNIQUE (return_no)
);

CREATE INDEX IF NOT EXISTS idx_subcontract_return_order ON subcontract_return(order_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_return_supplier ON subcontract_return(supplier_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_return_status ON subcontract_return(status);
CREATE INDEX IF NOT EXISTS idx_subcontract_return_create_time ON subcontract_return(create_time);

COMMENT ON TABLE subcontract_return IS '委外退料单主表';
COMMENT ON COLUMN subcontract_return.return_no IS '退料单号';
COMMENT ON COLUMN subcontract_return.order_id IS '关联委外加工单ID';
COMMENT ON COLUMN subcontract_return.order_no IS '关联委外加工单号';
COMMENT ON COLUMN subcontract_return.supplier_id IS '供应商ID';
COMMENT ON COLUMN subcontract_return.supplier_name IS '供应商名称';
COMMENT ON COLUMN subcontract_return.return_date IS '退料日期';
COMMENT ON COLUMN subcontract_return.status IS '状态:0草稿 1待审核 2已生效 3已退库';
COMMENT ON COLUMN subcontract_return.total_quantity IS '总数量';
COMMENT ON COLUMN subcontract_return.remark IS '备注';
COMMENT ON COLUMN subcontract_return.create_by IS '创建人';
COMMENT ON COLUMN subcontract_return.create_time IS '创建时间';
COMMENT ON COLUMN subcontract_return.update_time IS '更新时间';

-- 10. 委外退料单明细表
CREATE TABLE IF NOT EXISTS subcontract_return_item (
    id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL,
    order_item_id BIGINT,
    material_id BIGINT,
    material_code VARCHAR(64),
    material_name VARCHAR(256),
    specification VARCHAR(256),
    unit VARCHAR(32),
    quantity NUMERIC(18,4) DEFAULT 0,
    warehouse_id BIGINT,
    warehouse_name VARCHAR(128),
    remark VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subcontract_return_item_return ON subcontract_return_item(return_id);
CREATE INDEX IF NOT EXISTS idx_subcontract_return_item_material ON subcontract_return_item(material_id);

COMMENT ON TABLE subcontract_return_item IS '委外退料单明细表';
COMMENT ON COLUMN subcontract_return_item.return_id IS '退料单ID';
COMMENT ON COLUMN subcontract_return_item.order_item_id IS '加工单明细ID';
COMMENT ON COLUMN subcontract_return_item.material_id IS '物料ID';
COMMENT ON COLUMN subcontract_return_item.material_code IS '物料编码';
COMMENT ON COLUMN subcontract_return_item.material_name IS '物料名称';
COMMENT ON COLUMN subcontract_return_item.specification IS '规格型号';
COMMENT ON COLUMN subcontract_return_item.unit IS '单位';
COMMENT ON COLUMN subcontract_return_item.quantity IS '数量';
COMMENT ON COLUMN subcontract_return_item.warehouse_id IS '仓库ID';
COMMENT ON COLUMN subcontract_return_item.warehouse_name IS '仓库名称';
COMMENT ON COLUMN subcontract_return_item.remark IS '备注';
COMMENT ON COLUMN subcontract_return_item.create_time IS '创建时间';
COMMENT ON COLUMN subcontract_return_item.update_time IS '更新时间';
