-- 单据编号配置表
CREATE TABLE IF NOT EXISTS sys_serial_no_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    doc_type VARCHAR(50) NOT NULL COMMENT '单据类型: SALE_ORDER, PURCHASE_ORDER, SALE_DELIVERY, PURCHASE_INBOUND, SALE_RETURN, SALE_INVOICE等',
    doc_name VARCHAR(100) COMMENT '单据名称',
    prefix VARCHAR(20) COMMENT '编号前缀, 如: SO, PO',
    date_format VARCHAR(20) COMMENT '日期格式: yyyyMMdd, yyMM, yyyy-MM-dd等',
    serial_length INT DEFAULT 4 COMMENT '流水号位数',
    reset_type VARCHAR(20) DEFAULT 'NONE' COMMENT '流水号重置类型: NONE不重置, DAILY按日, MONTHLY按月, YEARLY按年',
    separator VARCHAR(10) COMMENT '分隔符, 如: -',
    current_serial INT DEFAULT 0 COMMENT '当前流水号',
    current_date VARCHAR(20) COMMENT '当前日期, 用于判断是否重置',
    sort_order INT DEFAULT 0 COMMENT '排序',
    enabled INT DEFAULT 1 COMMENT '是否启用: 0否 1是',
    remark VARCHAR(500) COMMENT '备注',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(50) COMMENT '创建人',
    updated_by VARCHAR(50) COMMENT '更新人',
    UNIQUE KEY uk_doc_type (doc_type),
    KEY idx_enabled (enabled)
) COMMENT='单据编号配置表';
