<template>
  <div class="erp-page">
    <CrudTable
      title="会计科目"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="accountId"
      perm-add="basedata:account:add"
      perm-edit="basedata:account:edit"
      perm-delete="basedata:account:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
      <template #leafSlot="{ row }">
        <el-tag v-if="row.isLeaf === 1" type="primary" size="small">是</el-tag>
        <span v-else>-</span>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import CrudTable from '@/components/CrudTable.vue'
import { listAccount, saveAccount, updateAccount, deleteAccount, treeAccount } from '@/api/basedata/account'

// 科目树数据（CrudTable表单暂不支持自定义slot，parentAccountId字段先用数字ID输入）
const accountTree = ref([])
async function loadAccountTree() {
  try {
    const res = await treeAccount()
    accountTree.value = res || []
  } catch (e) {
    // 错误已在拦截器处理
  }
}
onMounted(loadAccountTree)

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'accountCode', label: '科目编码', minWidth: 120 },
  { prop: 'accountName', label: '科目名称', minWidth: 140 },
  { prop: 'accountType', label: '类型', minWidth: 100 },
  { prop: 'balanceDirection', label: '余额方向', minWidth: 100 },
  { prop: 'accountLevel', label: '层级', minWidth: 80 },
  { prop: 'isLeaf', label: '末级', minWidth: 80, slot: 'leafSlot' },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'accountCode', label: '科目编码', required: true, placeholder: '如 1001' },
  { prop: 'accountName', label: '科目名称', required: true, placeholder: '如 库存现金' },
  { prop: 'accountFullName', label: '科目全称', placeholder: '如 资产-库存现金' },
  // 注意：CrudTable暂不支持表单自定义slot，parentAccountId暂用数字ID输入（可空）；后续可扩展为el-tree-select
  { prop: 'parentAccountId', label: '上级科目ID', type: 'number', placeholder: '留空表示顶级科目', min: 0 },
  { prop: 'accountType', label: '科目类型', type: 'select', required: true, options: [
    { value: '资产', label: '资产' },
    { value: '负债', label: '负债' },
    { value: '权益', label: '权益' },
    { value: '成本', label: '成本' },
    { value: '损益', label: '损益' }
  ] },
  { prop: 'balanceDirection', label: '余额方向', type: 'select', required: true, default: '借', options: [
    { value: '借', label: '借' }, { value: '贷', label: '贷' }
  ] },
  { prop: 'accountLevel', label: '层级', type: 'number', default: 1 },
  { prop: 'isLeaf', label: '是否末级', type: 'switch', activeValue: 1, inactiveValue: 0, default: 1 },
  { prop: 'isCashAccount', label: '现金科目', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
    { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
  ] }
]

async function fetchData(params) {
  const res = await listAccount(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateAccount(data)
  else await saveAccount(data)
}
async function onDelete(row) {
  await deleteAccount(row.accountId)
}
</script>
