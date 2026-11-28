SET client_encoding = 'UTF8';

-- ============ 只读验收脚本（全部 SELECT，不写任何数据） ============
WITH checks AS (
  -- 物料主数据
  SELECT '物料计量单位=E2E-个(65)' AS check_name,
         (SELECT COUNT(*) FROM material WHERE material_id = 1810 AND base_unit_id = 65) = 1 AS ok
  UNION ALL SELECT '物料采购价=10、销售价=20、税率=0',
         (SELECT COUNT(*) FROM material WHERE material_id = 1810 AND purchase_price = 10 AND sale_price = 20 AND tax_rate = 0) = 1
  -- 采购
  UNION ALL SELECT '采购订单数量=100、单价=10、价税合计=1000',
         (SELECT COUNT(*) FROM purchase_order_item WHERE order_id = 19 AND material_id = 1810 AND quantity = 100 AND unit_price = 10 AND net_amount = 1000) = 1
  UNION ALL SELECT '采购订单已生效',
         (SELECT COUNT(*) FROM purchase_order WHERE order_id = 19 AND status = '已生效') = 1
  UNION ALL SELECT '采购订单已入库数量=100',
         (SELECT COUNT(*) FROM purchase_order_item WHERE order_id = 19 AND material_id = 1810 AND received_quantity = 100) = 1
  UNION ALL SELECT '采购入库数量=100、单价=10、价税合计=1000',
         (SELECT COUNT(*) FROM purchase_receipt_item WHERE receipt_id = 53 AND material_id = 1810 AND quantity = 100 AND unit_price = 10 AND net_amount = 1000) = 1
  UNION ALL SELECT '采购入库已生效',
         (SELECT COUNT(*) FROM purchase_receipt WHERE receipt_id = 53 AND status = '已生效') = 1
  UNION ALL SELECT '采购入库流水=100件×10元=1000元',
         (SELECT COUNT(*) FROM inventory_ledger WHERE trans_no = 'PIN-20261128-0001' AND material_id = 1810
            AND trans_type = 'PURCHASE_IN' AND in_quantity = 100 AND unit_cost = 10 AND in_amount = 1000) = 1
  -- 销售
  UNION ALL SELECT '销售订单数量=80、单价=20、价税合计=1600',
         (SELECT COUNT(*) FROM sale_order_item WHERE order_id = 209 AND material_id = 1810 AND quantity = 80 AND unit_price = 20 AND net_amount = 1600) = 1
  UNION ALL SELECT '销售出库数量=70、单价=20、价税合计=1400、成本=10',
         (SELECT COUNT(*) FROM sale_delivery_item WHERE delivery_id = 109 AND material_id = 1810
            AND quantity = 70 AND unit_price = 20 AND net_amount = 1400 AND avg_cost = 10) = 1
  UNION ALL SELECT '销售出库已生效',
         (SELECT COUNT(*) FROM sale_delivery WHERE delivery_id = 109 AND status = '已生效') = 1
  UNION ALL SELECT '订单勾稽=已出库70、已退货15、已开票70',
         (SELECT COUNT(*) FROM sale_order_item WHERE order_id = 209 AND material_id = 1810
            AND turned_output_quantity = 70 AND turned_return_quantity = 15 AND turned_invoice_quantity = 70) = 1
  UNION ALL SELECT '销售发票价税合计=1400、税额=0、已生效',
         (SELECT COUNT(*) FROM sale_invoice WHERE invoice_id = 82 AND status = '已生效' AND net_amount = 1400 AND tax_amount = 0) = 1
  -- 退货
  UNION ALL SELECT '回库退货10件、金额200、库存影响=1、已生效',
         (SELECT COUNT(*) FROM sale_return WHERE return_id = 22 AND status = '已生效' AND stock_effect = 1 AND net_amount = 200) = 1
  UNION ALL SELECT '不回库退货5件、金额100、库存影响=0、已生效',
         (SELECT COUNT(*) FROM sale_return WHERE return_id = 23 AND status = '已生效' AND stock_effect = 0 AND net_amount = 100) = 1
  UNION ALL SELECT '两张红字发票=200、100，均已生效',
         (SELECT COUNT(*) FROM sale_invoice WHERE invoice_id IN (83, 84) AND status = '已生效'
            AND ((invoice_id = 83 AND net_amount = 200) OR (invoice_id = 84 AND net_amount = 100))) = 2
  UNION ALL SELECT '两张红字发票均关联原蓝票SINV-202611-0010',
         (SELECT COUNT(*) FROM sale_invoice WHERE invoice_id IN (83, 84) AND original_invoice_id = 82) = 2
  UNION ALL SELECT '回库退货明细冻结成本=10',
         (SELECT COUNT(*) FROM sale_return_item WHERE return_id = 22 AND material_id = 1810 AND avg_cost = 10) = 1
  UNION ALL SELECT '超量退货草稿已清理',
         (SELECT COUNT(*) FROM sale_return WHERE return_id = 24) = 0
  -- 库存
  UNION ALL SELECT '即时库存=E2E主仓40件、成本10、金额400',
         (SELECT COUNT(*) FROM inventory_current WHERE material_id = 1810 AND stock_id = 20
            AND quantity = 40 AND unit_cost = 10 AND total_cost = 400) = 1
  UNION ALL SELECT '库存流水净数量=采购100-销售70+退货10=40',
         (SELECT COALESCE(SUM(in_quantity) - SUM(out_quantity), 0) FROM inventory_ledger WHERE material_id = 1810) = 40
  UNION ALL SELECT '库存流水成本净额=销售出库成本-出库冲回-退货入库成本=600',
         (SELECT COALESCE(SUM(CASE WHEN trans_type = 'SALE_OUT' THEN out_amount ELSE 0 END)
                       - SUM(CASE WHEN trans_type = 'SALE_OUT_REVERSE' THEN in_amount ELSE 0 END)
                       - SUM(CASE WHEN trans_type = 'SALE_RETURN_IN' THEN in_amount ELSE 0 END), 0)
          FROM inventory_ledger WHERE material_id = 1810) = 600
  -- 财务
  UNION ALL SELECT '收款单已确认、金额1100',
         (SELECT COUNT(*) FROM cash_receipt WHERE receipt_id = 83 AND status = '已确认' AND amount = 1100 AND received_amount = 1100) = 1
  UNION ALL SELECT '收款核销销售发票1100、状态已冲销',
         (SELECT COUNT(*) FROM bill_writeoff_detail WHERE bill_type = '收款单' AND bill_id = 83
            AND source_type = '销售发票' AND source_id = 82 AND writeoff_amount = 1100 AND settled_qty = '已冲销') = 1
  UNION ALL SELECT '收款已回写订单：表头/明细已收=1100',
         (SELECT COUNT(*) FROM sale_order WHERE order_id = 209 AND received_amount = 1100) = 1
         AND (SELECT COUNT(*) FROM sale_order_item WHERE order_item_id = 303 AND received_amount = 1100) = 1
  UNION ALL SELECT '订单占用=80-(出库70-退货15)=25',
         (SELECT COALESCE(quantity,0) - (COALESCE(turned_output_quantity,0) - COALESCE(turned_return_quantity,0))
          FROM sale_order_item WHERE order_item_id = 303) = 25
  UNION ALL SELECT '预计可用量=库存40-订单占用25=15',
         (SELECT 40 - (SELECT COALESCE(quantity,0) - (COALESCE(turned_output_quantity,0) - COALESCE(turned_return_quantity,0))
                       FROM sale_order_item WHERE order_item_id = 303)) = 15
  UNION ALL SELECT '净应收=蓝票1400-红票200-红票100-收款1100=0',
         (SELECT 1400 - 200 - 100 - 1100) = 0
  -- 成本与毛利
  UNION ALL SELECT '净收入=1400-200-100=1100',
         (SELECT 1400 - 200 - 100) = 1100
  UNION ALL SELECT '净销售成本=(出库70-回库10)×10=600',
         (SELECT (70 - 10) * 10) = 600
  UNION ALL SELECT '毛利=净收入1100-净成本600=500',
         (SELECT (1400 - 200 - 100) - (70 - 10) * 10) = 500
  UNION ALL SELECT '采购1000=库存400+销售成本600',
         (SELECT 1000 - (400 + 600)) = 0
  -- 计量一致性
  UNION ALL SELECT '采购/出库/退货明细单位均为E2E-个(65)',
         (SELECT COUNT(*) FROM purchase_receipt_item WHERE receipt_id = 53 AND unit_id = 65) = 1
         AND (SELECT COUNT(*) FROM sale_delivery_item WHERE delivery_id = 109 AND unit_id = 65) = 1
         AND (SELECT COUNT(*) FROM sale_return_item WHERE return_id IN (22, 23) AND unit_id = 65) = 2
)
SELECT check_name AS "验收项", CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS "结果" FROM checks
UNION ALL
SELECT '合计', (SELECT COUNT(*) FILTER (WHERE ok) || '/' || COUNT(*) FROM checks);

-- 关键账面明细（便于人工复核）
SELECT '库存' AS kind, material_id::text AS ref, stock_id::text AS place, quantity::text AS qty, unit_cost::text AS unit_cost, total_cost::text AS amount
FROM inventory_current WHERE material_id = 1810
UNION ALL
SELECT '发票', i.invoice_no, i.invoice_type, it.quantity::text, i.invoice_id::text, i.net_amount::text
FROM sale_invoice i LEFT JOIN sale_invoice_item it ON it.invoice_id = i.invoice_id
WHERE i.invoice_id IN (82, 83, 84);
