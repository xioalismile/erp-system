<template>
  <div class="erp-page">
    <CrudTable
      title="付款条件"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="paymentTermId"
      perm-add="basedata:payment-term:add"
      perm-edit="basedata:payment-term:edit"
      perm-delete="basedata:payment-term:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listPaymentTerm, savePaymentTerm, updatePaymentTerm, deletePaymentTerm } from '@/api/basedata/payment-term'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称' }
]

const columns = [
  { prop: 'paymentTermCode', label: '付款条件编码', minWidth: 140 },
  { prop: 'paymentTermName', label: '付款条件名称', minWidth: 140 },
  { prop: 'paymentDays', label: '账期天数', minWidth: 100 },
  { prop: 'discountDays', label: '折扣天数', minWidth: 100 },
  { prop: 'discountRate', label: '折扣率%', minWidth: 100 },
  { prop: 'advanceRate', label: '预付比例%', minWidth: 110 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'paymentTermCode', label: '付款条件编码', required: true, placeholder: '如 NET30' },
  { prop: 'paymentTermName', label: '付款条件名称', required: true, placeholder: '如 30天净额' },
  { prop: 'paymentDays', label: '账期天数', type: 'number', required: true, default: 0 },
  { prop: 'discountDays', label: '折扣天数', type: 'number', default: 0 },
  { prop: 'discountRate', label: '折扣率%', type: 'number', precision: 4, default: 0 },
  { prop: 'advanceRate', label: '预付比例%', type: 'number', precision: 4, default: 0 },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
    { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
  ] }
]

async function fetchData(params) {
  const res = await listPaymentTerm(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updatePaymentTerm(data)
  else await savePaymentTerm(data)
}
async function onDelete(row) {
  await deletePaymentTerm(row.paymentTermId)
}
</script>
