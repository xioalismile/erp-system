<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['print']"
      :title="$tt('计量单位')"
      custom-field-entity="unit"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/unit/new"
      edit-route="/basedata/unit/edit"
      id-field="unitId"
      perm-add="basedata:unit:add"
      perm-edit="basedata:unit:edit"
      perm-delete="basedata:unit:delete"
      entity-name="unit"
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
import { listUnit, saveUnit, updateUnit, deleteUnit, batchDeleteUnit } from '@/api/basedata/unit'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'unitCode', label: '单位编码', minWidth: 120 },
  { prop: 'unitName', label: '单位名称', minWidth: 120 },
  { prop: 'unitGroup', label: '单位组', minWidth: 100 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
]

async function fetchData(params) {
  const res = await listUnit(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateUnit(data)
  else await saveUnit(data)
}
async function onDelete(row) {
  await deleteUnit(row.unitId)
}
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteUnit(rows.map(r => r.unitId))
}
</script>
