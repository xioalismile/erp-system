<template>
  <!-- 会计科目不接入自定义字段：原 custom-field-entity="account" 已按用户要求移除（HTML 属性无法注释，故直接删），勿恢复 -->
  <BaseEdit
    :title="$tt('会计科目')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    :option-loaders="optionLoaders"
    id-field="accountId"
    list-route="/basedata/account"
    :show-change-record="true"
    change-record-bill-type="账户"
    change-record-bill-field="accountCode"
    page-key="account"
  />
</template>

<script setup>
import BaseEdit from '@/components/BaseEdit.vue'
import { getAccount, saveAccount, updateAccount, treeAccount } from '@/api/basedata/account'

const optionLoaders = {
  parentAccountId: async () => {
    const list = await treeAccount()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    return arr.map(a => ({ value: a.accountId, label: `${a.accountCode} - ${a.accountName}` }))
  }
}

const sections = [
  {
    title: '基本信息',
    subtitle: '科目的标识和层级',
    fields: [
      { prop: 'accountCode', label: '科目编码', required: true, placeholder: '如 1001' },
      { prop: 'accountName', label: '科目名称', required: true, placeholder: '如 库存现金' },
      { prop: 'accountFullName', label: '科目全称', placeholder: '科目全称' },
      { prop: 'parentAccountId', label: '上级科目', type: 'select', filterable: true, placeholder: '留空表示顶级科目' }
    ]
  },
  {
    title: '类型与方向',
    subtitle: '科目的类型和余额方向',
    fields: [
      { prop: 'accountType', label: '科目类型', type: 'select', required: true, options: [
        { value: '资产', label: '资产' },
        { value: '负债', label: '负债' },
        { value: '权益', label: '权益' },
        { value: '成本', label: '成本' },
        { value: '损益', label: '损益' }
      ] },
      { prop: 'balanceDirection', label: '余额方向', type: 'select', required: true, default: '借', options: [
        { value: '借', label: '借' },
        { value: '贷', label: '贷' }
      ] },
      { prop: 'accountLevel', label: '科目层级', type: 'number', default: 1 }
    ]
  },
  {
    title: '控制选项',
    subtitle: '科目的管理控制',
    fields: [
      { prop: 'isLeaf', label: '是否末级科目', type: 'switch', activeValue: 1, inactiveValue: 0, default: 1 },
      { prop: 'isCashAccount', label: '是否现金科目', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 },
      { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
        { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

async function fetchData(id) {
  return await getAccount(id)
}
async function onSave(data, isEdit) {
  if (isEdit) await updateAccount(data)
  else await saveAccount(data)
}
</script>
