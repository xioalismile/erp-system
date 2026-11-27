-- =====================================================================
-- 88 单据审批开关默认行补齐（设置中心-审批设置-单据审批开关）
-- 背景：后端 ApprovalController.listBillTypes() 用固定数组列出全部"单据身份"，
--       每个身份都应有对应的 sys_config 行 approval.bill.<身份>.enabled。
--       此前 PAYMENT_RECEIPT(收款单) / PAYMENT_VOUCHER(付款单) 在库中没有登记行，
--       设置中心缺项；另外部分行是被"保存开关"流程裸插进去的（只有 key+value，没有中文名/分组/类型）。
-- 本脚本按 listBillTypes() 数组逐个身份补齐默认行（中文名一律照抄该数组，不另行起名）。
-- 幂等：INSERT ... SELECT ... WHERE NOT EXISTS，重复执行不产生重复行；
--       已存在的行（含用户已改过的开关值）一律不覆盖、不改值。
-- 默认值：'false'（= 免审批，与升级前行为一致，不改动任何现状）。
-- =====================================================================

-- ============ 销售管理 ============
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.SALE_QUOTE.enabled', 'false', '销售报价单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制销售报价单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.SALE_QUOTE.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.SALE_ORDER.enabled', 'false', '销售订单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制销售订单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.SALE_ORDER.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.SALE_CONTRACT.enabled', 'false', '销售合同需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制销售合同是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.SALE_CONTRACT.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.SALE_DELIVERY.enabled', 'false', '销售出库单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制销售出库单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.SALE_DELIVERY.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.SALE_RETURN.enabled', 'false', '销售退货需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制销售退货是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.SALE_RETURN.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.SALE_INVOICE.enabled', 'false', '销售发票需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制销售发票是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.SALE_INVOICE.enabled');

-- ============ 采购管理 ============
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PURCHASE_REQUEST.enabled', 'false', '采购申请单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制采购申请单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PURCHASE_REQUEST.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PURCHASE_ORDER.enabled', 'false', '采购订单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制采购订单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PURCHASE_ORDER.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PURCHASE_CONTRACT.enabled', 'false', '采购合同需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制采购合同是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PURCHASE_CONTRACT.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PURCHASE_INBOUND.enabled', 'false', '采购入库单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制采购入库单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PURCHASE_INBOUND.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PURCHASE_RETURN.enabled', 'false', '采购退货单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制采购退货单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PURCHASE_RETURN.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PURCHASE_INVOICE.enabled', 'false', '采购发票需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制采购发票是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PURCHASE_INVOICE.enabled');

-- ============ 生产管理 ============
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PRODUCTION_ORDER.enabled', 'false', '生产工单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制生产工单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PRODUCTION_ORDER.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PRODUCTION_RECEIPT.enabled', 'false', '生产入库单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制生产入库单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PRODUCTION_RECEIPT.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.MATERIAL_REQUISITION.enabled', 'false', '生产领料单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制生产领料单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.MATERIAL_REQUISITION.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PRODUCE_ASSEMBLE.enabled', 'false', '生产组装单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制生产组装单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PRODUCE_ASSEMBLE.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PRODUCE_DISASSEMBLE.enabled', 'false', '生产拆卸单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制生产拆卸单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PRODUCE_DISASSEMBLE.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.MATERIAL_RETURN.enabled', 'false', '生产退料单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制生产退料单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.MATERIAL_RETURN.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.MATERIAL_SUPPLEMENT.enabled', 'false', '生产补料单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制生产补料单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.MATERIAL_SUPPLEMENT.enabled');

-- ============ 委外管理 ============
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.SUBCONTRACT_ORDER.enabled', 'false', '委外加工单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制委外加工单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.SUBCONTRACT_ORDER.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.SUBCONTRACT_DELIVERY.enabled', 'false', '委外发料单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制委外发料单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.SUBCONTRACT_DELIVERY.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.SUBCONTRACT_RECEIPT.enabled', 'false', '委外入库单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制委外入库单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.SUBCONTRACT_RECEIPT.enabled');

-- ============ 打样/样品 ============
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.SAMPLE_ORDER.enabled', 'false', '打样单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制打样单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.SAMPLE_ORDER.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.FREE_SAMPLE.enabled', 'false', '免费样品订单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制免费样品订单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.FREE_SAMPLE.enabled');

-- ============ 库存管理 ============
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.STOCK_TRANSFER.enabled', 'false', '库存调拨单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制库存调拨单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.STOCK_TRANSFER.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.STOCK_CHECK.enabled', 'false', '库存盘点单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制库存盘点单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.STOCK_CHECK.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.OTHER_RECEIPT.enabled', 'false', '其他入库单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制其他入库单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.OTHER_RECEIPT.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.OTHER_ISSUE.enabled', 'false', '其他出库单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制其他出库单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.OTHER_ISSUE.enabled');

-- ============ 财务管理 ============
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PURCHASE_EXPENSE.enabled', 'false', '采购费用单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制采购费用单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PURCHASE_EXPENSE.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PURCHASE_PRE_PAYMENT.enabled', 'false', '预付款单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制预付款单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PURCHASE_PRE_PAYMENT.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PAYMENT_RECEIPT.enabled', 'false', '收款单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制收款单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PAYMENT_RECEIPT.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.PAYMENT_VOUCHER.enabled', 'false', '付款单需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制付款单是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.PAYMENT_VOUCHER.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.OPERATION_EXPENSE.enabled', 'false', '费用分摊需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制费用分摊是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.OPERATION_EXPENSE.enabled');

-- ============ 其他单据 ============
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.BOM.enabled', 'false', 'BOM需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制BOM是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.BOM.enabled');

INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_type, config_options, remark, created_by, created_at)
SELECT 'approval.bill.CUSTOMER_CREDIT.enabled', 'false', '客户授信（月结一次性审批）需要审批', 'approval', 'boolean',
       '[{"label":"启用","value":true},{"label":"停用","value":false}]',
       '控制客户授信（月结一次性审批）是否进入审批流程', 'system', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'approval.bill.CUSTOMER_CREDIT.enabled');
