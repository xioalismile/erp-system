<template>
  <BillDetailReportPage
    api-base="/report/sale/order-detail"
    page-key="SaleOrderDetailReport"
    title="销售订单明细报表"
    badge="SALE ORDER DETAIL"
    sub-title="销售订单主表与商品明细拍平成一张表 · 全部字段可勾选 · 全部字段可筛选 · 默认加载本月"
    back-path="/sale/orders"
    export-name="销售订单明细报表"
  />
</template>

<script setup>
/**
 * 销售订单明细报表（薄壳）
 *
 * 功能实现全在通用组件 components/report/BillDetailReportPage.vue 里 ——
 * 销售订单 / 销售出库 / 销售发票 三个报表共用同一个组件，只传各自的配置，
 * 保证"三个报表功能一模一样，复刻不缺东西"。
 *
 * 三个报表的 pageKey / apiBase 必须各不相同（否则列设置与筛选设置会互相串）。
 */
import BillDetailReportPage from '@/components/report/BillDetailReportPage.vue'
</script>
