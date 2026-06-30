<template>
  <div class="erp-page">
    <CrudTable
      title="币种"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="currencyId"
      perm-add="basedata:currency:add"
      perm-edit="basedata:currency:edit"
      perm-delete="basedata:currency:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
      <template #baseCurrencySlot="{ row }">
        <el-tag v-if="row.isBaseCurrency === 1" type="primary" size="small">是</el-tag>
        <span v-else>-</span>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listCurrency, saveCurrency, updateCurrency, deleteCurrency } from '@/api/basedata/currency'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'currencyCode', label: '币种编码', minWidth: 120 },
  { prop: 'currencyName', label: '币种名称', minWidth: 120 },
  { prop: 'currencySymbol', label: '符号', minWidth: 100 },
  { prop: 'exchangeRate', label: '汇率', minWidth: 120 },
  { prop: 'isBaseCurrency', label: '本位币', minWidth: 100, slot: 'baseCurrencySlot' },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'currencyCode', label: '币种编码', required: true, placeholder: '如 CNY' },
  { prop: 'currencyName', label: '币种名称', required: true, placeholder: '如 人民币' },
  { prop: 'currencySymbol', label: '符号', placeholder: '如 ¥' },
  { prop: 'exchangeRate', label: '汇率', type: 'number', precision: 8, default: 1 },
  { prop: 'isBaseCurrency', label: '是否本位币', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
    { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
  ] }
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
</script>
