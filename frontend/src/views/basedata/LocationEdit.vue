<template>
  <BaseEdit
    :title="$tt('货位档案')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    :option-loaders="optionLoaders"
    id-field="binId"
    list-route="/basedata/location"
    custom-field-entity="location"
    :show-change-record="true"
    change-record-bill-type="库位"
    change-record-bill-field="binCode"
    page-key="location"
  />
</template>

<script setup>
import BaseEdit from '@/components/BaseEdit.vue'
import { ElMessage } from 'element-plus'
import { getLocation, saveLocation, updateLocation } from '@/api/basedata/location'
import { listAllWarehouse } from '@/api/basedata/warehouse'
import { tt } from '@/i18n'

const optionLoaders = {
  stockId: async () => {
    const list = await listAllWarehouse()
    const arr = Array.isArray(list) ? list : (list?.records || [])
    // 只显示开启了仓位管理（多货位）的仓库，避免选择无货位管理的仓库
    const multiBinList = arr.filter(w => w.isBinControl === 1 || w.isBinControl === '1' || w.isBinControl === true)
    if (multiBinList.length === 0) {
      ElMessage.warning('没有开启仓位管理的仓库，请先到仓库档案中勾选tt("仓位管理")')
    }
    return multiBinList.map(w => ({ value: w.stockId, label: `${w.stockCode} - ${w.stockName}` }))
  }
}

const sections = [
  {
    title: '基本信息',
    subtitle: '货位的标识和归属',
    fields: [
      { prop: 'binCode', label: '货位编码', required: true, placeholder: '如 A-01-01' },
      { prop: 'binName', label: '货位名称', required: true, placeholder: '如 A区1排1层' },
      { prop: 'stockId', label: '所属仓库', type: 'select', filterable: true, required: true, placeholder: '请选择所属仓库' }
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
  return await getLocation(id)
}
async function onSave(data, isEdit) {
  if (isEdit) await updateLocation(data)
  else await saveLocation(data)
}
</script>
