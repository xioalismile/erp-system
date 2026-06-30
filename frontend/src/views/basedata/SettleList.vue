<template>
  <div class="erp-page">
    <CrudTable
      title="结算方式"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="settleModeId"
      perm-add="basedata:settle:add"
      perm-edit="basedata:settle:edit"
      perm-delete="basedata:settle:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listSettle, saveSettle, updateSettle, deleteSettle } from '@/api/basedata/settle'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'settleModeCode', label: '结算编码', minWidth: 120 },
  { prop: 'settleModeName', label: '结算名称', minWidth: 120 },
  { prop: 'settleType', label: '结算类型', minWidth: 120 },
  { prop: 'sortOrder', label: '排序', minWidth: 100 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'settleModeCode', label: '结算编码', required: true, placeholder: '如 CASH' },
  { prop: 'settleModeName', label: '结算名称', required: true, placeholder: '如 现金' },
  { prop: 'settleType', label: '结算类型', type: 'select', required: true, default: '通用', options: [
    { value: '通用', label: '通用' }, { value: '收款', label: '收款' }, { value: '付款', label: '付款' }
  ] },
  { prop: 'sortOrder', label: '排序', type: 'number', default: 0 },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
    { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
  ] }
]

async function fetchData(params) {
  const res = await listSettle(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateSettle(data)
  else await saveSettle(data)
}
async function onDelete(row) {
  await deleteSettle(row.settleModeId)
}
</script>
