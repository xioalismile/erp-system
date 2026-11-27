<template>
  <div class="erp-page">
    <CrudTable
      :hide-buttons="['print']"
      :title="$tt('部门')"
      custom-field-entity="department"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/department/new"
      edit-route="/basedata/department/edit"
      id-field="deptId"
      perm-add="basedata:department:add"
      perm-edit="basedata:department:edit"
      perm-delete="basedata:department:delete"
      entity-name="department"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, onMounted } from 'vue'
import CrudTable from '@/components/CrudTable.vue'
import { listDepartment, saveDepartment, updateDepartment, deleteDepartment, batchDeleteDepartment, treeDepartment } from '@/api/basedata/department'

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
useAutoRefresh(loadDeptTree)

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'deptCode', label: '部门编码', minWidth: 120 },
  { prop: 'deptName', label: '部门名称', minWidth: 140 },
  { prop: 'deptType', label: '部门类型', minWidth: 120 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
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
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteDepartment(rows.map(r => r.deptId))
}
</script>
