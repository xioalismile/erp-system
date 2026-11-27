-- ===========================================================================
-- 单据编号配置表增强：增加自动/手动生成开关，补齐全部单据类型
-- 适用数据库：PostgreSQL（兼容MySQL语法通过CREATE TABLE IF NOT EXISTS）
-- 版本：v1.0  日期：2026-07-31
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1. 确保表存在（PostgreSQL 版本，原 sys_serial_no_config_schema.sql 是 MySQL 语法）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_serial_no_config (
    id BIGSERIAL PRIMARY KEY,
    doc_type VARCHAR(50) NOT NULL,
    doc_name VARCHAR(100),
    prefix VARCHAR(20),
    date_format VARCHAR(20),
    serial_length INT DEFAULT 4,
    reset_type VARCHAR(20) DEFAULT 'NONE',
    separator VARCHAR(10),
    current_serial INT DEFAULT 0,
    "current_date" VARCHAR(20),
    sort_order INT DEFAULT 0,
    enabled INT DEFAULT 1,
    remark VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

-- ---------------------------------------------------------------------------
-- 2. 新增 auto_generate 字段：1=自动生成（默认），0=手动输入
-- ---------------------------------------------------------------------------
ALTER TABLE sys_serial_no_config ADD COLUMN IF NOT EXISTS auto_generate INT DEFAULT 1;
COMMENT ON COLUMN sys_serial_no_config.auto_generate IS '是否自动生成：1=自动生成 0=手动输入';

-- ---------------------------------------------------------------------------
-- 3. 补齐唯一索引（若不存在）
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE c.relname = 'uk_sys_serial_no_doc_type' AND n.nspname = 'public') THEN
        CREATE UNIQUE INDEX uk_sys_serial_no_doc_type ON sys_serial_no_config(doc_type);
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 4. 默认编码规则初始化（全部默认自动生成）
--    使用 INSERT ... WHERE NOT EXISTS 保证幂等
-- ---------------------------------------------------------------------------

-- 4.1 销售链单据
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SALE_QUOTE', '销售报价', 'QUT', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 1, 1, 1, '销售报价单编号规则：QUT-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SALE_QUOTE');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SALE_ORDER', '销售订单', 'SO', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 2, 1, 1, '销售订单编号规则：SO-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SALE_ORDER');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SALE_CONTRACT', '销售合同', 'SCON', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 3, 1, 1, '销售合同编号规则：SCON-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SALE_CONTRACT');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SALE_DELIVERY', '销售出库单', 'SOUT', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 4, 1, 1, '销售出库单编号规则：SOUT-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SALE_DELIVERY');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SALE_RETURN', '销售退货单', 'SRT', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 5, 1, 1, '销售退货单编号规则：SRT-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SALE_RETURN');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SALE_INVOICE', '销售发票', 'SINV', 'yyyyMM', 4, 'MONTHLY', '-', 0, NULL, 6, 1, 1, '销售发票编号规则：SINV-yyyyMM-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SALE_INVOICE');

-- 4.2 采购链单据
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PURCHASE_REQUEST', '采购申请', 'PR', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 10, 1, 1, '采购申请编号规则：PR-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PURCHASE_REQUEST');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PURCHASE_ORDER', '采购订单', 'PO', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 11, 1, 1, '采购订单编号规则：PO-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PURCHASE_ORDER');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PURCHASE_CONTRACT', '采购合同', 'PCON', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 12, 1, 1, '采购合同编号规则：PCON-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PURCHASE_CONTRACT');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PURCHASE_INBOUND', '采购入库单', 'PIN', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 13, 1, 1, '采购入库单编号规则：PIN-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PURCHASE_INBOUND');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PURCHASE_RETURN', '采购退货单', 'PRT', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 14, 1, 1, '采购退货单编号规则：PRT-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PURCHASE_RETURN');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PURCHASE_INVOICE', '采购发票', 'PIV', 'yyyyMM', 4, 'MONTHLY', '-', 0, NULL, 15, 1, 1, '采购发票编号规则：PIV-yyyyMM-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PURCHASE_INVOICE');

-- 4.3 库存/仓库单据
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'STOCK_CHECK', '库存盘点单', 'SC', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 20, 1, 1, '库存盘点单编号规则：SC-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'STOCK_CHECK');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'STOCK_TRANSFER', '库存调拨单', 'ST', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 21, 1, 1, '库存调拨单编号规则：ST-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'STOCK_TRANSFER');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'OTHER_RECEIPT', '其他入库单', 'OR', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 22, 1, 1, '其他入库单编号规则：OR-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'OTHER_RECEIPT');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'OTHER_ISSUE', '其他出库单', 'OI', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 23, 1, 1, '其他出库单编号规则：OI-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'OTHER_ISSUE');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'MATERIAL_REQUISITION', '领料单', 'MR', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 24, 1, 1, '领料单编号规则：MR-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'MATERIAL_REQUISITION');

-- 4.4 生产单据
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'MRP_PLAN', 'MRP计划', 'MP', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 30, 1, 1, 'MRP计划编号规则：MP-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'MRP_PLAN');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PRODUCTION_ORDER', '生产订单', 'MO', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 31, 1, 1, '生产订单编号规则：MO-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PRODUCTION_ORDER');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'WORK_ORDER', '工单', 'WO', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 32, 1, 1, '工单编号规则：WO-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'WORK_ORDER');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'OPERATION_REPORT', '报工单', 'ORP', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 34, 1, 1, '报工单编号规则：ORP-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'OPERATION_REPORT');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PRODUCTION_RECEIPT', '生产入库单', 'PRS', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 35, 1, 1, '生产入库单编号规则：PRS-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PRODUCTION_RECEIPT');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PRODUCE_ASSEMBLE', '组装单', 'ASM', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 36, 1, 1, '组装单编号规则：ASM-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PRODUCE_ASSEMBLE');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PRODUCE_DISASSEMBLE', '拆卸单', 'DASM', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 37, 1, 1, '拆卸单编号规则：DASM-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PRODUCE_DISASSEMBLE');

-- 4.5 委外单据
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SUBCONTRACT_ORDER', '委外订单', 'SCO', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 40, 1, 1, '委外订单编号规则：SCO-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SUBCONTRACT_ORDER');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SUBCONTRACT_PICK', '委外领料单', 'SCP', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 41, 1, 1, '委外领料单编号规则：SCP-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SUBCONTRACT_PICK');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SUBCONTRACT_INBOUND', '委外入库单', 'SCI', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 42, 1, 1, '委外入库单编号规则：SCI-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SUBCONTRACT_INBOUND');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SUBCONTRACT_SUPPLEMENT', '委外补料单', 'SCS', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 43, 1, 1, '委外补料单编号规则：SCS-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SUBCONTRACT_SUPPLEMENT');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SUBCONTRACT_RETURN', '委外退料单', 'SCR', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 44, 1, 1, '委外退料单编号规则：SCR-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SUBCONTRACT_RETURN');

-- 4.6 财务单据
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'ACCOUNT_RECEIVABLE', '应收单', 'AR', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 50, 1, 1, '应收单编号规则：AR-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'ACCOUNT_RECEIVABLE');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'ACCOUNT_PAYABLE', '应付单', 'AP', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 51, 1, 1, '应付单编号规则：AP-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'ACCOUNT_PAYABLE');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'RECEIPT', '收款单', 'RCP', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 52, 1, 1, '收款单编号规则：RCP-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'RECEIPT');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PAYMENT', '付款单', 'PAY', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 53, 1, 1, '付款单编号规则：PAY-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PAYMENT');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'OPERATION_EXPENSE', '费用分摊单', 'OE', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 54, 1, 1, '费用分摊单编号规则：OE-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'OPERATION_EXPENSE');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SALE_EXPENSE', '销售费用单', 'SE', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 55, 1, 1, '销售费用单编号规则：SE-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SALE_EXPENSE');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PURCHASE_EXPENSE', '采购费用单', 'PE', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 56, 1, 1, '采购费用单编号规则：PE-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PURCHASE_EXPENSE');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SALE_PRE_RECEIPT', '预收款单', 'SPR', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 57, 1, 1, '预收款单编号规则：SPR-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SALE_PRE_RECEIPT');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PURCHASE_PRE_PAYMENT', '预付款单', 'PPP', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 58, 1, 1, '预付款单编号规则：PPP-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PURCHASE_PRE_PAYMENT');

-- 4.7 CRM / 获客
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'LEAD', '线索', 'LD', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 60, 1, 1, '线索编号规则：LD-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'LEAD');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'OPPORTUNITY', '商机', 'OPP', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 61, 1, 1, '商机编号规则：OPP-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'OPPORTUNITY');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'CONTACT', '联系人', 'CT', '', 4, 'NONE', '', 0, NULL, 62, 1, 1, '联系人编号规则：CT-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'CONTACT');

-- 4.8 样品单
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SAMPLE_ORDER', '样品单', 'SAMPLE', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 70, 1, 1, '样品单编号规则：SAMPLE-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SAMPLE_ORDER');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'FREE_SAMPLE_ORDER', '免费样品单', 'FS', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 71, 1, 1, '免费样品单编号规则：FS-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'FREE_SAMPLE_ORDER');

-- 4.9 基础资料（仅客户、供应商、物料、BOM 支持自动编码；其余默认手动也可按需开启）
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'CUSTOMER', '客户档案', 'CUS', '', 4, 'NONE', '', 0, NULL, 80, 1, 1, '客户编号规则：CUS-0001（默认自动生成，可改为手动）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'CUSTOMER');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SUPPLIER', '供应商档案', 'SUP', '', 4, 'NONE', '', 0, NULL, 81, 1, 1, '供应商编号规则：SUP-0001（默认自动生成，可改为手动）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SUPPLIER');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'MATERIAL', '物料档案', 'MAT', '', 4, 'NONE', '', 0, NULL, 82, 1, 1, '物料编号规则：MAT-0001（仅在未配置物料编码规则时使用，默认自动生成）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'MATERIAL');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'BOM', '物料清单', 'BOM', 'yyyy', 4, 'YEARLY', '-', 0, NULL, 83, 1, 1, 'BOM编号规则：BOM-yyyy-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'BOM');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'EMPLOYEE', '员工档案', 'EMP', '', 4, 'NONE', '', 0, NULL, 84, 1, 1, '员工编号规则：EMP-0001（默认自动生成，可改为手动）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'EMPLOYEE');

-- 4.10 修正原有初始化数据中的前缀（保证与业务文档一致）
--     文档规范前缀：SALE_DELIVERY=SOUT（而不是SD）、SALE_RETURN=SRT（不是SR）、SALE_INVOICE=SINV（不是SI）、
--                    PURCHASE_INVOICE=PIV（不是PI）
UPDATE sys_serial_no_config SET prefix = 'SOUT', remark = '销售出库单编号规则：SOUT-yyyyMMdd-0001'
WHERE doc_type = 'SALE_DELIVERY' AND prefix = 'SD';

UPDATE sys_serial_no_config SET prefix = 'SRT', remark = '销售退货单编号规则：SRT-yyyyMMdd-0001'
WHERE doc_type = 'SALE_RETURN' AND prefix = 'SR';

UPDATE sys_serial_no_config SET prefix = 'SINV', date_format = 'yyyyMM', reset_type = 'MONTHLY',
    remark = '销售发票编号规则：SINV-yyyyMM-0001'
WHERE doc_type = 'SALE_INVOICE' AND prefix = 'SI';

UPDATE sys_serial_no_config SET prefix = 'PIV', date_format = 'yyyyMM', reset_type = 'MONTHLY',
    remark = '采购发票编号规则：PIV-yyyyMM-0001'
WHERE doc_type = 'PURCHASE_INVOICE' AND prefix = 'PI';

-- 4.11 确保所有已存在记录的 auto_generate 默认 1（自动生成）
UPDATE sys_serial_no_config SET auto_generate = 1 WHERE auto_generate IS NULL;
