<template>
  <div class="bc-card">
    <div class="bc-head">
      <span class="bc-icon" :style="{ background: card.color || '#2563eb' }" v-html="iconSvg"></span>
      <span class="bc-title" :title="card.title">{{ card.title || $tt("未命名卡片") }}</span>
      <slot name="extra"></slot>
    </div>

    <div v-if="error" class="bc-error">{{ error }}</div>

    <div
      v-else-if="card.type === 'metric'"
      class="bc-metric"
      :class="{ 'bc-clickable': drillable }"
      @click="drillFromMetric"
    >
      <div class="bc-metric-value" :style="{ color: card.color || '#2563eb' }">
        {{ metricText }}<span v-if="unit" class="bc-unit">{{ unit }}</span>
        <span v-if="drillable && hasValue" class="bc-more">›</span>
      </div>
      <div class="bc-metric-foot">{{ metricFoot }}</div>
    </div>

    <div v-else-if="card.type === 'table'" class="bc-table-wrap">
      <table class="bc-table">
        <thead>
          <tr><th v-for="c in columns" :key="c.field">{{ c.label }}</th></tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in rows" :key="i">
            <td v-for="c in columns" :key="c.field">{{ cellText(r[c.field], c.type) }}</td>
          </tr>
          <tr v-if="!rows.length"><td :colspan="columns.length || 1" class="bc-empty-cell">{{ $tt("暂无数据") }}</td></tr>
        </tbody>
      </table>
    </div>

    <div v-else class="bc-chart" :class="{ 'bc-clickable': drillable }" :ref="setChartEl"></div>

    <BoardDrillDialog
      v-model="drillVisible"
      :card="card"
      :period="drillPeriod"
      :dim-label="drillDimLabel"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import echarts from '@/utils/echartsCore'
import BoardDrillDialog from '@/components/board/BoardDrillDialog.vue'

const props = defineProps({
  card: { type: Object, required: true },
  data: { type: Object, default: () => ({}) },
  error: { type: String, default: '' },
  /** 是否允许点卡片下钻（设计器预览时关闭） */
  drillable: { type: Boolean, default: true }
})

const ICONS = {
  target: '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4"/></svg>',
  wave: '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><path d="M2 12c3-6 6-6 9 0s6 6 11-2"/></svg>',
  cmp: '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><path d="M5 20V10"/><path d="M12 20V4"/><path d="M19 20v-7"/></svg>',
  pie: '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><path d="M12 3a9 9 0 1 0 9 9h-9z"/></svg>',
  table: '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 10h18M9 4v16"/></svg>',
  alert: '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><path d="M12 3 2 20h20z"/><path d="M12 10v4"/></svg>',
  gear: '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><circle cx="12" cy="12" r="3.2"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3"/></svg>',
  star: '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><path d="m12 3 2.7 5.8 6.3.8-4.6 4.3 1.2 6.1L12 17l-5.6 3 1.2-6.1L3 9.6l6.3-.8z"/></svg>'
}
const iconSvg = computed(() => ICONS[props.card.icon] || ICONS.target)
const unit = computed(() => props.card.unit || '')

const metricText = computed(() => {
  const v = props.data ? props.data.value : null
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return '—'
  const digits = props.card.decimals === undefined || props.card.decimals === null ? 2 : Number(props.card.decimals)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
})
const metricFoot = computed(() => {
  const d = props.data || {}
  if (d.error) return d.error
  if (d.formulaError) return d.formulaError
  return d.formula || ''
})

const columns = computed(() => props.data?.columns || [])
const rows = computed(() => props.data?.rows || [])

// ==================== 下钻 ====================
const drillVisible = ref(false)
const drillDimLabel = ref('')
const drillPeriod = computed(() => props.data?.period || 'month')
const hasValue = computed(() => {
  const v = props.data ? props.data.value : null
  return v !== null && v !== undefined && v !== ''
})

function openDrill(label) {
  if (!props.drillable || props.error) return
  drillDimLabel.value = label || ''
  drillVisible.value = true
}
function drillFromMetric() {
  if (!hasValue.value) return
  openDrill('')
}
function onChartClick(p) {
  if (p && p.name) openDrill(String(p.name))
}

function cellText(v, type) {
  if (v === null || v === undefined || v === '') return ''
  if (type === 'amount' || type === 'number') {
    const n = Number(v)
    if (!Number.isNaN(n)) return n.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
  }
  if (type === 'date' || type === 'datetime') return String(v).replace('T', ' ').slice(0, type === 'date' ? 10 : 19)
  if (type === 'bool') return v === true || v === 'true' || v === 1 ? '是' : '否'
  return String(v)
}

// ==================== 图表 ====================
let chart = null
const chartEl = ref(null)

function setChartEl(el) {
  chartEl.value = el
}

function renderChart() {
  if (!chartEl.value) return
  if (props.card.type === 'metric' || props.card.type === 'table') return
  if (!chart) chart = echarts.init(chartEl.value)
  chart.off('click')
  chart.on('click', onChartClick)
  const points = props.data?.points || []
  const previous = props.data?.previous || []
  const style = props.card.style || ''
  if (!points.length) {
    chart.clear()
    chart.setOption({ title: { text: '暂无数据', left: 'center', top: 'middle', textStyle: { color: '#94a3b8', fontSize: 12, fontWeight: 'normal' } } })
    return
  }
  const labels = points.map(p => p.label)
  const values = points.map(p => (p.value === null ? null : Number(p.value)))

  if (props.card.type === 'ana') {
    const ring = style === 'ring'
    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}<br/>{c}（{d}%）' },
      legend: { type: 'scroll', orient: 'vertical', right: 6, top: 'middle', textStyle: { fontSize: 11, color: '#64748b' } },
      series: [
        {
          type: 'pie',
          radius: ring ? ['42%', '68%'] : '68%',
          center: ['36%', '50%'],
          data: points.map(p => ({ name: p.label, value: p.value })),
          label: { show: !ring, fontSize: 10, color: '#64748b' },
          itemStyle: { borderColor: '#fff', borderWidth: 1.5 }
        }
      ]
    }, true)
    return
  }

  if (props.card.type === 'compare') {
    const horizontal = style === 'hbar'
    const series = [
      { name: '本期', type: 'bar', data: values, barMaxWidth: 22, itemStyle: { color: props.card.color || '#2563eb', borderRadius: 3 } }
    ]
    if (previous.length) {
      const prevMap = new Map(previous.map(p => [p.label, p.value]))
      series.push({
        name: '上期',
        type: 'bar',
        data: labels.map(l => (prevMap.has(l) ? prevMap.get(l) : null)),
        barMaxWidth: 22,
        itemStyle: { color: '#cbd5e1', borderRadius: 3 }
      })
    }
    chart.setOption({
      grid: { left: horizontal ? 90 : 46, right: 16, top: previous.length ? 30 : 16, bottom: horizontal ? 20 : 30, containLabel: false },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: previous.length ? { top: 0, textStyle: { fontSize: 11, color: '#64748b' } } : { show: false },
      xAxis: horizontal
        ? { type: 'value', axisLabel: { fontSize: 10, color: '#94a3b8' }, splitLine: { lineStyle: { color: '#eef2f7' } } }
        : { type: 'category', data: labels, axisLabel: { fontSize: 10, color: '#94a3b8', hideOverlap: true } },
      yAxis: horizontal
        ? { type: 'category', data: labels, axisLabel: { fontSize: 10, color: '#94a3b8' } }
        : { type: 'value', axisLabel: { fontSize: 10, color: '#94a3b8' }, splitLine: { lineStyle: { color: '#eef2f7' } } },
      series
    }, true)
    return
  }

  // wave：平滑面积趋势
  chart.setOption({
    grid: { left: 46, right: 16, top: 16, bottom: 30 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: labels, boundaryGap: false, axisLabel: { fontSize: 10, color: '#94a3b8', hideOverlap: true } },
    yAxis: { type: 'value', scale: true, axisLabel: { fontSize: 10, color: '#94a3b8' }, splitLine: { lineStyle: { color: '#eef2f7' } } },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 4,
        data: values,
        lineStyle: { color: props.card.color || '#0891b2', width: 2 },
        itemStyle: { color: props.card.color || '#0891b2' },
        areaStyle: style === 'line' ? undefined : { opacity: 0.18, color: props.card.color || '#0891b2' }
      }
    ]
  }, true)
}

function resize() {
  if (chart && !chart.isDisposed()) chart.resize()
}

watch(() => props.data, () => nextTick(renderChart), { deep: true })
watch(() => props.card, () => nextTick(renderChart), { deep: true })

onMounted(() => {
  nextTick(renderChart)
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  if (chart && !chart.isDisposed()) chart.dispose()
  chart = null
})
</script>

<style scoped>
.bc-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 132px;
  box-sizing: border-box;
}
.bc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.bc-icon {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.bc-icon :deep(svg) {
  width: 13px;
  height: 13px;
}
.bc-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bc-metric {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.bc-metric-value {
  font-size: 27px;
  font-weight: 700;
  line-height: 1.2;
}
.bc-unit {
  font-size: 13px;
  margin-left: 3px;
}
.bc-metric-foot {
  font-size: 11.5px;
  color: #94a3b8;
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bc-clickable {
  cursor: pointer;
}
.bc-more {
  font-size: 16px;
  color: #cbd5e1;
  margin-left: 4px;
  vertical-align: 2px;
}
.bc-clickable:hover .bc-more {
  color: #2563eb;
}
.bc-chart {
  flex: 1;
  width: 100%;
  min-height: 160px;
}
.bc-table-wrap {
  flex: 1;
  overflow: auto;
}
.bc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.bc-table th {
  text-align: left;
  color: #64748b;
  font-weight: 500;
  padding: 4px 8px;
  border-bottom: 1px solid #e2e8f0;
  white-space: nowrap;
  position: sticky;
  top: 0;
  background: #fff;
}
.bc-table td {
  padding: 4px 8px;
  border-bottom: 1px dashed #f1f5f9;
  color: #1e293b;
  white-space: nowrap;
}
.bc-empty-cell {
  text-align: center;
  color: #94a3b8;
  padding: 14px 0;
}
.bc-error {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12.5px;
  color: #b45309;
  background: #fffbeb;
  border: 1px dashed #fde68a;
  border-radius: 8px;
  padding: 10px;
  text-align: center;
}
</style>
