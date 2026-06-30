<template>
  <div class="erp-page">
    <CrudTable
      title="员工"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="employeeId"
      perm-add="basedata:employee:add"
      perm-edit="basedata:employee:edit"
      perm-delete="basedata:employee:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '在职' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listEmployee, saveEmployee, updateEmployee, deleteEmployee } from '@/api/basedata/employee'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'employeeCode', label: '员工编码', minWidth: 120 },
  { prop: 'employeeName', label: '员工姓名', minWidth: 120 },
  { prop: 'gender', label: '性别', minWidth: 80 },
  { prop: 'mobilePhone', label: '手机', minWidth: 130 },
  { prop: 'email', label: '邮箱', minWidth: 180 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'employeeCode', label: '员工编码', required: true, placeholder: '如 E001' },
  { prop: 'employeeName', label: '员工姓名', required: true, placeholder: '如 张三' },
  { prop: 'deptId', label: '所属部门ID', type: 'number', required: true, placeholder: '部门ID' },
  { prop: 'position', label: '职位', placeholder: '如 经理' },
  { prop: 'gender', label: '性别', type: 'select', default: '男', options: [
    { value: '男', label: '男' }, { value: '女', label: '女' }, { value: '未知', label: '未知' }
  ] },
  { prop: 'mobilePhone', label: '手机', placeholder: '请输入手机号' },
  { prop: 'email', label: '邮箱', placeholder: '请输入邮箱' },
  { prop: 'entryDate', label: '入职日期', type: 'date' },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '在职', options: [
    { value: '在职', label: '在职' }, { value: '离职', label: '离职' }
  ] }
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
</script>
