<template>
  <BaseEdit
    :title="$tt('币种')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    id-field="currencyId"
    list-route="/basedata/currency"
    custom-field-entity="currency"
    :show-change-record="true"
    change-record-bill-type="币种"
    change-record-bill-field="currencyCode"
    auto-doc-type="BD_CURRENCY"
    auto-doc-code-field="currencyCode"
    page-key="currency"
  />
</template>

<script setup>
import BaseEdit from '@/components/BaseEdit.vue'
import { getCurrency, saveCurrency, updateCurrency } from '@/api/basedata/currency'

const sections = [
  {
    title: '基本信息',
    subtitle: '币种的标识',
    fields: [
      { prop: 'currencyCode', label: '币种编码', placeholder: '如 CNY' },
      { prop: 'currencyName', label: '币种名称', required: true, placeholder: '如 人民币' },
      { prop: 'currencySymbol', label: '币种符号', placeholder: '如 ¥' }
    ]
  },
  {
    title: '汇率与状态',
    subtitle: '汇率和启用状态',
    fields: [
      { prop: 'exchangeRate', label: '汇率', type: 'number', precision: 8, default: 1 },
      { prop: 'isBaseCurrency', label: '是否基本币种', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 },
      { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
        { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

async function fetchData(id) {
  return await getCurrency(id)
}
async function onSave(data, isEdit) {
  if (isEdit) await updateCurrency(data)
  else await saveCurrency(data)
}
</script>
