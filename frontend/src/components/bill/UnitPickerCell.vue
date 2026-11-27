<template>
  <div class="unit-picker-cell">
    <el-select
      :model-value="modelValue"
      :placeholder="$tt('单位')"
      filterable
      clearable
      size="small"
      style="width:100%"
      :disabled="disabled"
      @update:model-value="onUnitChange"
    >
      <el-option v-for="u in options" :key="u.unitId" :label="u.unitName" :value="u.unitId" />
    </el-select>

    <!-- 只读带出基础资料维护的换算率，单据不提供改写入口 -->
    <div v-if="showRate" class="unit-rate-text">
      1 {{ unitName }} = {{ rateText }} {{ baseUnitName }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { rateInfoOf, resolveUnitRate, unitOptionsFor } from '@/composables/useUnitConvert'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  units: { type: Array, default: () => [] },
  material: { type: Object, default: null },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'change'])

const options = computed(() => unitOptionsFor(props.material, props.units))
const unitName = computed(() => {
  const u = (props.units || []).find(x => x.unitId === props.modelValue)
  return u ? u.unitName : ''
})
const baseUnitName = computed(() => {
  const id = props.material ? props.material.baseUnitId : null
  const u = (props.units || []).find(x => x.unitId === id)
  return u ? u.unitName : ''
})
const rate = computed(() => resolveUnitRate(props.material, props.modelValue, props.units))
const rateText = computed(() => {
  const n = Number(rate.value || 0)
  return Number.isInteger(n) ? String(n) : String(Number(n.toFixed(8)))
})
const showRate = computed(() => {
  if (!props.material || !props.modelValue || props.material.baseUnitId === props.modelValue) return false
  // 浮动行且档案换算率为空/0（本来就不固定）：不显示「1 X = N 基本单位」，避免编出一个假率（2026-11-11）
  const info = rateInfoOf(props.material, props.modelValue, props.units)
  if (info.mode === '浮动' && !(info.row && Number(info.row.conversionRate) > 0)) return false
  return true
})

function onUnitChange(v) {
  emit('update:modelValue', v)
  emit('change', { unitId: v, rate: resolveUnitRate(props.material, v, props.units) })
}
</script>

<style scoped>
.unit-picker-cell { width: 100%; }
.unit-rate-text { font-size: 11px; color: #909399; margin-top: 2px; line-height: 1.3; }
</style>
