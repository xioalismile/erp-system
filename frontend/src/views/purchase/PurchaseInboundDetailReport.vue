<template>
  <BillDetailReportPage
    api-base="/report/purchase/inbound-detail"
    page-key="PurchaseInboundDetailReport"
    title="采购入库单明细报表"
    badge="PURCHASE INBOUND DETAIL"
    sub-title="采购入库单主表与商品明细拍平成一张表 · 全部字段可勾选 · 全部字段可筛选 · 默认加载本月"
    back-path="/purchase/inbounds"
    export-name="采购入库单明细报表"
  />
</template>

<script setup>
/**
 * 采购入库单明细报表（薄壳）
 *
 * 功能实现全在通用组件 components/report/BillDetailReportPage.vue 里 —— 全部明细报表共用同一个组件，
 * 只传各自配置，保证"功能一模一样、复刻不缺东西"。
 * pageKey 必须每个报表各不相同（否则列设置与筛选设置会互相串）。
 */
import BillDetailReportPage from '@/components/report/BillDetailReportPage.vue'
</script>
