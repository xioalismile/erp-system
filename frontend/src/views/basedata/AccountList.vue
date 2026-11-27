<template>
  <div class="erp-page">
    <!-- 会计科目不接入自定义字段：原 custom-field-entity="account" 已按用户要求移除（HTML 属性无法注释，故直接删），勿恢复 -->
    <CrudTable
      :hide-buttons="['print']"
      :title="$tt('会计科目')"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/account/new"
      edit-route="/basedata/account/edit"
      id-field="accountId"
      perm-add="basedata:account:add"
      perm-edit="basedata:account:edit"
      perm-delete="basedata:account:delete"
      entity-name="account"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
      </template>
      <template #leafSlot="{ row }">
        <el-tag v-if="row.isLeaf === 1" type="primary" size="small">{{ $tt("是") }}</el-tag>
        <span v-else>-</span>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import { useAutoRefresh } from '@/composables/useAutoRefresh'
import { ref, onMounted } from 'vue'
import CrudTable from '@/components/CrudTable.vue'
import { listAccount, saveAccount, updateAccount, deleteAccount, batchDeleteAccount, treeAccount } from '@/api/basedata/account'

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
useAutoRefresh(loadAccountTree)

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
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
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
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteAccount(rows.map(r => r.accountId))
}
</script>
