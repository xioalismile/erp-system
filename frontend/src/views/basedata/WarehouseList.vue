<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['print']"
      :title="$tt('仓库')"
      custom-field-entity="warehouse"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/warehouse/new"
      edit-route="/basedata/warehouse/edit"
      id-field="stockId"
      perm-add="basedata:warehouse:add"
      perm-edit="basedata:warehouse:edit"
      perm-delete="basedata:warehouse:delete"
      entity-name="warehouse"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
      </template>
      <template #binControlSlot="{ row }">
        <el-tag v-if="row.isBinControl === 1" type="primary" size="small">{{ $tt("是") }}</el-tag>
        <span v-else>-</span>
      </template>
      <template #mrpSlot="{ row }">
        <el-tag v-if="row.isMrpAvailable === 1" type="primary" size="small">{{ $tt("是") }}</el-tag>
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
import { listWarehouse, saveWarehouse, updateWarehouse, deleteWarehouse, batchDeleteWarehouse } from '@/api/basedata/warehouse'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'stockCode', label: '仓库编码', minWidth: 120 },
  { prop: 'stockName', label: '仓库名称', minWidth: 140 },
  { prop: 'stockType', label: '仓库类型', minWidth: 120 },
  { prop: 'isBinControl', label: '货位管理', minWidth: 100, slot: 'binControlSlot' },
  { prop: 'isMrpAvailable', label: 'MRP可用', minWidth: 100, slot: 'mrpSlot' },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
]

async function fetchData(params) {
  const res = await listWarehouse(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateWarehouse(data)
  else await saveWarehouse(data)
}
async function onDelete(row) {
  await deleteWarehouse(row.stockId)
}
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteWarehouse(rows.map(r => r.stockId))
}
</script>
