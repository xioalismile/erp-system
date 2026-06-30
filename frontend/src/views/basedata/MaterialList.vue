<template>
  <div class="erp-page">
    <CrudTable
      title="物料"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="materialId"
      perm-add="basedata:material:add"
      perm-edit="basedata:material:edit"
      perm-delete="basedata:material:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '正常' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listMaterial, saveMaterial, updateMaterial, deleteMaterial } from '@/api/basedata/material'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称/全称' }
]

const columns = [
  { prop: 'materialCode', label: '物料编码', minWidth: 140 },
  { prop: 'materialName', label: '物料名称', minWidth: 160 },
  { prop: 'model', label: '规格型号', minWidth: 140 },
  { prop: 'materialType', label: '物料类型', minWidth: 120 },
  { prop: 'salePrice', label: '销售单价', minWidth: 120 },
  { prop: 'purchasePrice', label: '采购单价', minWidth: 120 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'materialCode', label: '物料编码', required: true, placeholder: '如 M001' },
  { prop: 'materialName', label: '物料名称', required: true, placeholder: '如 螺丝' },
  { prop: 'fullName', label: '全称', placeholder: '物料全称' },
  { prop: 'model', label: '规格型号', placeholder: '如 M8*20' },
  { prop: 'materialType', label: '物料类型', type: 'select', default: '自制件', options: [
    { value: '原材料', label: '原材料' },
    { value: '半成品', label: '半成品' },
    { value: '成品', label: '成品' },
    { value: '包材', label: '包材' },
    { value: '辅助材料', label: '辅助材料' },
    { value: '自制件', label: '自制件' }
  ] },
  { prop: 'categoryId', label: '分类ID', type: 'number', placeholder: '物料分类ID' },
  { prop: 'baseUnitId', label: '基本单位ID', type: 'number', required: true, placeholder: '基本单位ID' },
  { prop: 'isLotControl', label: '批次管理', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 },
  { prop: 'isSnControl', label: '序列号管理', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 },
  { prop: 'isBinControl', label: '货位管理', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 },
  { prop: 'isQcRequired', label: '质检', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 },
  { prop: 'salePrice', label: '销售单价', type: 'number', precision: 4, default: 0 },
  { prop: 'purchasePrice', label: '采购单价', type: 'number', precision: 4, default: 0 },
  { prop: 'taxRate', label: '税率', type: 'number', precision: 4, default: 13 },
  { prop: 'minStock', label: '最低库存', type: 'number', precision: 4, default: 0 },
  { prop: 'maxStock', label: '最高库存', type: 'number', precision: 4, default: 0 },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '正常', options: [
    { value: '正常', label: '正常' }, { value: '停用', label: '停用' }
  ] }
]

async function fetchData(params) {
  const res = await listMaterial(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateMaterial(data)
  else await saveMaterial(data)
}
async function onDelete(row) {
  await deleteMaterial(row.materialId)
}
</script>
