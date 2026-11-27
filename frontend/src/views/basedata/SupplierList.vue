<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['print']"
      :title="$tt('供应商')"
      custom-field-entity="supplier"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/supplier/new"
      edit-route="/basedata/supplier/edit"
      id-field="supplierId"
      perm-add="basedata:supplier:add"
      perm-edit="basedata:supplier:edit"
      perm-delete="basedata:supplier:delete"
      entity-name="supplier"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '正常' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
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
import { listSupplier, saveSupplier, updateSupplier, deleteSupplier, batchDeleteSupplier } from '@/api/basedata/supplier'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称/简称' }
]

const columns = [
  { prop: 'supplierCode', label: '供应商编码', minWidth: 140 },
  { prop: 'supplierName', label: '供应商名称', minWidth: 160 },
  { prop: 'shortName', label: '简称', minWidth: 120 },
  { prop: 'supplierType', label: '供应商类型', minWidth: 130 },
  { prop: 'supplierLevel', label: '供应商等级', minWidth: 130 },
  { prop: 'contactPerson', label: '联系人', minWidth: 120 },
  { prop: 'contactPhone', label: '联系电话', minWidth: 130 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
]

async function fetchData(params) {
  const res = await listSupplier(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateSupplier(data)
  else await saveSupplier(data)
}
async function onDelete(row) {
  await deleteSupplier(row.supplierId)
}
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteSupplier(rows.map(r => r.supplierId))
}
</script>
