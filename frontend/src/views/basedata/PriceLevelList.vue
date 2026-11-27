<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['print']"
      :title="$tt('价格等级')"
      custom-field-entity="price_level"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/price-level/new"
      edit-route="/basedata/price-level/edit"
      id-field="priceLevelId"
      perm-add="basedata:price-level:add"
      perm-edit="basedata:price-level:edit"
      perm-delete="basedata:price-level:delete"
      entity-name="priceLevel"
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
import { listPriceLevel, savePriceLevel, updatePriceLevel, deletePriceLevel, batchDeletePriceLevel } from '@/api/basedata/price-level'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'priceLevelCode', label: '价格等级编码', minWidth: 140 },
  { prop: 'priceLevelName', label: '价格等级名称', minWidth: 140 },
  { prop: 'priceAdjustRate', label: '调整率%', minWidth: 100 },
  { prop: 'sortOrder', label: '排序', minWidth: 100 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
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
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeletePriceLevel(rows.map(r => r.priceLevelId))
}
</script>
