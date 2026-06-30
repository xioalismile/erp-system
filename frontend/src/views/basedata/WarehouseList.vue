<template>
  <div class="erp-page">
    <CrudTable
      title="仓库"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="stockId"
      perm-add="basedata:warehouse:add"
      perm-edit="basedata:warehouse:edit"
      perm-delete="basedata:warehouse:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
      <template #binControlSlot="{ row }">
        <el-tag v-if="row.isBinControl === 1" type="primary" size="small">是</el-tag>
        <span v-else>-</span>
      </template>
      <template #mrpSlot="{ row }">
        <el-tag v-if="row.isMrpAvailable === 1" type="primary" size="small">是</el-tag>
        <span v-else>-</span>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listWarehouse, saveWarehouse, updateWarehouse, deleteWarehouse } from '@/api/basedata/warehouse'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'stockCode', label: '仓库编码', minWidth: 120 },
  { prop: 'stockName', label: '仓库名称', minWidth: 140 },
  { prop: 'stockType', label: '仓库类型', minWidth: 120 },
  { prop: 'isBinControl', label: '货位管理', minWidth: 100, slot: 'binControlSlot' },
  { prop: 'isMrpAvailable', label: 'MRP可用', minWidth: 100, slot: 'mrpSlot' },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'stockCode', label: '仓库编码', required: true, placeholder: '如 WH01' },
  { prop: 'stockName', label: '仓库名称', required: true, placeholder: '如 原材料仓' },
  { prop: 'stockType', label: '仓库类型', type: 'select', required: true, default: '普通仓库', options: [
    { value: '原材料仓', label: '原材料仓' },
    { value: '成品仓', label: '成品仓' },
    { value: '半成品仓', label: '半成品仓' },
    { value: '普通仓库', label: '普通仓库' },
    { value: '废品仓', label: '废品仓' },
    { value: '辅助材料仓', label: '辅助材料仓' }
  ] },
  { prop: 'address', label: '仓库地址', textarea: true, rows: 2, placeholder: '请输入仓库地址' },
  { prop: 'managerId', label: '管理员ID', type: 'number', placeholder: '管理员ID' },
  { prop: 'isBinControl', label: '货位管理', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 },
  { prop: 'isMrpAvailable', label: 'MRP可用', type: 'switch', activeValue: 1, inactiveValue: 0, default: 1 },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
    { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
  ] }
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
</script>
