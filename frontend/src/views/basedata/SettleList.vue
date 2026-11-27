<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['print']"
      :title="$tt('结算方式')"
      custom-field-entity="settle"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/settle/new"
      edit-route="/basedata/settle/edit"
      id-field="settleModeId"
      perm-add="basedata:settle:add"
      perm-edit="basedata:settle:edit"
      perm-delete="basedata:settle:delete"
      entity-name="settlementMode"
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
import { listSettle, saveSettle, updateSettle, deleteSettle, batchDeleteSettle } from '@/api/basedata/settle'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'settleModeCode', label: '结算编码', minWidth: 120 },
  { prop: 'settleModeName', label: '结算名称', minWidth: 120 },
  { prop: 'settleType', label: '结算类型', minWidth: 120 },
  { prop: 'sortOrder', label: '排序', minWidth: 100 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
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
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteSettle(rows.map(r => r.settleModeId))
}
</script>
