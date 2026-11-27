-- 系统参数配置表
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    config_key VARCHAR(100) NOT NULL COMMENT '参数key',
    config_value TEXT COMMENT '参数值',
    config_name VARCHAR(200) COMMENT '参数名称',
    config_group VARCHAR(50) COMMENT '参数分组: basic/precision/font/approval/inventory/purchase/sale/produce/subcontract/attendance',
    config_type VARCHAR(20) COMMENT '参数类型: string/number/boolean/select',
    config_options TEXT COMMENT '选项值JSON, 用于select类型',
    sort_order INT DEFAULT 0 COMMENT '排序',
    remark VARCHAR(500) COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(50) COMMENT '创建人',
    updated_by VARCHAR(50) COMMENT '更新人',
    UNIQUE KEY uk_config_key (config_key),
    KEY idx_config_group (config_group)
) COMMENT='系统参数配置表';
