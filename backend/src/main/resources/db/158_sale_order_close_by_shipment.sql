-- 158: 销售订单完结口径修正（完结只看发货，退货冲减）· 存量重算
-- 背景：旧口径 totalTurned = 已发货 + 已退货 + 已开票 ≥ 订购数量 → 明细/订单结案；
--       直接从订单下推全额发票（未发过货）也会把订单置为已结案 → 无法再下推发货单。
-- 新口径（2026-10-14 用户拍板）：净发货 = 已发货(turned_output_quantity) - 已退货(turned_return_quantity)
--       ≥ 订购数量 → 结案；开票(turned_invoice_quantity)只作开票进度显示，不参与完结。
--       退货冲减：退货越多净发货越少，跌破订购量自动解结案。
-- 代码侧同口径实现：SaleOrderServiceImpl.updateTurnedQuantity（2026-10-14）。
-- 本脚本幂等可重跑：明细全量按新公式重算；订单级只重算"非手动结案"的订单
--   （closed_reason = '手动结案' 的人工决定保持不动）；无明细订单保持原值。

-- 1) 明细重算（净发货口径；订购数量为 0 的异常明细一律不结案，与 Java 侧防呆一致）
UPDATE sale_order_item
SET detail_close_status = CASE
    WHEN COALESCE(quantity, 0) > 0
         AND COALESCE(turned_output_quantity, 0) - COALESCE(turned_return_quantity, 0) >= COALESCE(quantity, 0)
    THEN 1 ELSE 0 END;

-- 2) 订单重算（按明细汇总；无明细保持原值；手动结案订单不动）
UPDATE sale_order o
SET close_status = CASE
    WHEN NOT EXISTS (SELECT 1 FROM sale_order_item i WHERE i.order_id = o.order_id) THEN o.close_status
    WHEN EXISTS (SELECT 1 FROM sale_order_item i
                 WHERE i.order_id = o.order_id AND COALESCE(i.detail_close_status, 0) <> 1) THEN 0
    ELSE 1 END
WHERE COALESCE(o.closed_reason, '') <> '手动结案';
