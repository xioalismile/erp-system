<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['print']"
      :title="$tt('币种')"
      custom-field-entity="currency"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/currency/new"
      edit-route="/basedata/currency/edit"
      id-field="currencyId"
      perm-add="basedata:currency:add"
      perm-edit="basedata:currency:edit"
      perm-delete="basedata:currency:delete"
      entity-name="currency"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
      </template>
      <template #baseCurrencySlot="{ row }">
        <el-tag v-if="row.isBaseCurrency === 1" type="primary" size="small">{{ $tt("是") }}</el-tag>
        <span v-else>-</span>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
const tableRef = ref(null)
useAutoRefresh(() => tableRef.value?.loadData?.())
import CrudTable from '@/components/CrudTable.vue'
import { listCurrency, saveCurrency, updateCurrency, deleteCurrency, batchDeleteCurrency } from '@/api/basedata/currency'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'currencyCode', label: '币种编码', minWidth: 120 },
  { prop: 'currencyName', label: '币种名称', minWidth: 120 },
  { prop: 'currencySymbol', label: '符号', minWidth: 100 },
  { prop: 'exchangeRate', label: '汇率', minWidth: 120 },
  { prop: 'isBaseCurrency', label: '本位币', minWidth: 100, slot: 'baseCurrencySlot' },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
]

async function fetchData(params) {
  const res = await listCurrency(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateCurrency(data)
  else await saveCurrency(data)
}
async function onDelete(row) {
  await deleteCurrency(row.currencyId)
}
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteCurrency(rows.map(r => r.currencyId))
}
</script>
