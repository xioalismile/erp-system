<template>
  <div class="erp-page">
    <CrudTable ref="tableRef"
      :hide-buttons="['print']"
      :title="$tt('银行账户')"
      custom-field-entity="bank_account"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :on-batch-delete="onBatchDelete"
      :search-fields="searchFields"
      add-route="/basedata/bank-account/new"
      edit-route="/basedata/bank-account/edit"
      id-field="accountId"
      perm-add="basedata:bank-account:add"
      perm-edit="basedata:bank-account:edit"
      perm-delete="basedata:bank-account:delete"
      entity-name="bankAccount"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ $dt(row.status) }}</el-tag>
      </template>
      <template #defaultSlot="{ row }">
        <el-tag v-if="row.isDefault === 1" type="primary" size="small">{{ $tt("默认") }}</el-tag>
        <span v-else>-</span>
      </template>
      <template #currencySlot="{ row }">
        {{ getCurrencyName(row.currencyId) }}
      </template>
      <template #balanceSlot="{ row }">
        <span class="balance-num">¥{{ Number(row.currentBalance || 0).toFixed(2) }}</span>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAutoRefresh } from '@/composables/useAutoRefresh'
const tableRef = ref(null)
useAutoRefresh(() => tableRef.value?.loadData?.())
import CrudTable from '@/components/CrudTable.vue'
import { listBankAccount, saveBankAccount, updateBankAccount, deleteBankAccount, batchDeleteBankAccount } from '@/api/basedata/bankAccount'
import { listAllCurrency } from '@/api/basedata/currency'

// 币种名称映射：列表接口只返回币种主键，这里加载币种档案做 id→名称映射，避免表格里显示数字主键
const currencyOptions = ref([])
const getCurrencyName = (currencyId) => {
  if (currencyId === null || currencyId === undefined || currencyId === '') return ''
  const hit = currencyOptions.value.find(c => c.currencyId === currencyId)
  return hit ? hit.currencyName : ''
}
async function loadCurrencyOptions() {
  try {
    const list = await listAllCurrency()
    currencyOptions.value = Array.isArray(list) ? list : (list?.records || [])
  } catch (e) { console.warn('加载币种失败', e) }
}
onMounted(loadCurrencyOptions)

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称/银行' }
]

const columns = [
  { prop: 'accountNo', label: '账户编码', minWidth: 110 },
  { prop: 'accountName', label: '账户名称', minWidth: 150 },
  { prop: 'bankName', label: '开户银行', minWidth: 150 },
  { prop: 'accountType', label: '账户类型', minWidth: 100 },
  { prop: 'currencyId', label: '币种', minWidth: 100, slot: 'currencySlot' },
  { prop: 'openingBalance', label: '期初余额', minWidth: 110, align: 'right' },
  { prop: 'currentBalance', label: '当前余额', minWidth: 110, align: 'right', slot: 'balanceSlot' },
  { prop: 'isDefault', label: '默认账户', width: 90, slot: 'defaultSlot' },
  { prop: 'status', label: '状态', width: 90, slot: 'statusSlot' },
  { prop: 'remark', label: '备注', minWidth: 150, showOverflowTooltip: true }
]

async function fetchData(params) {
  const res = await listBankAccount(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateBankAccount(data)
  else await saveBankAccount(data)
}
async function onDelete(row) {
  await deleteBankAccount(row.accountId)
}
/** 批量删除（2026-09-20 接入：CrudTable 一次请求批量删，替代原先逐条循环） */
async function onBatchDelete(rows) {
  await batchDeleteBankAccount(rows.map(r => r.accountId))
}
</script>

<style scoped>
.balance-num {
  font-variant-numeric: tabular-nums;
}
</style>
