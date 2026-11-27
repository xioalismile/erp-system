-- ========================================
-- 库存预警模块建表脚本
-- ========================================

-- 预警规则表
DROP TABLE IF EXISTS `inventory_warning_rule`;
CREATE TABLE `inventory_warning_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_name` VARCHAR(200) NOT NULL COMMENT '规则名称',
  `material_id` BIGINT NOT NULL COMMENT '商品ID',
  `material_code` VARCHAR(100) DEFAULT NULL COMMENT '商品编码',
  `material_name` VARCHAR(200) DEFAULT NULL COMMENT '商品名称',
  `warehouse_id` BIGINT DEFAULT NULL COMMENT '仓库ID，为空表示所有仓库',
  `warehouse_name` VARCHAR(200) DEFAULT NULL COMMENT '仓库名称',
  `warning_type` VARCHAR(20) NOT NULL DEFAULT 'MIN' COMMENT '预警类型: MIN最低库存/MAX最高库存/BOTH两者',
  `min_quantity` DECIMAL(20,6) DEFAULT NULL COMMENT '最低库存数量',
  `max_quantity` DECIMAL(20,6) DEFAULT NULL COMMENT '最高库存数量',
  `notify_type` TINYINT DEFAULT 0 COMMENT '通知方式: 0系统消息 1邮件 2短信',
  `notify_users` TEXT DEFAULT NULL COMMENT '通知人，JSON数组',
  `status` TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1启用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by` VARCHAR(100) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预警规则表';

-- 预警日志表
DROP TABLE IF EXISTS `inventory_warning_log`;
CREATE TABLE `inventory_warning_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` BIGINT NOT NULL COMMENT '规则ID',
  `material_id` BIGINT NOT NULL COMMENT '商品ID',
  `material_name` VARCHAR(200) DEFAULT NULL COMMENT '商品名称',
  `warehouse_id` BIGINT DEFAULT NULL COMMENT '仓库ID',
  `warehouse_name` VARCHAR(200) DEFAULT NULL COMMENT '仓库名称',
  `current_qty` DECIMAL(20,6) DEFAULT NULL COMMENT '当前库存',
  `warning_type` VARCHAR(20) DEFAULT NULL COMMENT '预警类型: MIN/MAX',
  `warning_level` VARCHAR(20) DEFAULT '一般' COMMENT '预警级别: 一般/严重',
  `is_handled` TINYINT DEFAULT 0 COMMENT '是否已处理: 0否 1是',
  `handled_by` VARCHAR(100) DEFAULT NULL COMMENT '处理人',
  `handled_at` DATETIME DEFAULT NULL COMMENT '处理时间',
  `handle_remark` VARCHAR(500) DEFAULT NULL COMMENT '处理备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_rule_id` (`rule_id`),
  `idx_material_id` (`material_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_is_handled` (`is_handled`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预警日志表';

-- ========================================
-- 初始示例数据
-- ========================================

-- 示例预警规则
INSERT INTO `inventory_warning_rule` (`rule_name`, `material_id`, `material_code`, `material_name`, `warehouse_id`, `warehouse_name`, `warning_type`, `min_quantity`, `max_quantity`, `notify_type`, `notify_users`, `status`, `remark`, `create_by`) VALUES
('A类商品最低库存预警', 1, 'MAT001', '示例商品A', 1, '总仓库', 'MIN', 100.000000, NULL, 0, '["admin","manager"]', 1, 'A类商品库存低于100时预警', 'admin'),
('B类商品最高库存预警', 2, 'MAT002', '示例商品B', 1, '总仓库', 'MAX', NULL, 5000.000000, 0, '["admin"]', 1, 'B类商品库存高于5000时预警', 'admin'),
('C类商品双向库存预警', 3, 'MAT003', '示例商品C', NULL, NULL, 'BOTH', 50.000000, 2000.000000, 1, '["admin","warehouse_manager"]', 1, 'C类商品所有仓库双向预警', 'admin'),
('D类商品最低库存-华东仓', 4, 'MAT004', '示例商品D', 2, '华东仓库', 'MIN', 200.000000, NULL, 0, '["admin"]', 0, '已禁用的规则示例', 'admin');

-- 示例预警日志
INSERT INTO `inventory_warning_log` (`rule_id`, `material_id`, `material_name`, `warehouse_id`, `warehouse_name`, `current_qty`, `warning_type`, `warning_level`, `is_handled`, `handled_by`, `handled_at`, `handle_remark`, `create_time`) VALUES
(1, 1, '示例商品A', 1, '总仓库', 50.000000, 'MIN', '严重', 0, NULL, NULL, NULL, NOW() - INTERVAL 2 HOUR),
(1, 1, '示例商品A', 2, '华东仓库', 80.000000, 'MIN', '一般', 1, 'manager', NOW() - INTERVAL 1 HOUR, '已安排采购', NOW() - INTERVAL 3 HOUR),
(3, 3, '示例商品C', 1, '总仓库', 2500.000000, 'MAX', '一般', 0, NULL, NULL, NULL, NOW() - INTERVAL 30 MINUTE);
