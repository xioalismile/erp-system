-- =====================================================
-- 委外管理模块 SQL 建表脚本
-- =====================================================

-- -----------------------------------------------------
-- 1. 委外加工单主表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `subcontract_order`;
CREATE TABLE `subcontract_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '加工单号',
    `supplier_id` BIGINT DEFAULT NULL COMMENT '委外供应商ID',
    `supplier_name` VARCHAR(128) DEFAULT NULL COMMENT '委外供应商名称',
    `process_item` VARCHAR(256) DEFAULT NULL COMMENT '加工项目',
    `order_date` DATE DEFAULT NULL COMMENT '下单日期',
    `expect_date` DATE DEFAULT NULL COMMENT '预计完工日期',
    `actual_date` DATE DEFAULT NULL COMMENT '实际完工日期',
    `total_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '总数量',
    `finished_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '已完工数量',
    `unit_price` DECIMAL(18,4) DEFAULT 0 COMMENT '加工单价',
    `total_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '加工费总额',
    `tax_rate` DECIMAL(10,2) DEFAULT 0 COMMENT '税率(%)',
    `tax_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '税额',
    `net_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '价税合计',
    `currency_code` VARCHAR(32) DEFAULT NULL COMMENT '币种编码',
    `exchange_rate` DECIMAL(18,6) DEFAULT 1 COMMENT '汇率',
    `base_total_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '本位币加工费总额',
    `base_tax_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '本位币税额',
    `base_net_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '本位币价税合计',
    `source_bill_type` VARCHAR(50) DEFAULT NULL COMMENT '来源单据类型',
    `source_bill_no` VARCHAR(50) DEFAULT NULL COMMENT '来源单据号',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0草稿 1待审核 2已审核 3领料中 4加工中 5已完工 6已结案 7已作废',
    `close_status` TINYINT DEFAULT 0 COMMENT '结案状态: 0未结案 1已结案',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外加工单主表';

-- -----------------------------------------------------
-- 2. 委外加工单明细表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `subcontract_order_item`;
CREATE TABLE `subcontract_order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '加工单ID',
    `material_id` BIGINT DEFAULT NULL COMMENT '成品/加工品物料ID',
    `material_code` VARCHAR(64) DEFAULT NULL COMMENT '成品/加工品物料编码',
    `material_name` VARCHAR(128) DEFAULT NULL COMMENT '成品/加工品物料名称',
    `specification` VARCHAR(256) DEFAULT NULL COMMENT '规格',
    `unit` VARCHAR(32) DEFAULT NULL COMMENT '单位',
    `quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '数量',
    `finished_qty` DECIMAL(18,4) DEFAULT 0 COMMENT '已完工数量',
    `actual_qty` DECIMAL(18,4) DEFAULT 0 COMMENT '实发数量',
    `unit_price` DECIMAL(18,4) DEFAULT 0 COMMENT '加工单价',
    `amount` DECIMAL(18,2) DEFAULT 0 COMMENT '加工费金额',
    `tax_rate` DECIMAL(10,2) DEFAULT 0 COMMENT '税率(%)',
    `tax_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '税额',
    `net_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '价税合计',
    `bom_id` BIGINT DEFAULT NULL COMMENT 'BOM ID',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `detail_close_status` TINYINT DEFAULT 0 COMMENT '行结案状态: 0未结案 1已结案',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_material_id` (`material_id`),
    KEY `idx_bom_id` (`bom_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外加工单明细表';

-- -----------------------------------------------------
-- 3. 委外领料单主表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `subcontract_pick`;
CREATE TABLE `subcontract_pick` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `pick_no` VARCHAR(64) NOT NULL COMMENT '领料单号',
    `order_id` BIGINT DEFAULT NULL COMMENT '关联委外加工单ID',
    `order_no` VARCHAR(64) DEFAULT NULL COMMENT '关联委外加工单号',
    `supplier_id` BIGINT DEFAULT NULL COMMENT '供应商ID',
    `supplier_name` VARCHAR(128) DEFAULT NULL COMMENT '供应商名称',
    `pick_date` DATE DEFAULT NULL COMMENT '领料日期',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0草稿 1待审核 2已审核 3已出库',
    `total_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '总数量',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pick_no` (`pick_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外领料单主表';

-- -----------------------------------------------------
-- 4. 委外领料单明细表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `subcontract_pick_item`;
CREATE TABLE `subcontract_pick_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `pick_id` BIGINT NOT NULL COMMENT '领料单ID',
    `order_item_id` BIGINT DEFAULT NULL COMMENT '加工单明细ID',
    `material_id` BIGINT DEFAULT NULL COMMENT '物料ID',
    `material_code` VARCHAR(64) DEFAULT NULL COMMENT '物料编码',
    `material_name` VARCHAR(128) DEFAULT NULL COMMENT '物料名称',
    `specification` VARCHAR(256) DEFAULT NULL COMMENT '规格',
    `unit` VARCHAR(32) DEFAULT NULL COMMENT '单位',
    `quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '领料数量',
    `warehouse_id` BIGINT DEFAULT NULL COMMENT '仓库ID',
    `warehouse_name` VARCHAR(128) DEFAULT NULL COMMENT '仓库名称',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_pick_id` (`pick_id`),
    KEY `idx_order_item_id` (`order_item_id`),
    KEY `idx_material_id` (`material_id`),
    KEY `idx_warehouse_id` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外领料单明细表';

-- -----------------------------------------------------
-- 5. 委外退料单主表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `subcontract_return`;
CREATE TABLE `subcontract_return` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `return_no` VARCHAR(64) NOT NULL COMMENT '退料单号',
    `order_id` BIGINT DEFAULT NULL COMMENT '关联委外加工单ID',
    `order_no` VARCHAR(64) DEFAULT NULL COMMENT '关联委外加工单号',
    `supplier_id` BIGINT DEFAULT NULL COMMENT '供应商ID',
    `supplier_name` VARCHAR(128) DEFAULT NULL COMMENT '供应商名称',
    `return_date` DATE DEFAULT NULL COMMENT '退料日期',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0草稿 1待审核 2已审核 3已入库',
    `total_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '总数量',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_return_no` (`return_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外退料单主表';

-- -----------------------------------------------------
-- 6. 委外退料单明细表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `subcontract_return_item`;
CREATE TABLE `subcontract_return_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `return_id` BIGINT NOT NULL COMMENT '退料单ID',
    `order_item_id` BIGINT DEFAULT NULL COMMENT '加工单明细ID',
    `material_id` BIGINT DEFAULT NULL COMMENT '物料ID',
    `material_code` VARCHAR(64) DEFAULT NULL COMMENT '物料编码',
    `material_name` VARCHAR(128) DEFAULT NULL COMMENT '物料名称',
    `specification` VARCHAR(256) DEFAULT NULL COMMENT '规格',
    `unit` VARCHAR(32) DEFAULT NULL COMMENT '单位',
    `quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '退料数量',
    `warehouse_id` BIGINT DEFAULT NULL COMMENT '仓库ID',
    `warehouse_name` VARCHAR(128) DEFAULT NULL COMMENT '仓库名称',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_return_id` (`return_id`),
    KEY `idx_order_item_id` (`order_item_id`),
    KEY `idx_material_id` (`material_id`),
    KEY `idx_warehouse_id` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外退料单明细表';

-- -----------------------------------------------------
-- 7. 委外补料单主表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `subcontract_supplement`;
CREATE TABLE `subcontract_supplement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `supplement_no` VARCHAR(64) NOT NULL COMMENT '补料单号',
    `order_id` BIGINT DEFAULT NULL COMMENT '关联委外加工单ID',
    `order_no` VARCHAR(64) DEFAULT NULL COMMENT '关联委外加工单号',
    `supplier_id` BIGINT DEFAULT NULL COMMENT '供应商ID',
    `supplier_name` VARCHAR(128) DEFAULT NULL COMMENT '供应商名称',
    `supplement_date` DATE DEFAULT NULL COMMENT '补料日期',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0草稿 1待审核 2已审核 3已出库',
    `total_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '总数量',
    `reason` VARCHAR(512) DEFAULT NULL COMMENT '补料原因',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_supplement_no` (`supplement_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外补料单主表';

-- -----------------------------------------------------
-- 8. 委外补料单明细表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `subcontract_supplement_item`;
CREATE TABLE `subcontract_supplement_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `supplement_id` BIGINT NOT NULL COMMENT '补料单ID',
    `order_item_id` BIGINT DEFAULT NULL COMMENT '加工单明细ID',
    `material_id` BIGINT DEFAULT NULL COMMENT '物料ID',
    `material_code` VARCHAR(64) DEFAULT NULL COMMENT '物料编码',
    `material_name` VARCHAR(128) DEFAULT NULL COMMENT '物料名称',
    `specification` VARCHAR(256) DEFAULT NULL COMMENT '规格',
    `unit` VARCHAR(32) DEFAULT NULL COMMENT '单位',
    `quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '补料数量',
    `warehouse_id` BIGINT DEFAULT NULL COMMENT '仓库ID',
    `warehouse_name` VARCHAR(128) DEFAULT NULL COMMENT '仓库名称',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_supplement_id` (`supplement_id`),
    KEY `idx_order_item_id` (`order_item_id`),
    KEY `idx_material_id` (`material_id`),
    KEY `idx_warehouse_id` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外补料单明细表';

-- -----------------------------------------------------
-- 9. 委外入库单主表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `subcontract_inbound`;
CREATE TABLE `subcontract_inbound` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `inbound_no` VARCHAR(64) NOT NULL COMMENT '入库单号',
    `order_id` BIGINT DEFAULT NULL COMMENT '关联委外加工单ID',
    `order_no` VARCHAR(64) DEFAULT NULL COMMENT '关联委外加工单号',
    `supplier_id` BIGINT DEFAULT NULL COMMENT '供应商ID',
    `supplier_name` VARCHAR(128) DEFAULT NULL COMMENT '供应商名称',
    `inbound_date` DATE DEFAULT NULL COMMENT '入库日期',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0草稿 1待审核 2已审核',
    `total_quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '总数量',
    `processing_fee` DECIMAL(18,2) DEFAULT 0 COMMENT '加工费',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inbound_no` (`inbound_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外入库单主表';

-- -----------------------------------------------------
-- 10. 委外入库单明细表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `subcontract_inbound_item`;
CREATE TABLE `subcontract_inbound_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `inbound_id` BIGINT NOT NULL COMMENT '入库单ID',
    `order_item_id` BIGINT DEFAULT NULL COMMENT '加工单明细ID',
    `material_id` BIGINT DEFAULT NULL COMMENT '成品物料ID',
    `material_code` VARCHAR(64) DEFAULT NULL COMMENT '成品物料编码',
    `material_name` VARCHAR(128) DEFAULT NULL COMMENT '成品物料名称',
    `specification` VARCHAR(256) DEFAULT NULL COMMENT '规格',
    `unit` VARCHAR(32) DEFAULT NULL COMMENT '单位',
    `quantity` DECIMAL(18,4) DEFAULT 0 COMMENT '入库数量',
    `warehouse_id` BIGINT DEFAULT NULL COMMENT '仓库ID',
    `warehouse_name` VARCHAR(128) DEFAULT NULL COMMENT '仓库名称',
    `processing_fee` DECIMAL(18,2) DEFAULT 0 COMMENT '该成品加工费',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_inbound_id` (`inbound_id`),
    KEY `idx_order_item_id` (`order_item_id`),
    KEY `idx_material_id` (`material_id`),
    KEY `idx_warehouse_id` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委外入库单明细表';
