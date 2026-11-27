<template>
  <div class="kpi" :class="[gradeClass, { clickable }]" @click="onClick">
    <div class="kpi-label">{{ label }}</div>
    <div class="kpi-value">
      <span class="num">{{ text }}</span>
      <span v-if="unit" class="unit">{{ unit }}</span>
    </div>
    <div v-if="hint" class="kpi-hint">{{ hint }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { fmtMoney, fmtInt, fmtPercent, toNum } from './useCockpit'

const props = defineProps({
  label: { type: String, default: '' },
  value: { type: [Number, String], default: null },
  unit: { type: String, default: '' },
  money: { type: Boolean, default: false },
  percent: { type: Boolean, default: false },
  int: { type: Boolean, default: false },
  digits: { type: Number, default: 0 },
  grade: { type: String, default: 'normal' },
  hint: { type: String, default: '' },
  clickable: { type: Boolean, default: false }
})
const emit = defineEmits(['click'])

const text = computed(() => {
  if (props.money) return fmtMoney(props.value)
  if (props.percent) return fmtPercent(props.value)
  if (props.int) return fmtInt(props.value)
  const n = toNum(props.value)
  if (n === null) return '--'
  return props.digits > 0 ? n.toFixed(props.digits) : String(n)
})

const gradeClass = computed(() => 'grade-' + props.grade)

function onClick() {
  if (props.clickable) emit('click')
}
</script>

<style scoped>
.kpi {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 16px;
  min-height: 92px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: box-shadow .18s, border-color .18s;
}
.kpi.clickable { cursor: pointer; }
.kpi.clickable:hover { border-color: #409eff; box-shadow: 0 2px 12px rgba(64, 158, 255, .16); }
.kpi-label { font-size: 13px; color: #909399; }
.kpi-value { display: flex; align-items: baseline; gap: 4px; }
.num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.15;
  color: #303133;
  font-variant-numeric: tabular-nums;
  letter-spacing: -.5px;
}
.unit { font-size: 12px; color: #909399; }
.kpi-hint { font-size: 12px; color: #c0c4cc; }
.grade-warn .num { color: #e6a23c; }
.grade-bad .num { color: #f56c6c; }
</style>
