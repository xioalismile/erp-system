-- 159: 销售订单完结口径可配置（设置中心「销售订单状态设置」）+ 明细「已开票金额」列 + 存量回填
-- 背景（2026-10-14 用户需求）：完结口径从"写死只看发货"升级为设置中心三选一：
--   ① 销售发货单（默认，= 158 现行口径）：发完货自动结案；
--   ② 销售发票：该订单已生效发票金额收齐（明细行已开票金额 ≥ 行价税合计）自动结案；
--   ③ 销售发货+销售发票+收款单：三者全部完成才自动结案。
-- 下推联动：已结案订单不允许下推（前端按钮隐藏 + 后端 pushToDelivery/pushToPurchase/发票保存/出库审核守卫）。
-- 本脚本幂等可重跑。同内容文件另存 deploy/sql/159_sale_order_close_mode_setting.sql（守则04 编号体系）。
-- 代码侧同口径实现：SaleOrderServiceImpl.recalcOrderCloseStatus（三模式统一重算）。

-- 1) 设置键（缺则补、有则不动管理员已改过的值；「销售订单」分区与 43 号脚本口径一致）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, config_section,
                        config_type, config_options, sort_order, remark,
                        created_at, updated_at, created_by, updated_by)
SELECT 'sale.order.auto.close.mode', '销售发货单', '销售订单状态设置', 'sale', '销售订单',
       'select',
       '[{"label":"销售发货单","value":"销售发货单"},{"label":"销售发票","value":"销售发票"},{"label":"销售发货+销售发票+收款单","value":"销售发货+销售发票+收款单"}]',
       60,
       '销售发货单：当前订单发完货之后才会进入自动结案状态；销售发票：当前销售订单下推收完发票的金额之后才会进入自动结案状态；销售发货+销售发票+收款单：当前销售订单的下游单据这三种单必须全部完成才算是自动结案',
       NOW(), NOW(), 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sale.order.auto.close.mode');

-- 2) 明细行「已开票金额」（与 turned_invoice_quantity 同路径双向维护：发票生效 +net_amount、重置草稿 -net_amount）
ALTER TABLE sale_order_item ADD COLUMN IF NOT EXISTS turned_invoice_amount NUMERIC(18,2) NOT NULL DEFAULT 0;

-- 3) 存量回填（幂等：只回填列值为 0 的行；按 158/运行时同口径聚合——
--    已生效发票、来源为「销售订单直开」或「销售出库单」两条链路；「销售退货单」来源不回写订单（与运行时镜像））
UPDATE sale_order_item oi
SET turned_invoice_amount = agg.amt
FROM (
    SELECT src_item_id AS oid, SUM(amt) AS amt FROM (
        -- 订单直开：发票明细行 → 订单明细
        SELECT i.source_item_id AS src_item_id, COALESCE(i.net_amount, 0) AS amt
        FROM sale_invoice_item i
        JOIN sale_invoice h ON h.invoice_id = i.invoice_id
        WHERE h.status = '已生效' AND i.source_bill_type = '销售订单' AND i.source_item_id IS NOT NULL
        UNION ALL
        -- 从出库开票：发票明细行 → 出库明细 → 订单明细
        SELECT di.source_item_id AS src_item_id, COALESCE(i.net_amount, 0) AS amt
        FROM sale_invoice_item i
        JOIN sale_invoice h ON h.invoice_id = i.invoice_id
        JOIN sale_delivery_item di ON di.delivery_item_id = i.source_item_id
        WHERE h.status = '已生效' AND i.source_bill_type IN ('销售出库单', '销售出库')
          AND di.source_item_id IS NOT NULL
    ) t
    GROUP BY src_item_id
) agg
WHERE oi.order_item_id = agg.oid
  AND COALESCE(oi.turned_invoice_amount, 0) = 0
  AND agg.amt <> 0;
