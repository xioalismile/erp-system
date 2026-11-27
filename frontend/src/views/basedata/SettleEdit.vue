<template>
  <BaseEdit
    :title="$tt('结算方式')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    id-field="settleModeId"
    list-route="/basedata/settle"
    custom-field-entity="settle"
    :show-change-record="true"
    change-record-bill-type="结算方式"
    change-record-bill-field="settleModeCode"
    page-key="settlement_mode"
  />
</template>

<script setup>
import BaseEdit from '@/components/BaseEdit.vue'
import { getSettle, saveSettle, updateSettle } from '@/api/basedata/settle'

const sections = [
  {
    title: '基本信息',
    subtitle: '结算方式的标识和类型',
    fields: [
      { prop: 'settleModeCode', label: '结算编码', required: true, placeholder: '如 CASH' },
      { prop: 'settleModeName', label: '结算名称', required: true, placeholder: '如 现金' },
      { prop: 'settleType', label: '结算类型', type: 'select', required: true, default: '通用', options: [
        { value: '通用', label: '通用' },
        { value: '收款', label: '收款' },
        { value: '付款', label: '付款' }
      ] }
    ]
  },
  {
    title: '排序与状态',
    subtitle: '排序和启用状态',
    fields: [
      { prop: 'sortOrder', label: '排序', type: 'number', default: 0 },
      { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
        { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

async function fetchData(id) {
  return await getSettle(id)
}
async function onSave(data, isEdit) {
  if (isEdit) await updateSettle(data)
  else await saveSettle(data)
}
</script>
