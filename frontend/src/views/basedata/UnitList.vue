<template>
  <div class="erp-page">
    <CrudTable
      title="计量单位"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="unitId"
      perm-add="basedata:unit:add"
      perm-edit="basedata:unit:edit"
      perm-delete="basedata:unit:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
      <template #baseUnitSlot="{ row }">
        <el-tag v-if="row.isBaseUnit === 1" type="primary" size="small">基本单位</el-tag>
        <span v-else>-</span>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listUnit, saveUnit, updateUnit, deleteUnit } from '@/api/basedata/unit'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'unitCode', label: '单位编码', minWidth: 120 },
  { prop: 'unitName', label: '单位名称', minWidth: 120 },
  { prop: 'unitGroup', label: '单位组', minWidth: 100 },
  { prop: 'conversionRate', label: '换算率', minWidth: 120 },
  { prop: 'isBaseUnit', label: '基本单位', minWidth: 100, slot: 'baseUnitSlot' },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'unitCode', label: '单位编码', required: true, placeholder: '如 PCS' },
  { prop: 'unitName', label: '单位名称', required: true, placeholder: '如 件' },
  { prop: 'unitGroup', label: '单位组', placeholder: '如 个数/重量/长度' },
  { prop: 'conversionRate', label: '换算率', type: 'number', precision: 6, default: 1 },
  { prop: 'isBaseUnit', label: '是否基本单位', type: 'switch', activeValue: 1, inactiveValue: 0, default: 1 },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
    { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
  ] }
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
</script>
