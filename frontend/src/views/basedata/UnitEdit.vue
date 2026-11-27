<template>
  <BaseEdit
    :title="$tt('计量单位')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    id-field="unitId"
    list-route="/basedata/unit"
    custom-field-entity="unit"
    :show-change-record="true"
    change-record-bill-type="计量单位"
    change-record-bill-field="unitCode"
    auto-doc-type="BD_UNIT"
    auto-doc-code-field="unitCode"
    page-key="unit_of_measure"
  />
</template>

<script setup>
import BaseEdit from '@/components/BaseEdit.vue'
import { getUnit, saveUnit, updateUnit } from '@/api/basedata/unit'

const sections = [
  {
    title: '基本信息',
    subtitle: '单位的标识和分类',
    fields: [
      { prop: 'unitCode', label: '单位编码', placeholder: '如 PCS' },
      { prop: 'unitName', label: '单位名称', required: true, placeholder: '如 件' },
      { prop: 'unitGroup', label: '单位组', placeholder: '如 个数/重量/长度' }
    ]
  },
  {
    title: '换算与状态',
    subtitle: '启用状态与备注',
    fields: [
      { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
        { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

async function fetchData(id) {
  return await getUnit(id)
}
async function onSave(data, isEdit) {
  if (isEdit) await updateUnit(data)
  else await saveUnit(data)
}
</script>
