<template>
  <div class="erp-page">
    <CrudTable
      title="部门"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="deptId"
      perm-add="basedata:department:add"
      perm-edit="basedata:department:edit"
      perm-delete="basedata:department:delete"
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
import { listDepartment, saveDepartment, updateDepartment, deleteDepartment, treeDepartment } from '@/api/basedata/department'

// 部门树数据（CrudTable表单暂不支持自定义slot，parentDeptId字段先用数字ID输入）
const deptTree = ref([])
async function loadDeptTree() {
  try {
    const res = await treeDepartment()
    deptTree.value = res || []
  } catch (e) {
    // 错误已在拦截器处理
  }
}
onMounted(loadDeptTree)

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'deptCode', label: '部门编码', minWidth: 120 },
  { prop: 'deptName', label: '部门名称', minWidth: 140 },
  { prop: 'deptType', label: '部门类型', minWidth: 120 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'deptCode', label: '部门编码', required: true, placeholder: '如 D001' },
  { prop: 'deptName', label: '部门名称', required: true, placeholder: '如 财务部' },
  // 注意：CrudTable暂不支持表单自定义slot，parentDeptId暂用数字ID输入（可空）；后续可扩展为el-tree-select
  { prop: 'parentDeptId', label: '上级部门ID', type: 'number', placeholder: '留空表示顶级部门', min: 0 },
  { prop: 'deptType', label: '部门类型', type: 'select', required: true, default: '职能部门', options: [
    { value: '职能部门', label: '职能部门' }, { value: '业务部门', label: '业务部门' }, { value: '决策部门', label: '决策部门' }
  ] },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
    { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
  ] }
]

async function fetchData(params) {
  const res = await listDepartment(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateDepartment(data)
  else await saveDepartment(data)
}
async function onDelete(row) {
  await deleteDepartment(row.deptId)
}
</script>
