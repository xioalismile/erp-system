<template>
  <BaseEdit
    :title="$tt('仓库档案')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    :option-loaders="optionLoaders"
    id-field="stockId"
    list-route="/basedata/warehouse"
    custom-field-entity="warehouse"
    :show-change-record="true"
    change-record-bill-type="仓库"
    change-record-bill-field="stockCode"
    auto-doc-type="BD_WAREHOUSE"
    auto-doc-code-field="stockCode"
    page-key="warehouse"
  />
</template>

<script setup>
import BaseEdit from '@/components/BaseEdit.vue'
import { getWarehouse, saveWarehouse, updateWarehouse } from '@/api/basedata/warehouse'
import { listAllEmployee } from '@/api/basedata/employee'

const optionLoaders = {
  managerId: async () => {
    const list = await listAllEmployee()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    return arr.map(e => ({ value: e.employeeId, label: `${e.employeeCode} - ${e.employeeName}` }))
  }
}

const sections = [
  {
    title: '基本信息',
    subtitle: '仓库的标识和类型',
    fields: [
      { prop: 'stockCode', label: '仓库编码', placeholder: '如 WH01' },
      { prop: 'stockName', label: '仓库名称', required: true, placeholder: '如 原材料仓' },
      { prop: 'stockType', label: '仓库类型', type: 'select', required: true, default: '普通仓库', options: [
        { value: '原材料仓', label: '原材料仓' },
        { value: '成品仓', label: '成品仓' },
        { value: '半成品仓', label: '半成品仓' },
        { value: '普通仓库', label: '普通仓库' },
        { value: '废品仓', label: '废品仓' },
        { value: '辅助材料仓', label: '辅助材料仓' }
      ] }
    ]
  },
  {
    title: '地址与管理',
    subtitle: '仓库位置和管理员',
    fields: [
      { prop: 'address', label: '仓库地址', type: 'textarea', rows: 2, placeholder: '仓库地址' },
      { prop: 'managerId', label: '管理员', type: 'select', filterable: true, placeholder: '请选择管理员' }
    ]
  },
  {
    title: '控制选项',
    subtitle: '仓库的管理控制',
    fields: [
      { prop: 'isBinControl', label: '货位管理', type: 'switch', activeValue: 1, inactiveValue: 0, default: 0 },
      { prop: 'isMrpAvailable', label: '参与MRP', type: 'switch', activeValue: 1, inactiveValue: 0, default: 1 },
      { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
        { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

async function fetchData(id) {
  return await getWarehouse(id)
}
async function onSave(data, isEdit) {
  if (isEdit) await updateWarehouse(data)
  else await saveWarehouse(data)
}
</script>
