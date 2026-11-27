-- 修正和补齐单据编号配置（使用 "current_date" 双引号避免保留字冲突）

-- 修正几个已有记录的错误前缀（与业务文档一致）
UPDATE sys_serial_no_config SET prefix = 'PIN', remark = '采购入库单编号规则：PIN-yyyyMMdd-0001'
WHERE doc_type = 'PURCHASE_INBOUND' AND prefix = 'PI';

UPDATE sys_serial_no_config SET prefix = 'PRT', remark = '采购退货单编号规则：PRT-yyyyMMdd-0001'
WHERE doc_type = 'PURCHASE_RETURN' AND prefix = 'PR';

-- 4.1 销售链 - 缺失的
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SALE_QUOTE', '销售报价', 'QUT', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 1, 1, 1, '销售报价单编号规则：QUT-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SALE_QUOTE');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SALE_CONTRACT', '销售合同', 'SCON', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 3, 1, 1, '销售合同编号规则：SCON-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SALE_CONTRACT');

-- 4.2 采购链 - 缺失的
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PURCHASE_REQUEST', '采购申请', 'PR', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 10, 1, 1, '采购申请编号规则：PR-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PURCHASE_REQUEST');

INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'PURCHASE_CONTRACT', '采购合同', 'PCON', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 12, 1, 1, '采购合同编号规则：PCON-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'PURCHASE_CONTRACT');

-- 4.4 生产单据 - 缺失的
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'MRP_PLAN', 'MRP计划', 'MP', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 30, 1, 1, 'MRP计划编号规则：MP-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'MRP_PLAN');

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
SELECT 'CONTACT', '联系人', 'CT', '', 4, 'NONE', '', 0, NULL, 62, 1, 1, '联系人编号规则：CT0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'CONTACT');

-- 4.8 样品单
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'SAMPLE_ORDER', '样品单', 'SAMPLE', 'yyyyMMdd', 4, 'DAILY', '-', 0, NULL, 70, 1, 1, '样品单编号规则：SAMPLE-yyyyMMdd-0001'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'SAMPLE_ORDER');

-- 4.9 员工档案
INSERT INTO sys_serial_no_config (doc_type, doc_name, prefix, date_format, serial_length, reset_type, separator, current_serial, "current_date", sort_order, enabled, auto_generate, remark)
SELECT 'EMPLOYEE', '员工档案', 'EMP', '', 4, 'NONE', '', 0, NULL, 84, 1, 1, '员工编号规则：EMP0001（默认自动生成，可改为手动）'
WHERE NOT EXISTS (SELECT 1 FROM sys_serial_no_config WHERE doc_type = 'EMPLOYEE');

-- 确保所有 auto_generate 为 1
UPDATE sys_serial_no_config SET auto_generate = 1 WHERE auto_generate IS NULL OR auto_generate = 0;
