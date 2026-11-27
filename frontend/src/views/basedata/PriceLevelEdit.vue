<template>
  <BaseEdit
    :title="$tt('价格等级')"
    :sections="sections"
    :fetch-data="fetchData"
    :on-save="onSave"
    id-field="priceLevelId"
    list-route="/basedata/price-level"
    custom-field-entity="price_level"
    :show-change-record="true"
    change-record-bill-type="价格等级"
    change-record-bill-field="priceLevelCode"
    :disabled-fields="disabledFields"
    :on-create-init="onCreateInit"
    page-key="price_level"
  />
</template>

<script setup>
import { computed } from 'vue'
import BaseEdit from '@/components/BaseEdit.vue'
import { getPriceLevel, savePriceLevel, updatePriceLevel } from '@/api/basedata/price-level'
import { useAutoDocCode } from '@/composables/useAutoDocCode'

const { docCodeDisabled, autoFillDocCodeOnCreate } = useAutoDocCode('BD_PRICE_LEVEL')

const sections = [
  {
    title: '基本信息',
    subtitle: '价格等级的标识',
    fields: [
      { prop: 'priceLevelCode', label: '等级编码', placeholder: '留空自动生成，如 LV01' },
      { prop: 'priceLevelName', label: '等级名称', required: true, placeholder: '如 批发价' }
    ]
  },
  {
    title: '调整与状态',
    subtitle: '价格调整和启用状态',
    fields: [
      { prop: 'priceAdjustRate', label: '价格调整率', type: 'number', precision: 4, default: 0 },
      { prop: 'sortOrder', label: '排序', type: 'number', default: 0 },
      { prop: 'status', label: '状态', type: 'select', required: true, default: '启用', options: [
        { value: '启用', label: '启用' }, { value: '停用', label: '停用' }
      ] },
      { prop: 'remark', label: '备注', type: 'textarea', rows: 2, placeholder: '请输入备注' }
    ]
  }
]

const disabledFields = computed(() => ({
  priceLevelCode: docCodeDisabled.value
}))

async function onCreateInit(form) {
  await autoFillDocCodeOnCreate(form, 'priceLevelCode')
}

async function fetchData(id) {
  return await getPriceLevel(id)
}
async function onSave(data, isEdit) {
  if (isEdit) await updatePriceLevel(data)
  else await savePriceLevel(data)
}
</script>
