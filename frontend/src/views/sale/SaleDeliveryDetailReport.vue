<template>
  <BillDetailReportPage
    api-base="/report/sale/delivery-detail"
    page-key="SaleDeliveryDetailReport"
    title="销售出库明细报表"
    badge="SALE DELIVERY DETAIL"
    sub-title="销售出库单主表与商品明细拍平成一张表 · 全部字段可勾选 · 全部字段可筛选 · 默认加载本月"
    back-path="/sale/deliveries"
    export-name="销售出库明细报表"
  />
</template>

<script setup>
/**
 * 销售出库明细报表（薄壳，与销售订单明细报表同一组件、同一引擎）
 */
import BillDetailReportPage from '@/components/report/BillDetailReportPage.vue'
</script>
