-- [1] select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt from sale_order where
PREPARE chk_1 AS select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt from sale_order where source_bill_no = $1;
DEALLOCATE chk_1;
-- [2] select contract_id as id, contract_no as no, contract_date as dt, status as st, net_amount as amt from sale_co
PREPARE chk_2 AS select contract_id as id, contract_no as no, contract_date as dt, status as st, net_amount as amt from sale_contract where source_bill_no = $1;
DEALLOCATE chk_2;
-- [3] select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt from sale_order where
PREPARE chk_3 AS select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt from sale_order where source_bill_no = $1;
DEALLOCATE chk_3;
-- [4] select delivery_id as id, delivery_no as no, delivery_date as dt, status as st, net_amount as amt from sale_de
PREPARE chk_4 AS select delivery_id as id, delivery_no as no, delivery_date as dt, status as st, net_amount as amt from sale_delivery where source_bill_no = $1;
DEALLOCATE chk_4;
-- [5] select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt from purchase_order w
PREPARE chk_5 AS select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt from purchase_order where source_bill_no = $1;
DEALLOCATE chk_5;
-- [6] select ar_id as id, ar_no as no, ar_date as dt, status as st, total_amount as amt from account_receivable wher
PREPARE chk_6 AS select ar_id as id, ar_no as no, ar_date as dt, status as st, total_amount as amt from account_receivable where source_bill_no = $1;
DEALLOCATE chk_6;
-- [7] select order_id as id, order_no as no, order_date as dt, status as st, null as amt from production_order where
PREPARE chk_7 AS select order_id as id, order_no as no, order_date as dt, status as st, null as amt from production_order where source_no = $1 and source_type = '销售订单';
DEALLOCATE chk_7;
-- [8] select p.id as id, p.plan_no as no, p.plan_date as dt, p.plan_status as st, null as amt from mrp_plan p where 
PREPARE chk_8 AS select p.id as id, p.plan_no as no, p.plan_date as dt, p.plan_status as st, null as amt from mrp_plan p where p.id in (select i.plan_id from mrp_plan_item i where i.sale_order_no = $1);
DEALLOCATE chk_8;
-- [9] select invoice_id as id, invoice_no as no, invoice_date as dt, status as st, net_amount as amt from sale_invoi
PREPARE chk_9 AS select invoice_id as id, invoice_no as no, invoice_date as dt, status as st, net_amount as amt from sale_invoice where source_bill_no = $1;
DEALLOCATE chk_9;
-- [10] select ar_id as id, ar_no as no, ar_date as dt, status as st, total_amount as amt from account_receivable wher
PREPARE chk_10 AS select ar_id as id, ar_no as no, ar_date as dt, status as st, total_amount as amt from account_receivable where source_bill_no = $1;
DEALLOCATE chk_10;
-- [11] select ar_id as id, ar_no as no, ar_date as dt, status as st, total_amount as amt from account_receivable wher
PREPARE chk_11 AS select ar_id as id, ar_no as no, ar_date as dt, status as st, total_amount as amt from account_receivable where source_bill_no = $1;
DEALLOCATE chk_11;
-- [12] select invoice_id as id, invoice_no as no, invoice_date as dt, status as st, net_amount as amt from sale_invoi
PREPARE chk_12 AS select invoice_id as id, invoice_no as no, invoice_date as dt, status as st, net_amount as amt from sale_invoice where source_bill_no = $1;
DEALLOCATE chk_12;
-- [13] select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt from cash_rec
PREPARE chk_13 AS select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt from cash_receipt where source_bill_no = $1;
DEALLOCATE chk_13;
-- [14] select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt from purchase_order w
PREPARE chk_14 AS select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt from purchase_order where source_bill_no = $1;
DEALLOCATE chk_14;
-- [15] select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt from purchase_order w
PREPARE chk_15 AS select order_id as id, order_no as no, order_date as dt, status as st, net_amount as amt from purchase_order where source_bill_no = $1;
DEALLOCATE chk_15;
-- [16] select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt from purchase
PREPARE chk_16 AS select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt from purchase_receipt where source_bill_no = $1;
DEALLOCATE chk_16;
-- [17] select ap_id as id, ap_no as no, ap_date as dt, status as st, total_amount as amt from account_payable where s
PREPARE chk_17 AS select ap_id as id, ap_no as no, ap_date as dt, status as st, total_amount as amt from account_payable where source_bill_no = $1;
DEALLOCATE chk_17;
-- [18] select invoice_id as id, invoice_no as no, invoice_date as dt, status as st, net_amount as amt from purchase_i
PREPARE chk_18 AS select invoice_id as id, invoice_no as no, invoice_date as dt, status as st, net_amount as amt from purchase_invoice where source_bill_no = $1;
DEALLOCATE chk_18;
-- [19] select payment_id as id, payment_no as no, payment_date as dt, status as st, total_amount as amt from cash_pay
PREPARE chk_19 AS select payment_id as id, payment_no as no, payment_date as dt, status as st, total_amount as amt from cash_payment where source_bill_no = $1;
DEALLOCATE chk_19;
-- [20] select requisition_id as id, requisition_no as no, requisition_date as dt, status as st, total_amount as amt f
PREPARE chk_20 AS select requisition_id as id, requisition_no as no, requisition_date as dt, status as st, total_amount as amt from material_requisition where source_bill_no = $1;
DEALLOCATE chk_20;
-- [21] select transfer_id as id, transfer_no as no, transfer_date as dt, status as st, null as amt from process_trans
PREPARE chk_21 AS select transfer_id as id, transfer_no as no, transfer_date as dt, status as st, null as amt from process_transfer where source_bill_no = $1;
DEALLOCATE chk_21;
-- [22] select report_id as id, report_no as no, report_date as dt, status as st, null as amt from operation_report wh
PREPARE chk_22 AS select report_id as id, report_no as no, report_date as dt, status as st, null as amt from operation_report where source_bill_no = $1;
DEALLOCATE chk_22;
-- [23] select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt from producti
PREPARE chk_23 AS select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt from production_receipt where source_bill_no = $1;
DEALLOCATE chk_23;
-- [24] select id as id, order_no as no, order_date as dt, status as st, net_amount as amt from subcontract_order wher
PREPARE chk_24 AS select id as id, order_no as no, order_date as dt, status as st, net_amount as amt from subcontract_order where source_bill_no = $1;
DEALLOCATE chk_24;
-- [25] select report_id as id, report_no as no, report_date as dt, status as st, null as amt from operation_report wh
PREPARE chk_25 AS select report_id as id, report_no as no, report_date as dt, status as st, null as amt from operation_report where source_bill_no = $1;
DEALLOCATE chk_25;
-- [26] select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt from producti
PREPARE chk_26 AS select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt from production_receipt where source_bill_no = $1;
DEALLOCATE chk_26;
-- [27] select request_id as id, request_no as no, request_date as dt, status as st, null as amt from purchase_request
PREPARE chk_27 AS select request_id as id, request_no as no, request_date as dt, status as st, null as amt from purchase_request where source_bill_no = $1;
DEALLOCATE chk_27;
-- [28] select order_id as id, order_no as no, order_date as dt, status as st, null as amt from production_order where
PREPARE chk_28 AS select order_id as id, order_no as no, order_date as dt, status as st, null as amt from production_order where source_no = $1;
DEALLOCATE chk_28;
-- [29] select id as id, order_no as no, order_date as dt, status as st, net_amount as amt from subcontract_order wher
PREPARE chk_29 AS select id as id, order_no as no, order_date as dt, status as st, net_amount as amt from subcontract_order where source_bill_no = $1;
DEALLOCATE chk_29;
-- [30] select id as id, pick_no as no, pick_date as dt, status as st, null as amt from subcontract_pick where order_i
PREPARE chk_30 AS select id as id, pick_no as no, pick_date as dt, status as st, null as amt from subcontract_pick where order_id in (select id from subcontract_order where order_no = $1);
DEALLOCATE chk_30;
-- [31] select id as id, inbound_no as no, inbound_date as dt, status as st, null as amt from subcontract_inbound wher
PREPARE chk_31 AS select id as id, inbound_no as no, inbound_date as dt, status as st, null as amt from subcontract_inbound where order_id in (select id from subcontract_order where order_no = $1);
DEALLOCATE chk_31;
-- [32] select ap_id as id, ap_no as no, ap_date as dt, status as st, total_amount as amt from account_payable where s
PREPARE chk_32 AS select ap_id as id, ap_no as no, ap_date as dt, status as st, total_amount as amt from account_payable where source_bill_no = $1;
DEALLOCATE chk_32;
-- [33] select requisition_id as id, requisition_no as no, requisition_date as dt, status as st, total_amount as amt f
PREPARE chk_33 AS select requisition_id as id, requisition_no as no, requisition_date as dt, status as st, total_amount as amt from material_requisition where source_bill_no = $1;
DEALLOCATE chk_33;
-- [34] select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt from producti
PREPARE chk_34 AS select receipt_id as id, receipt_no as no, receipt_date as dt, status as st, total_amount as amt from production_receipt where source_bill_no = $1;
DEALLOCATE chk_34;
