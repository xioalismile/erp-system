<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['print']"
      :title="$tt('货位')"
      custom-field-entity="location"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/location/new"
      edit-route="/basedata/location/edit"
      id-field="binId"
      perm-add="basedata:location:add"
      perm-edit="basedata:location:edit"
      perm-delete="basedata:location:delete"
      entity-name="location"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
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
import { listLocation, saveLocation, updateLocation, deleteLocation, batchDeleteLocation } from '@/api/basedata/location'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'binCode', label: '货位编码', minWidth: 140 },
  { prop: 'binName', label: '货位名称', minWidth: 140 },
  { prop: 'stockId', label: '仓库ID', minWidth: 100 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
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
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteLocation(rows.map(r => r.binId))
}
</script>
