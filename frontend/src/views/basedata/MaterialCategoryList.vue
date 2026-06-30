<template>
  <div class="erp-page">
    <CrudTable
      title="物料分类"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="categoryId"
      perm-add="basedata:material-category:add"
      perm-edit="basedata:material-category:edit"
      perm-delete="basedata:material-category:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import CrudTable from '@/components/CrudTable.vue'
import { listMaterialCategory, saveMaterialCategory, updateMaterialCategory, deleteMaterialCategory, treeMaterialCategory } from '@/api/basedata/material-category'

// 物料分类树数据（CrudTable表单暂不支持自定义slot，parentId字段先用数字ID输入）
const categoryTree = ref([])
async function loadCategoryTree() {
  try {
    const res = await treeMaterialCategory()
    categoryTree.value = res || []
  } catch (e) {
    // 错误已在拦截器处理
  }
}
onMounted(loadCategoryTree)

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'categoryCode', label: '分类编码', minWidth: 140 },
  { prop: 'categoryName', label: '分类名称', minWidth: 140 },
  { prop: 'categoryLevel', label: '层级', minWidth: 80 },
  { prop: 'sortOrder', label: '排序', minWidth: 100 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'categoryCode', label: '分类编码', required: true, placeholder: '如 1001' },
  { prop: 'categoryName', label: '分类名称', required: true, placeholder: '如 原材料' },
  // 注意：CrudTable暂不支持表单自定义slot，parentId暂用数字ID输入（可空）；后续可扩展为el-tree-select
  { prop: 'parentId', label: '上级分类ID', type: 'number', placeholder: '留空表示顶级分类', min: 0 },
  { prop: 'categoryLevel', label: '层级', type: 'number', default: 1 },
  { prop: 'sortOrder', label: '排序', type: 'number', default: 0 },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
    { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
  ] }
]

async function fetchData(params) {
  const res = await listMaterialCategory(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateMaterialCategory(data)
  else await saveMaterialCategory(data)
}
async function onDelete(row) {
  await deleteMaterialCategory(row.categoryId)
}
</script>
