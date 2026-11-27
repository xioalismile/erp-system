-- 55_finance_ar_source_fix.sql
-- 应收账款自动推送关闭：默认改为 none（不自动生成），财务在收款单/付款单自行拉取源单。
--
-- 背景：sys_config.finance.ar.source_bill_type 历史值 'sale_order'，导致销售订单审核通过
--       立即自动生成应收账款（is_auto_gen=1），订单想重置草稿时被"下游引用校验"挡死：
--       "销售订单[SO-xxx]已被下游单据引用: 应收账款单:AR-xxx，请先作废下游后再重置草稿"。
-- 2026-09-02 用户决策：应收/应付不自动推送（none），财务在收款单/付款单里自行拉取源单信息；
--       收款单编辑页已有"来源销售订单选择"（直接对订单收款并回写 received_amount）。
-- 代码侧：SaleOrderServiceImpl/SaleInvoiceServiceImpl/SaleDeliveryServiceImpl 三处
--       getValue("finance.ar.source_bill_type") 默认值已同步改为 none。
--       可选值: none(默认,不生成) / sale_order(订单审核) / sale_delivery(出库审核) / sale_invoice(发票审核)
--
-- 本脚本：将历史值收敛为 'none'（幂等条件更新，重复执行影响 0 行）。
-- 缓存说明：SysConfigService 有 Caffeine 1 小时缓存；部署链路中 SQL kit 在 jar 重启前执行，
--           重启后缓存重新加载，天然生效。已运行环境手工改值后需调 POST /api/sys-config/refresh。

UPDATE sys_config
SET config_value = 'none',
    updated_at = NOW()
WHERE config_key = 'finance.ar.source_bill_type'
  AND COALESCE(config_value, '') <> 'none';
