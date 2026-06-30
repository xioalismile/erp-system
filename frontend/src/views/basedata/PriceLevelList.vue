<template>
  <div class="erp-page">
    <CrudTable
      title="价格等级"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="priceLevelId"
      perm-add="basedata:price-level:add"
      perm-edit="basedata:price-level:edit"
      perm-delete="basedata:price-level:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listPriceLevel, savePriceLevel, updatePriceLevel, deletePriceLevel } from '@/api/basedata/price-level'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'priceLevelCode', label: '价格等级编码', minWidth: 140 },
  { prop: 'priceLevelName', label: '价格等级名称', minWidth: 140 },
  { prop: 'priceAdjustRate', label: '调整率%', minWidth: 100 },
  { prop: 'sortOrder', label: '排序', minWidth: 100 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'priceLevelCode', label: '价格等级编码', required: true, placeholder: '如 LV01' },
  { prop: 'priceLevelName', label: '价格等级名称', required: true, placeholder: '如 批发价' },
  { prop: 'priceAdjustRate', label: '调整率%', type: 'number', precision: 4, default: 0 },
  { prop: 'sortOrder', label: '排序', type: 'number', default: 0 },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
    { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
  ] }
]

async function fetchData(params) {
  const res = await listPriceLevel(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updatePriceLevel(data)
  else await savePriceLevel(data)
}
async function onDelete(row) {
  await deletePriceLevel(row.priceLevelId)
}
</script>
