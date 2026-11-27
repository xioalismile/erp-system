<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['print']"
      :title="$tt('客户')"
      custom-field-entity="customer"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      :preset-search="presetSearch"
      add-route="/basedata/customer/new"
      edit-route="/basedata/customer/edit"
      id-field="customerId"
      perm-add="basedata:customer:add"
      perm-edit="basedata:customer:edit"
      perm-delete="basedata:customer:delete"
      entity-name="customer"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '正常' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
const tableRef = ref(null)
useAutoRefresh(() => tableRef.value?.loadData?.())

// 数据看板穿透：区域 → 按自定义字段 region 预置检索；新客户 → 按建档时间区间（无 query 时为空对象，行为不变）
const route = useRoute()
const presetSearch = computed(() => {
  const q = route.query || {}
  const s = {}
  if (q.region) s.cf_region = String(q.region)
  if (q.createdFrom) s.createdFrom = String(q.createdFrom)
  if (q.createdTo) s.createdTo = String(q.createdTo)
  return s
})
import CrudTable from '@/components/CrudTable.vue'
import { listCustomer, saveCustomer, updateCustomer, deleteCustomer, batchDeleteCustomer } from '@/api/basedata/customer'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称/简称' }
]

const columns = [
  { prop: 'customerCode', label: '客户编码', minWidth: 140 },
  { prop: 'customerName', label: '客户名称', minWidth: 160 },
  { prop: 'shortName', label: '简称', minWidth: 120 },
  { prop: 'customerType', label: '客户类型', minWidth: 120 },
  { prop: 'customerLevel', label: '客户等级', minWidth: 120 },
  { prop: 'contactPerson', label: '联系人', minWidth: 120 },
  { prop: 'contactPhone', label: '联系电话', minWidth: 130 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
]

async function fetchData(params) {
  const res = await listCustomer(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateCustomer(data)
  else await saveCustomer(data)
}
async function onDelete(row) {
  await deleteCustomer(row.customerId)
}
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteCustomer(rows.map(r => r.customerId))
}
</script>
