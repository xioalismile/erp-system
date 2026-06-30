<template>
  <div class="erp-page">
    <CrudTable
      title="供应商"
      :columns="columns"
      :fetch-data="fetchData"
      :on-save="onSave"
      :on-delete="onDelete"
      :search-fields="searchFields"
      :form-fields="formFields"
      id-field="supplierId"
      perm-add="basedata:supplier:add"
      perm-edit="basedata:supplier:edit"
      perm-delete="basedata:supplier:delete"
    >
      <template #statusSlot="{ row }">
        <el-tag :type="row.status === '正常' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
      </template>
    </CrudTable>
  </div>
</template>

<script setup>
import CrudTable from '@/components/CrudTable.vue'
import { listSupplier, saveSupplier, updateSupplier, deleteSupplier } from '@/api/basedata/supplier'

const searchFields = [
  { prop: 'keyword', label: '关键字', placeholder: '编码/名称/简称' }
]

const columns = [
  { prop: 'supplierCode', label: '供应商编码', minWidth: 140 },
  { prop: 'supplierName', label: '供应商名称', minWidth: 160 },
  { prop: 'shortName', label: '简称', minWidth: 120 },
  { prop: 'supplierType', label: '供应商类型', minWidth: 130 },
  { prop: 'supplierLevel', label: '供应商等级', minWidth: 130 },
  { prop: 'contactPerson', label: '联系人', minWidth: 120 },
  { prop: 'contactPhone', label: '联系电话', minWidth: 130 },
  { prop: 'status', label: '状态', width: 100, slot: 'statusSlot' }
]

const formFields = [
  { prop: 'supplierCode', label: '供应商编码', required: true, placeholder: '如 S001' },
  { prop: 'supplierName', label: '供应商名称', required: true, placeholder: '如 XX材料有限公司' },
  { prop: 'shortName', label: '简称', placeholder: '供应商简称' },
  { prop: 'supplierType', label: '供应商类型', type: 'select', default: '一般供应商', options: [
    { value: '一般供应商', label: '一般供应商' },
    { value: '战略供应商', label: '战略供应商' }
  ] },
  { prop: 'supplierLevel', label: '供应商等级', type: 'select', default: 'C级', options: [
    { value: 'A级', label: 'A级' },
    { value: 'B级', label: 'B级' },
    { value: 'C级', label: 'C级' }
  ] },
  { prop: 'taxNo', label: '税号', placeholder: '税号' },
  { prop: 'contactPerson', label: '联系人', placeholder: '联系人' },
  { prop: 'contactPhone', label: '联系电话', placeholder: '联系电话' },
  { prop: 'email', label: '邮箱', placeholder: '邮箱' },
  { prop: 'address', label: '地址', textarea: true, rows: 2, placeholder: '请输入地址' },
  { prop: 'bankName', label: '开户行', placeholder: '开户行' },
  { prop: 'bankAccount', label: '银行账号', placeholder: '银行账号' },
  { prop: 'paymentTermId', label: '付款条件ID', type: 'number', placeholder: '付款条件ID' },
  { prop: 'currencyId', label: '币种ID', type: 'number', placeholder: '币种ID' },
  { prop: 'defaultBuyerId', label: '默认采购员ID', type: 'number', placeholder: '默认采购员ID' },
  { prop: 'deliveryDays', label: '交货天数', type: 'number', default: 0 },
  { prop: 'status', label: '状态', type: 'select', required: true, default: '正常', options: [
    { value: '正常', label: '正常' }, { value: '停用', label: '停用' }
  ] }
]

async function fetchData(params) {
  const res = await listSupplier(params)
  return { records: res.records, total: res.total }
}
async function onSave(data, isEdit) {
  if (isEdit) await updateSupplier(data)
  else await saveSupplier(data)
}
async function onDelete(row) {
  await deleteSupplier(row.supplierId)
}
</script>
