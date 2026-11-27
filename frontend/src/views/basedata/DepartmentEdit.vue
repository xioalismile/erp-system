<template>
  <BaseEdit
    :title="$tt('部门档案')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    id-field="deptId"
    list-route="/basedata/department"
    :option-loaders="optionLoaders"
    custom-field-entity="department"
    :show-change-record="true"
    change-record-bill-type="部门"
    change-record-bill-field="deptCode"
    auto-doc-type="BD_DEPARTMENT"
    auto-doc-code-field="deptCode"
    page-key="department"
  />
</template>

<script setup>
import BaseEdit from '@/components/BaseEdit.vue'
import { getDepartment, saveDepartment, updateDepartment, listAllDepartment } from '@/api/basedata/department'

// 上级部门下拉数据加载器（部门列表，编辑时排除自身）
const optionLoaders = {
  parentDeptId: async () => {
    const list = await listAllDepartment()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    // 注意：编辑时自身的过滤在保存时校验，这里只提供完整列表
    return arr.map(d => ({ value: d.deptId, label: `${d.deptName} (${d.deptCode})` }))
  }
}

const sections = [
  {
    title: '基本信息',
    subtitle: '部门的标识和分类',
    fields: [
      { prop: 'deptCode', label: '部门编码', placeholder: '如 D001' },
      { prop: 'deptName', label: '部门名称', required: true, placeholder: '如 财务部' },
      { prop: 'parentDeptId', label: '上级部门', type: 'select', filterable: true, placeholder: '留空表示顶级部门' },
      { prop: 'deptType', label: '部门类型', type: 'select', required: true, default: '职能部门', options: [
        { value: '职能部门', label: '职能部门' },
        { value: '业务部门', label: '业务部门' },
        { value: '决策部门', label: '决策部门' }
      ] }
    ]
  },
  {
    title: '状态',
    subtitle: '启用状态',
    fields: [
      { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
        { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

async function fetchData(id) {
  return await getDepartment(id)
}
async function onSave(data, isEdit) {
  if (isEdit) await updateDepartment(data)
  else await saveDepartment(data)
}
</script>
