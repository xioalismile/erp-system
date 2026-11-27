<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['print']"
      :title="$tt('员工')"
      custom-field-entity="employee"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/employee/new"
      edit-route="/basedata/employee/edit"
      id-field="employeeId"
      perm-add="basedata:employee:add"
      perm-edit="basedata:employee:edit"
      perm-delete="basedata:employee:delete"
      entity-name="employee"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '在职' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
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
import { listEmployee, saveEmployee, updateEmployee, deleteEmployee, batchDeleteEmployee } from '@/api/basedata/employee'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'employeeCode', label: '员工编码', minWidth: 120 },
  { prop: 'employeeName', label: '员工姓名', minWidth: 120 },
  { prop: 'deptName', label: '部门', minWidth: 120 },
  { prop: 'position', label: '岗位', minWidth: 120 },
  // 第5期：工时费率（工序计时工资取数来源）
  { prop: 'hourlyRate', label: '工时费率(元/小时)', minWidth: 140, align: 'right' },
  { prop: 'gender', label: '性别', minWidth: 80 },
  { prop: 'mobilePhone', label: '手机', minWidth: 130 },
  { prop: 'email', label: '邮箱', minWidth: 180 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
]

async function fetchData(params) {
  const res = await listEmployee(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateEmployee(data)
  else await saveEmployee(data)
}
async function onDelete(row) {
  await deleteEmployee(row.employeeId)
}
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteEmployee(rows.map(r => r.employeeId))
}
</script>
