<template>
  <div class="erp-page">
    <CrudTable
      title="客户"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="customerId"
      perm-add="basedata:customer:add"
      perm-edit="basedata:customer:edit"
      perm-delete="basedata:customer:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '正常' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listCustomer, saveCustomer, updateCustomer, deleteCustomer } from '@/api/basedata/customer'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称/简称' }
]

const columns = [
  { prop: 'customerCode', label: '客户编码', minWidth: 140 },
  { prop: 'customerName', label: '客户名称', minWidth: 160 },
  { prop: 'shortName', label: '简称', minWidth: 120 },
  { prop: 'customerType', label: '客户类型', minWidth: 120 },
  { prop: 'customerLevel', label: '客户等级', minWidth: 120 },
  { prop: 'contactPerson', label: '联系人', minWidth: 120 },
  { prop: 'contactPhone', label: '联系电话', minWidth: 130 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'customerCode', label: '客户编码', required: true, placeholder: '如 C001' },
  { prop: 'customerName', label: '客户名称', required: true, placeholder: '如 XX科技有限公司' },
  { prop: 'shortName', label: '简称', placeholder: '客户简称' },
  { prop: 'customerType', label: '客户类型', type: 'select', default: '企业客户', options: [
    { value: '企业客户', label: '企业客户' },
    { value: '个人客户', label: '个人客户' }
  ] },
  { prop: 'customerLevel', label: '客户等级', type: 'select', default: '普通客户', options: [
    { value: '普通客户', label: '普通客户' },
    { value: 'VIP客户', label: 'VIP客户' },
    { value: '战略客户', label: '战略客户' }
  ] },
  { prop: 'taxNo', label: '税号', placeholder: '税号' },
  { prop: 'contactPerson', label: '联系人', placeholder: '联系人' },
  { prop: 'contactPhone', label: '联系电话', placeholder: '联系电话' },
  { prop: 'email', label: '邮箱', placeholder: '邮箱' },
  { prop: 'address', label: '地址', textarea: true, rows: 2, placeholder: '请输入地址' },
  { prop: 'bankName', label: '开户行', placeholder: '开户行' },
  { prop: 'bankAccount', label: '银行账号', placeholder: '银行账号' },
  { prop: 'creditLimit', label: '信用额度', type: 'number', precision: 2, default: 0 },
  { prop: 'paymentTermId', label: '付款条件ID', type: 'number', placeholder: '付款条件ID' },
  { prop: 'currencyId', label: '币种ID', type: 'number', placeholder: '币种ID' },
  { prop: 'defaultSalerId', label: '默认业务员ID', type: 'number', placeholder: '默认业务员ID' },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '正常', options: [
    { value: '正常', label: '正常' }, { value: '停用', label: '停用' }
  ] }
]

async function fetchData(params) {
  const res = await listCustomer(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateCustomer(data)
  else await saveCustomer(data)
}
async function onDelete(row) {
  await deleteCustomer(row.customerId)
}
</script>
