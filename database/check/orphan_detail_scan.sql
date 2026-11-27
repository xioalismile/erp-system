-- ============================================================================
-- 孤儿明细扫描：找出「主表有单据、明细表 0 行」的历史异常单据
--
-- 用途：排查"明细不持久化"这一类缺陷（自动生成/下推单据只建主表不建明细、
--       更新时误删明细等）在存量数据里造成的影响面。
-- 用法：psql -h localhost -p 5432 -U erp_user -d erp_db -f orphan_detail_scan.sql
--
-- 说明：
--   1) 生产工单有两张明细表，这里只扫"成品明细"(work_order_product)；
--      "原料明细"(production_order_item) 是工单下达时按 BOM 展开的，草稿态为空属正常，故不纳入。
--   2) 各明细表的关联列取自 information_schema 实测，非猜测。
--   3) 不输出"状态"列：各表 status 类型不统一（varchar/int），避免 UNION 类型冲突。
-- ============================================================================
select 单据类型, 主表, 单号
from (
    select '销售报价单' as 单据类型, 'sale_quote' as 主表, m.quote_no as 单号
      from sale_quote m where not exists (select 1 from sale_quote_item i where i.quote_id = m.quote_id)
    union all
    select '销售合同', 'sale_contract', m.contract_no
      from sale_contract m where not exists (select 1 from sale_contract_item i where i.contract_id = m.contract_id)
    union all
    select '销售订单', 'sale_order', m.order_no
      from sale_order m where not exists (select 1 from sale_order_item i where i.order_id = m.order_id)
    union all
    select '销售出库单', 'sale_delivery', m.delivery_no
      from sale_delivery m where not exists (select 1 from sale_delivery_item i where i.delivery_id = m.delivery_id)
    union all
    select '销售退货单', 'sale_return', m.return_no
      from sale_return m where not exists (select 1 from sale_return_item i where i.return_id = m.return_id)
    union all
    select '销售发票', 'sale_invoice', m.invoice_no
      from sale_invoice m where not exists (select 1 from sale_invoice_item i where i.invoice_id = m.invoice_id)
    union all
    select '采购申请单', 'purchase_request', m.request_no
      from purchase_request m where not exists (select 1 from purchase_request_item i where i.request_id = m.request_id)
    union all
    select '采购合同', 'purchase_contract', m.contract_no
      from purchase_contract m where not exists (select 1 from purchase_contract_item i where i.contract_id = m.contract_id)
    union all
    select '采购订单', 'purchase_order', m.order_no
      from purchase_order m where not exists (select 1 from purchase_order_item i where i.order_id = m.order_id)
    union all
    select '采购入库单', 'purchase_receipt', m.receipt_no
      from purchase_receipt m where not exists (select 1 from purchase_receipt_item i where i.receipt_id = m.receipt_id)
    union all
    select '采购退货单', 'purchase_return', m.return_no
      from purchase_return m where not exists (select 1 from purchase_return_item i where i.return_id = m.return_id)
    union all
    select '采购发票', 'purchase_invoice', m.invoice_no
      from purchase_invoice m where not exists (select 1 from purchase_invoice_item i where i.invoice_id = m.invoice_id)
    union all
    select '其他入库单', 'other_receipt', m.receipt_no
      from other_receipt m where not exists (select 1 from other_receipt_item i where i.receipt_id = m.receipt_id)
    union all
    select '其他出库单', 'other_issue', m.issue_no
      from other_issue m where not exists (select 1 from other_issue_item i where i.issue_id = m.issue_id)
    union all
    select '调拨单', 'stock_transfer', m.transfer_no
      from stock_transfer m where not exists (select 1 from stock_transfer_item i where i.transfer_id = m.transfer_id)
    union all
    select '盘点单', 'stock_check', m.check_no
      from stock_check m where not exists (select 1 from stock_check_item i where i.check_id = m.check_id)
    union all
    select '领料单', 'material_requisition', m.requisition_no
      from material_requisition m where not exists (select 1 from material_requisition_item i where i.requisition_id = m.requisition_id)
    union all
    select '生产工单(成品明细)', 'production_order', m.order_no
      from production_order m where not exists (select 1 from work_order_product i where i.order_id = m.order_id)
    union all
    select '生产入库单', 'production_receipt', m.receipt_no
      from production_receipt m where not exists (select 1 from production_receipt_item i where i.receipt_id = m.receipt_id)
    union all
    select '工序转移单', 'process_transfer', m.transfer_no
      from process_transfer m where not exists (select 1 from process_transfer_item i where i.transfer_id = m.transfer_id)
    union all
    select 'MRP计划', 'mrp_plan', m.plan_no
      from mrp_plan m where not exists (select 1 from mrp_plan_item i where i.plan_id = m.id)
    union all
    select '组装单', 'produce_assemble', m.serial_no
      from produce_assemble m where not exists (select 1 from produce_assemble_detail i where i.assemble_id = m.id)
    union all
    select '拆卸单', 'produce_disassemble', m.serial_no
      from produce_disassemble m where not exists (select 1 from produce_disassemble_detail i where i.disassemble_id = m.id)
    union all
    select '委外加工单', 'subcontract_order', m.order_no
      from subcontract_order m where not exists (select 1 from subcontract_order_item i where i.order_id = m.id)
    union all
    select '委外发料单', 'subcontract_pick', m.pick_no
      from subcontract_pick m where not exists (select 1 from subcontract_pick_item i where i.pick_id = m.id)
    union all
    select '委外入库单', 'subcontract_inbound', m.inbound_no
      from subcontract_inbound m where not exists (select 1 from subcontract_inbound_item i where i.inbound_id = m.id)
    union all
    select '委外退料单', 'subcontract_return', m.return_no
      from subcontract_return m where not exists (select 1 from subcontract_return_item i where i.return_id = m.id)
    union all
    select '委外补料单', 'subcontract_supplement', m.supplement_no
      from subcontract_supplement m where not exists (select 1 from subcontract_supplement_item i where i.supplement_id = m.id)
    union all
    select '销售预收款单', 'sale_pre_receipt', m.receipt_no
      from sale_pre_receipt m where not exists (select 1 from sale_pre_receipt_item i where i.pre_receipt_id = m.id)
    union all
    select '采购预付款单', 'purchase_pre_payment', m.payment_no
      from purchase_pre_payment m where not exists (select 1 from purchase_pre_payment_item i where i.pre_payment_id = m.id)
    union all
    select '收款单', 'cash_receipt', m.receipt_no
      from cash_receipt m where not exists (select 1 from cash_receipt_item i where i.receipt_id = m.receipt_id)
    union all
    select '付款单', 'cash_payment', m.payment_no
      from cash_payment m where not exists (select 1 from cash_payment_item i where i.payment_id = m.payment_id)
    union all
    select '打样单', 'sample_order', m.sample_no
      from sample_order m where not exists (select 1 from sample_order_item i where i.sample_id = m.sample_id)
    union all
    select '免费样品单', 'free_sample_order', m.order_no
      from free_sample_order m where not exists (select 1 from free_sample_order_item i where i.order_id = m.order_id)
    union all
    select 'BOM', 'bom', m.color_no
      from bom m where not exists (select 1 from bom_item i where i.bom_id = m.bom_id)
) t
order by 单据类型, 单号;
