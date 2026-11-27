<template>
  <BillDetailReportPage
    api-base="/report/sale/invoice-detail"
    page-key="SaleInvoiceDetailReport"
    title="销售发票明细报表"
    badge="SALE INVOICE DETAIL"
    sub-title="销售发票主表与商品明细拍平成一张表 · 全部字段可勾选 · 全部字段可筛选 · 默认加载本月"
    back-path="/sale/invoices"
    export-name="销售发票明细报表"
  />
</template>

<script setup>
/**
 * 销售发票明细报表（薄壳，与销售订单明细报表同一组件、同一引擎）
 *
 * 注意：这与「销售发票报表」（views/sale/SaleInvoiceReport.vue，开票概览/趋势/客户分析/税额分析
 * 四个分析区块 + 明细弹窗，入口在销售分析报表页）**不是一回事**，两者并存：
 * 那个是分析报表，这个是拍平明细表。本次没有覆盖它（覆盖会丢掉 4 个分析区块）。
 */
import BillDetailReportPage from '@/components/report/BillDetailReportPage.vue'
</script>
