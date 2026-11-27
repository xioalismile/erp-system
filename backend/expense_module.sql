-- ========================================
-- 费用管理模块建表脚本
-- ========================================

-- 费用类型表
DROP TABLE IF EXISTS `expense_type`;
CREATE TABLE `expense_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `type_code` VARCHAR(50) NOT NULL COMMENT '类型编码',
  `type_name` VARCHAR(100) NOT NULL COMMENT '类型名称',
  `expense_category` VARCHAR(20) NOT NULL DEFAULT 'OTHER' COMMENT '费用分类: SALE销售费用/PURCHASE采购费用/OTHER其他',
  `cost_sharing_method` VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '分摊方式: NONE不分摊/AMOUNT按金额/QUANTITY按数量/WEIGHT按重量',
  `status` TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_code` (`type_code`),
  KEY `idx_expense_category` (`expense_category`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用类型表';

-- 销售费用单主表
DROP TABLE IF EXISTS `sale_expense`;
CREATE TABLE `sale_expense` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `expense_no` VARCHAR(50) NOT NULL COMMENT '费用单号',
  `customer_id` BIGINT DEFAULT NULL COMMENT '客户ID',
  `customer_name` VARCHAR(200) DEFAULT NULL COMMENT '客户名称',
  `expense_type_id` BIGINT DEFAULT NULL COMMENT '费用类型ID',
  `expense_type_name` VARCHAR(100) DEFAULT NULL COMMENT '费用类型名称',
  `expense_date` DATE DEFAULT NULL COMMENT '费用日期',
  `total_amount` DECIMAL(20,2) DEFAULT 0.00 COMMENT '费用总金额',
  `base_amount` DECIMAL(20,2) DEFAULT 0.00 COMMENT '本位币金额',
  `currency_code` VARCHAR(20) DEFAULT NULL COMMENT '币种编码',
  `exchange_rate` DECIMAL(20,6) DEFAULT 1.000000 COMMENT '汇率',
  `related_doc_id` BIGINT DEFAULT NULL COMMENT '关联单据ID',
  `related_doc_no` VARCHAR(50) DEFAULT NULL COMMENT '关联单据编号',
  `related_doc_type` VARCHAR(50) DEFAULT NULL COMMENT '关联单据类型',
  `cost_sharing_method` VARCHAR(20) DEFAULT 'NONE' COMMENT '分摊方式: NONE不分摊/AMOUNT按金额/QUANTITY按数量/WEIGHT按重量',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0草稿 1待审核 2已审核 3已分摊 4已作废',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by` VARCHAR(100) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expense_no` (`expense_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_expense_type_id` (`expense_type_id`),
  KEY `idx_expense_date` (`expense_date`),
  KEY `idx_status` (`status`),
  KEY `idx_related_doc` (`related_doc_id`, `related_doc_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售费用单主表';

-- 销售费用明细表
DROP TABLE IF EXISTS `sale_expense_item`;
CREATE TABLE `sale_expense_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `expense_id` BIGINT NOT NULL COMMENT '费用单ID',
  `source_id` BIGINT DEFAULT NULL COMMENT '源单ID',
  `source_no` VARCHAR(50) DEFAULT NULL COMMENT '源单编号',
  `source_type` VARCHAR(50) DEFAULT NULL COMMENT '源单类型',
  `material_id` BIGINT DEFAULT NULL COMMENT '商品ID',
  `material_name` VARCHAR(200) DEFAULT NULL COMMENT '商品名称',
  `quantity` DECIMAL(20,6) DEFAULT 0.000000 COMMENT '数量',
  `amount` DECIMAL(20,2) DEFAULT 0.00 COMMENT '金额',
  `sharing_amount` DECIMAL(20,2) DEFAULT 0.00 COMMENT '分摊金额',
  `sharing_ratio` DECIMAL(10,2) DEFAULT 0.00 COMMENT '分摊比例(%)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_expense_id` (`expense_id`),
  KEY `idx_source_id` (`source_id`),
  KEY `idx_material_id` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售费用明细表';

-- 采购费用单主表
DROP TABLE IF EXISTS `purchase_expense`;
CREATE TABLE `purchase_expense` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `expense_no` VARCHAR(50) NOT NULL COMMENT '费用单号',
  `supplier_id` BIGINT DEFAULT NULL COMMENT '供应商ID',
  `supplier_name` VARCHAR(200) DEFAULT NULL COMMENT '供应商名称',
  `expense_type_id` BIGINT DEFAULT NULL COMMENT '费用类型ID',
  `expense_type_name` VARCHAR(100) DEFAULT NULL COMMENT '费用类型名称',
  `expense_date` DATE DEFAULT NULL COMMENT '费用日期',
  `total_amount` DECIMAL(20,2) DEFAULT 0.00 COMMENT '费用总金额',
  `base_amount` DECIMAL(20,2) DEFAULT 0.00 COMMENT '本位币金额',
  `currency_code` VARCHAR(20) DEFAULT NULL COMMENT '币种编码',
  `exchange_rate` DECIMAL(20,6) DEFAULT 1.000000 COMMENT '汇率',
  `related_doc_id` BIGINT DEFAULT NULL COMMENT '关联单据ID',
  `related_doc_no` VARCHAR(50) DEFAULT NULL COMMENT '关联单据编号',
  `related_doc_type` VARCHAR(50) DEFAULT NULL COMMENT '关联单据类型',
  `cost_sharing_method` VARCHAR(20) DEFAULT 'NONE' COMMENT '分摊方式: NONE不分摊/AMOUNT按金额/QUANTITY按数量/WEIGHT按重量',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0草稿 1待审核 2已审核 3已分摊 4已作废',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by` VARCHAR(100) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expense_no` (`expense_no`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_expense_type_id` (`expense_type_id`),
  KEY `idx_expense_date` (`expense_date`),
  KEY `idx_status` (`status`),
  KEY `idx_related_doc` (`related_doc_id`, `related_doc_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购费用单主表';

-- 采购费用明细表
DROP TABLE IF EXISTS `purchase_expense_item`;
CREATE TABLE `purchase_expense_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `expense_id` BIGINT NOT NULL COMMENT '费用单ID',
  `source_id` BIGINT DEFAULT NULL COMMENT '源单ID',
  `source_no` VARCHAR(50) DEFAULT NULL COMMENT '源单编号',
  `source_type` VARCHAR(50) DEFAULT NULL COMMENT '源单类型',
  `material_id` BIGINT DEFAULT NULL COMMENT '商品ID',
  `material_name` VARCHAR(200) DEFAULT NULL COMMENT '商品名称',
  `quantity` DECIMAL(20,6) DEFAULT 0.000000 COMMENT '数量',
  `amount` DECIMAL(20,2) DEFAULT 0.00 COMMENT '金额',
  `sharing_amount` DECIMAL(20,2) DEFAULT 0.00 COMMENT '分摊金额',
  `sharing_ratio` DECIMAL(10,2) DEFAULT 0.00 COMMENT '分摊比例(%)',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_expense_id` (`expense_id`),
  KEY `idx_source_id` (`source_id`),
  KEY `idx_material_id` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购费用明细表';

-- ========================================
-- 初始示例数据
-- ========================================

-- 费用类型示例数据
INSERT INTO `expense_type` (`type_code`, `type_name`, `expense_category`, `cost_sharing_method`, `status`, `remark`) VALUES
('SALE_TRANSPORT', '销售运费', 'SALE', 'AMOUNT', 1, '销售环节产生的运输费用，按金额分摊'),
('SALE_INSURANCE', '销售保险费', 'SALE', 'AMOUNT', 1, '销售环节产生的保险费用'),
('SALE_COMMISSION', '销售佣金', 'SALE', 'AMOUNT', 1, '销售佣金费用'),
('SALE_OTHER', '其他销售费用', 'SALE', 'NONE', 1, '其他销售费用，不分摊'),
('PURCHASE_TRANSPORT', '采购运费', 'PURCHASE', 'QUANTITY', 1, '采购环节产生的运输费用，按数量分摊'),
('PURCHASE_INSURANCE', '采购保险费', 'PURCHASE', 'AMOUNT', 1, '采购环节产生的保险费用'),
('PURCHASE_TARIFF', '采购关税', 'PURCHASE', 'AMOUNT', 1, '进口关税费用'),
('PURCHASE_OTHER', '其他采购费用', 'PURCHASE', 'NONE', 1, '其他采购费用，不分摊'),
('OFFICE_RENT', '办公租金', 'OTHER', 'NONE', 1, '办公场地租金'),
('UTILITY_FEE', '水电费', 'OTHER', 'NONE', 1, '水电费用');
