-- ========================================
-- 序列号/批次号追踪模块建表脚本
-- ========================================

-- 序列号主表
DROP TABLE IF EXISTS `serial_number`;
CREATE TABLE `serial_number` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `serial_no` VARCHAR(200) NOT NULL COMMENT '序列号',
  `material_id` BIGINT DEFAULT NULL COMMENT '商品ID',
  `material_code` VARCHAR(100) DEFAULT NULL COMMENT '商品编码',
  `material_name` VARCHAR(200) DEFAULT NULL COMMENT '商品名称',
  `specification` VARCHAR(500) DEFAULT NULL COMMENT '规格型号',
  `unit` VARCHAR(50) DEFAULT NULL COMMENT '单位',
  `warehouse_id` BIGINT DEFAULT NULL COMMENT '仓库ID',
  `warehouse_name` VARCHAR(200) DEFAULT NULL COMMENT '仓库名称',
  `location_id` BIGINT DEFAULT NULL COMMENT '库位ID',
  `location_name` VARCHAR(200) DEFAULT NULL COMMENT '库位名称',
  `batch_no` VARCHAR(200) DEFAULT NULL COMMENT '批次号（可选）',
  `production_date` DATE DEFAULT NULL COMMENT '生产日期',
  `expiry_date` DATE DEFAULT NULL COMMENT '有效期/到期日',
  `supplier_id` BIGINT DEFAULT NULL COMMENT '供应商ID',
  `supplier_name` VARCHAR(200) DEFAULT NULL COMMENT '供应商名称',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0在库 1已出库 2已报废 3冻结',
  `inbound_id` BIGINT DEFAULT NULL COMMENT '入库单号ID',
  `inbound_no` VARCHAR(100) DEFAULT NULL COMMENT '入库单号',
  `outbound_id` BIGINT DEFAULT NULL COMMENT '出库单号ID',
  `outbound_no` VARCHAR(100) DEFAULT NULL COMMENT '出库单号',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_serial_no` (`serial_no`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_location_id` (`location_id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_status` (`status`),
  KEY `idx_expiry_date` (`expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='序列号主表';

-- 序列号追踪表/流水表
DROP TABLE IF EXISTS `serial_number_trace`;
CREATE TABLE `serial_number_trace` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `serial_number_id` BIGINT NOT NULL COMMENT '序列号ID',
  `serial_no` VARCHAR(200) NOT NULL COMMENT '序列号',
  `operation_type` VARCHAR(50) NOT NULL COMMENT '操作类型: INBOUND入库/OUTBOUND出库/TRANSFER调拨/RETURN退货/SCRAP报废/ADJUST调整',
  `warehouse_id` BIGINT DEFAULT NULL COMMENT '仓库ID',
  `warehouse_name` VARCHAR(200) DEFAULT NULL COMMENT '仓库名称',
  `location_id` BIGINT DEFAULT NULL COMMENT '库位ID',
  `location_name` VARCHAR(200) DEFAULT NULL COMMENT '库位名称',
  `related_doc_id` BIGINT DEFAULT NULL COMMENT '关联单据ID',
  `related_doc_no` VARCHAR(100) DEFAULT NULL COMMENT '关联单据号',
  `related_doc_type` VARCHAR(50) DEFAULT NULL COMMENT '关联单据类型',
  `operation_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_serial_number_id` (`serial_number_id`),
  KEY `idx_serial_no` (`serial_no`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_operation_time` (`operation_time`),
  KEY `idx_related_doc` (`related_doc_id`, `related_doc_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='序列号追踪表/流水表';

-- ========================================
-- 批次号管理模块
-- ========================================

-- 批次信息表
DROP TABLE IF EXISTS `batch_info`;
CREATE TABLE `batch_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no` VARCHAR(200) NOT NULL COMMENT '批次号',
  `material_id` BIGINT DEFAULT NULL COMMENT '商品ID',
  `material_code` VARCHAR(100) DEFAULT NULL COMMENT '商品编码',
  `material_name` VARCHAR(200) DEFAULT NULL COMMENT '商品名称',
  `specification` VARCHAR(500) DEFAULT NULL COMMENT '规格型号',
  `unit` VARCHAR(50) DEFAULT NULL COMMENT '单位',
  `production_date` DATE DEFAULT NULL COMMENT '生产日期',
  `expiry_date` DATE DEFAULT NULL COMMENT '有效期/到期日',
  `supplier_id` BIGINT DEFAULT NULL COMMENT '供应商ID',
  `supplier_name` VARCHAR(200) DEFAULT NULL COMMENT '供应商名称',
  `quantity` DECIMAL(20,6) DEFAULT 0 COMMENT '批次总数量',
  `available_qty` DECIMAL(20,6) DEFAULT 0 COMMENT '可用数量',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0正常 1部分出库 2已出库完 3已过期',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_status` (`status`),
  KEY `idx_production_date` (`production_date`),
  KEY `idx_expiry_date` (`expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次信息表';

-- 批次库存表
DROP TABLE IF EXISTS `batch_inventory`;
CREATE TABLE `batch_inventory` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_id` BIGINT NOT NULL COMMENT '批次ID',
  `batch_no` VARCHAR(200) NOT NULL COMMENT '批次号',
  `material_id` BIGINT DEFAULT NULL COMMENT '商品ID',
  `material_code` VARCHAR(100) DEFAULT NULL COMMENT '商品编码',
  `material_name` VARCHAR(200) DEFAULT NULL COMMENT '商品名称',
  `warehouse_id` BIGINT DEFAULT NULL COMMENT '仓库ID',
  `warehouse_name` VARCHAR(200) DEFAULT NULL COMMENT '仓库名称',
  `location_id` BIGINT DEFAULT NULL COMMENT '库位ID',
  `location_name` VARCHAR(200) DEFAULT NULL COMMENT '库位名称',
  `quantity` DECIMAL(20,6) DEFAULT 0 COMMENT '数量',
  `available_qty` DECIMAL(20,6) DEFAULT 0 COMMENT '可用数量',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_location_id` (`location_id`),
  UNIQUE KEY `uk_batch_warehouse` (`batch_id`, `warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次库存表';

-- ========================================
-- 初始示例数据
-- ========================================

-- 示例序列号
INSERT INTO `serial_number` (`serial_no`, `material_id`, `material_code`, `material_name`, `specification`, `unit`, `warehouse_id`, `warehouse_name`, `location_id`, `location_name`, `batch_no`, `production_date`, `expiry_date`, `supplier_id`, `supplier_name`, `status`, `inbound_no`, `remark`) VALUES
('SN2025010100001', 1, 'MAT001', '示例商品A', '规格1', '个', 1, '总仓库', 1, 'A-01-01', 'B20250101001', '2025-01-01', '2026-01-01', 1, '示例供应商', 0, 'IN20250101001', '序列号示例数据1'),
('SN2025010100002', 1, 'MAT001', '示例商品A', '规格1', '个', 1, '总仓库', 1, 'A-01-01', 'B20250101001', '2025-01-01', '2026-01-01', 1, '示例供应商', 0, 'IN20250101001', '序列号示例数据2'),
('SN2025011500001', 2, 'MAT002', '示例商品B', '规格2', '台', 1, '总仓库', 2, 'B-02-01', 'B20250115001', '2025-01-15', '2027-01-15', 2, '示例供应商B', 0, 'IN20250115001', '序列号示例数据3'),
('SN2025020100001', 1, 'MAT001', '示例商品A', '规格1', '个', 2, '华东仓库', 3, 'C-01-01', 'B20250201001', '2025-02-01', '2026-02-01', 1, '示例供应商', 1, 'IN20250201001', '已出库示例'),
('SN2025030100001', 3, 'MAT003', '示例商品C', '规格3', '件', 1, '总仓库', 1, 'A-01-02', NULL, '2025-03-01', NULL, 3, '示例供应商C', 2, 'IN20250301001', '已报废示例');

-- 示例序列号追踪记录
INSERT INTO `serial_number_trace` (`serial_number_id`, `serial_no`, `operation_type`, `warehouse_id`, `warehouse_name`, `location_id`, `location_name`, `related_doc_no`, `related_doc_type`, `operation_time`, `operator`, `remark`) VALUES
(1, 'SN2025010100001', 'INBOUND', 1, '总仓库', 1, 'A-01-01', 'IN20250101001', 'INBOUND', '2025-01-01 10:00:00', 'admin', '入库'),
(2, 'SN2025010100002', 'INBOUND', 1, '总仓库', 1, 'A-01-01', 'IN20250101001', 'INBOUND', '2025-01-01 10:00:00', 'admin', '入库'),
(3, 'SN2025011500001', 'INBOUND', 1, '总仓库', 2, 'B-02-01', 'IN20250115001', 'INBOUND', '2025-01-15 14:00:00', 'admin', '入库'),
(4, 'SN2025020100001', 'INBOUND', 2, '华东仓库', 3, 'C-01-01', 'IN20250201001', 'INBOUND', '2025-02-01 09:00:00', 'admin', '入库'),
(4, 'SN2025020100001', 'OUTBOUND', 2, '华东仓库', 3, 'C-01-01', 'OUT20250215001', 'OUTBOUND', '2025-02-15 16:00:00', 'admin', '销售出库'),
(5, 'SN2025030100001', 'INBOUND', 1, '总仓库', 1, 'A-01-02', 'IN20250301001', 'INBOUND', '2025-03-01 11:00:00', 'admin', '入库'),
(5, 'SN2025030100001', 'SCRAP', 1, '总仓库', 1, 'A-01-02', NULL, 'SCRAP', '2025-04-01 10:00:00', 'admin', '质量问题报废');

-- 示例批次信息
INSERT INTO `batch_info` (`batch_no`, `material_id`, `material_code`, `material_name`, `specification`, `unit`, `production_date`, `expiry_date`, `supplier_id`, `supplier_name`, `quantity`, `available_qty`, `status`, `remark`) VALUES
('B20250101001', 1, 'MAT001', '示例商品A', '规格1', '个', '2025-01-01', '2026-01-01', 1, '示例供应商', 1000.000000, 800.000000, 1, '批次示例数据1，部分出库'),
('B20250115001', 2, 'MAT002', '示例商品B', '规格2', '台', '2025-01-15', '2027-01-15', 2, '示例供应商B', 500.000000, 500.000000, 0, '批次示例数据2，未出库'),
('B20250201001', 1, 'MAT001', '示例商品A', '规格1', '个', '2025-02-01', '2026-02-01', 1, '示例供应商', 300.000000, 0.000000, 2, '批次示例数据3，已出库完'),
('B20250301001', 3, 'MAT003', '示例商品C', '规格3', '件', '2024-03-01', '2025-03-01', 3, '示例供应商C', 200.000000, 50.000000, 3, '批次示例数据4，已过期');

-- 示例批次库存
INSERT INTO `batch_inventory` (`batch_id`, `batch_no`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_name`, `location_id`, `location_name`, `quantity`, `available_qty`) VALUES
(1, 'B20250101001', 1, 'MAT001', '示例商品A', 1, '总仓库', 1, 'A-01-01', 600.000000, 500.000000),
(1, 'B20250101001', 1, 'MAT001', '示例商品A', 2, '华东仓库', 3, 'C-01-01', 400.000000, 300.000000),
(2, 'B20250115001', 2, 'MAT002', '示例商品B', 1, '总仓库', 2, 'B-02-01', 500.000000, 500.000000),
(4, 'B20250301001', 3, 'MAT003', '示例商品C', 1, '总仓库', 1, 'A-01-02', 200.000000, 50.000000);
