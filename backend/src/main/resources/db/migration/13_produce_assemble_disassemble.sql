-- =====================================================
-- 生产组装单和拆卸单数据库表
-- 创建日期: 2026-07-09
-- 说明: 包含组装单和拆卸单的主表和明细表
-- =====================================================

-- =====================================================
-- 1. 组装单主表 (produce_assemble)
-- 说明: 记录生产组装单基本信息
-- =====================================================
CREATE TABLE IF NOT EXISTS produce_assemble (
    id BIGSERIAL PRIMARY KEY,
    serial_no VARCHAR(50) NOT NULL UNIQUE COMMENT '单据编号 AS-yyyyMMdd-XXXX',
    dept VARCHAR(100) COMMENT '生产部门',
    wh_id BIGINT COMMENT '成品仓库ID',
    wh_name VARCHAR(100) COMMENT '成品仓库名称',
    wh_id_bom BIGINT COMMENT '原料仓库ID',
    wh_name_bom VARCHAR(100) COMMENT '原料仓库名称',
    is_tax BOOLEAN DEFAULT false COMMENT '是否含税',
    tax_type INTEGER DEFAULT 0 COMMENT '计税方式 0-不含税 1-含税',
    currency_id BIGINT COMMENT '币别ID',
    currency_rate DECIMAL(18,4) DEFAULT 1.0000 COMMENT '汇率',
    amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '单据金额',
    base_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '本位币金额',
    tax_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '税额',
    tax_rate DECIMAL(5,2) COMMENT '税率',
    print_num INTEGER DEFAULT 0 COMMENT '打印次数',
    images TEXT COMMENT '图片',
    files TEXT COMMENT '附件JSON',
    notes TEXT COMMENT '备注',
    dateline TIMESTAMP COMMENT '业务日期',
    status INTEGER DEFAULT 0 COMMENT '状态 0-草稿 1-待审批 3-作废 6-生效',
    flow_id VARCHAR(100) COMMENT '流程实例ID',
    flow_status VARCHAR(50) COMMENT '流程状态',
    flow_status_name VARCHAR(100) COMMENT '流程状态名称',
    flow_operator VARCHAR(200) COMMENT '流程操作人',
    etime TIMESTAMP COMMENT '生效时间',
    creator VARCHAR(100) COMMENT '创建人',
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    modifier VARCHAR(100) COMMENT '修改人',
    utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    salesman VARCHAR(100) COMMENT '业务员',
    salesman_str VARCHAR(200) COMMENT '业务员名称',
    source_type VARCHAR(50) COMMENT '来源类型',
    source_id BIGINT COMMENT '来源单据ID'
);

COMMENT ON TABLE produce_assemble IS '生产组装单主表';
COMMENT ON COLUMN produce_assemble.id IS '主键ID';
COMMENT ON COLUMN produce_assemble.serial_no IS '单据编号 AS-yyyyMMdd-XXXX';
COMMENT ON COLUMN produce_assemble.dept IS '生产部门';
COMMENT ON COLUMN produce_assemble.wh_id IS '成品仓库ID';
COMMENT ON COLUMN produce_assemble.wh_name IS '成品仓库名称';
COMMENT ON COLUMN produce_assemble.wh_id_bom IS '原料仓库ID';
COMMENT ON COLUMN produce_assemble.wh_name_bom IS '原料仓库名称';
COMMENT ON COLUMN produce_assemble.is_tax IS '是否含税';
COMMENT ON COLUMN produce_assemble.tax_type IS '计税方式';
COMMENT ON COLUMN produce_assemble.currency_id IS '币别ID';
COMMENT ON COLUMN produce_assemble.currency_rate IS '汇率';
COMMENT ON COLUMN produce_assemble.amount IS '单据金额';
COMMENT ON COLUMN produce_assemble.base_amount IS '本位币金额';
COMMENT ON COLUMN produce_assemble.tax_amount IS '税额';
COMMENT ON COLUMN produce_assemble.tax_rate IS '税率';
COMMENT ON COLUMN produce_assemble.print_num IS '打印次数';
COMMENT ON COLUMN produce_assemble.images IS '图片';
COMMENT ON COLUMN produce_assemble.files IS '附件JSON';
COMMENT ON COLUMN produce_assemble.notes IS '备注';
COMMENT ON COLUMN produce_assemble.dateline IS '业务日期';
COMMENT ON COLUMN produce_assemble.status IS '状态';
COMMENT ON COLUMN produce_assemble.flow_id IS '流程实例ID';
COMMENT ON COLUMN produce_assemble.flow_status IS '流程状态';
COMMENT ON COLUMN produce_assemble.flow_status_name IS '流程状态名称';
COMMENT ON COLUMN produce_assemble.flow_operator IS '流程操作人';
COMMENT ON COLUMN produce_assemble.etime IS '生效时间';
COMMENT ON COLUMN produce_assemble.creator IS '创建人';
COMMENT ON COLUMN produce_assemble.ctime IS '创建时间';
COMMENT ON COLUMN produce_assemble.modifier IS '修改人';
COMMENT ON COLUMN produce_assemble.utime IS '修改时间';
COMMENT ON COLUMN produce_assemble.salesman IS '业务员';
COMMENT ON COLUMN produce_assemble.salesman_str IS '业务员名称';
COMMENT ON COLUMN produce_assemble.source_type IS '来源类型';
COMMENT ON COLUMN produce_assemble.source_id IS '来源单据ID';

CREATE INDEX idx_produce_assemble_serial_no ON produce_assemble(serial_no);
CREATE INDEX idx_produce_assemble_status ON produce_assemble(status);
CREATE INDEX idx_produce_assemble_dateline ON produce_assemble(dateline);
CREATE INDEX idx_produce_assemble_creator ON produce_assemble(creator);

-- =====================================================
-- 2. 组装单明细表 - 成品入库 (produce_assemble_detail)
-- 说明: 记录组装单成品明细信息
-- =====================================================
CREATE TABLE IF NOT EXISTS produce_assemble_detail (
    id BIGSERIAL PRIMARY KEY,
    assemble_id BIGINT NOT NULL COMMENT '组装单ID',
    row_id VARCHAR(50) COMMENT '行ID',
    product_id BIGINT COMMENT '物料ID',
    product_code VARCHAR(100) COMMENT '物料编码',
    product_name VARCHAR(200) COMMENT '物料名称',
    specification VARCHAR(200) COMMENT '规格型号',
    unit VARCHAR(50) COMMENT '单位',
    unit_id BIGINT COMMENT '单位ID',
    basic_unit VARCHAR(50) COMMENT '主单位',
    basic_unit_id BIGINT COMMENT '主单位ID',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    basic_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '主单位数量',
    wh_id BIGINT COMMENT '仓库ID',
    wh_name VARCHAR(100) COMMENT '仓库名称',
    storage_id BIGINT COMMENT '货位ID',
    storage_name VARCHAR(100) COMMENT '货位名称',
    price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '金额',
    tax_rate DECIMAL(5,2) COMMENT '税率',
    tax_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '税额',
    source_type VARCHAR(50) COMMENT '来源类型',
    source_id BIGINT COMMENT '来源单据ID',
    source_item_id BIGINT COMMENT '来源明细ID',
    source_serial_no VARCHAR(100) COMMENT '来源单据编号',
    notes TEXT COMMENT '备注',
    sort_order INTEGER DEFAULT 0 COMMENT '排序'
);

COMMENT ON TABLE produce_assemble_detail IS '组装单明细表-成品入库';
COMMENT ON COLUMN produce_assemble_detail.id IS '主键ID';
COMMENT ON COLUMN produce_assemble_detail.assemble_id IS '组装单ID';
COMMENT ON COLUMN produce_assemble_detail.row_id IS '行ID';
COMMENT ON COLUMN produce_assemble_detail.product_id IS '物料ID';
COMMENT ON COLUMN produce_assemble_detail.product_code IS '物料编码';
COMMENT ON COLUMN produce_assemble_detail.product_name IS '物料名称';
COMMENT ON COLUMN produce_assemble_detail.specification IS '规格型号';
COMMENT ON COLUMN produce_assemble_detail.unit IS '单位';
COMMENT ON COLUMN produce_assemble_detail.unit_id IS '单位ID';
COMMENT ON COLUMN produce_assemble_detail.basic_unit IS '主单位';
COMMENT ON COLUMN produce_assemble_detail.basic_unit_id IS '主单位ID';
COMMENT ON COLUMN produce_assemble_detail.quantity IS '数量';
COMMENT ON COLUMN produce_assemble_detail.basic_quantity IS '主单位数量';
COMMENT ON COLUMN produce_assemble_detail.wh_id IS '仓库ID';
COMMENT ON COLUMN produce_assemble_detail.wh_name IS '仓库名称';
COMMENT ON COLUMN produce_assemble_detail.storage_id IS '货位ID';
COMMENT ON COLUMN produce_assemble_detail.storage_name IS '货位名称';
COMMENT ON COLUMN produce_assemble_detail.price IS '单价';
COMMENT ON COLUMN produce_assemble_detail.amount IS '金额';
COMMENT ON COLUMN produce_assemble_detail.tax_rate IS '税率';
COMMENT ON COLUMN produce_assemble_detail.tax_amount IS '税额';
COMMENT ON COLUMN produce_assemble_detail.source_type IS '来源类型';
COMMENT ON COLUMN produce_assemble_detail.source_id IS '来源单据ID';
COMMENT ON COLUMN produce_assemble_detail.source_item_id IS '来源明细ID';
COMMENT ON COLUMN produce_assemble_detail.source_serial_no IS '来源单据编号';
COMMENT ON COLUMN produce_assemble_detail.notes IS '备注';
COMMENT ON COLUMN produce_assemble_detail.sort_order IS '排序';

CREATE INDEX idx_produce_assemble_detail_assemble_id ON produce_assemble_detail(assemble_id);
CREATE INDEX idx_produce_assemble_detail_product_id ON produce_assemble_detail(product_id);

-- =====================================================
-- 3. 组装单BOM明细表 - 原料出库 (produce_assemble_bom)
-- 说明: 记录组装单BOM原料明细信息
-- =====================================================
CREATE TABLE IF NOT EXISTS produce_assemble_bom (
    id BIGSERIAL PRIMARY KEY,
    assemble_id BIGINT NOT NULL COMMENT '组装单ID',
    parent_row_id VARCHAR(50) COMMENT '父行ID',
    row_id VARCHAR(50) COMMENT '行ID',
    product_id BIGINT COMMENT '物料ID',
    product_code VARCHAR(100) COMMENT '物料编码',
    product_name VARCHAR(200) COMMENT '物料名称',
    specification VARCHAR(200) COMMENT '规格型号',
    unit VARCHAR(50) COMMENT '单位',
    unit_id BIGINT COMMENT '单位ID',
    basic_unit VARCHAR(50) COMMENT '主单位',
    basic_unit_id BIGINT COMMENT '主单位ID',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    basic_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '主单位数量',
    wh_id BIGINT COMMENT '仓库ID',
    wh_name VARCHAR(100) COMMENT '仓库名称',
    storage_id BIGINT COMMENT '货位ID',
    storage_name VARCHAR(100) COMMENT '货位名称',
    price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '金额',
    tax_rate DECIMAL(5,2) COMMENT '税率',
    tax_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '税额',
    source_type VARCHAR(50) COMMENT '来源类型',
    source_id BIGINT COMMENT '来源单据ID',
    source_item_id BIGINT COMMENT '来源明细ID',
    source_serial_no VARCHAR(100) COMMENT '来源单据编号',
    notes TEXT COMMENT '备注',
    sort_order INTEGER DEFAULT 0 COMMENT '排序'
);

COMMENT ON TABLE produce_assemble_bom IS '组装单BOM明细表-原料出库';
COMMENT ON COLUMN produce_assemble_bom.id IS '主键ID';
COMMENT ON COLUMN produce_assemble_bom.assemble_id IS '组装单ID';
COMMENT ON COLUMN produce_assemble_bom.parent_row_id IS '父行ID';
COMMENT ON COLUMN produce_assemble_bom.row_id IS '行ID';
COMMENT ON COLUMN produce_assemble_bom.product_id IS '物料ID';
COMMENT ON COLUMN produce_assemble_bom.product_code IS '物料编码';
COMMENT ON COLUMN produce_assemble_bom.product_name IS '物料名称';
COMMENT ON COLUMN produce_assemble_bom.specification IS '规格型号';
COMMENT ON COLUMN produce_assemble_bom.unit IS '单位';
COMMENT ON COLUMN produce_assemble_bom.unit_id IS '单位ID';
COMMENT ON COLUMN produce_assemble_bom.basic_unit IS '主单位';
COMMENT ON COLUMN produce_assemble_bom.basic_unit_id IS '主单位ID';
COMMENT ON COLUMN produce_assemble_bom.quantity IS '数量';
COMMENT ON COLUMN produce_assemble_bom.basic_quantity IS '主单位数量';
COMMENT ON COLUMN produce_assemble_bom.wh_id IS '仓库ID';
COMMENT ON COLUMN produce_assemble_bom.wh_name IS '仓库名称';
COMMENT ON COLUMN produce_assemble_bom.storage_id IS '货位ID';
COMMENT ON COLUMN produce_assemble_bom.storage_name IS '货位名称';
COMMENT ON COLUMN produce_assemble_bom.price IS '单价';
COMMENT ON COLUMN produce_assemble_bom.amount IS '金额';
COMMENT ON COLUMN produce_assemble_bom.tax_rate IS '税率';
COMMENT ON COLUMN produce_assemble_bom.tax_amount IS '税额';
COMMENT ON COLUMN produce_assemble_bom.source_type IS '来源类型';
COMMENT ON COLUMN produce_assemble_bom.source_id IS '来源单据ID';
COMMENT ON COLUMN produce_assemble_bom.source_item_id IS '来源明细ID';
COMMENT ON COLUMN produce_assemble_bom.source_serial_no IS '来源单据编号';
COMMENT ON COLUMN produce_assemble_bom.notes IS '备注';
COMMENT ON COLUMN produce_assemble_bom.sort_order IS '排序';

CREATE INDEX idx_produce_assemble_bom_assemble_id ON produce_assemble_bom(assemble_id);
CREATE INDEX idx_produce_assemble_bom_product_id ON produce_assemble_bom(product_id);
CREATE INDEX idx_produce_assemble_bom_parent_row_id ON produce_assemble_bom(parent_row_id);

-- =====================================================
-- 4. 拆卸单主表 (produce_disassemble)
-- 说明: 记录生产拆卸单基本信息
-- =====================================================
CREATE TABLE IF NOT EXISTS produce_disassemble (
    id BIGSERIAL PRIMARY KEY,
    serial_no VARCHAR(50) NOT NULL UNIQUE COMMENT '单据编号 DS-yyyyMMdd-XXXX',
    dept VARCHAR(100) COMMENT '生产部门',
    wh_id BIGINT COMMENT '成品仓库ID',
    wh_name VARCHAR(100) COMMENT '成品仓库名称',
    wh_id_bom BIGINT COMMENT '原料仓库ID',
    wh_name_bom VARCHAR(100) COMMENT '原料仓库名称',
    is_tax BOOLEAN DEFAULT false COMMENT '是否含税',
    tax_type INTEGER DEFAULT 0 COMMENT '计税方式 0-不含税 1-含税',
    currency_id BIGINT COMMENT '币别ID',
    currency_rate DECIMAL(18,4) DEFAULT 1.0000 COMMENT '汇率',
    amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '单据金额',
    base_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '本位币金额',
    tax_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '税额',
    tax_rate DECIMAL(5,2) COMMENT '税率',
    print_num INTEGER DEFAULT 0 COMMENT '打印次数',
    images TEXT COMMENT '图片',
    files TEXT COMMENT '附件JSON',
    notes TEXT COMMENT '备注',
    dateline TIMESTAMP COMMENT '业务日期',
    status INTEGER DEFAULT 0 COMMENT '状态 0-草稿 1-待审批 3-作废 6-生效',
    flow_id VARCHAR(100) COMMENT '流程实例ID',
    flow_status VARCHAR(50) COMMENT '流程状态',
    flow_status_name VARCHAR(100) COMMENT '流程状态名称',
    flow_operator VARCHAR(200) COMMENT '流程操作人',
    etime TIMESTAMP COMMENT '生效时间',
    creator VARCHAR(100) COMMENT '创建人',
    ctime TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    modifier VARCHAR(100) COMMENT '修改人',
    utime TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    salesman VARCHAR(100) COMMENT '业务员',
    salesman_str VARCHAR(200) COMMENT '业务员名称',
    source_type VARCHAR(50) COMMENT '来源类型',
    source_id BIGINT COMMENT '来源单据ID'
);

COMMENT ON TABLE produce_disassemble IS '生产拆卸单主表';
COMMENT ON COLUMN produce_disassemble.id IS '主键ID';
COMMENT ON COLUMN produce_disassemble.serial_no IS '单据编号 DS-yyyyMMdd-XXXX';
COMMENT ON COLUMN produce_disassemble.dept IS '生产部门';
COMMENT ON COLUMN produce_disassemble.wh_id IS '成品仓库ID';
COMMENT ON COLUMN produce_disassemble.wh_name IS '成品仓库名称';
COMMENT ON COLUMN produce_disassemble.wh_id_bom IS '原料仓库ID';
COMMENT ON COLUMN produce_disassemble.wh_name_bom IS '原料仓库名称';
COMMENT ON COLUMN produce_disassemble.is_tax IS '是否含税';
COMMENT ON COLUMN produce_disassemble.tax_type IS '计税方式';
COMMENT ON COLUMN produce_disassemble.currency_id IS '币别ID';
COMMENT ON COLUMN produce_disassemble.currency_rate IS '汇率';
COMMENT ON COLUMN produce_disassemble.amount IS '单据金额';
COMMENT ON COLUMN produce_disassemble.base_amount IS '本位币金额';
COMMENT ON COLUMN produce_disassemble.tax_amount IS '税额';
COMMENT ON COLUMN produce_disassemble.tax_rate IS '税率';
COMMENT ON COLUMN produce_disassemble.print_num IS '打印次数';
COMMENT ON COLUMN produce_disassemble.images IS '图片';
COMMENT ON COLUMN produce_disassemble.files IS '附件JSON';
COMMENT ON COLUMN produce_disassemble.notes IS '备注';
COMMENT ON COLUMN produce_disassemble.dateline IS '业务日期';
COMMENT ON COLUMN produce_disassemble.status IS '状态';
COMMENT ON COLUMN produce_disassemble.flow_id IS '流程实例ID';
COMMENT ON COLUMN produce_disassemble.flow_status IS '流程状态';
COMMENT ON COLUMN produce_disassemble.flow_status_name IS '流程状态名称';
COMMENT ON COLUMN produce_disassemble.flow_operator IS '流程操作人';
COMMENT ON COLUMN produce_disassemble.etime IS '生效时间';
COMMENT ON COLUMN produce_disassemble.creator IS '创建人';
COMMENT ON COLUMN produce_disassemble.ctime IS '创建时间';
COMMENT ON COLUMN produce_disassemble.modifier IS '修改人';
COMMENT ON COLUMN produce_disassemble.utime IS '修改时间';
COMMENT ON COLUMN produce_disassemble.salesman IS '业务员';
COMMENT ON COLUMN produce_disassemble.salesman_str IS '业务员名称';
COMMENT ON COLUMN produce_disassemble.source_type IS '来源类型';
COMMENT ON COLUMN produce_disassemble.source_id IS '来源单据ID';

CREATE INDEX idx_produce_disassemble_serial_no ON produce_disassemble(serial_no);
CREATE INDEX idx_produce_disassemble_status ON produce_disassemble(status);
CREATE INDEX idx_produce_disassemble_dateline ON produce_disassemble(dateline);
CREATE INDEX idx_produce_disassemble_creator ON produce_disassemble(creator);

-- =====================================================
-- 5. 拆卸单明细表 - 成品出库 (produce_disassemble_detail)
-- 说明: 记录拆卸单成品明细信息
-- =====================================================
CREATE TABLE IF NOT EXISTS produce_disassemble_detail (
    id BIGSERIAL PRIMARY KEY,
    disassemble_id BIGINT NOT NULL COMMENT '拆卸单ID',
    row_id VARCHAR(50) COMMENT '行ID',
    product_id BIGINT COMMENT '物料ID',
    product_code VARCHAR(100) COMMENT '物料编码',
    product_name VARCHAR(200) COMMENT '物料名称',
    specification VARCHAR(200) COMMENT '规格型号',
    unit VARCHAR(50) COMMENT '单位',
    unit_id BIGINT COMMENT '单位ID',
    basic_unit VARCHAR(50) COMMENT '主单位',
    basic_unit_id BIGINT COMMENT '主单位ID',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    basic_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '主单位数量',
    wh_id BIGINT COMMENT '仓库ID',
    wh_name VARCHAR(100) COMMENT '仓库名称',
    storage_id BIGINT COMMENT '货位ID',
    storage_name VARCHAR(100) COMMENT '货位名称',
    price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '金额',
    tax_rate DECIMAL(5,2) COMMENT '税率',
    tax_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '税额',
    source_type VARCHAR(50) COMMENT '来源类型',
    source_id BIGINT COMMENT '来源单据ID',
    source_item_id BIGINT COMMENT '来源明细ID',
    source_serial_no VARCHAR(100) COMMENT '来源单据编号',
    notes TEXT COMMENT '备注',
    sort_order INTEGER DEFAULT 0 COMMENT '排序'
);

COMMENT ON TABLE produce_disassemble_detail IS '拆卸单明细表-成品出库';
COMMENT ON COLUMN produce_disassemble_detail.id IS '主键ID';
COMMENT ON COLUMN produce_disassemble_detail.disassemble_id IS '拆卸单ID';
COMMENT ON COLUMN produce_disassemble_detail.row_id IS '行ID';
COMMENT ON COLUMN produce_disassemble_detail.product_id IS '物料ID';
COMMENT ON COLUMN produce_disassemble_detail.product_code IS '物料编码';
COMMENT ON COLUMN produce_disassemble_detail.product_name IS '物料名称';
COMMENT ON COLUMN produce_disassemble_detail.specification IS '规格型号';
COMMENT ON COLUMN produce_disassemble_detail.unit IS '单位';
COMMENT ON COLUMN produce_disassemble_detail.unit_id IS '单位ID';
COMMENT ON COLUMN produce_disassemble_detail.basic_unit IS '主单位';
COMMENT ON COLUMN produce_disassemble_detail.basic_unit_id IS '主单位ID';
COMMENT ON COLUMN produce_disassemble_detail.quantity IS '数量';
COMMENT ON COLUMN produce_disassemble_detail.basic_quantity IS '主单位数量';
COMMENT ON COLUMN produce_disassemble_detail.wh_id IS '仓库ID';
COMMENT ON COLUMN produce_disassemble_detail.wh_name IS '仓库名称';
COMMENT ON COLUMN produce_disassemble_detail.storage_id IS '货位ID';
COMMENT ON COLUMN produce_disassemble_detail.storage_name IS '货位名称';
COMMENT ON COLUMN produce_disassemble_detail.price IS '单价';
COMMENT ON COLUMN produce_disassemble_detail.amount IS '金额';
COMMENT ON COLUMN produce_disassemble_detail.tax_rate IS '税率';
COMMENT ON COLUMN produce_disassemble_detail.tax_amount IS '税额';
COMMENT ON COLUMN produce_disassemble_detail.source_type IS '来源类型';
COMMENT ON COLUMN produce_disassemble_detail.source_id IS '来源单据ID';
COMMENT ON COLUMN produce_disassemble_detail.source_item_id IS '来源明细ID';
COMMENT ON COLUMN produce_disassemble_detail.source_serial_no IS '来源单据编号';
COMMENT ON COLUMN produce_disassemble_detail.notes IS '备注';
COMMENT ON COLUMN produce_disassemble_detail.sort_order IS '排序';

CREATE INDEX idx_produce_disassemble_detail_disassemble_id ON produce_disassemble_detail(disassemble_id);
CREATE INDEX idx_produce_disassemble_detail_product_id ON produce_disassemble_detail(product_id);

-- =====================================================
-- 6. 拆卸单BOM明细表 - 原料入库 (produce_disassemble_bom)
-- 说明: 记录拆卸单BOM原料明细信息
-- =====================================================
CREATE TABLE IF NOT EXISTS produce_disassemble_bom (
    id BIGSERIAL PRIMARY KEY,
    disassemble_id BIGINT NOT NULL COMMENT '拆卸单ID',
    parent_row_id VARCHAR(50) COMMENT '父行ID',
    row_id VARCHAR(50) COMMENT '行ID',
    product_id BIGINT COMMENT '物料ID',
    product_code VARCHAR(100) COMMENT '物料编码',
    product_name VARCHAR(200) COMMENT '物料名称',
    specification VARCHAR(200) COMMENT '规格型号',
    unit VARCHAR(50) COMMENT '单位',
    unit_id BIGINT COMMENT '单位ID',
    basic_unit VARCHAR(50) COMMENT '主单位',
    basic_unit_id BIGINT COMMENT '主单位ID',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    basic_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '主单位数量',
    wh_id BIGINT COMMENT '仓库ID',
    wh_name VARCHAR(100) COMMENT '仓库名称',
    storage_id BIGINT COMMENT '货位ID',
    storage_name VARCHAR(100) COMMENT '货位名称',
    price DECIMAL(18,4) DEFAULT 0 COMMENT '单价',
    amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '金额',
    tax_rate DECIMAL(5,2) COMMENT '税率',
    tax_amount DECIMAL(18,2) DEFAULT 0.00 COMMENT '税额',
    source_type VARCHAR(50) COMMENT '来源类型',
    source_id BIGINT COMMENT '来源单据ID',
    source_item_id BIGINT COMMENT '来源明细ID',
    source_serial_no VARCHAR(100) COMMENT '来源单据编号',
    notes TEXT COMMENT '备注',
    sort_order INTEGER DEFAULT 0 COMMENT '排序'
);

COMMENT ON TABLE produce_disassemble_bom IS '拆卸单BOM明细表-原料入库';
COMMENT ON COLUMN produce_disassemble_bom.id IS '主键ID';
COMMENT ON COLUMN produce_disassemble_bom.disassemble_id IS '拆卸单ID';
COMMENT ON COLUMN produce_disassemble_bom.parent_row_id IS '父行ID';
COMMENT ON COLUMN produce_disassemble_bom.row_id IS '行ID';
COMMENT ON COLUMN produce_disassemble_bom.product_id IS '物料ID';
COMMENT ON COLUMN produce_disassemble_bom.product_code IS '物料编码';
COMMENT ON COLUMN produce_disassemble_bom.product_name IS '物料名称';
COMMENT ON COLUMN produce_disassemble_bom.specification IS '规格型号';
COMMENT ON COLUMN produce_disassemble_bom.unit IS '单位';
COMMENT ON COLUMN produce_disassemble_bom.unit_id IS '单位ID';
COMMENT ON COLUMN produce_disassemble_bom.basic_unit IS '主单位';
COMMENT ON COLUMN produce_disassemble_bom.basic_unit_id IS '主单位ID';
COMMENT ON COLUMN produce_disassemble_bom.quantity IS '数量';
COMMENT ON COLUMN produce_disassemble_bom.basic_quantity IS '主单位数量';
COMMENT ON COLUMN produce_disassemble_bom.wh_id IS '仓库ID';
COMMENT ON COLUMN produce_disassemble_bom.wh_name IS '仓库名称';
COMMENT ON COLUMN produce_disassemble_bom.storage_id IS '货位ID';
COMMENT ON COLUMN produce_disassemble_bom.storage_name IS '货位名称';
COMMENT ON COLUMN produce_disassemble_bom.price IS '单价';
COMMENT ON COLUMN produce_disassemble_bom.amount IS '金额';
COMMENT ON COLUMN produce_disassemble_bom.tax_rate IS '税率';
COMMENT ON COLUMN produce_disassemble_bom.tax_amount IS '税额';
COMMENT ON COLUMN produce_disassemble_bom.source_type IS '来源类型';
COMMENT ON COLUMN produce_disassemble_bom.source_id IS '来源单据ID';
COMMENT ON COLUMN produce_disassemble_bom.source_item_id IS '来源明细ID';
COMMENT ON COLUMN produce_disassemble_bom.source_serial_no IS '来源单据编号';
COMMENT ON COLUMN produce_disassemble_bom.notes IS '备注';
COMMENT ON COLUMN produce_disassemble_bom.sort_order IS '排序';

CREATE INDEX idx_produce_disassemble_bom_disassemble_id ON produce_disassemble_bom(disassemble_id);
CREATE INDEX idx_produce_disassemble_bom_product_id ON produce_disassemble_bom(product_id);
CREATE INDEX idx_produce_disassemble_bom_parent_row_id ON produce_disassemble_bom(parent_row_id);