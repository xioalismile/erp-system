<template>
  <div class="erp-page">
    <CrudTable
      title="货位"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="binId"
      perm-add="basedata:location:add"
      perm-edit="basedata:location:edit"
      perm-delete="basedata:location:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listLocation, saveLocation, updateLocation, deleteLocation } from '@/api/basedata/location'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'binCode', label: '货位编码', minWidth: 140 },
  { prop: 'binName', label: '货位名称', minWidth: 140 },
  { prop: 'stockId', label: '仓库ID', minWidth: 100 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'binCode', label: '货位编码', required: true, placeholder: '如 A-01-01' },
  { prop: 'binName', label: '货位名称', required: true, placeholder: '如 A区1排1层' },
  { prop: 'stockId', label: '所属仓库ID', type: 'number', required: true, placeholder: '仓库ID' },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
    { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
  ] }
]

async function fetchData(params) {
  const res = await listLocation(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateLocation(data)
  else await saveLocation(data)
}
async function onDelete(row) {
  await deleteLocation(row.binId)
}
</script>
